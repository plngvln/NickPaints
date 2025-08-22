package net.p4pingvin4ik.NickPaints.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.p4pingvin4ik.NickPaints.config.ConfigManager;
import net.p4pingvin4ik.NickPaints.util.GradientUtil;

public class GradientOptionsScreen extends Screen {

    private CompletionTextFieldWidget gradientField;
    private final Screen parent;

    public GradientOptionsScreen(Screen parent) {
        super(Text.of("NickPaints Settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int fieldWidth = this.width / 2;
        int fieldX = this.width / 2 - fieldWidth / 2;

        this.gradientField = new CompletionTextFieldWidget(this.textRenderer, fieldX, this.height / 2 - 10, fieldWidth, 20, Text.of(""));
        this.gradientField.setMaxLength(256);
        this.gradientField.setText(ConfigManager.CONFIG.currentGradient);
        this.addSelectableChild(this.gradientField);

        this.addDrawableChild(ButtonWidget.builder(Text.of("Save Local"), (button) -> {
            ConfigManager.CONFIG.currentGradient = this.gradientField.getText();
            ConfigManager.saveConfig();
        }).dimensions(this.width / 2 - 155, this.height / 2 + 20, 150, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.of("Save & Sync to Cloud"), (button) -> {
            ConfigManager.CONFIG.currentGradient = this.gradientField.getText();
            ConfigManager.saveConfig();
            if (this.client != null && this.client.player != null) {
                CloudSyncManager.syncMyPaint(this.client.player.getUuid());
            }
            if (this.client != null) {
                this.client.setScreen(this.parent);
            }
        }).dimensions(this.width / 2 + 5, this.height / 2 + 20, 150, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.drawCenteredTextWithShadow(this.textRenderer, Text.of("Format: #hex... [speed(ms)] [segment(chars)]"), this.width / 2, this.height / 2 - 40, 0xA0A0A0);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 20, 0xFFFFFF);
        this.gradientField.render(context, mouseX, mouseY, delta);
        drawPreview(context);
        super.render(context, mouseX, mouseY, delta);
    }

    private void drawPreview(DrawContext context) {
        if (this.client == null || this.client.player == null) return;
        String currentGradient = this.gradientField.getText();
        String playerName = this.client.player.getName().getString();
        float totalWidth = this.textRenderer.getWidth(playerName);
        float currentX = (this.width / 2.0f) - (totalWidth / 2.0f);
        int y = this.height / 2 - 70;
        for (int i = 0; i < playerName.length(); i++) {
            String characterStr = String.valueOf(playerName.charAt(i));
            int color = GradientUtil.getColor(currentGradient, i, playerName.length());
            context.drawText(this.textRenderer, characterStr, (int) (currentX + 1), y + 1, 0x3F000000, false);
            context.drawText(this.textRenderer, characterStr, (int) currentX, y, color, false);
            currentX += this.textRenderer.getWidth(characterStr);
        }
    }
}