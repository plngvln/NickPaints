package net.p4pingvin4ik.NickPaints.client.gui;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


public final class GradientEditorState {

    public boolean rainbowMode;
    public int rainbowSpeed = 3000;
    public final List<float[]> colors = new ArrayList<>();
    public int speed = 4000;
    public int segment = 16;
    public boolean staticGradient;
    public boolean blockStyle;
    public int angle = 45;

    public GradientEditorState() {
        colors.add(new float[]{1f, 1f, 1f});
    }

    public void parseGradientString(String gradientString) {
        if (gradientString == null || gradientString.isEmpty()) {
            rainbowMode = false;
            colors.clear();
            colors.add(new float[]{1f, 1f, 1f});
            return;
        }
        Matcher rainbowMatcher = Pattern.compile("rainbow\\((\\d+)\\)").matcher(gradientString);
        if (rainbowMatcher.matches()) {
            rainbowMode = true;
            rainbowSpeed = Integer.parseInt(rainbowMatcher.group(1));
            return;
        }
        rainbowMode = false;
        String tempString = gradientString.toLowerCase();
        Matcher angleMatcher = Pattern.compile("angle\\((\\d+)\\)").matcher(tempString);
        if (angleMatcher.find()) {
            angle = Integer.parseInt(angleMatcher.group(1));
            tempString = angleMatcher.replaceAll("");
        }
        Matcher staticMatcher = Pattern.compile("static\\(true\\)").matcher(tempString);
        staticGradient = staticMatcher.find();
        if (staticGradient) {
            tempString = staticMatcher.replaceAll("");
        }
        Matcher speedMatcher = Pattern.compile("speed\\((\\d+)\\)").matcher(tempString);
        if (speedMatcher.find()) {
            speed = Integer.parseInt(speedMatcher.group(1));
            tempString = speedMatcher.replaceAll("");
        }
        Matcher segmentMatcher = Pattern.compile("segment\\((\\d+)\\)").matcher(tempString);
        if (segmentMatcher.find()) {
            segment = Integer.parseInt(segmentMatcher.group(1));
            tempString = segmentMatcher.replaceAll("");
        }
        Matcher styleMatcher = Pattern.compile("style\\((block)\\)").matcher(tempString);
        blockStyle = styleMatcher.find();
        if (blockStyle) {
            tempString = styleMatcher.replaceAll("");
        }
        colors.clear();
        String[] hexCodes = tempString.trim().split(",");
        for (String hex : hexCodes) {
            if (hex.trim().isEmpty()) {
                continue;
            }
            try {
                Color c = Color.decode(hex.trim());
                colors.add(new float[]{c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f});
            } catch (NumberFormatException ignored) {
            }
        }
        if (colors.isEmpty()) {
            colors.add(new float[]{1f, 1f, 1f});
        }
    }

    public String reconstructGradientString() {
        if (rainbowMode) {
            return String.format("rainbow(%d)", rainbowSpeed);
        }
        StringBuilder sb = new StringBuilder();
        String colorsString = colors.stream()
                .map(color -> String.format("#%02x%02x%02x",
                        (int) (color[0] * 255), (int) (color[1] * 255), (int) (color[2] * 255)))
                .collect(Collectors.joining(", "));
        sb.append(colorsString);
        if (staticGradient) {
            sb.append(" static(true)");
        } else {
            sb.append(" speed(").append(speed).append(")");
        }
        sb.append(" segment(").append(segment).append(")");
        sb.append(" angle(").append(angle).append(")");
        if (blockStyle) {
            sb.append(" style(block)");
        }
        return sb.toString().trim();
    }
}
