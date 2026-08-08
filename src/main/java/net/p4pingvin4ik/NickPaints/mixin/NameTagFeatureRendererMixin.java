package net.p4pingvin4ik.NickPaints.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.feature.NameTagFeatureRenderer;
import net.p4pingvin4ik.NickPaints.util.GradientData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(NameTagFeatureRenderer.class)
public class NameTagFeatureRendererMixin {

    @WrapOperation(
            method = "buildGroup",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/Font$PreparedText;visit(Lnet/minecraft/client/gui/Font$GlyphVisitor;)V"
            )
    )
    private void nickpaints$clearGradientAfterVisit(Font.PreparedText preparedText, Font.GlyphVisitor visitor, Operation<Void> original) {
        try {
            original.call(preparedText, visitor);
        } finally {
            GradientData.CURRENT_GRADIENT.remove();
            GradientData.NAMETAG_GRADIENT_ANCHOR_X.get().set(null);
        }
    }
}
