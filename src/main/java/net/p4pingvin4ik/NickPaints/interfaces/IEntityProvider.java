package net.p4pingvin4ik.NickPaints.interfaces;

import net.minecraft.world.entity.Entity;

public interface IEntityProvider {
    Entity getEntity();
    void setEntity(Entity entity);
}