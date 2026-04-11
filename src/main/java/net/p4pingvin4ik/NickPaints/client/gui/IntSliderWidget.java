package net.p4pingvin4ik.NickPaints.client.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.function.Consumer;


public class IntSliderWidget extends ClickableWidget {

    private final int min;
    private final int max;
    private int value;
    private final Text label;
    private final Consumer<IntSliderWidget> onChange;
    private boolean dragging;

    /** Same height as flat buttons so editor rows line up in two columns. */
    public static final int SLIDER_HEIGHT = 20;

    public IntSliderWidget(int x, int y, int width, Text label, int min, int max, int initial,
                           Consumer<IntSliderWidget> onChange) {
        super(x, y, width, SLIDER_HEIGHT, Text.empty());
        this.min = min;
        this.max = max;
        this.value = MathHelper.clamp(initial, min, max);
        this.label = label;
        this.onChange = onChange;
    }

    public int getIntValue() {
        return value;
    }

    public void setIntValue(int v) {
        int nv = MathHelper.clamp(v, min, max);
        if (nv != value) {
            value = nv;
            onChange.accept(this);
        }
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();
        int track = 0xFF2a2a3e;
        int trackHi = 0xFF3d3d55;
        int accent = 0xFF5eead4;
        int knobColor = active || dragging ? 0xFF7ff5e8 : accent;

        MutableText line = label.copy().append(Text.literal(": " + value));
        context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, line, x, y + 1, 0xFFE8E8F0);

        int trackY = y + h - 6;
        context.fill(x, trackY - 2, x + w, trackY + 2, track);
        float t = (value - min) / (float) (max - min);
        int fillEnd = x + (int) (t * w);
        context.fill(x, trackY - 2, fillEnd, trackY + 2, trackHi);

        int knobX = x + (int) (t * (w - 8)) - 1;
        knobX = MathHelper.clamp(knobX, x - 2, x + w - 6);
        int knobTop = trackY - 3;
        int knobBottom = trackY + 3;
        context.fill(knobX, knobTop, knobX + 8, knobBottom, knobColor);
        context.drawBorder(knobX, knobTop, 8, knobBottom - knobTop, 0xFF1a1a2e);
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        applyMouse(mouseX);
        dragging = true;
    }

    @Override
    protected void onDrag(double mouseX, double mouseY, double deltaX, double deltaY) {
        applyMouse(mouseX);
    }

    @Override
    public void onRelease(double mouseX, double mouseY) {
        dragging = false;
    }

    private void applyMouse(double mouseX) {
        double t = (mouseX - getX()) / getWidth();
        t = MathHelper.clamp(t, 0.0, 1.0);
        int nv = Math.round(min + (float) (t * (max - min)));
        setIntValue(nv);
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        appendDefaultNarrations(builder);
    }
}
