package net.p4pingvin4ik.NickPaints.client.gui;

import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Inline color palette popup.
 *
 * Layout:
 *   ┌─────────────────────────────────┐
 *   │  SV Square (hue strip on right) │
 *   │  Hue strip (vertical)           │
 *   │  [ #rrggbb  hex field ]         │
 *   └─────────────────────────────────┘
 *
 * Call open(x, y, r, g, b, callback) to show.
 * The popup draws itself inside render(); it is not a ClickableWidget itself.
 */
public class ColorPickerPopup {

    private static final int W  = 160;
    private static final int H  = 148; // no swatches row
    private static final int SV = 120; // SV square size
    private static final int HS = 12;  // hue strip width

    private boolean visible = false;
    private int popX, popY;

    // Current HSV state
    private float hue = 0f, sat = 1f, val = 1f;

    private boolean draggingSV  = false;
    private boolean draggingHue = false;

    private Consumer<float[]> callback;

    // Simple hex field state
    private String hexInput = "#ffffff";
    private boolean hexFocused = false;
    private int hexCursor = 0;

    // ── Public API ────────────────────────────────────────────────────────────

    public void open(int x, int y, float r, float g, float b, Consumer<float[]> callback) {
        this.popX = x;
        this.popY = y;
        this.callback = callback;
        this.visible  = true;
        float[] hsv = rgbToHsv(r, g, b);
        hue = hsv[0]; sat = hsv[1]; val = hsv[2];
        hexInput = String.format("#%02x%02x%02x", (int)(r*255), (int)(g*255), (int)(b*255));
        hexCursor = hexInput.length();
    }

    public void close() { visible = false; }
    public boolean isVisible() { return visible; }

    /** Returns true if a click at (mx, my) was consumed by this popup. */
    public boolean mouseClicked(double mx, double my, int button) {
        if (!visible) return false;
        int x = popX, y = popY;

        // Click outside → close
        if (mx < x || mx > x + W || my < y || my > y + H) {
            close();
            return false;
        }

        // SV square
        if (inSV(mx, my)) {
            draggingSV = true;
            updateSV(mx, my);
            return true;
        }

        // Hue strip
        if (inHue(mx, my)) {
            draggingHue = true;
            updateHue(my);
            return true;
        }

        // Hex field click
        if (inHexField(mx, my)) {
            hexFocused = true;
            return true;
        }

        return true; // consume anyway (inside popup)
    }

    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (!visible) return false;
        if (draggingSV)  { updateSV(mx, my);  return true; }
        if (draggingHue) { updateHue(my);     return true; }
        return false;
    }

    public boolean mouseReleased(double mx, double my, int button) {
        draggingSV = draggingHue = false;
        return false;
    }

    /** Returns true if a char typed was consumed. */
    public boolean charTyped(char chr, int modifiers) {
        if (!visible || !hexFocused) return false;
        if (hexInput.length() < 7 && isHexChar(chr)) {
            hexInput = hexInput.substring(0, hexCursor) + chr + hexInput.substring(hexCursor);
            hexCursor++;
            tryApplyHex();
            return true;
        }
        return false;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!visible || !hexFocused) return false;
        if (keyCode == 259 /* BACKSPACE */ && hexCursor > 1) {
            hexInput = hexInput.substring(0, hexCursor - 1) + hexInput.substring(hexCursor);
            hexCursor--;
            tryApplyHex();
            return true;
        }
        if (keyCode == 262 /* RIGHT */ && hexCursor < hexInput.length()) { hexCursor++; return true; }
        if (keyCode == 263 /* LEFT  */ && hexCursor > 0)                 { hexCursor--; return true; }
        if (keyCode == 256 /* ESC   */) { close(); return true; }
        return false;
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    public void render(GuiGraphicsExtractor ctx, net.minecraft.client.gui.Font tr, int mx, int my) {
        if (!visible) return;
        int x = popX, y = popY;

        // Shadow
        ctx.fill(x + 3, y + 3, x + W + 3, y + H + 3, 0x88000000);

        // Background
        ctx.fill(x, y, x + W, y + H, 0xFF111111);
        // Border
        drawBorder(ctx, x, y, W, H, 0xFF444444);

        // ── SV Square ──
        drawSVSquare(ctx, x + 2, y + 2, SV, SV);

        // SV cursor
        int cx = x + 2 + (int)(sat * (SV - 1));
        int cy = y + 2 + (int)((1 - val) * (SV - 1));
        ctx.fill(cx - 3, cy - 3, cx + 4, cy + 4, 0xFF000000);
        ctx.fill(cx - 2, cy - 2, cx + 3, cy + 3, 0xFFFFFFFF);
        ctx.fill(cx - 1, cy - 1, cx + 2, cy + 2, 0xFF000000);

        // ── Hue Strip ──
        int hx = x + 2 + SV + 4;
        drawHueStrip(ctx, hx, y + 2, HS, SV);

        // Hue cursor (horizontal bar)
        int hy = y + 2 + (int)(hue / 360f * (SV - 1));
        ctx.fill(hx - 1, hy - 1, hx + HS + 1, hy + 2, 0xFF000000);
        ctx.fill(hx,     hy,     hx + HS,     hy + 1, 0xFFFFFFFF);

        // ── Current color preview ──
        float[] rgb = hsvToRgb(hue, sat, val);
        int cur = 0xFF000000 | ((int)(rgb[0]*255) << 16) | ((int)(rgb[1]*255) << 8) | (int)(rgb[2]*255);
        int prevX = hx + HS + 6;
        ctx.fill(prevX, y + 2, prevX + 16, y + 14, cur);
        drawBorder(ctx, prevX, y + 2, 16, 12, 0xFF555555);

        // ── Hex field ──
        int fy = y + SV + 6;
        ctx.fill(x + 2, fy, x + W - 2, fy + 14, 0xFF0D0D0D);
        drawBorder(ctx, x + 2, fy, W - 4, 14, hexFocused ? 0xFF666666 : 0xFF333333);
        ctx.text(tr, Component.literal(hexInput), x + 6, fy + 3, 0xFFDDDDDD);
        // Cursor blink
        if (hexFocused && (System.currentTimeMillis() / 500) % 2 == 0) {
            int cursorX = x + 6 + tr.width(hexInput.substring(0, hexCursor));
            ctx.fill(cursorX, fy + 2, cursorX + 1, fy + 12, 0xFFAAAAAA);
        }
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    /**
     * SV square: saturation → X, value → Y.
     * RGB scales linearly with V, so each column is a cheap vertical fillGradient
     * instead of thousands of per-pixel fills every frame.
     */
    private void drawSVSquare(GuiGraphicsExtractor ctx, int x, int y, int w, int h) {
        int step = 2;
        for (int px = 0; px < w; px += step) {
            float s = w <= 1 ? 0f : (float) px / (w - 1);
            int top = packRgb(hsvToRgb(hue, s, 1f));
            int x2 = Math.min(x + px + step, x + w);
            ctx.fillGradient(x + px, y, x2, y + h, top, 0xFF000000);
        }
    }

    private void drawHueStrip(GuiGraphicsExtractor ctx, int x, int y, int w, int h) {
        int step = 3;
        for (int py = 0; py < h; py += step) {
            float h2 = h <= 1 ? 0f : (float) py / (h - 1) * 360f;
            int c = packRgb(hsvToRgb(h2, 1f, 1f));
            int y2 = Math.min(y + py + step, y + h);
            ctx.fill(x, y + py, x + w, y2, c);
        }
    }

    private static int packRgb(float[] rgb) {
        return 0xFF000000
                | ((int) (rgb[0] * 255) << 16)
                | ((int) (rgb[1] * 255) << 8)
                | (int) (rgb[2] * 255);
    }

    private boolean inSV(double mx, double my) {
        return mx >= popX + 2 && mx < popX + 2 + SV && my >= popY + 2 && my < popY + 2 + SV;
    }

    private boolean inHue(double mx, double my) {
        int hx = popX + 2 + SV + 4;
        return mx >= hx && mx < hx + HS && my >= popY + 2 && my < popY + 2 + SV;
    }

    private boolean inHexField(double mx, double my) {
        int fy = popY + SV + 6;
        return mx >= popX + 2 && mx < popX + W - 2 && my >= fy && my < fy + 14;
    }

    private void updateSV(double mx, double my) {
        sat = (float) Math.max(0, Math.min(1, (mx - popX - 2) / (SV - 1)));
        val = (float) Math.max(0, Math.min(1, 1 - (my - popY - 2) / (SV - 1)));
        refreshHex();
        emitCallback();
    }

    private void updateHue(double my) {
        hue = (float) Math.max(0, Math.min(360, (my - popY - 2) / (SV - 1) * 360f));
        refreshHex();
        emitCallback();
    }

    private void refreshHex() {
        float[] rgb = hsvToRgb(hue, sat, val);
        hexInput = String.format("#%02x%02x%02x", (int)(rgb[0]*255), (int)(rgb[1]*255), (int)(rgb[2]*255));
        hexCursor = hexInput.length();
    }

    private void tryApplyHex() {
        String t = hexInput.startsWith("#") ? hexInput : "#" + hexInput;
        if (t.length() == 7) {
            try {
                java.awt.Color c = java.awt.Color.decode(t);
                float[] hsv = rgbToHsv(c.getRed()/255f, c.getGreen()/255f, c.getBlue()/255f);
                hue = hsv[0]; sat = hsv[1]; val = hsv[2];
                emitCallback();
            } catch (Exception ignored) {}
        }
    }

    private void emitCallback() {
        if (callback != null) {
            float[] rgb = hsvToRgb(hue, sat, val);
            callback.accept(rgb);
        }
    }

    private static boolean isHexChar(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F') || c == '#';
    }

    private static void drawBorder(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x,       y,       x + w,   y + 1,   color);
        ctx.fill(x,       y + h-1, x + w,   y + h,   color);
        ctx.fill(x,       y,       x + 1,   y + h,   color);
        ctx.fill(x + w-1, y,       x + w,   y + h,   color);
    }

    // ── Color math ────────────────────────────────────────────────────────────

    public static float[] hsvToRgb(float h, float s, float v) {
        if (s == 0) return new float[]{v, v, v};
        float sector = h / 60f;
        int   i = (int) sector;
        float f = sector - i;
        float p = v * (1 - s);
        float q = v * (1 - s * f);
        float t = v * (1 - s * (1 - f));
        return switch (i % 6) {
            case 0 -> new float[]{v, t, p};
            case 1 -> new float[]{q, v, p};
            case 2 -> new float[]{p, v, t};
            case 3 -> new float[]{p, q, v};
            case 4 -> new float[]{t, p, v};
            default -> new float[]{v, p, q};
        };
    }

    public static float[] rgbToHsv(float r, float g, float b) {
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float d   = max - min;
        float v   = max;
        float s   = max == 0 ? 0 : d / max;
        float h   = 0;
        if (d != 0) {
            if      (max == r) h = 60 * (((g - b) / d) % 6);
            else if (max == g) h = 60 * (((b - r) / d) + 2);
            else               h = 60 * (((r - g) / d) + 4);
        }
        if (h < 0) h += 360;
        return new float[]{h, s, v};
    }
}