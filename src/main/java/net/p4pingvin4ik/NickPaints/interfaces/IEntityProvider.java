package net.p4pingvin4ik.NickPaints.interfaces;

import net.minecraft.entity.Entity;

public interface IEntityProvider {
    Entity getEntity();
    void setEntity(Entity entity);
}