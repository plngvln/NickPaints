package net.p4pingvin4ik.NickPaints.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.p4pingvin4ik.NickPaints.client.NickPaintsMod;
import net.p4pingvin4ik.NickPaints.client.WebSocketManager;
import net.p4pingvin4ik.NickPaints.config.ConfigManager;
import net.p4pingvin4ik.NickPaints.interfaces.IEntityProvider;
import net.p4pingvin4ik.NickPaints.util.GradientUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import net.minecraft.text.PlainTextContent;
/**
 * This mixin uses a more compatible approach to apply gradients. Instead of cancelling the render method
 * and reimplementing it, we modify the `Text` object just before it's rendered. This preserves
 * all vanilla rendering logic (including depth, layers, and shadows) and drastically improves
 * compatibility with other mods like Iris.
 */
@Mixin(value = EntityRenderer.class,priority = 990)
public abstract class EntityRendererMixin<T extends Entity, S extends EntityRenderState> {

    /**
     * This injection point targets the `Text` variable right before it is used for rendering.
     * We receive the original text, and we must return a new (or modified) text.
     *
     * @param originalText The original nametag text.
     * @param state The render state, used to get the entity.
     * @return A new, colorized Text object, or the original if no paint is applied.
     */
    @ModifyVariable(method = "renderLabelIfPresent", at = @At("HEAD"), argsOnly = true)
    private Text modifyNametagText(Text originalText, S state) {
        Entity entity = ((IEntityProvider) state).getEntity();
        if (!(entity instanceof PlayerEntity player) || state.nameLabelPos == null) {
            return originalText; // Return original if not a valid player
        }

        if (!ConfigManager.CONFIG.isRenderingEnabledFor(player.getUuid())) {
            return originalText; // Return original if rendering is disabled
        }

        String paintToShow = null;
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

        // If no paint is available, do nothing and return the original text.
        if (paintToShow == null) {
            return originalText;
        }

        int totalLengthForGradient = calculatePaintableLength(originalText);
        if (totalLengthForGradient == 0) {
            return originalText;
        }

        MutableText newText = Text.empty();
        processTextComponent(originalText, newText, paintToShow, totalLengthForGradient, 0);

        return newText;
    }

    private int processTextComponent(Text component, MutableText builder, String paint, int totalLength, int paintedChars) {
        if (NickPaintsMod.PROTECTED_TAG_INSERTION_KEY.equals(component.getStyle().getInsertion())) {
            builder.append(component.copy());
            return paintedChars;
        }

        if (component.getContent() instanceof PlainTextContent literalContent) {
            String text = literalContent.string();
            for (int i = 0; i < text.length(); i++) {
                int color = GradientUtil.getColor(paint, paintedChars + i, totalLength);
                Style originalStyle = component.getStyle();
                Style newStyle = originalStyle.withColor(TextColor.fromRgb(color));
                builder.append(Text.literal(String.valueOf(text.charAt(i))).setStyle(newStyle));
            }
            paintedChars += text.length();
        } else {
            builder.append(component.copy());
        }

        for (Text sibling : component.getSiblings()) {
            paintedChars = processTextComponent(sibling, builder, paint, totalLength, paintedChars);
        }

        return paintedChars;
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