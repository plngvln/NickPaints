package net.p4pingvin4ik.NickPaints.client;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.exceptions.AuthenticationException;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.network.chat.Component;
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
import java.util.concurrent.atomic.AtomicLong;
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

    /** Monotonic id so stale onOpen/onClose/onError cannot wipe a newer session. */
    private static final AtomicLong CONNECTION_GENERATION = new AtomicLong(0);
    private static volatile long activeConnectionId = 0;
    private static volatile long openedConnectionId = 0;
    private static volatile boolean disconnectionHandled = false;

    private static ScheduledExecutorService scheduler;
    private static ScheduledFuture<?> queueProcessorTask;

    private static final WebSocketManager INSTANCE = new WebSocketManager();

    private final StringBuilder textFragmentBuffer = new StringBuilder();

    private static final Set<UUID> lastVisiblePlayers = new CopyOnWriteArraySet<>();
    private static int tickCounter = 0;

    private static final Object reconnectLock = new Object();

    private static final Object paintSyncAckLock = new Object();
    private static final ScheduledExecutorService paintSyncAckScheduler = Executors.newSingleThreadScheduledExecutor(
            new ThreadFactoryBuilder().setNameFormat("NickPaints-PaintSyncAck-%d").setDaemon(true).build());
    private static volatile boolean awaitingUserPaintSyncAck;
    private static ScheduledFuture<?> paintSyncAckTimeoutTask;
    /** When set, successful ack persists this gradient; failure/timeout rolls back to previous. */
    private static String pendingPersistGradient;
    private static String previousGradientBeforePersist;

    public static void connect() {
        manuallyDisconnected = false;
        authenticationPermanentlyFailed = false;
        if (isAuthenticated || isConnecting || Minecraft.getInstance().getUser().getProfileId() == null) {
            return;
        }
        isConnecting = true;
        final long connectionId = CONNECTION_GENERATION.incrementAndGet();
        activeConnectionId = connectionId;
        disconnectionHandled = false;
        try {
            if (reconnectAttempts == 0) {
                LOGGER.info("Connecting to NickPaints server...");
            }
            client.newWebSocketBuilder()
                    .header("X-API-Key", ConfigManager.CONFIG.apiKey)
                    .buildAsync(URI.create(ConfigManager.CONFIG.baseUrl), INSTANCE)
                    .exceptionally(e -> {
                        if (connectionId != activeConnectionId) {
                            return null;
                        }
                        isConnecting = false;
                        if (reconnectAttempts <= MAX_LOGGED_RECONNECT_ATTEMPTS) {
                            LOGGER.error("WebSocket connection failed to build: {}", e.getMessage());
                        }
                        handleDisconnectionOnce(connectionId);
                        return null;
                    });
        } catch (Exception e) {
            if (connectionId == activeConnectionId) {
                isConnecting = false;
            }
            if (reconnectAttempts <= MAX_LOGGED_RECONNECT_ATTEMPTS) {
                LOGGER.error("Failed to initiate WebSocket connection", e);
            }
            handleDisconnectionOnce(connectionId);
        }
    }

    public static void disconnect() {
        manuallyDisconnected = true;
        isAuthenticated = false;
        isConnecting = false;
        reconnectAttempts = 0;
        disconnectionHandled = true;
        activeConnectionId = CONNECTION_GENERATION.incrementAndGet();
        cancelPaintSyncAckWait();
        resetVisibilityTracking();
        INSTANCE.textFragmentBuffer.setLength(0);
        if (webSocket != null && !webSocket.isOutputClosed()) {
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Client shutting down");
            webSocket = null;
        }
        stopQueueProcessor();
        LOGGER.info("WebSocket disconnected manually.");
    }

    /**
     * World/server leave: clear visibility on the paint server, drop caches, close the socket.
     */
    public static void onWorldDisconnect() {
        if (isAuthenticated && webSocket != null && !webSocket.isOutputClosed()) {
            try {
                JsonObject payload = new JsonObject();
                payload.add("uuids", new JsonArray());
                JsonObject message = new JsonObject();
                message.addProperty("type", "updateVisiblePlayers");
                message.add("payload", payload);
                webSocket.sendText(gson.toJson(message), true);
            } catch (Exception e) {
                LOGGER.debug("Failed to send empty visibility on world disconnect: {}", e.getMessage());
            }
        }
        paintCache.clear();
        uuidQueue.clear();
        disconnect();
    }

    @Override
    public void onOpen(WebSocket ws) {
        if (openedConnectionId == activeConnectionId && webSocket != null && webSocket != ws) {
            // Newer connect already in progress; ignore stale open.
            try {
                ws.sendClose(WebSocket.NORMAL_CLOSURE, "Stale connection");
            } catch (Exception ignored) {
            }
            return;
        }
        openedConnectionId = activeConnectionId;
        WebSocketManager.webSocket = ws;
        textFragmentBuffer.setLength(0);
        // Keep isConnecting true until auth_success / auth_failure so JOIN cannot open a second socket.
        LOGGER.info("WebSocket connection opened, awaiting authentication challenge...");
        ws.request(1);
    }

    @Override
    public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
        if (openedConnectionId != activeConnectionId || (webSocket != null && webSocket != ws)) {
            ws.request(1);
            return null;
        }
        try {
            if (data != null) {
                textFragmentBuffer.append(data);
            }
            if (!last) {
                ws.request(1);
                return null;
            }
            String raw = textFragmentBuffer.toString();
            textFragmentBuffer.setLength(0);

            if (raw.trim().isEmpty()) {
                LOGGER.warn("Received empty WebSocket message");
                ws.request(1);
                return null;
            }
            JsonElement parsedElement = JsonParser.parseString(raw);
            if (!parsedElement.isJsonObject()) {
                LOGGER.warn("Received non-JSON-object message: {}", raw);
                ws.request(1);
                return null;
            }

            JsonObject message = parsedElement.getAsJsonObject();
            if (!message.has("type") || !message.has("payload")) {
                LOGGER.warn("Received message with missing 'type' or 'payload': {}", raw);
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
                    case "paintSyncAck":
                        handlePaintSyncAckPayload(payloadElement.getAsJsonObject());
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
                        isConnecting = false;
                        authenticationPermanentlyFailed = true;
                        disconnect();
                        break;
                    default:
                        LOGGER.warn("Unknown pre-auth message type: {}", type);
                        break;
                }
            }
        } catch (Exception e) {
            textFragmentBuffer.setLength(0);
            LOGGER.error("Failed to process WebSocket message: {}", data != null ? data.toString() : "(null)", e);
        }
        ws.request(1);
        return null;
    }
    public static void updateVisiblePlayers() {
        if (!isAuthenticated || webSocket == null || Minecraft.getInstance().level == null) {
            return;
        }

        tickCounter++;
        // ~1s at 20 TPS — keep subscriptions fresh without spamming
        if (tickCounter < 20) {
            return;
        }
        tickCounter = 0;
        syncVisiblePlayersNow(false);
    }

    /**
     * Re-registers visibility subscriptions with the server.
     * Must be forced after (re)auth: otherwise lastVisiblePlayers can match the world
     * while the server has empty subscriptions, so paintUpdate never arrives.
     */
    private static void syncVisiblePlayersNow(boolean force) {
        if (!isAuthenticated || webSocket == null) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) {
            return;
        }
        final UUID selfUuid = client.player.getUUID();
        Set<UUID> currentlyVisiblePlayers = client.level.players().stream()
                .filter(player -> !player.getUUID().equals(selfUuid))
                .map(player -> player.getUUID())
                .collect(Collectors.toSet());

        if (!force && lastVisiblePlayers.equals(currentlyVisiblePlayers)) {
            return;
        }

        // Newly visible players may have missed paintUpdate while we were not subscribed —
        // drop their cache entries so requestPaints picks up the latest paint.
        for (UUID uuid : currentlyVisiblePlayers) {
            if (force || !lastVisiblePlayers.contains(uuid)) {
                paintCache.remove(uuid);
                uuidQueue.add(uuid);
            }
        }

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

    private static void resetVisibilityTracking() {
        lastVisiblePlayers.clear();
        tickCounter = 0;
    }
    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        if (openedConnectionId != activeConnectionId || (WebSocketManager.webSocket != null && WebSocketManager.webSocket != webSocket)) {
            return null;
        }
        if (isAuthenticated && reconnectAttempts <= MAX_LOGGED_RECONNECT_ATTEMPTS) {
            LOGGER.warn("WebSocket closed unexpectedly: {} - {}", statusCode, reason);
        }
        isAuthenticated = false;
        WebSocketManager.webSocket = null;
        textFragmentBuffer.setLength(0);
        cancelPaintSyncAckWait();
        resetVisibilityTracking();
        handleDisconnectionOnce(openedConnectionId);
        return null;
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        if (openedConnectionId != activeConnectionId || (WebSocketManager.webSocket != null && WebSocketManager.webSocket != webSocket)) {
            return;
        }
        if (reconnectAttempts <= MAX_LOGGED_RECONNECT_ATTEMPTS) {
            LOGGER.error("WebSocket error occurred: {}", error.getMessage());
        }
        isAuthenticated = false;
        WebSocketManager.webSocket = null;
        textFragmentBuffer.setLength(0);
        cancelPaintSyncAckWait();
        resetVisibilityTracking();
        // Shared with onClose via disconnectionHandled so reconnect runs at most once.
        handleDisconnectionOnce(openedConnectionId);
    }

    private void handleAuthChallenge(WebSocket ws, String authHash) {
        Minecraft client = Minecraft.getInstance();
        User session = client.getUser();

        JsonObject verifyPayload = new JsonObject();
        verifyPayload.addProperty("paint", ConfigManager.CONFIG.currentGradient);

        JsonObject verifyMessage = new JsonObject();

        if (client.isLocalServer()) {
            LOGGER.info("In singleplayer mode, using token authentication...");
            verifyMessage.addProperty("type", "auth_verify_token");
            verifyPayload.addProperty("uuid", session.getProfileId().toString());
            verifyPayload.addProperty("accessToken", session.getAccessToken());
        } else {
            LOGGER.info("In multiplayer mode, using session authentication...");
            try {
                client.services().sessionService().joinServer(session.getProfileId(), session.getAccessToken(), authHash);

                verifyMessage.addProperty("type", "auth_verify_session");
                verifyPayload.addProperty("username", session.getName());
            } catch (AuthenticationException e) {
                LOGGER.error("Session authentication failed. This can happen with a cracked client or invalid session. Disabling auto-reconnect.");
                isConnecting = false;
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
        isConnecting = false;
        String username = payload.get("username").getAsString();
        if (reconnectAttempts > 0) {
            LOGGER.info("Successfully authenticated with server after {} attempt(s). Welcome, {}!", reconnectAttempts, username);
        } else {
            LOGGER.info("Authentication successful. Welcome, {}!", username);
        }
        reconnectAttempts = 0;
        // Server subscriptions are empty after (re)connect; force a visibility resync
        // even if the nearby player set looks unchanged on the client.
        resetVisibilityTracking();
        startQueueProcessor();
        Minecraft client = Minecraft.getInstance();
        if (client != null) {
            client.execute(() -> syncVisiblePlayersNow(true));
        }
    }

    private void handlePaintUpdate(JsonObject payload) {
        UUID uuid = UUID.fromString(payload.get("uuid").getAsString());
        String paint = payload.has("paint") ? payload.get("paint").getAsString() : "";
        putPaintInCache(uuid, paint);
    }

    private void handlePaintData(JsonArray paints) {
        for (JsonElement element : paints) {
            JsonObject paintObj = element.getAsJsonObject();
            UUID uuid = UUID.fromString(paintObj.get("uuid").getAsString());
            String paint = paintObj.has("paint") ? paintObj.get("paint").getAsString() : "";
            putPaintInCache(uuid, paint);
        }
    }

    private static void putPaintInCache(UUID uuid, String paint) {
        paintCache.put(uuid, paint == null ? "" : paint);
    }

    private void handlePlayerLeft(JsonObject payload) {
        UUID uuid = UUID.fromString(payload.get("uuid").getAsString());
        paintCache.remove(uuid);
        // Drop from last-visible so the next sync re-subscribes if they are still in the world
        // (or when they reconnect while still nearby).
        lastVisiblePlayers.remove(uuid);
    }

    private static void handleDisconnectionOnce(long connectionId) {
        synchronized (reconnectLock) {
            if (connectionId != activeConnectionId || disconnectionHandled) {
                return;
            }
            disconnectionHandled = true;
            // End in-flight connect/auth so scheduleReconnect may proceed exactly once.
            isConnecting = false;
            isAuthenticated = false;
        }
        handleDisconnection();
    }

    private static void handleDisconnection() {
        stopQueueProcessor();
        if (manuallyDisconnected || authenticationPermanentlyFailed) {
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
            sendMessageToPlayer(Component.translatable("chat.nickpaints.sync.failure", "Not authenticated").withStyle(ChatFormatting.RED));
            return;
        }
        beginPaintSyncAckWait(false, null, null);
        sendPaintUpdateInternal();
    }

    /**
     * Applies {@code newGradient} locally for sync/display, waits for server ack,
     * then persists on success or rolls back on failure/timeout.
     */
    public static void syncMyPaintAndPersist(String newGradient) {
        if (!isAuthenticated) {
            sendMessageToPlayer(Component.translatable("chat.nickpaints.sync.failure", "Not authenticated").withStyle(ChatFormatting.RED));
            return;
        }
        String previous = ConfigManager.CONFIG.currentGradient;
        ConfigManager.CONFIG.currentGradient = newGradient;
        beginPaintSyncAckWait(true, newGradient, previous);
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
        if (playerUuid == null || paintCache.containsKey(playerUuid)) {
            return;
        }
        uuidQueue.add(playerUuid);
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
        cancelPaintSyncAckWait();
        resetVisibilityTracking();
        if (isAuthenticated) {
            syncVisiblePlayersNow(true);
        }
        LOGGER.info("All NickPaints caches have been cleared.");
    }

    private static void cancelPaintSyncAckWait() {
        synchronized (paintSyncAckLock) {
            awaitingUserPaintSyncAck = false;
            pendingPersistGradient = null;
            previousGradientBeforePersist = null;
            if (paintSyncAckTimeoutTask != null) {
                paintSyncAckTimeoutTask.cancel(false);
                paintSyncAckTimeoutTask = null;
            }
        }
    }

    private static void beginPaintSyncAckWait(boolean persistOnAck, String pendingGradient, String previousGradient) {
        synchronized (paintSyncAckLock) {
            if (paintSyncAckTimeoutTask != null) {
                paintSyncAckTimeoutTask.cancel(false);
                paintSyncAckTimeoutTask = null;
            }
            awaitingUserPaintSyncAck = true;
            if (persistOnAck) {
                pendingPersistGradient = pendingGradient;
                previousGradientBeforePersist = previousGradient;
            } else {
                pendingPersistGradient = null;
                previousGradientBeforePersist = null;
            }
            paintSyncAckTimeoutTask = paintSyncAckScheduler.schedule(WebSocketManager::onPaintSyncAckTimeout, 5, TimeUnit.SECONDS);
        }
    }

    private static void onPaintSyncAckTimeout() {
        boolean timedOut;
        String previous;
        synchronized (paintSyncAckLock) {
            timedOut = awaitingUserPaintSyncAck;
            awaitingUserPaintSyncAck = false;
            paintSyncAckTimeoutTask = null;
            previous = previousGradientBeforePersist;
            pendingPersistGradient = null;
            previousGradientBeforePersist = null;
        }
        if (!timedOut) {
            return;
        }
        if (previous != null) {
            ConfigManager.CONFIG.currentGradient = previous;
        }
        Minecraft client = Minecraft.getInstance();
        if (client != null) {
            client.execute(() -> sendMessageToPlayer(Component.translatable("chat.nickpaints.sync.no_ack").withStyle(ChatFormatting.RED)));
        }
    }

    private static void handlePaintSyncAckPayload(JsonObject payload) {
        boolean ok = payload.has("ok") && payload.get("ok").getAsBoolean();
        String error = payload.has("error") && !payload.get("error").isJsonNull() ? payload.get("error").getAsString() : null;
        boolean wasWaiting;
        String pending;
        String previous;
        synchronized (paintSyncAckLock) {
            wasWaiting = awaitingUserPaintSyncAck;
            awaitingUserPaintSyncAck = false;
            pending = pendingPersistGradient;
            previous = previousGradientBeforePersist;
            pendingPersistGradient = null;
            previousGradientBeforePersist = null;
            if (paintSyncAckTimeoutTask != null) {
                paintSyncAckTimeoutTask.cancel(false);
                paintSyncAckTimeoutTask = null;
            }
        }
        if (!wasWaiting) {
            return;
        }
        if (ok) {
            if (pending != null) {
                ConfigManager.CONFIG.currentGradient = pending;
                ConfigManager.saveConfig();
            }
        } else if (previous != null) {
            ConfigManager.CONFIG.currentGradient = previous;
        }
        Minecraft client = Minecraft.getInstance();
        if (client == null) {
            return;
        }
        client.execute(() -> {
            if (ok) {
                sendMessageToPlayer(Component.translatable("chat.nickpaints.sync.success").withStyle(ChatFormatting.GREEN));
            } else if ("invalid_paint".equals(error)) {
                sendMessageToPlayer(Component.translatable("chat.nickpaints.sync.invalid_paint").withStyle(ChatFormatting.RED));
            } else {
                sendMessageToPlayer(Component.translatable("chat.nickpaints.sync.failure", error != null ? error : "rejected").withStyle(ChatFormatting.RED));
            }
        });
    }

    private static void sendMessageToPlayer(Component message) {
        Minecraft client = Minecraft.getInstance();
        if (client != null && client.player != null) {
            client.player.sendSystemMessage(message);
        }
    }
}
