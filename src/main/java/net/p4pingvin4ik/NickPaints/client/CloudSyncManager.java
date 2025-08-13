package net.p4pingvin4ik.NickPaints.client;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.p4pingvin4ik.NickPaints.config.ConfigManager;
import org.slf4j.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Manages all communication with the backend server for synchronizing player paints.
 * This class handles fetching paints for other players and publishing the local player's paint.
 * It employs a batching and queuing system to minimize API calls.
 */
public class CloudSyncManager {

    // --- Service Configuration ---
    private static final String BASE_URL = "https://api.nickpaints.ru";
    /**
     * This key serves more as a public application identifier than a true cryptographic secret.
     * Since it is embedded in the client-side mod, it must be considered publicly accessible.
     * Its primary purpose is to allow the server to reject requests that do not originate from the mod,
     * acting as a basic first layer of filtering. True security is enforced on the server-side
     */
    private static final String API_KEY = "20oymFVk6uOPmY0s6/XLiHgS--yF=LsQuJ0x/hgzOBCDZ8yvG-hvZyNBFttmodyj";

    // --- Core Components ---
    private static final HttpClient client = HttpClient.newHttpClient();
    private static final Logger LOGGER = NickPaintsMod.LOGGER;
    private static final Gson gson = new Gson();

    // --- State Management ---
    /**
     * In-memory cache for player paints. Maps UUID to a paint string.
     * Special values:
     * - "fetching": A request for this UUID is in progress.
     * - "no_paint": The server confirmed this player has no paint.
     */
    public static final Map<UUID, String> paintCache = new ConcurrentHashMap<>();
    private static final Set<UUID> uuidQueue = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> inFlightUuids = ConcurrentHashMap.newKeySet();

    /** Timer to delay batch requests, allowing more UUIDs to accumulate. -1 indicates an inactive timer. */
    private static int requestDelayTicks = -1;

    /**
     * Queues a player's UUID to be checked for a custom paint if not already cached or pending.
     * This is the entry point for fetching remote paints.
     * @param playerUuid The UUID of the player to check.
     */
    public static void queuePaintForPlayer(UUID playerUuid) {
        if (playerUuid != null && !paintCache.containsKey(playerUuid) && !inFlightUuids.contains(playerUuid)) {
            uuidQueue.add(playerUuid);
            LOGGER.debug("Queued player for paint check: {}", playerUuid);
        }
    }

    /**
     * Clears all local caches and resets the request timer. Can be invoked by a user command.
     */
    public static void clearCache() {
        paintCache.clear();
        uuidQueue.clear();
        inFlightUuids.clear();
        requestDelayTicks = -1;
        LOGGER.info("All NickPaints caches have been cleared by user command.");
    }

    /**
     * Processes the UUID queue. This method should be called on every client tick.
     * It uses a delay timer to batch multiple requests into a single network call.
     */
    public static void processQueue() {
        if (uuidQueue.isEmpty()) {
            requestDelayTicks = -1;
            return;
        }
        if (requestDelayTicks == -1) {
            requestDelayTicks = 20; // 1-second delay
            return;
        }
        if (requestDelayTicks > 0) {
            requestDelayTicks--;
            return;
        }

        // Timer expired, process the entire queue in batches of 30.
        while (!uuidQueue.isEmpty()) {
            List<UUID> batch = uuidQueue.stream().limit(30).collect(Collectors.toList());
            uuidQueue.removeAll(batch);
            inFlightUuids.addAll(batch);
            sendBatchRequest(batch);
        }
        requestDelayTicks = -1;
    }

    private static void sendBatchRequest(List<UUID> batch) {
        if (batch.isEmpty()) return;

        String jsonBody = gson.toJson(Map.of("uuids", batch));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/paints"))
                .header("Content-Type", "application/json")
                .header("X-API-Key", API_KEY)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> handleBatchResponse(response.body(), batch));
    }

    private static void handleBatchResponse(String responseBody, List<UUID> sentBatch) {
        Set<UUID> foundUuids = ConcurrentHashMap.newKeySet();
        try {
            JsonArray documents = JsonParser.parseString(responseBody).getAsJsonArray();
            for (JsonElement docElement : documents) {
                JsonObject doc = docElement.getAsJsonObject();
                UUID uuid = UUID.fromString(doc.get("uuid").getAsString());
                String paint = doc.get("paint").getAsString();
                paintCache.put(uuid, paint);
                foundUuids.add(uuid);
            }
            LOGGER.info("Received batch response. Found {} paints out of {}.", foundUuids.size(), sentBatch.size());
        } catch (Exception e) {
            LOGGER.error("Failed to parse response from server. Body: {}", responseBody, e);
        } finally {
            // For any UUID that was in the request but not in the response,
            // cache it as "no_paint" to prevent future lookups.
            for (UUID uuid : sentBatch) {
                if (!foundUuids.contains(uuid)) {
                    paintCache.put(uuid, "no_paint");
                }
                inFlightUuids.remove(uuid);
            }
        }
    }

    /**
     * Publishes the local player's current paint to the server.
     * This requires validating the player's session token with Mojang's auth server.
     * @param myUuid The UUID of the local player.
     */
    public static void syncMyPaint(UUID myUuid) {
        if (myUuid == null) return;
        LOGGER.info("Syncing local paint to server for UUID: {}", myUuid);

        String accessToken = MinecraftClient.getInstance().getSession().getAccessToken();
        String myPaint = ConfigManager.CONFIG.currentGradient;

        String jsonBody = gson.toJson(Map.of(
                "uuid", myUuid.toString(),
                "paint", myPaint,
                "accessToken", accessToken
        ));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/paint"))
                .header("Content-Type", "application/json")
                .header("X-API-Key", API_KEY)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    // Asynchronously handle the response on the main client thread to interact with game UI.
                    MinecraftClient.getInstance().execute(() -> {
                        handleSyncResponse(response);
                    });
                });
    }

    private static void handleSyncResponse(HttpResponse<String> response) {
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            LOGGER.info("Successfully synced paint. Response: {}", response.statusCode());
            sendMessageToPlayer(Text.translatable("chat.nickpaints.sync.success").formatted(Formatting.GREEN));
        } else {
            String errorMessage = "HTTP " + response.statusCode();
            try {
                JsonObject errorObject = JsonParser.parseString(response.body()).getAsJsonObject();
                if (errorObject.has("error")) {
                    errorMessage = errorObject.get("error").getAsString();
                }
            } catch (Exception ignored) {} // Fallback to HTTP status code if body is not valid JSON.

            LOGGER.error("Failed to sync paint. Code: {}, Body: {}", response.statusCode(), response.body());
            sendMessageToPlayer(Text.translatable("chat.nickpaints.sync.failure", errorMessage).formatted(Formatting.RED));

            if (response.statusCode() == 403) {
                sendMessageToPlayer(Text.translatable("chat.nickpaints.sync.auth_failed").formatted(Formatting.YELLOW));
            }
        }
    }

    /**
     * Sends a message to the player's chat HUD.
     */
    private static void sendMessageToPlayer(Text message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(message, false);
        }
    }
}