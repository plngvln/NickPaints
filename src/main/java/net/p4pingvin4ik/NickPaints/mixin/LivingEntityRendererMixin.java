package net.p4pingvin4ik.NickPaints.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererMixin {

    /**
     * @reason To force the game to render the local player's own nametag in third-person view.
     * By default, Minecraft's rendering logic in hasLabel() hides the nametag for the player entity
     * that matches the client's player. This mixin intercepts that check.
     */
    @Inject(method = "hasLabel(Lnet/minecraft/entity/LivingEntity;D)Z", at = @At("HEAD"), cancellable = true)
    private void showOwnNametag(LivingEntity livingEntity, double d, CallbackInfoReturnable<Boolean> cir) {
        MinecraftClient client = MinecraftClient.getInstance();

        // Check if the entity being rendered is the client's own player character.
        if (livingEntity == client.player) {
            // If it is, we bypass the original method's logic and return our own value.
            // We still respect the vanilla HUD visibility setting (F1) and ensure the player is not a passenger.
            // This makes the local player's nametag visible, allowing our other mixins to apply the custom paint.
            cir.setReturnValue(MinecraftClient.isHudEnabled() && !livingEntity.hasPassengers());
        }
    }
}