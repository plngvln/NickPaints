package net.p4pingvin4ik.NickPaints.client.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public class FlatButton extends ButtonWidget {

    public enum Variant { NORMAL, PRIMARY, DANGER, GHOST }

    // ── Palette ──────────────────────────────────────────────────────────────
    private static final int C_REST    = 0xFF1C1C1C;
    private static final int C_HOVER   = 0xFF2A2A2A;
    private static final int C_PRESS   = 0xFF363636;
    private static final int C_ACTIVE  = 0xFF303030;
    private static final int C_BORDER  = 0xFF3A3A3A;
    private static final int C_BORD_HI = 0xFF585858;
    private static final int C_BORD_AC = 0xFF707070;

    private static final int C_DANGER_REST  = 0xFF221414;
    private static final int C_DANGER_HOV   = 0xFF321A1A;
    private static final int C_DANGER_BORD  = 0xFF4A2020;

    private static final int C_PRIMARY_REST = 0xFF202020;
    private static final int C_PRIMARY_HOV  = 0xFF2E2E2E;
    private static final int C_PRIMARY_BORD = 0xFF666666;

    private static final int C_TEXT_ON   = 0xFFE8E8E8;
    private static final int C_TEXT_OFF  = 0xFF707070;
    private static final int C_TEXT_HINT = 0xFF909090;

    private Variant variant  = Variant.NORMAL;
    private boolean toggled  = false;
    private boolean pressed  = false;

    public FlatButton(int x, int y, int width, int height, Text message, PressAction onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION_SUPPLIER);
    }

    public static FlatButtonBuilder create(Text message, PressAction action) {
        return new FlatButtonBuilder(message, action);
    }

    public FlatButton variant(Variant v)  { this.variant = v; return this; }
    public FlatButton toggled(boolean t)  { this.toggled = t; return this; }

    @Override
    protected void renderWidget(DrawContext ctx, int mx, int my, float delta) {
        if (!visible) return;

        boolean hov  = isHovered();
        boolean act  = toggled;

        int bg, border;
        switch (variant) {
            case DANGER -> {
                bg     = (pressed || act) ? C_DANGER_HOV : (hov ? C_DANGER_HOV : C_DANGER_REST);
                border = C_DANGER_BORD;
            }
            case PRIMARY -> {
                bg     = (pressed || act) ? C_PRIMARY_HOV : (hov ? C_PRIMARY_HOV : C_PRIMARY_REST);
                border = hov ? C_BORD_HI : C_PRIMARY_BORD;
            }
            case GHOST -> {
                bg     = hov ? 0xFF1A1A1A : 0x00000000;
                border = hov ? C_BORDER : 0x00000000;
            }
            default -> {
                if (act) {
                    bg     = C_ACTIVE;
                    border = C_BORD_AC;
                } else {
                    bg     = pressed ? C_PRESS : (hov ? C_HOVER : C_REST);
                    border = hov ? C_BORD_HI : C_BORDER;
                }
            }
        }

        if (!active) {
            bg     = 0xFF141414;
            border = 0xFF252525;
        }

        int x = getX(), y = getY(), w = getWidth(), h = getHeight();

        // Fill
        ctx.fill(x, y, x + w, y + h, bg);

        // Border (1 px)
        ctx.fill(x,           y,       x + w,     y + 1,     border); // top
        ctx.fill(x,           y + h-1, x + w,     y + h,     border); // bottom
        ctx.fill(x,           y,       x + 1,     y + h,     border); // left
        ctx.fill(x + w - 1,   y,       x + w,     y + h,     border); // right

        // Active indicator: top accent bar
        if (act && variant == Variant.NORMAL) {
            ctx.fill(x + 2, y, x + w - 2, y + 2, 0xFF909090);
        }

        // Label
        int textColor = active
                ? (hov ? C_TEXT_ON  : (act ? C_TEXT_ON : C_TEXT_HINT))
                : C_TEXT_OFF;

        int ty = y + (h - 8) / 2;
        ctx.drawCenteredTextWithShadow(
                net.minecraft.client.MinecraftClient.getInstance().textRenderer,
                getMessage(), x + w / 2, ty, textColor
        );
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (isHovered() && active && visible) pressed = true;
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        pressed = false;
        return super.mouseReleased(mx, my, button);
    }

    // ── Convenience builder ───────────────────────────────────────────────────
    public static class FlatButtonBuilder {
        private final Text message;
        private final PressAction action;
        private int x, y, w = 80, h = 20;
        private Variant variant = Variant.NORMAL;
        private boolean toggled = false;

        public FlatButtonBuilder(Text message, PressAction action) {
            this.message = message;
            this.action  = action;
        }

        public FlatButtonBuilder dimensions(int x, int y, int w, int h) { this.x = x; this.y = y; this.w = w; this.h = h; return this; }
        public FlatButtonBuilder variant(Variant v) { this.variant = v; return this; }
        public FlatButtonBuilder toggled(boolean t) { this.toggled = t; return this; }

        public FlatButton build() {
            FlatButton b = new FlatButton(x, y, w, h, message, action);
            b.variant(variant).toggled(toggled);
            return b;
        }
    }
}