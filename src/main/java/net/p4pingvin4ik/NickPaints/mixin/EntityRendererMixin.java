package net.p4pingvin4ik.NickPaints.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.PlainTextContent;
import net.minecraft.text.Text;
import net.p4pingvin4ik.NickPaints.client.NickPaintsMod;
import net.p4pingvin4ik.NickPaints.client.WebSocketManager;
import net.p4pingvin4ik.NickPaints.config.ConfigManager;
import net.p4pingvin4ik.NickPaints.interfaces.IEntityProvider;
import net.p4pingvin4ik.NickPaints.util.GradientData;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = EntityRenderer.class, priority = 990)
public abstract class EntityRendererMixin<T extends Entity, S extends EntityRenderState> {

    @WrapOperation(
            method = "renderLabelIfPresent(Lnet/minecraft/client/render/entity/state/EntityRenderState;Lnet/minecraft/text/Text;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/font/TextRenderer;draw(Lnet/minecraft/text/Text;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/client/font/TextRenderer$TextLayerType;II)V")
    )
    private void onRenderNametag(TextRenderer textRenderer, Text text, float x, float y, int color, boolean shadow, Matrix4f matrix, VertexConsumerProvider vertexConsumers, TextRenderer.TextLayerType layerType, int backgroundColor, int light, Operation<Void> original, S state) {

        Entity entity = ((IEntityProvider) state).getEntity();
        String paintToShow = null;

        if (entity instanceof PlayerEntity player && state.nameLabelPos != null && ConfigManager.CONFIG.isRenderingEnabledFor(player.getUuid())) {
            if (player.equals(MinecraftClient.getInstance().player)) {
                paintToShow = ConfigManager.CONFIG.currentGradient;
            } else {
                String cachedPaint = WebSocketManager.paintCache.get(player.getUuid());
                if (cachedPaint != null && !cachedPaint.equals("no_paint") && !cachedPaint.equals("fetching")) {
                    paintToShow = cachedPaint;
                } else if (cachedPaint == null) {
                    WebSocketManager.queuePaintForPlayer(player.getUuid());
                }
            }
        }

        if (paintToShow != null) {
            try {
                int totalLength = calculatePaintableLength(text);
                if (totalLength > 0) {
                    GradientData.CURRENT_GRADIENT.set(new GradientData(paintToShow, totalLength));
                }
            } finally {
                original.call(textRenderer, text, x, y, color, shadow, matrix, vertexConsumers, layerType, backgroundColor, light);
                GradientData.CURRENT_GRADIENT.remove();
            }
        } else {
            original.call(textRenderer, text, x, y, color, shadow, matrix, vertexConsumers, layerType, backgroundColor, light);
        }
    }

    private int calculatePaintableLength(Text component) {
        if (NickPaintsMod.PROTECTED_TAG_INSERTION_KEY.equals(component.getStyle().getInsertion())) {
            return 0;
        }
        int length = 0;
        if (component.getContent() instanceof PlainTextContent literalContent) {
            length = literalContent.string().length();
        }
        for (Text sibling : component.getSiblings()) {
            length += calculatePaintableLength(sibling);
        }
        return length;
    }
}