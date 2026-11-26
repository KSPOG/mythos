package com.ksp.mythospets;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;

/** Server-owned idle events; model animation never changes movement or combat. */
public final class DragonEntity extends PathfinderMob {
    static final int NONE=0, FLAP=1, REAR=2, ROAR=3;
    private static final EntityDataAccessor<Integer> IDLE =
        SynchedEntityData.defineId(DragonEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> START =
        SynchedEntityData.defineId(DragonEntity.class, EntityDataSerializers.INT);
    private int quietTicks, nextIdle=100, sequence;
    private double previousX,previousY,previousZ;
    private boolean sampled;

    public DragonEntity(EntityType<? extends DragonEntity> type, Level level) { super(type, level); }
    @Override protected void registerGoals() {}

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(IDLE,NONE);
        builder.define(START,0);
    }

    static int duration(int action) {
        return switch(action){case FLAP->128;case REAR->160;case ROAR->72;default->0;};
    }
    int idleAction(){return entityData.get(IDLE);}
    int idleElapsed(){return Math.max(0,(int)level().getGameTime()-entityData.get(START));}

    @Override public void tick(){
        super.tick();
        if(level().isClientSide)return;
        double dx=getX()-previousX,dy=getY()-previousY,dz=getZ()-previousZ;
        boolean moving=!sampled||dx*dx+dy*dy+dz*dz>0.0004;
        previousX=getX();previousY=getY();previousZ=getZ();sampled=true;
        if(moving){
            quietTicks=0;
            if(idleAction()!=NONE)entityData.set(IDLE,NONE);
            return;
        }
        quietTicks++;
        int action=idleAction();
        if(action!=NONE){
            int elapsed=idleElapsed();
            if(action==ROAR&&elapsed==26)
                level().playSound(null,getX(),getY(),getZ(),SoundEvents.ENDER_DRAGON_GROWL,SoundSource.NEUTRAL,0.65F,1.25F);
            if(elapsed>=duration(action)){
                entityData.set(IDLE,NONE);
                quietTicks=0;
                nextIdle=100+random.nextInt(141);
            }
        }else if(quietTicks>=nextIdle){
            entityData.set(START,(int)level().getGameTime());
            entityData.set(IDLE,1+sequence++%3);
        }
    }
}
