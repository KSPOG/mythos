package com.ksp.mythospets;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;

public final class DragonEntity extends PathfinderMob {
    public DragonEntity(EntityType<? extends DragonEntity> type, Level level) { super(type, level); }
    @Override protected void registerGoals() {}
}
