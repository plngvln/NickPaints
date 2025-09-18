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
import net.p4pingvin4ik.NickPaints.client.CloudSyncManager;
import net.p4pingvin4ik.NickPaints.config.ConfigManager;
import net.p4pingvin4ik.NickPaints.interfaces.IEntityProvider;
import net.p4pingvin4ik.NickPaints.util.GradientUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

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
            String cachedPaint = CloudSyncManager.paintCache.get(player.getUuid());
            if (cachedPaint != null && !cachedPaint.equals("no_paint") && !cachedPaint.equals("fetching")) {
                paintToShow = cachedPaint;
            } else if (cachedPaint == null) {
                CloudSyncManager.queuePaintForPlayer(player.getUuid());
            }
        }

        // If no paint is available, do nothing and return the original text.
        if (paintToShow == null) {
            return originalText;
        }

        String name = originalText.getString();
        MutableText newText = Text.empty(); // Start with an empty, mutable text object.

        for (int i = 0; i < name.length(); i++) {
            // Get the color for this character from our utility.
            int color = GradientUtil.getColor(paintToShow, i, name.length());

            // Create a style with only the calculated color.
            Style style = Style.EMPTY.withColor(TextColor.fromRgb(color));

            // Append a new text component for this single character with its unique style.
            newText.append(Text.literal(String.valueOf(name.charAt(i))).setStyle(style));
        }

        // Return the newly constructed, fully colorized text object.
        // The original renderLabelIfPresent method will now render THIS text instead of the old one.
        return newText;
    }
}