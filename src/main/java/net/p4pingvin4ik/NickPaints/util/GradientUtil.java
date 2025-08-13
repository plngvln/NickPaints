package net.p4pingvin4ik.NickPaints.util;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GradientUtil {

    // Regex patterns are pre-compiled for performance.
    private static final Pattern RAINBOW_PATTERN = Pattern.compile("rainbow\\((\\d+)\\)");
    private static final Pattern SPEED_PATTERN = Pattern.compile("speed\\((\\d+)\\)");
    private static final Pattern SEGMENT_PATTERN = Pattern.compile("segment\\((\\d+)\\)");
    private static final Pattern STATIC_PATTERN = Pattern.compile("static\\(true\\)");
    private static final Pattern STYLE_PATTERN = Pattern.compile("style\\((block)\\)");
    private static final Pattern DIRECTION_PATTERN = Pattern.compile("direction\\((rtl|ltr)\\)");
    // Define a constant for the minimum allowed animation speed in milliseconds.
    private static final long MIN_ANIMATION_SPEED = 1000L;

    /**
     * A data class to hold all parsed options from the gradient string.
     * This simplifies the main getColor method by separating parsing from calculation.
     */
    private record GradientOptions(
            long speed,
            float segmentLength,
            boolean isStatic,
            boolean isBlockStyle,
            boolean isRightToLeft,
            List<Color> colors
    ) {}

    /**
     * Calculates the color for a specific character in a string based on a gradient definition.
     *
     * @param gradientString The raw string defining the gradient (e.g., "#ff0000, #0000ff speed(2000)").
     * @param charIndex      The index of the character for which to calculate the color.
     * @param totalChars     The total number of characters in the string.
     * @return The calculated ARGB color as an integer.
     */
    public static int getColor(String gradientString, int charIndex, int totalChars) {
        if (totalChars <= 0 || gradientString == null || gradientString.trim().isEmpty()) {
            return Color.WHITE.getRGB();
        }

        // Handle rainbow as a special, non-configurable case for simplicity.
        if (RAINBOW_PATTERN.matcher(gradientString.toLowerCase().trim()).matches()) {
            return getRainbowColor(gradientString, charIndex, totalChars);
        }

        GradientOptions options = parseOptions(gradientString, totalChars);

        if (options.colors().isEmpty()) return Color.WHITE.getRGB();
        if (options.colors().size() == 1) return options.colors().get(0).getRGB();

        // Determine the character's position in the gradient, accounting for direction.
        float effectiveCharIndex = options.isRightToLeft() ? (totalChars - 1 - charIndex) : charIndex;
        float timeOffset = options.isStatic() ? 0 : (float) (System.currentTimeMillis() % options.speed()) / options.speed();

        // The progress value cycles from 0.0 to 1.0 along the gradient.
        float progress = (effectiveCharIndex / options.segmentLength() + timeOffset) % 1.0f;

        return options.isBlockStyle()
                ? getBlockColor(options.colors(), progress)
                : blendColors(options.colors(), progress);
    }

    /**
     * Parses the raw gradient string to extract all arguments and hex colors.
     */
    private static GradientOptions parseOptions(String gradientString, int totalChars) {
        long speed = 4000L;
        float segmentLength = totalChars;
        boolean isStatic = false;
        boolean isBlockStyle = false;
        boolean isRightToLeft = false;
        String cleanGradientString = gradientString;

        // The order of parsing matters. We remove matched arguments to isolate the hex codes.
        Matcher staticMatcher = STATIC_PATTERN.matcher(cleanGradientString.toLowerCase());
        if (staticMatcher.find()) {
            isStatic = true;
            cleanGradientString = staticMatcher.replaceAll("").trim();
        }

        Matcher speedMatcher = SPEED_PATTERN.matcher(cleanGradientString.toLowerCase());
        if (speedMatcher.find()) {
            try {
                long parsedSpeed = Long.parseLong(speedMatcher.group(1));
                speed = Math.max(MIN_ANIMATION_SPEED, parsedSpeed);
            } catch (NumberFormatException ignored) {}
            cleanGradientString = speedMatcher.replaceAll("").trim();
        }

        Matcher segmentMatcher = SEGMENT_PATTERN.matcher(cleanGradientString.toLowerCase());
        if (segmentMatcher.find()) {
            try {
                int parsedSegment = Integer.parseInt(segmentMatcher.group(1));
                if (parsedSegment > 0) segmentLength = parsedSegment;
            } catch (NumberFormatException ignored) {}
            cleanGradientString = segmentMatcher.replaceAll("").trim();
        }

        Matcher styleMatcher = STYLE_PATTERN.matcher(cleanGradientString.toLowerCase());
        if (styleMatcher.find()) {
            if ("block".equals(styleMatcher.group(1))) {
                isBlockStyle = true;
            }
            cleanGradientString = styleMatcher.replaceAll("").trim();
        }

        Matcher directionMatcher = DIRECTION_PATTERN.matcher(cleanGradientString.toLowerCase());
        if (directionMatcher.find()) {
            if ("rtl".equals(directionMatcher.group(1))) {
                isRightToLeft = true;
            }
            cleanGradientString = directionMatcher.replaceAll("").trim();
        }

        List<Color> colors = parseHexColors(cleanGradientString);
        return new GradientOptions(speed, segmentLength, isStatic, isBlockStyle, isRightToLeft, colors);
    }

    private static List<Color> parseHexColors(String hexString) {
        List<Color> colors = new ArrayList<>();
        String[] hexColorArray = hexString.split(",");
        for (String hex : hexColorArray) {
            if (hex.trim().isEmpty()) continue;
            try {
                colors.add(Color.decode(hex.trim()));
            } catch (NumberFormatException e) {
                // On invalid color, return an empty list to signal failure.
                return new ArrayList<>();
            }
        }
        return colors;
    }

    private static int getRainbowColor(String gradientString, int charIndex, int totalChars) {
        Matcher rainbowMatcher = RAINBOW_PATTERN.matcher(gradientString.toLowerCase().trim());
        if (!rainbowMatcher.matches()) return Color.WHITE.getRGB();
        int speed = 3000;
        try {
            speed = Integer.parseInt(rainbowMatcher.group(1));
        } catch (NumberFormatException ignored) {}
        if (speed == 0) speed = 3000;
        float hue = (float) (System.currentTimeMillis() % speed) / speed - (float) charIndex / totalChars * 0.5f;
        return Color.HSBtoRGB(hue, 0.8f, 1.0f);
    }

    /**
     * Calculates a color by smoothly interpolating between two adjacent colors in a list.
     */
    private static int blendColors(List<Color> colors, float progress) {
        float colorIndexFloat = progress * (colors.size() - 1);
        int index1 = (int) colorIndexFloat;
        int index2 = Math.min(index1 + 1, colors.size() - 1);
        float blendFactor = colorIndexFloat - index1;
        Color c1 = colors.get(index1);
        Color c2 = colors.get(index2);
        int r = (int) (c1.getRed() + (c2.getRed() - c1.getRed()) * blendFactor);
        int g = (int) (c1.getGreen() + (c2.getGreen() - c1.getGreen()) * blendFactor);
        int b = (int) (c1.getBlue() + (c2.getBlue() - c1.getBlue()) * blendFactor);
        return new Color(r, g, b).getRGB();
    }

    /**
     * Calculates a color by picking one solid color from a list without blending.
     * This creates a "block" or "step" effect.
     */
    private static int getBlockColor(List<Color> colors, float progress) {
        int index = (int) (progress * colors.size());
        // Clamp the index to be within bounds, preventing IndexOutOfBoundsException.
        index = Math.max(0, Math.min(colors.size() - 1, index));
        return colors.get(index).getRGB();
    }
}