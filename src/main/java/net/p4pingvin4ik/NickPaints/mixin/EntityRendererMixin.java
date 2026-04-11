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
import net.p4pingvin4ik.NickPaints.util.NametagNicknameLocator;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Optional;

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
                if (cachedPaint == null) {
                    WebSocketManager.queuePaintForPlayer(player.getUuid());
                } else if (!cachedPaint.trim().isEmpty()) {
                    paintToShow = cachedPaint;
                }
            }
        }
        if (paintToShow != null && !paintToShow.trim().isEmpty()) {
            try {
                int paintGlyphStart = -1;
                int paintGlyphEnd = -1;
                int totalLength;
                if (entity instanceof PlayerEntity pl) {
                    Optional<NametagNicknameLocator.CharRange> nickname = NametagNicknameLocator.findNicknameRange(text, pl);
                    if (nickname.isPresent() && nickname.get().length() > 0) {
                        paintGlyphStart = nickname.get().start();
                        paintGlyphEnd = nickname.get().end();
                        totalLength = nickname.get().length();
                    } else {
                        totalLength = calculatePaintableLength(text);
                    }
                } else {
                    totalLength = calculatePaintableLength(text);
                }
                if (totalLength > 0) {
                    if (paintGlyphStart >= 0) {
                        GradientData.NAMETAG_GLYPH_INDEX.get().set(0);
                        GradientData.NAMETAG_GRADIENT_ANCHOR_X.get().set(null);
                    }
                    GradientData.CURRENT_GRADIENT.set(new GradientData(paintToShow, totalLength, paintGlyphStart, paintGlyphEnd));
                }
            } finally {
                original.call(textRenderer, text, x, y, color, shadow, matrix, vertexConsumers, layerType, backgroundColor, light);
                GradientData.CURRENT_GRADIENT.remove();
                GradientData.NAMETAG_GRADIENT_ANCHOR_X.get().set(null);
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