package net.p4pingvin4ik.NickPaints.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.font.TextRenderer.TextLayerType;
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

@Mixin(value = EntityRenderer.class, priority = 990)
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

        /**
        * Renders the nametag using a simplified, single-pass method that is compatible with shaders.
        * We trust the shader pack to handle lighting, shadows, and emissive effects.
        */
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

        // Render background
        int backgroundColor = (int)(MinecraftClient.getInstance().options.getTextBackgroundOpacity(0.25F) * 255.0F) << 24;
        textRenderer.draw(text, xOffset, (float) yOffset, 0, false, matrix4f, vertexConsumers, TextLayerType.SEE_THROUGH, backgroundColor, light);

        // Render the gradient text in a single pass
        float currentX = xOffset;
        for (int k = 0; k < name.length(); k++) {
            String characterStr = String.valueOf(name.charAt(k));
            int gradColor = GradientUtil.getColor(paintToShow, k, name.length());
            // We render the text once with full color and no special light modifications.
            textRenderer.draw(characterStr, currentX, (float) yOffset, gradColor, false, matrix4f, vertexConsumers, isNotSneaking ? TextLayerType.SEE_THROUGH : TextLayerType.NORMAL, 0, light);
            currentX += textRenderer.getWidth(characterStr);
        }

        matrices.pop();
    }
}