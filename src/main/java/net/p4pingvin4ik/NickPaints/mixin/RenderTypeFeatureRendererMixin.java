package net.p4pingvin4ik.NickPaints.mixin;

import net.minecraft.client.renderer.feature.FeatureFrameContext;
import net.minecraft.client.renderer.feature.NameTagFeatureRenderer;
import net.minecraft.client.renderer.feature.RenderTypeFeatureRenderer;
import net.p4pingvin4ik.NickPaints.util.GradientData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderTypeFeatureRenderer.class)
public class RenderTypeFeatureRendererMixin {

    @Inject(method = "finishExecute", at = @At("HEAD"))
    private void nickpaints$clearPendingNametagGradients(FeatureFrameContext context, CallbackInfo ci) {
        if ((Object) this instanceof NameTagFeatureRenderer) {
            GradientData.PENDING_NAMETAG_GRADIENTS.clear();
        }
    }
}
