package net.p4pingvin4ik.NickPaints.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

/**
 * Handles checking for new versions of the mod using the official Modrinth API.
 * The check is performed at startup, and the player is notified upon joining a world.
 */
public class VersionChecker {

    private static final Logger LOGGER = NickPaintsMod.LOGGER;
    private static final HttpClient client = HttpClient.newHttpClient();


    private static final String MODRINTH_PROJECT_SLUG = "nickpaints";
    private static final String MODRINTH_API_URL = "https://api.modrinth.com/v2/project/" + MODRINTH_PROJECT_SLUG + "/version";

    // --- State variables for delayed notification ---
    private static String pendingUpdateVersion = null;
    private static String pendingUpdateUrl = null;
    private static boolean hasNotifiedInSession = false;

    /**
     * Initiates an asynchronous check for a new, compatible version on Modrinth.
     * This should be called once during mod initialization.
     */
    public static void checkForUpdates() {
        LOGGER.info("Checking for NickPaints updates via Modrinth API...");
        try {
            String gameVersion = MinecraftClient.getInstance().getGameVersion();
            String loadersParam = URLEncoder.encode("[\"fabric\"]", StandardCharsets.UTF_8);
            String gameVersionsParam = URLEncoder.encode("[\"" + gameVersion + "\"]", StandardCharsets.UTF_8);
            String urlWithParams = String.format("%s?loaders=%s&game_versions=%s", MODRINTH_API_URL, loadersParam, gameVersionsParam);

            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(urlWithParams)).GET().build();
            client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(HttpResponse::body)
                    .thenAccept(VersionChecker::handleModrinthResponse)
                    .exceptionally(error -> {
                        LOGGER.error("Failed to check for updates from Modrinth.", error);
                        return null;
                    });
        } catch (Exception e) {
            LOGGER.error("Could not create Modrinth API request URI.", e);
        }
    }

    /**
     * Called when the player joins a world. Displays a pending update notification if one exists.
     * This ensures the message is not lost during the loading screen.
     */
    public static void onPlayerJoin() {
        if (pendingUpdateVersion != null && !hasNotifiedInSession) {
            notifyPlayerInChat(pendingUpdateVersion, pendingUpdateUrl);
            hasNotifiedInSession = true; // Ensure we only notify once per game session.
        }
    }

    private static void handleModrinthResponse(String responseBody) {
        try {
            JsonArray versions = JsonParser.parseString(responseBody).getAsJsonArray();
            if (versions.size() == 0) {
                LOGGER.info("No compatible version found for this Minecraft version on Modrinth.");
                return;
            }

            for (JsonElement versionElement : versions) {
                JsonObject versionObj = versionElement.getAsJsonObject();
                if ("release".equals(versionObj.get("version_type").getAsString())) {
                    String latestVersion = versionObj.get("version_number").getAsString();
                    String currentVersion = getCurrentModVersion();
                    if (isNewerVersion(latestVersion, currentVersion)) {
                        LOGGER.info("A new version of NickPaints is available: {} (current: {})", latestVersion, currentVersion);
                        pendingUpdateVersion = latestVersion;
                        pendingUpdateUrl = "https://modrinth.com/mod/" + MODRINTH_PROJECT_SLUG + "/version/" + latestVersion;
                    } else {
                        LOGGER.info("NickPaints is up to date. (Current: {}, Latest compatible: {})", currentVersion, latestVersion);
                    }
                    return;
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to parse update response from Modrinth.", e);
        }
    }

    private static void notifyPlayerInChat(String newVersion, String url) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        Text message = Text.literal("[NickPaints] ").formatted(Formatting.GOLD)
                // Main message body with a placeholder for the version number.
                .append(Text.translatable("chat.nickpaints.update.main", newVersion).formatted(Formatting.YELLOW))
                .append(" ") // Add a space before the link
                // The clickable link part.
                .append(Text.translatable("chat.nickpaints.update.link")
                        .formatted(Formatting.GREEN, Formatting.BOLD)
                        .styled(style -> {
                            try {
                                ClickEvent clickEvent = new ClickEvent.OpenUrl(new URI(url));
                                // The hover text is also translatable.
                                HoverEvent hoverEvent = new HoverEvent.ShowText(Text.translatable("chat.nickpaints.update.hover"));
                                return style.withClickEvent(clickEvent).withHoverEvent(hoverEvent);
                            } catch (Exception e) {
                                return style;
                            }
                        })
                );

        client.player.sendMessage(message, false);
    }
    private static String getCurrentModVersion() {
        return FabricLoader.getInstance()
                .getModContainer(NickPaintsMod.MOD_ID)
                .map(modContainer -> modContainer.getMetadata().getVersion().getFriendlyString())
                .orElse("0.0.0");
    }

    private static boolean isNewerVersion(String versionA, String versionB) {
        try {
            String[] partsA = versionA.split("\\.");
            String[] partsB = versionB.split("\\.");
            int length = Math.max(partsA.length, partsB.length);
            for (int i = 0; i < length; i++) {
                int partA = i < partsA.length ? Integer.parseInt(partsA[i].split("-")[0]) : 0;
                int partB = i < partsB.length ? Integer.parseInt(partsB[i].split("-")[0]) : 0;
                if (partA > partB) return true;
                if (partA < partB) return false;
            }
            return false;
        } catch (NumberFormatException e) {
            LOGGER.error("Could not compare versions: '{}' and '{}'", versionA, versionB);
            return false;
        }
    }
}