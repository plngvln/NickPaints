package net.p4pingvin4ik.NickPaints.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.font.TextRenderer.TextLayerType;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.p4pingvin4ik.NickPaints.client.CloudSyncManager;
import net.p4pingvin4ik.NickPaints.config.ConfigManager;
import net.p4pingvin4ik.NickPaints.interfaces.IEntityProvider;
import net.p4pingvin4ik.NickPaints.util.GradientUtil;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.awt.Color;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin<T extends Entity, S extends EntityRenderState> {

    @Shadow @Final private EntityRenderDispatcher dispatcher;
    @Shadow @Final private TextRenderer textRenderer;

    @Shadow public abstract TextRenderer getTextRenderer();

    @Inject(method = "renderLabelIfPresent", at = @At("HEAD"), cancellable = true)
    private void renderGradientLabel(S state, Text text, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        // This mixin replaces the vanilla nametag rendering to apply custom gradient paints.

        Entity entity = ((IEntityProvider) state).getEntity();
        if (!(entity instanceof PlayerEntity player) || state.nameLabelPos == null) {
            return;
        }

        // First, check if the user has locally disabled rendering for this player.
        if (!ConfigManager.CONFIG.isRenderingEnabledFor(player.getUuid())) {
            return;
        }

        String paintToShow = null;
        if (player.equals(MinecraftClient.getInstance().player)) {
            paintToShow = ConfigManager.CONFIG.currentGradient;
        } else {
            // Check the cloud cache for other players' paints.
            String cachedPaint = CloudSyncManager.paintCache.get(player.getUuid());
            if (cachedPaint != null) {
                // If we have a definitive answer (either a paint or "no_paint"), use it.
                if (!cachedPaint.equals("no_paint") && !cachedPaint.equals("fetching")) {
                    paintToShow = cachedPaint;
                }
            } else {
                // If the player is not in the cache at all, queue them for a fetch.
                CloudSyncManager.queuePaintForPlayer(player.getUuid());
            }
        }

        // If no paint is available for this player, we also let the vanilla logic proceed.
        if (paintToShow == null) {
            return;
        }

        // A paint is available, so we cancel the original method and render it ourselves.
        ci.cancel();

        boolean isNotSneaking = !state.sneaking;
        String name = text.getString();
        int yOffset = "deadmau5".equals(name) ? -10 : 0;
        TextRenderer textRenderer = this.getTextRenderer();
        float xOffset = (float)(-textRenderer.getWidth(text)) / 2.0F;

        matrices.push();
        matrices.translate(state.nameLabelPos.x, state.nameLabelPos.y + 0.5F, state.nameLabelPos.z);
        matrices.multiply(this.dispatcher.getRotation());
        matrices.scale(0.025F, -0.025F, 0.025F);
        Matrix4f matrix4f = matrices.peek().getPositionMatrix();

        // Pass 1: Render the background.
        // This pass renders a translucent background behind the entire text object.
        int backgroundColor = (int)(MinecraftClient.getInstance().options.getTextBackgroundOpacity(0.25F) * 255.0F) << 24;
        textRenderer.draw(text, xOffset, (float) yOffset, 0x20FFFFFF, false, matrix4f, vertexConsumers, isNotSneaking ? TextLayerType.SEE_THROUGH : TextLayerType.NORMAL, backgroundColor, light);

        // Pass 2: Render the main gradient text (or its shadow if not sneaking).
        // This pass is darker to create a subtle drop-shadow effect, improving readability.
        float currentX = xOffset;
        for (int k = 0; k < name.length(); k++) {
            String characterStr = String.valueOf(name.charAt(k));
            int gradColor = GradientUtil.getColor(paintToShow, k, name.length());

            // The text is rendered with its full color if sneaking, or a darkened version if not.
            // This is a creative way to handle the "shadow" pass.
            int pass2Color = gradColor;
            if (isNotSneaking) {
                Color c = new Color(gradColor);
                pass2Color = new Color((int) (c.getRed() * 0.25F), (int) (c.getGreen() * 0.25F), (int) (c.getBlue() * 0.25F)).getRGB();
            }

            textRenderer.draw(characterStr, currentX, (float) yOffset, pass2Color, false, matrix4f, vertexConsumers, isNotSneaking ? TextLayerType.SEE_THROUGH : TextLayerType.NORMAL, 0, light);
            currentX += textRenderer.getWidth(characterStr);
        }

        // Pass 3: Render the bright foreground text (emissive layer).
        // This pass only runs when not sneaking and renders the text at full brightness,
        // making it "glow" and stand out, just like vanilla nametags.
        if (isNotSneaking) {
            currentX = xOffset;
            int foregroundLight = LightmapTextureManager.applyEmission(light, 2);
            for (int k = 0; k < name.length(); k++) {
                String characterStr = String.valueOf(name.charAt(k));
                int gradColor = GradientUtil.getColor(paintToShow, k, name.length());

                textRenderer.draw(characterStr, currentX, (float) yOffset, gradColor, false, matrix4f, vertexConsumers, TextLayerType.NORMAL, 0, foregroundLight);
                currentX += textRenderer.getWidth(characterStr);
            }
        }

        matrices.pop();
    }
}