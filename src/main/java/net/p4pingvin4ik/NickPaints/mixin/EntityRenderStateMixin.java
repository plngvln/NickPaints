package net.p4pingvin4ik.NickPaints.mixin;

import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.Entity;
import net.p4pingvin4ik.NickPaints.interfaces.IEntityProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(EntityRenderState.class)
public class EntityRenderStateMixin implements IEntityProvider {

    @Unique
    private Entity nickpaints_entity;

    @Override
    public Entity getEntity() {
        return this.nickpaints_entity;
    }

    @Override
    public void setEntity(Entity entity) {
        this.nickpaints_entity = entity;
    }
}