package net.p4pingvin4ik.NickPaints.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.p4pingvin4ik.NickPaints.config.ConfigManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererMixin {

    /**
     * Force the local player's own nametag in third-person view only.
     * Vanilla hides it when the entity is the camera entity.
     */
    @Inject(method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;D)Z", at = @At("HEAD"), cancellable = true)
    private void showOwnNametag(LivingEntity livingEntity, double d, CallbackInfoReturnable<Boolean> cir) {
        if (!ConfigManager.CONFIG.showOwnNametag) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (livingEntity == client.player && !client.options.getCameraType().isFirstPerson()) {
            cir.setReturnValue(!client.gui.hud.isHidden() && !livingEntity.isVehicle());
        }
    }
}
