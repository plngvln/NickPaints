package net.p4pingvin4ik.NickPaints.mixin;

import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.Entity;
import net.p4pingvin4ik.NickPaints.interfaces.IEntityProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public class EntityRendererPopulatorMixin {

    @Inject(method = "updateRenderState", at = @At("TAIL"))
    private void populateEntityInState(Entity entity, EntityRenderState state, float tickDelta, CallbackInfo ci) {
        ((IEntityProvider) state).setEntity(entity);
    }
}