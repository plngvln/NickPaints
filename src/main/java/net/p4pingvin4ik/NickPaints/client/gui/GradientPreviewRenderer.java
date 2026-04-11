package net.p4pingvin4ik.NickPaints.client.gui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.p4pingvin4ik.NickPaints.util.GradientUtil;

public final class GradientPreviewRenderer {

    private static final float MC_CHAR_WIDTH = 8.0f;
    private static final float MC_FONT_HEIGHT = 9.0f;
    private static final int VERTICAL_SEGMENTS = 8;

    private GradientPreviewRenderer() {
    }

    public static int interpolateColor(int color1, int color2, float t) {
        int a1 = (color1 >> 24) & 0xFF;
        int r1 = (color1 >> 16) & 0xFF;
        int g1 = (color1 >> 8) & 0xFF;
        int b1 = color1 & 0xFF;

        int a2 = (color2 >> 24) & 0xFF;
        int r2 = (color2 >> 16) & 0xFF;
        int g2 = (color2 >> 8) & 0xFF;
        int b2 = color2 & 0xFF;

        int a = (int) (a1 + (a2 - a1) * t);
        int r = (int) (r1 + (r2 - r1) * t);
        int g = (int) (g1 + (g2 - g1) * t);
        int b = (int) (b1 + (b2 - b1) * t);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /**
     * @return total height used (line height)
     */
    public static int drawPreview(DrawContext context, TextRenderer textRenderer, int x, int y, int maxWidth,
                                  String gradientString, String playerName, boolean centered) {
        int totalLength = playerName.length();
        int lineHeight = textRenderer.fontHeight + 2;

        int startX = x;
        if (centered && maxWidth > 0) {
            int textWidth = textRenderer.getWidth(playerName);
            startX = x + (maxWidth - textWidth) / 2;
        }

        float fontHeight = textRenderer.fontHeight;
        float segmentHeight = fontHeight / VERTICAL_SEGMENTS;

        int currentX = startX;
        for (int i = 0; i < playerName.length(); i++) {
            String characterStr = String.valueOf(playerName.charAt(i));
            int charWidth = textRenderer.getWidth(characterStr);

            float characterCenterX = (i + 0.5f) * MC_CHAR_WIDTH;
            int topArgb = GradientUtil.get2DColor(gradientString, totalLength, characterCenterX, 0);
            int bottomArgb = GradientUtil.get2DColor(gradientString, totalLength, characterCenterX, MC_FONT_HEIGHT);

            for (int vSeg = 0; vSeg < VERTICAL_SEGMENTS; vSeg++) {
                float t = (vSeg + 0.5f) / VERTICAL_SEGMENTS;
                int argb = interpolateColor(topArgb, bottomArgb, t);
                int clipY1 = (int) (y + vSeg * segmentHeight);
                int clipY2 = (int) (y + (vSeg + 1) * segmentHeight + 1);
                context.enableScissor(currentX, clipY1, currentX + charWidth + 1, clipY2);
                int color = (argb & 0x00FFFFFF) | 0xFF000000;
                context.drawTextWithShadow(textRenderer, characterStr, currentX, y, color);
                context.disableScissor();
            }
            currentX += charWidth;
        }

        return lineHeight;
    }
}
