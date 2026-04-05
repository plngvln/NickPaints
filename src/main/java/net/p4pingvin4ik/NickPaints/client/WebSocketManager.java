package net.p4pingvin4ik.NickPaints.client;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.exceptions.AuthenticationException;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.session.Session;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.p4pingvin4ik.NickPaints.config.ConfigManager;
import org.slf4j.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class WebSocketManager implements WebSocket.Listener {

    private static final int MAX_LOGGED_RECONNECT_ATTEMPTS = 5;
    private static int reconnectAttempts = 0;

    private static final HttpClient client = HttpClient.newHttpClient();
    private static final Logger LOGGER = NickPaintsMod.LOGGER;
    private static final Gson gson = new Gson();

    public static final Map<UUID, String> paintCache = new ConcurrentHashMap<>();
    private static final Set<UUID> uuidQueue = ConcurrentHashMap.newKeySet();

    private static volatile WebSocket webSocket;
    private static volatile boolean isAuthenticated = false;
    private static volatile boolean isConnecting = false;
    private static volatile boolean manuallyDisconnected = false;
    private static volatile boolean authenticationPermanentlyFailed = false;

    private static ScheduledExecutorService scheduler;
    private static ScheduledFuture<?> queueProcessorTask;

    private static final WebSocketManager INSTANCE = new WebSocketManager();

    private static final Set<UUID> lastVisiblePlayers = new CopyOnWriteArraySet<>();
    private static int tickCounter = 0;

    private static final Object reconnectLock = new Object();

    public static void connect() {
        manuallyDisconnected = false;
        authenticationPermanentlyFailed = false;
        if (isAuthenticated || isConnecting || MinecraftClient.getInstance().getSession().getUuidOrNull() == null) {
            return;
        }
        isConnecting = true;
        try {
            if (reconnectAttempts == 0) {
                LOGGER.info("Connecting to NickPaints server...");
            }
            client.newWebSocketBuilder()
                    .header("X-API-Key", ConfigManager.CONFIG.apiKey)
                    .buildAsync(URI.create(ConfigManager.CONFIG.baseUrl), INSTANCE)
                    .exceptionally(e -> {
                        isConnecting = false;
                        if (reconnectAttempts <= MAX_LOGGED_RECONNECT_ATTEMPTS) {
                            LOGGER.error("WebSocket connection failed to build: {}", e.getMessage());
                        }
                        handleDisconnection();
                        return null;
                    });
        } catch (Exception e) {
            isConnecting = false;
            if (reconnectAttempts <= MAX_LOGGED_RECONNECT_ATTEMPTS) {
                LOGGER.error("Failed to initiate WebSocket connection", e);
            }
            handleDisconnection();
        }
    }

    public static void disconnect() {
        manuallyDisconnected = true;
        isAuthenticated = false;
        isConnecting = false;
        reconnectAttempts = 0;
        if (webSocket != null && !webSocket.isOutputClosed()) {
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Client shutting down");
            webSocket = null;
        }
        stopQueueProcessor();
        LOGGER.info("WebSocket disconnected manually.");
    }

    @Override
    public void onOpen(WebSocket ws) {
        WebSocketManager.webSocket = ws;
        isConnecting = false;
        LOGGER.info("WebSocket connection opened, awaiting authentication challenge...");
        ws.request(1);
    }

    @Override
    public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
        try {
            String raw = data != null ? data.toString() : "";
            if (raw.trim().isEmpty()) {
                LOGGER.warn("Received empty WebSocket message");
                ws.request(1);
                return null;
            }
            JsonElement parsedElement = JsonParser.parseString(raw);
            if (!parsedElement.isJsonObject()) {
                LOGGER.warn("Received non-JSON-object message: {}", data);
                ws.request(1);
                return null;
            }

            JsonObject message = parsedElement.getAsJsonObject();
            if (!message.has("type") || !message.has("payload")) {
                LOGGER.warn("Received message with missing 'type' or 'payload': {}", data);
                ws.request(1);
                return null;
            }

            String type = message.get("type").getAsString();
            JsonElement payloadElement = message.get("payload");

            if (isAuthenticated) {
                switch (type) {
                    case "paintUpdate":
                        handlePaintUpdate(payloadElement.getAsJsonObject());
                        break;
                    case "paintData":
                        handlePaintData(payloadElement.getAsJsonArray());
                        break;
                    case "playerLeft":
                        handlePlayerLeft(payloadElement.getAsJsonObject());
                        break;
                    default:
                        LOGGER.warn("Unknown authenticated message type: {}", type);
                        break;
                }
            } else {
                switch (type) {
                    case "auth_challenge":
                        handleAuthChallenge(ws, payloadElement.getAsJsonObject().get("authHash").getAsString());
                        break;
                    case "auth_success":
                        handleAuthSuccess(payloadElement.getAsJsonObject());
                        break;
                    case "auth_failure":
                        LOGGER.error("Authentication failed: {}", payloadElement.getAsJsonObject().get("error").getAsString());
                        authenticationPermanentlyFailed = true;
                        disconnect();
                        break;
                    default:
                        LOGGER.warn("Unknown pre-auth message type: {}", type);
                        break;
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to process WebSocket message: {}", data != null ? data.toString() : "(null)", e);
        }
        ws.request(1);
        return null;
    }
    public static void updateVisiblePlayers() {
        if (!isAuthenticated || webSocket == null || MinecraftClient.getInstance().world == null) {
            return;
        }

        tickCounter++;
        if (tickCounter < 100) {
            return;
        }
        tickCounter = 0;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return;
        }
        final UUID selfUuid = client.player.getUuid();
        Set<UUID> currentlyVisiblePlayers = client.world.getPlayers().stream()
                .filter(player -> !player.getUuid().equals(selfUuid))
                .map(player -> player.getUuid())
                .collect(Collectors.toSet());

        if (!lastVisiblePlayers.equals(currentlyVisiblePlayers)) {
            lastVisiblePlayers.clear();
            lastVisiblePlayers.addAll(currentlyVisiblePlayers);

            List<String> uuidsAsString = currentlyVisiblePlayers.stream()
                    .map(UUID::toString)
                    .collect(Collectors.toList());

            JsonObject payload = new JsonObject();
            payload.add("uuids", gson.toJsonTree(uuidsAsString));

            JsonObject message = new JsonObject();
            message.addProperty("type", "updateVisiblePlayers");
            message.add("payload", payload);

            webSocket.sendText(gson.toJson(message), true);
        }
    }
    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        if (isAuthenticated && reconnectAttempts <= MAX_LOGGED_RECONNECT_ATTEMPTS) {
            LOGGER.warn("WebSocket closed unexpectedly: {} - {}", statusCode, reason);
        }
        isAuthenticated = false;
        isConnecting = false;
        WebSocketManager.webSocket = null;
        handleDisconnection();
        return new CompletableFuture<>();
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        if (reconnectAttempts <= MAX_LOGGED_RECONNECT_ATTEMPTS) {
            LOGGER.error("WebSocket error occurred: {}", error.getMessage());
        }
        isAuthenticated = false;
        isConnecting = false;
        WebSocketManager.webSocket = null;
        handleDisconnection();
    }

    private void handleAuthChallenge(WebSocket ws, String authHash) {
        MinecraftClient client = MinecraftClient.getInstance();
        Session session = client.getSession();

        JsonObject verifyPayload = new JsonObject();
        verifyPayload.addProperty("paint", ConfigManager.CONFIG.currentGradient);

        JsonObject verifyMessage = new JsonObject();

        if (client.isInSingleplayer()) {
            LOGGER.info("In singleplayer mode, using token authentication...");
            verifyMessage.addProperty("type", "auth_verify_token");
            verifyPayload.addProperty("uuid", session.getUuidOrNull().toString());
            verifyPayload.addProperty("accessToken", session.getAccessToken());
        } else {
            LOGGER.info("In multiplayer mode, using session authentication...");
            try {
                GameProfile gameProfile = new GameProfile(session.getUuidOrNull(), session.getUsername());
                client.getSessionService().joinServer(gameProfile.getId(), session.getAccessToken(), authHash);

                verifyMessage.addProperty("type", "auth_verify_session");
                verifyPayload.addProperty("username", session.getUsername());
            } catch (AuthenticationException e) {
                LOGGER.error("Session authentication failed. This can happen with a cracked client or invalid session. Disabling auto-reconnect.");
                authenticationPermanentlyFailed = true;
                disconnect();
                return;
            }
        }

        verifyMessage.add("payload", verifyPayload);
        ws.sendText(gson.toJson(verifyMessage), true);
    }

    private void handleAuthSuccess(JsonObject payload) {
        isAuthenticated = true;
        String username = payload.get("username").getAsString();
        if (reconnectAttempts > 0) {
            LOGGER.info("Successfully authenticated with server after {} attempt(s). Welcome, {}!", reconnectAttempts, username);
        } else {
            LOGGER.info("Authentication successful. Welcome, {}!", username);
        }
        reconnectAttempts = 0;
        startQueueProcessor();
    }

    private void handlePaintUpdate(JsonObject payload) {
        UUID uuid = UUID.fromString(payload.get("uuid").getAsString());
        String paint = payload.has("paint") ? payload.get("paint").getAsString() : "";
        paintCache.put(uuid, paint);
    }

    private void handlePaintData(JsonArray paints) {
        for (JsonElement element : paints) {
            JsonObject paintObj = element.getAsJsonObject();
            UUID uuid = UUID.fromString(paintObj.get("uuid").getAsString());
            String paint = paintObj.has("paint") ? paintObj.get("paint").getAsString() : "";
            paintCache.put(uuid, paint);
        }
    }

    private void handlePlayerLeft(JsonObject payload) {
        UUID uuid = UUID.fromString(payload.get("uuid").getAsString());
        paintCache.remove(uuid);
    }

    private static void handleDisconnection() {
        stopQueueProcessor();
        if (isAuthenticated || isConnecting) {
            isAuthenticated = false;
            return;
        }
        scheduleReconnect();
    }

    private static void scheduleReconnect() {
        final long delaySeconds = 10;
        synchronized (reconnectLock) {
            if (isConnecting || manuallyDisconnected || authenticationPermanentlyFailed) {
                return;
            }
            isConnecting = true;
            reconnectAttempts++;
            if (reconnectAttempts <= MAX_LOGGED_RECONNECT_ATTEMPTS) {
                LOGGER.warn("Connection lost. Reconnecting...");
            } else if (reconnectAttempts == MAX_LOGGED_RECONNECT_ATTEMPTS + 1) {
                LOGGER.warn("Max logged reconnection attempts reached. Further attempts will be silent.");
            }
        }
        CompletableFuture.delayedExecutor(delaySeconds, TimeUnit.SECONDS).execute(() -> {
            synchronized (reconnectLock) {
                isConnecting = false;
            }
            connect();
        });
    }

    private static void startQueueProcessor() {
        if (scheduler == null || scheduler.isShutdown()) {
            final ThreadFactory threadFactory = new ThreadFactoryBuilder()
                    .setNameFormat("NickPaints-Queue-Processor-%d")
                    .setDaemon(true)
                    .build();

            scheduler = Executors.newSingleThreadScheduledExecutor(threadFactory);

            queueProcessorTask = scheduler.scheduleAtFixedRate(WebSocketManager::processQueue, 2, 2, TimeUnit.SECONDS);
            LOGGER.info("Queue processor started.");
        }
    }

    private static void stopQueueProcessor() {
        if (queueProcessorTask != null && !queueProcessorTask.isDone()) {
            queueProcessorTask.cancel(false);
            queueProcessorTask = null;
        }
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            scheduler = null;
        }
    }

    public static void syncMyPaint() {
        if (!isAuthenticated) {
            sendMessageToPlayer(Text.translatable("chat.nickpaints.sync.failure", "Not authenticated").formatted(Formatting.RED));
            return;
        }
        sendPaintUpdateInternal();
        sendMessageToPlayer(Text.translatable("chat.nickpaints.sync.success").formatted(Formatting.GREEN));
    }

    public static void syncMyPaintSilently(UUID myUuid) {
        if (myUuid == null || !isAuthenticated) {
            return;
        }
        sendPaintUpdateInternal();
    }

    private static void sendPaintUpdateInternal() {
        if (!isAuthenticated || webSocket == null) {
            return;
        }
        String myPaint = ConfigManager.CONFIG.currentGradient;

        JsonObject payload = new JsonObject();
        payload.addProperty("paint", myPaint);

        JsonObject message = new JsonObject();
        message.addProperty("type", "updatePaint");
        message.add("payload", payload);

        webSocket.sendText(gson.toJson(message), true);
    }

    public static void queuePaintForPlayer(UUID playerUuid) {
        if (playerUuid != null && !paintCache.containsKey(playerUuid)) {
            uuidQueue.add(playerUuid);
        }
    }

    public static void processQueue() {
        if (uuidQueue.isEmpty() || !isAuthenticated || webSocket == null) {
            return;
        }

        List<UUID> uuidsToProcess;
        synchronized (uuidQueue) {
            uuidsToProcess = new java.util.ArrayList<>(uuidQueue);
            uuidQueue.clear();
        }

        for (int i = 0; i < uuidsToProcess.size(); i += 30) {
            List<UUID> batchUuids = uuidsToProcess.subList(i, Math.min(i + 30, uuidsToProcess.size()));

            List<String> batchStrings = batchUuids.stream()
                    .map(UUID::toString)
                    .toList();

            JsonObject payload = new JsonObject();
            JsonArray uuidsAsJson = new JsonArray();
            batchStrings.forEach(uuidsAsJson::add);
            payload.add("uuids", uuidsAsJson);

            JsonObject message = new JsonObject();
            message.addProperty("type", "requestPaints");
            message.add("payload", payload);

            webSocket.sendText(gson.toJson(message), true);
        }
    }

    public static void clearCache() {
        paintCache.clear();
        uuidQueue.clear();
        LOGGER.info("All NickPaints caches have been cleared.");
    }

    private static void sendMessageToPlayer(Text message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.player != null) {
            client.player.sendMessage(message, false);
        }
    }
}