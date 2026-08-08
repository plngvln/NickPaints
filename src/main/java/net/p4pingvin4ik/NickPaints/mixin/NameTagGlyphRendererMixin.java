package net.p4pingvin4ik.NickPaints.mixin;

import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.feature.NameTagFeatureRenderer;
import net.p4pingvin4ik.NickPaints.util.GradientData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.renderer.feature.NameTagFeatureRenderer$GlyphRenderer")
public class NameTagGlyphRendererMixin {

    @Inject(method = "prepare", at = @At("HEAD"))
    private void nickpaints$applyPendingGradient(NameTagFeatureRenderer.Submit submit, Font.DisplayMode displayMode, CallbackInfo ci) {
        // NORMAL and SEE_THROUGH are separate submits that share the same Component.
        // Use get (not remove) so both passes receive paint; map is cleared in finishExecute.
        GradientData data = GradientData.PENDING_NAMETAG_GRADIENTS.get(submit.text());
        if (data != null && data.paintString != null && !data.paintString.trim().isEmpty()) {
            if (data.isNicknameRestricted()) {
                GradientData.NAMETAG_GLYPH_INDEX.get().set(0);
                GradientData.NAMETAG_GRADIENT_ANCHOR_X.get().set(null);
            }
            GradientData.CURRENT_GRADIENT.set(data);
        } else {
            GradientData.CURRENT_GRADIENT.remove();
            GradientData.NAMETAG_GRADIENT_ANCHOR_X.get().set(null);
        }
    }
}
