package com.ksp.mythospets;

import net.minecraft.world.entity.EntityType;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;

/** A registered companion with no autonomous combat or spawning AI. */
public final class MooncatEntity extends PathfinderMob {
    static final int NONE=0, STRETCH=1, GROOM=2;
    private static final EntityDataAccessor<Integer> ACTION=SynchedEntityData.defineId(MooncatEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> START=SynchedEntityData.defineId(MooncatEntity.class,EntityDataSerializers.INT);
    private int quiet,next=100,sequence;
    private double px,py,pz;
    private boolean sampled;
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder){
        super.defineSynchedData(builder);builder.define(ACTION,NONE);builder.define(START,0);
    }
    int idleAction(){return entityData.get(ACTION);}
    int idleElapsed(){return Math.max(0,(int)level().getGameTime()-entityData.get(START));}
    @Override public void tick(){
        super.tick();
        if(level().isClientSide){
            // Body yaw is derived from the interpolated network entity yaw.
            // No-AI companions cannot rely on vanilla AI body rotation.
            setYBodyRot(getYRot());
            return;
        }
        double dx=getX()-px,dy=getY()-py,dz=getZ()-pz;
        boolean moving=!sampled||dx*dx+dy*dy+dz*dz>0.0004;
        px=getX();py=getY();pz=getZ();sampled=true;
        if(moving){quiet=0;if(idleAction()!=NONE)entityData.set(ACTION,NONE);return;}
        if(idleAction()!=NONE){
            if(idleElapsed()>=(idleAction()==STRETCH?100:140)){
                entityData.set(ACTION,NONE);quiet=0;next=100+random.nextInt(141);
            }
        }else if(++quiet>=next){
            entityData.set(START,(int)level().getGameTime());entityData.set(ACTION,1+sequence++%2);
        }
    }
    public MooncatEntity(EntityType<? extends MooncatEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void registerGoals() {
        // The authoritative Pets controller owns movement and abilities.
    }
}
