package net.p4pingvin4ik.NickPaints.mixin;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.font.glyphs.BakedSheetGlyph;
import net.minecraft.util.ARGB;
import net.p4pingvin4ik.NickPaints.client.GradientCache;
import net.p4pingvin4ik.NickPaints.client.NickPaintsMod;
import net.p4pingvin4ik.NickPaints.util.GradientData;
import net.p4pingvin4ik.NickPaints.util.GradientUtil;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.awt.Color;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Mixin(BakedSheetGlyph.class)
public abstract class BakedGlyphMixin {

    @Shadow @Final private float u0;
    @Shadow @Final private float u1;
    @Shadow @Final private float v0;
    @Shadow @Final private float v1;
    @Shadow @Final private float left;
    @Shadow @Final private float right;
    @Shadow @Final private float up;
    @Shadow @Final private float down;

    @Unique
    private static final float SHADOW_DARKEN_FACTOR = 0.25f;

    @Inject(method = "renderChar", at = @At("HEAD"), cancellable = true)
    private void drawWithPixelGradient(@Coerce Object glyphObj, Matrix4fc matrix, VertexConsumer vertexConsumer, int light, boolean fixedZ, CallbackInfo ci) {
        GradientData gradientData = GradientData.CURRENT_GRADIENT.get();
        // No paint / blank paint → leave the glyph to vanilla (incl. SEE_THROUGH dimming).
        if (gradientData == null || gradientData.paintString == null || gradientData.paintString.trim().isEmpty()) {
            return;
        }

        BakedSheetGlyphInstanceAccessor glyph = (BakedSheetGlyphInstanceAccessor) glyphObj;

        if (NickPaintsMod.PROTECTED_TAG_INSERTION_KEY.equals(glyph.nickpaints$style().getInsertion())) {
            return;
        }

        if (gradientData.isNicknameRestricted()) {
            int glyphIndex = GradientData.NAMETAG_GLYPH_INDEX.get().getAndIncrement();
            if (glyphIndex < gradientData.paintGlyphStart || glyphIndex >= gradientData.paintGlyphEnd) {
                return;
            }
        }

        if (glyph.nickpaints$shadowColor() != 0) {
            drawGradientQuad(gradientData, glyph, matrix, vertexConsumer, light, glyph.nickpaints$shadowOffset(), true);
            if (glyph.nickpaints$style().isBold()) {
                drawGradientQuad(gradientData, glyph, matrix, vertexConsumer, light, glyph.nickpaints$shadowOffset() + glyph.nickpaints$boldOffset(), true);
            }
        }

        drawGradientQuad(gradientData, glyph, matrix, vertexConsumer, light, 0.0f, false);
        if (glyph.nickpaints$style().isBold()) {
            drawGradientQuad(gradientData, glyph, matrix, vertexConsumer, light, glyph.nickpaints$boldOffset(), false);
        }

        ci.cancel();
    }

    @Unique
    private static float gradientSpaceX(float pixelX, GradientData data) {
        if (!data.isNicknameRestricted()) {
            return pixelX;
        }
        AtomicReference<Float> ref = GradientData.NAMETAG_GRADIENT_ANCHOR_X.get();
        ref.compareAndSet(null, pixelX);
        Float anchor = ref.get();
        return pixelX - (anchor != null ? anchor : 0f);
    }

    /**
     * Bake in the prepared glyph color (e.g. 50% alpha for {@code SEE_THROUGH}) so painted
     * nametags dim behind blocks like vanilla.
     */
    @Unique
    private static int applyGlyphColor(int gradientColor, BakedSheetGlyphInstanceAccessor glyph) {
        return ARGB.multiply(gradientColor, glyph.nickpaints$color());
    }

    @Unique
    private void drawGradientQuad(GradientData data, BakedSheetGlyphInstanceAccessor glyph, Matrix4fc matrix, VertexConsumer vertexConsumer, int light, float offset, boolean isShadow) {
        GradientUtil.GradientOptions options = GradientCache.getOptions(data.paintString, data.totalLength);

        float angle = Math.abs(options.angle());

        if (options.isBlockStyle() && (angle == 90.0f || angle == 270.0f)) {
            drawVerticalBlocks(options, data, glyph, matrix, vertexConsumer, light, offset, isShadow);
        } else {
            drawSmoothGradient(data, glyph, matrix, vertexConsumer, light, offset, isShadow);
        }
    }

    @Unique
    private void drawVerticalBlocks(GradientUtil.GradientOptions options, GradientData data, BakedSheetGlyphInstanceAccessor glyph, Matrix4fc matrix, VertexConsumer vertexConsumer, int light, float offset, boolean isShadow) {
        List<Color> colors = options.colors();
        if (colors.isEmpty()) {
            return;
        }

        int numBlocks = colors.size();
        float baseX = glyph.nickpaints$x() + offset;
        float baseY = glyph.nickpaints$y();

        float totalGlyphHeight = this.down - this.up;
        float blockHeight = totalGlyphHeight / numBlocks;

        float totalVRange = this.v1 - this.v0;
        float blockVStep = totalVRange / numBlocks;

        for (int i = 0; i < numBlocks; i++) {
            float normalizedY = (i + 0.5f) * blockHeight;
            int blockColor = GradientUtil.get2DColor(data.paintString, data.totalLength, gradientSpaceX(baseX + this.left, data), normalizedY);

            if (isShadow) {
                blockColor = darken(blockColor);
            }
            blockColor = applyGlyphColor(blockColor, glyph);

            float currentMinY = baseY + this.up + i * blockHeight;
            float currentMaxY = currentMinY + blockHeight;
            float currentMinV = this.v0 + i * blockVStep;
            float currentMaxV = currentMinV + blockVStep;

            vertexConsumer.addVertex(matrix, baseX + left, currentMinY, 0).setColor(blockColor).setUv(u0, currentMinV).setLight(light);
            vertexConsumer.addVertex(matrix, baseX + left, currentMaxY, 0).setColor(blockColor).setUv(u0, currentMaxV).setLight(light);
            vertexConsumer.addVertex(matrix, baseX + right, currentMaxY, 0).setColor(blockColor).setUv(u1, currentMaxV).setLight(light);
            vertexConsumer.addVertex(matrix, baseX + right, currentMinY, 0).setColor(blockColor).setUv(u1, currentMinV).setLight(light);
        }
    }

    @Unique
    private void drawSmoothGradient(GradientData data, BakedSheetGlyphInstanceAccessor glyph, Matrix4fc matrix, VertexConsumer vertexConsumer, int light, float offset, boolean isShadow) {
        float baseX = glyph.nickpaints$x() + offset;
        float baseY = glyph.nickpaints$y();

        float glyphRelativeTopY = 0;
        float glyphRelativeBottomY = down - up;

        int colorTopLeft = GradientUtil.get2DColor(data.paintString, data.totalLength, gradientSpaceX(baseX + left, data), glyphRelativeTopY);
        int colorBottomLeft = GradientUtil.get2DColor(data.paintString, data.totalLength, gradientSpaceX(baseX + left, data), glyphRelativeBottomY);
        int colorBottomRight = GradientUtil.get2DColor(data.paintString, data.totalLength, gradientSpaceX(baseX + right, data), glyphRelativeBottomY);
        int colorTopRight = GradientUtil.get2DColor(data.paintString, data.totalLength, gradientSpaceX(baseX + right, data), glyphRelativeTopY);

        if (isShadow) {
            colorTopLeft = darken(colorTopLeft);
            colorBottomLeft = darken(colorBottomLeft);
            colorBottomRight = darken(colorBottomRight);
            colorTopRight = darken(colorTopRight);
        }

        colorTopLeft = applyGlyphColor(colorTopLeft, glyph);
        colorBottomLeft = applyGlyphColor(colorBottomLeft, glyph);
        colorBottomRight = applyGlyphColor(colorBottomRight, glyph);
        colorTopRight = applyGlyphColor(colorTopRight, glyph);

        vertexConsumer.addVertex(matrix, baseX + left, baseY + up, 0).setColor(colorTopLeft).setUv(u0, v0).setLight(light);
        vertexConsumer.addVertex(matrix, baseX + left, baseY + down, 0).setColor(colorBottomLeft).setUv(u0, v1).setLight(light);
        vertexConsumer.addVertex(matrix, baseX + right, baseY + down, 0).setColor(colorBottomRight).setUv(u1, v1).setLight(light);
        vertexConsumer.addVertex(matrix, baseX + right, baseY + up, 0).setColor(colorTopRight).setUv(u1, v0).setLight(light);
    }

    @Unique
    private int darken(int color) {
        int alpha = (color >> 24) & 0xFF;
        int red = (int) (((color >> 16) & 0xFF) * SHADOW_DARKEN_FACTOR);
        int green = (int) (((color >> 8) & 0xFF) * SHADOW_DARKEN_FACTOR);
        int blue = (int) ((color & 0xFF) * SHADOW_DARKEN_FACTOR);
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }
}
