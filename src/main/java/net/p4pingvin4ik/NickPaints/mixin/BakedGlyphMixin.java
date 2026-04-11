package net.p4pingvin4ik.NickPaints.mixin;

import net.minecraft.client.font.BakedGlyph;
import net.minecraft.client.render.VertexConsumer;
import net.p4pingvin4ik.NickPaints.client.GradientCache;
import net.p4pingvin4ik.NickPaints.client.NickPaintsMod;
import net.p4pingvin4ik.NickPaints.util.GradientData;
import net.p4pingvin4ik.NickPaints.util.GradientUtil;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.awt.Color;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Mixin(BakedGlyph.class)
public abstract class BakedGlyphMixin {

    @Shadow @Final private float minU;
    @Shadow @Final private float maxU;
    @Shadow @Final private float minV;
    @Shadow @Final private float maxV;
    @Shadow @Final private float minX;
    @Shadow @Final private float maxX;
    @Shadow @Final private float minY;
    @Shadow @Final private float maxY;

    @Unique
    private static final float SHADOW_DARKEN_FACTOR = 0.25f;

    /**
     * Injects custom drawing logic at the head of the draw() method, effectively overriding it.
     * This method is the entry point for rendering glyphs with a gradient.
     *
     * @param glyph The glyph being drawn.
     * @param matrix The transformation matrix.
     * @param vertexConsumer The vertex consumer to draw to.
     * @param light The light level.
     * @param fixedZ Unused in this implementation.
     * @param ci The callback info, used to cancel the original method.
     */
    @Inject(method = "draw(Lnet/minecraft/client/font/BakedGlyph$DrawnGlyph;Lorg/joml/Matrix4f;Lnet/minecraft/client/render/VertexConsumer;IZ)V", at = @At("HEAD"), cancellable = true)
    private void drawWithPixelGradient(BakedGlyph.DrawnGlyph glyph, Matrix4f matrix, VertexConsumer vertexConsumer, int light, boolean fixedZ, CallbackInfo ci) {
        GradientData gradientData = GradientData.CURRENT_GRADIENT.get();
        if (gradientData == null) {
            return;
        }

        if (gradientData.isNicknameRestricted()) {
            int glyphIndex = GradientData.NAMETAG_GLYPH_INDEX.get().getAndIncrement();
            if (glyphIndex < gradientData.paintGlyphStart || glyphIndex >= gradientData.paintGlyphEnd) {
                return;
            }
        }

        if (NickPaintsMod.PROTECTED_TAG_INSERTION_KEY.equals(glyph.style().getInsertion())) {
            return;
        }

        if (glyph.shadowColor() != 0) {
            drawGradientQuad(gradientData, glyph, matrix, vertexConsumer, light, glyph.shadowOffset(), true);
            if (glyph.style().isBold()) {
                drawGradientQuad(gradientData, glyph, matrix, vertexConsumer, light, glyph.shadowOffset() + glyph.boldOffset(), true);
            }
        }

        drawGradientQuad(gradientData, glyph, matrix, vertexConsumer, light, 0.0f, false);
        if (glyph.style().isBold()) {
            drawGradientQuad(gradientData, glyph, matrix, vertexConsumer, light, glyph.boldOffset(), false);
        }

        ci.cancel();
    }

    /** Horizontal position in gradient space: nametags use an anchor so only the nickname spans the palette. */
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
     * Determines whether to draw a smooth or block-style gradient and calls the appropriate method.
     * This acts as a dispatcher based on the gradient's style options.
     */
    @Unique
    private void drawGradientQuad(GradientData data, BakedGlyph.DrawnGlyph glyph, Matrix4f matrix, VertexConsumer vertexConsumer, int light, float offset, boolean isShadow) {
        GradientUtil.GradientOptions options = GradientCache.getOptions(data.paintString, data.totalLength);

        float angle = Math.abs(options.angle());

        if (options.isBlockStyle() && (angle == 90.0f || angle == 270.0f)) {
            drawVerticalBlocks(options, data, glyph, matrix, vertexConsumer, light, offset, isShadow);
        } else {
            drawSmoothGradient(data, glyph, matrix, vertexConsumer, light, offset, isShadow);
        }
    }

    /**
     * Renders the glyph with a vertical block-style gradient.
     * The glyph is divided into horizontal slices, each filled with a solid color from the gradient.
     */
    @Unique
    private void drawVerticalBlocks(GradientUtil.GradientOptions options, GradientData data, BakedGlyph.DrawnGlyph glyph, Matrix4f matrix, VertexConsumer vertexConsumer, int light, float offset, boolean isShadow) {
        List<Color> colors = options.colors();
        if (colors.isEmpty()) {
            return;
        }

        int numBlocks = colors.size();
        float baseX = glyph.x() + offset;
        float baseY = glyph.y();

        float totalGlyphHeight = this.maxY - this.minY;
        float blockHeight = totalGlyphHeight / numBlocks;

        float totalVRange = this.maxV - this.minV;
        float blockVStep = totalVRange / numBlocks;

        for (int i = 0; i < numBlocks; i++) {
            float normalizedY = (i + 0.5f) * blockHeight;
            int blockColor = GradientUtil.get2DColor(data.paintString, data.totalLength, gradientSpaceX(baseX + this.minX, data), normalizedY);

            if (isShadow) {
                blockColor = darken(blockColor);
            }

            float currentMinY = baseY + this.minY + i * blockHeight;
            float currentMaxY = currentMinY + blockHeight;
            float currentMinV = this.minV + i * blockVStep;
            float currentMaxV = currentMinV + blockVStep;

            vertexConsumer.vertex(matrix, baseX + minX, currentMinY, 0).color(blockColor).texture(minU, currentMinV).light(light);
            vertexConsumer.vertex(matrix, baseX + minX, currentMaxY, 0).color(blockColor).texture(minU, currentMaxV).light(light);
            vertexConsumer.vertex(matrix, baseX + maxX, currentMaxY, 0).color(blockColor).texture(maxU, currentMaxV).light(light);
            vertexConsumer.vertex(matrix, baseX + maxX, currentMinY, 0).color(blockColor).texture(maxU, currentMinV).light(light);
        }
    }

    /**
     * Renders the glyph with a smooth, interpolated gradient.
     * It calculates the color at each of the four corners of the glyph's quad,
     * and the GPU handles the smooth interpolation between them.
     */
    @Unique
    private void drawSmoothGradient(GradientData data, BakedGlyph.DrawnGlyph glyph, Matrix4f matrix, VertexConsumer vertexConsumer, int light, float offset, boolean isShadow) {
        float baseX = glyph.x() + offset;
        float baseY = glyph.y();

        float glyphRelativeTopY = 0;
        float glyphRelativeBottomY = maxY - minY;

        int colorTopLeft = GradientUtil.get2DColor(data.paintString, data.totalLength, gradientSpaceX(baseX + minX, data), glyphRelativeTopY);
        int colorBottomLeft = GradientUtil.get2DColor(data.paintString, data.totalLength, gradientSpaceX(baseX + minX, data), glyphRelativeBottomY);
        int colorBottomRight = GradientUtil.get2DColor(data.paintString, data.totalLength, gradientSpaceX(baseX + maxX, data), glyphRelativeBottomY);
        int colorTopRight = GradientUtil.get2DColor(data.paintString, data.totalLength, gradientSpaceX(baseX + maxX, data), glyphRelativeTopY);

        if (isShadow) {
            colorTopLeft = darken(colorTopLeft);
            colorBottomLeft = darken(colorBottomLeft);
            colorBottomRight = darken(colorBottomRight);
            colorTopRight = darken(colorTopRight);
        }

        vertexConsumer.vertex(matrix, baseX + minX, baseY + minY, 0).color(colorTopLeft).texture(minU, minV).light(light);
        vertexConsumer.vertex(matrix, baseX + minX, baseY + maxY, 0).color(colorBottomLeft).texture(minU, maxV).light(light);
        vertexConsumer.vertex(matrix, baseX + maxX, baseY + maxY, 0).color(colorBottomRight).texture(maxU, maxV).light(light);
        vertexConsumer.vertex(matrix, baseX + maxX, baseY + minY, 0).color(colorTopRight).texture(maxU, minV).light(light);
    }

    /**
     * Darkens a color by a fixed factor. Used for rendering shadows.
     * The alpha channel is preserved.
     *
     * @param color The original color in integer ARGB format.
     * @return The darkened color in integer ARGB format.
     */
    @Unique
    private int darken(int color) {
        int alpha = (color >> 24) & 0xFF;
        int red = (int) (((color >> 16) & 0xFF) * SHADOW_DARKEN_FACTOR);
        int green = (int) (((color >> 8) & 0xFF) * SHADOW_DARKEN_FACTOR);
        int blue = (int) ((color & 0xFF) * SHADOW_DARKEN_FACTOR);
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }
}