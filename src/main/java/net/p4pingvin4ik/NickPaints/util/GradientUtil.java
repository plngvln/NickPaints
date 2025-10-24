package net.p4pingvin4ik.NickPaints.util;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for creating and handling color gradients.
 */
public class GradientUtil {

    private static final Pattern ANGLE_PATTERN = Pattern.compile("angle\\(-?(\\d+)\\)");
    private static final Pattern RAINBOW_PATTERN = Pattern.compile("rainbow\\((\\d+)\\)");
    private static final Pattern SPEED_PATTERN = Pattern.compile("speed\\((\\d+)\\)");
    private static final Pattern SEGMENT_PATTERN = Pattern.compile("segment\\((\\d+)\\)");
    private static final Pattern STATIC_PATTERN = Pattern.compile("static\\(true\\)");
    private static final Pattern STYLE_PATTERN = Pattern.compile("style\\((block)\\)");
    private static final Pattern DIRECTION_PATTERN = Pattern.compile("direction\\((rtl|ltr)\\)");
    private static final long MIN_ANIMATION_SPEED = 1000L;

    /**
     * A record to hold the parsed options for a gradient.
     *
     * @param speed                  The animation speed in milliseconds.
     * @param segmentLength          The length of the gradient segment.
     * @param isStatic               Whether the gradient is static (non-animated).
     * @param isBlockStyle           Whether the gradient has a block style.
     * @param isRightToLeft          Whether the gradient flows from right to left.
     * @param angle                  The angle of the gradient in degrees.
     * @param isSegmentUserDefined   Whether the segment length was defined by the user.
     * @param colors                 The list of colors to be used in the gradient.
     */
    public record GradientOptions(
            long speed, float segmentLength, boolean isStatic,
            boolean isBlockStyle, boolean isRightToLeft, float angle,
            boolean isSegmentUserDefined,
            List<Color> colors
    ) {}

    /**
     * Calculates the color for a 2D gradient at a specific point.
     *
     * @param gradientString The string defining the gradient.
     * @param totalLength    The total length for the gradient calculation.
     * @param localX         The x-coordinate of the point.
     * @param localY         The y-coordinate of the point.
     * @return The calculated RGB color value.
     */
    public static int get2DColor(String gradientString, int totalLength, float localX, float localY) {
        if (gradientString == null || gradientString.trim().isEmpty()) {
            return Color.WHITE.getRGB();
        }

        GradientOptions options = parseOptions(gradientString, totalLength);

        if (RAINBOW_PATTERN.matcher(gradientString.toLowerCase().trim()).matches()) {
            return get2DRainbowColor(options, localX, localY);
        }

        if (options.colors().isEmpty()) return Color.WHITE.getRGB();
        if (options.colors().size() == 1) return options.colors().get(0).getRGB();

        float angleRad = (float) Math.toRadians(options.angle());
        float cos = (float) Math.cos(angleRad);
        float sin = (float) Math.sin(angleRad);

        float projectedPosition = localX * cos + localY * sin;
        float timeOffset = options.isStatic() ? 0 : (float) (System.currentTimeMillis() % options.speed()) / options.speed();

        float progress;

        if (options.isSegmentUserDefined()) {
            progress = (projectedPosition / options.segmentLength() + timeOffset) % 1.0f;
        } else {
            float textWidth = totalLength * 8.0f;
            float fontHeight = 9.0f;

            float minProjected = 0;
            if (cos < 0) minProjected += textWidth * cos;
            if (sin < 0) minProjected += fontHeight * sin;

            float normalizedPosition = projectedPosition - minProjected;
            progress = (normalizedPosition / options.segmentLength() + timeOffset) % 1.0f;
        }


        if (progress < 0) {
            progress += 1.0f;
        }
        if (options.isRightToLeft()) {
            progress = 1.0f - progress;
        }

        return options.isBlockStyle()
                ? getBlockColor(options.colors(), progress)
                : blendColors(options.colors(), progress);
    }

    /**
     * Calculates the color for a character in a gradient string.
     *
     * @param gradientString The string defining the gradient.
     * @param charIndex      The index of the character.
     * @param totalChars     The total number of characters.
     * @return The calculated RGB color value.
     */
    public static int getColor(String gradientString, int charIndex, int totalChars) {
        if (totalChars <= 0 || gradientString == null || gradientString.trim().isEmpty()) return Color.WHITE.getRGB();
        if (RAINBOW_PATTERN.matcher(gradientString.toLowerCase().trim()).matches())
            return getRainbowColor(gradientString, charIndex, totalChars);
        GradientOptions options = parseOptions(gradientString, totalChars);
        if (options.colors().isEmpty()) return Color.WHITE.getRGB();
        if (options.colors().size() == 1) return options.colors().get(0).getRGB();
        float effectiveCharIndex = options.isRightToLeft() ? (totalChars - 1 - charIndex) : charIndex;
        float timeOffset = options.isStatic() ? 0 : (float) (System.currentTimeMillis() % options.speed()) / options.speed();
        float progress = (effectiveCharIndex / options.segmentLength() + timeOffset) % 1.0f;
        if (progress < 0) progress += 1.0f;
        return options.isBlockStyle() ? getBlockColor(options.colors(), progress) : blendColors(options.colors(), progress);
    }

    /**
     * Parses the gradient string to extract the options.
     *
     * @param gradientString The string defining the gradient.
     * @param totalChars     The total number of characters.
     * @return The parsed gradient options.
     */
    public static GradientOptions parseOptions(String gradientString, int totalChars) {
        final float FONT_HEIGHT = 9.0f; // Actual glyph height (-1 to 8)
        final float AVG_CHAR_WIDTH = 8.0f;

        long speed = 4000L;
        boolean isStatic = false, isBlockStyle = false, isRightToLeft = false;
        boolean isSegmentUserDefined = false;
        float angle = 45.0f;
        String cleanGradientString = gradientString;

        Matcher angleMatcher = ANGLE_PATTERN.matcher(cleanGradientString.toLowerCase());
        if (angleMatcher.find()) {
            try {
                angle = Float.parseFloat(angleMatcher.group(1));
            } catch (NumberFormatException ignored) {
            }
            cleanGradientString = angleMatcher.replaceAll("").trim();
        }
        Matcher staticMatcher = STATIC_PATTERN.matcher(cleanGradientString.toLowerCase());
        if (staticMatcher.find()) {
            isStatic = true;
            cleanGradientString = staticMatcher.replaceAll("").trim();
        }
        Matcher speedMatcher = SPEED_PATTERN.matcher(cleanGradientString.toLowerCase());
        if (speedMatcher.find()) {
            try {
                speed = Math.max(MIN_ANIMATION_SPEED, Long.parseLong(speedMatcher.group(1)));
            } catch (NumberFormatException ignored) {
            }
            cleanGradientString = speedMatcher.replaceAll("").trim();
        }
        Matcher styleMatcher = STYLE_PATTERN.matcher(cleanGradientString.toLowerCase());
        if (styleMatcher.find()) {
            if ("block".equals(styleMatcher.group(1))) isBlockStyle = true;
            cleanGradientString = styleMatcher.replaceAll("").trim();
        }
        Matcher directionMatcher = DIRECTION_PATTERN.matcher(cleanGradientString.toLowerCase());
        if (directionMatcher.find()) {
            if ("rtl".equals(directionMatcher.group(1))) isRightToLeft = true;
            cleanGradientString = directionMatcher.replaceAll("").trim();
        }

        float segmentLength;
        Matcher segmentMatcher = SEGMENT_PATTERN.matcher(cleanGradientString.toLowerCase());
        if (segmentMatcher.find()) {
            isSegmentUserDefined = true;
            try {
                segmentLength = Integer.parseInt(segmentMatcher.group(1));
            } catch (NumberFormatException e) {
                segmentLength = totalChars * AVG_CHAR_WIDTH;
            }
            cleanGradientString = segmentMatcher.replaceAll("").trim();
        } else {
            isSegmentUserDefined = false;
            float angleRad = (float) Math.toRadians(angle);
            float textWidth = totalChars * AVG_CHAR_WIDTH;
            segmentLength = (float) (Math.abs(textWidth * Math.cos(angleRad)) + Math.abs(FONT_HEIGHT * Math.sin(angleRad)));
        }
        if (segmentLength < 1.0f) segmentLength = 1.0f;

        List<Color> colors = parseHexColors(cleanGradientString);
        return new GradientOptions(speed, segmentLength, isStatic, isBlockStyle, isRightToLeft, angle, isSegmentUserDefined, colors);
    }

    private static int get2DRainbowColor(GradientOptions options, float localX, float localY) {
        float angleRad = (float) Math.toRadians(options.angle());
        float cos = (float) Math.cos(angleRad);
        float sin = (float) Math.sin(angleRad);
        float projectedPosition = localX * cos + localY * sin;
        float timeOffset = (float) (System.currentTimeMillis() % options.speed()) / options.speed();
        float hue = timeOffset - projectedPosition * 0.1f;
        return Color.HSBtoRGB(hue % 1.0f, 0.8f, 1.0f);
    }

    private static List<Color> parseHexColors(String hexString) {
        List<Color> colors = new ArrayList<>();
        String[] hexColorArray = hexString.split(",");
        for (String hex : hexColorArray) {
            if (hex.trim().isEmpty()) continue;
            try {
                colors.add(Color.decode(hex.trim()));
            } catch (NumberFormatException e) {
                return new ArrayList<>();
            }
        }
        return colors;
    }

    private static int getRainbowColor(String gradientString, int charIndex, int totalChars) {
        Matcher rainbowMatcher = RAINBOW_PATTERN.matcher(gradientString.toLowerCase().trim());
        if (!rainbowMatcher.matches()) return Color.WHITE.getRGB();
        long speed = 3000L;
        try {
            speed = Math.max(MIN_ANIMATION_SPEED, Long.parseLong(rainbowMatcher.group(1)));
        } catch (NumberFormatException ignored) {
        }
        float hue = (float) (System.currentTimeMillis() % speed) / speed - (float) charIndex / totalChars * 0.5f;
        return Color.HSBtoRGB(hue, 0.8f, 1.0f);
    }

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

    private static int getBlockColor(List<Color> colors, float progress) {
        int index = (int) (progress * colors.size());
        index = Math.max(0, Math.min(colors.size() - 1, index));
        return colors.get(index).getRGB();
    }
}