package com.ksp.mythospets;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;

/** A registered companion with no autonomous combat or spawning AI. */
public final class MooncatEntity extends PathfinderMob {
    public MooncatEntity(EntityType<? extends MooncatEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void registerGoals() {
        // The authoritative Pets controller owns movement and abilities.
    }
}
