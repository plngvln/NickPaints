package net.p4pingvin4ik.NickPaints.client;

import net.p4pingvin4ik.NickPaints.util.GradientUtil;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caches parsed {@link GradientUtil.GradientOptions} by gradient string and text length.
 * Keys include {@code totalLength} because segment length depends on it; {@link GradientUtil#parseOptions}
 * does not depend on wall-clock time (animation uses time only when sampling colors).
 */
public class GradientCache {

    private static final int MAX_CACHE_ENTRIES = 512;

    private static final Map<String, GradientUtil.GradientOptions> optionsCache = new ConcurrentHashMap<>();

    /**
     * Gets parsed gradient options from the cache or computes and caches them.
     *
     * @param gradientString The gradient string (e.g., "#FF0000,#00FF00;angle(90)").
     * @param totalLength    The total length of the text for which the gradient is applied.
     * @return A cached or newly created GradientOptions object.
     */
    public static GradientUtil.GradientOptions getOptions(String gradientString, int totalLength) {
        if (optionsCache.size() > MAX_CACHE_ENTRIES) {
            optionsCache.clear();
        }
        String cacheKey = gradientString + "::" + totalLength;
        return optionsCache.computeIfAbsent(cacheKey, key -> GradientUtil.parseOptions(gradientString, totalLength));
    }
}
