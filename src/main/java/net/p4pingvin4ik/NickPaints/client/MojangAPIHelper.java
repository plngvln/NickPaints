package net.p4pingvin4ik.NickPaints.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * A helper class to interact with the Mojang API for player profile information.
 * All operations are performed asynchronously to prevent blocking the main game thread.
 */
public class MojangAPIHelper {

    private static final HttpClient client = HttpClient.newHttpClient();
    private static final String MOJANG_API_URL = "https://api.mojang.com/users/profiles/minecraft/";

    /**
     * Asynchronously fetches a player's UUID by their username.
     *
     * @param username The Minecraft username.
     * @return A CompletableFuture containing an Optional with the UUID if found, or an empty Optional otherwise.
     */
    public static CompletableFuture<Optional<?>> getUuidForUsername(String username) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(MOJANG_API_URL + username))
                .GET()
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        try {
                            JsonObject jsonObject = JsonParser.parseString(response.body()).getAsJsonObject();
                            String uuidString = jsonObject.get("id").getAsString();
                            // Mojang API returns UUID without dashes, we need to re-format it.
                            String formattedUuid = uuidString.replaceFirst(
                                    "(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})",
                                    "$1-$2-$3-$4-$5"
                            );
                            return Optional.of(UUID.fromString(formattedUuid));
                        } catch (Exception e) {
                            return Optional.empty();
                        }
                    }
                    return Optional.empty();
                })
                .exceptionally(e -> Optional.empty()); // In case of network errors
    }
}