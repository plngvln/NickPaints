package net.p4pingvin4ik.NickPaints.client;

import net.p4pingvin4ik.NickPaints.util.GradientUtil;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caches parsed GradientOptions objects.
 * This is a primary performance optimization that prevents re-parsing
 * the gradient string (using regex, etc.) on every frame for every pixel.
 * The cache is cleared every game tick to ensure correct behavior
 * with dynamically changing nicknames or texts of different lengths.
 */
public class GradientCache {

    private static final Map<String, GradientUtil.GradientOptions> optionsCache = new ConcurrentHashMap<>();

    /**
     * Called every client tick to clear the cache.
     * This is necessary because the result of parseOptions depends on totalLength,
     * and we want to avoid stale data without complicating the key.
     */
    public static void tick() {
        optionsCache.clear();
    }

    /**
     * Gets parsed gradient options from the cache or computes and caches them.
     *
     * @param gradientString The gradient string (e.g., "#FF0000,#00FF00;angle(90)").
     * @param totalLength    The total length of the text for which the gradient is applied.
     * @return A cached or newly created GradientOptions object.
     */
    public static GradientUtil.GradientOptions getOptions(String gradientString, int totalLength) {
        // Create a unique key that accounts for both the string and the length,
        // as the auto-calculation of segmentLength depends on the length.
        String cacheKey = gradientString + "::" + totalLength;

        return optionsCache.computeIfAbsent(cacheKey, key ->
                // This lambda will only be executed if the options are not in the cache.
                GradientUtil.parseOptions(gradientString, totalLength)
        );
    }
}