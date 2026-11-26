package com.ksp.mythospets;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Server-owned idle events; model animation never changes movement or combat. */
public final class DragonEntity extends PathfinderMob {
    static final int NONE=0, FLAP=1, REAR=2, ROAR=3, BREATH=4, GUARD=5;
    private static final EntityDataAccessor<Integer> IDLE =
        SynchedEntityData.defineId(DragonEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> START =
        SynchedEntityData.defineId(DragonEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> FLIGHT=SynchedEntityData.defineId(DragonEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> AIM_X=SynchedEntityData.defineId(DragonEntity.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> AIM_Y=SynchedEntityData.defineId(DragonEntity.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> AIM_Z=SynchedEntityData.defineId(DragonEntity.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> STREAM=SynchedEntityData.defineId(DragonEntity.class,EntityDataSerializers.FLOAT);
    private int quietTicks, nextIdle=100, sequence;
    private double previousX,previousY,previousZ;
    private boolean sampled;

    public DragonEntity(EntityType<? extends DragonEntity> type, Level level) { super(type, level); }
    @Override protected void registerGoals() {}

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(IDLE,NONE);
        builder.define(START,0);
        builder.define(FLIGHT,0);builder.define(AIM_X,0F);builder.define(AIM_Y,0F);builder.define(AIM_Z,1F);builder.define(STREAM,0F);
    }

    static int duration(int action) {
        return switch(action){case FLAP->128;case REAR->160;case ROAR->72;case BREATH->44;case GUARD->24;default->0;};
    }
    int idleAction(){return entityData.get(IDLE);}
    int idleElapsed(){return Math.max(0,(int)level().getGameTime()-entityData.get(START));}

    int flight(){return entityData.get(FLIGHT);}
    void flight(int value){
        entityData.set(FLIGHT,value);
        if(value>0&&idleAction()<BREATH)entityData.set(IDLE,NONE);
    }
    Vec3 breathAim(){return new Vec3(entityData.get(AIM_X),entityData.get(AIM_Y),entityData.get(AIM_Z));}
    float streamLength(){return entityData.get(STREAM);}
    void stream(Vec3 aim,float length){
        entityData.set(AIM_X,(float)aim.x);entityData.set(AIM_Y,(float)aim.y);entityData.set(AIM_Z,(float)aim.z);
        entityData.set(STREAM,length);
    }
    // Model snout is about 0.85 blocks ahead and 1 block above feet.
    Vec3 mouth(){return position().add(Vec3.directionFromRotation(0,getYRot()).scale(0.85)).add(0,0.95,0);}
    void beginCombat(int action){
        entityData.set(START,(int)level().getGameTime());
        entityData.set(IDLE,action);
    }
    void endCombat(){
        if(idleAction()>=BREATH)entityData.set(IDLE,NONE);
        stream(Vec3.ZERO,0);quietTicks=0;
    }
    @Override public void tick(){
        super.tick();
        if(level().isClientSide){
            // Body yaw is derived from the interpolated network entity yaw.
            // No-AI companions cannot rely on vanilla AI body rotation.
            setYBodyRot(getYRot());
            return;
        }
        double dx=getX()-previousX,dy=getY()-previousY,dz=getZ()-previousZ;
        boolean moving=!sampled||dx*dx+dy*dy+dz*dz>0.0004;
        previousX=getX();previousY=getY();previousZ=getZ();sampled=true;
        if(idleAction()>=BREATH||flight()>0)return; // Combat controller owns the cast lifecycle.
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
