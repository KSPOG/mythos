package com.ksp.mythospets;

import net.minecraft.world.entity.EntityType;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;

/** A registered companion with no autonomous combat or spawning AI. */
public final class MooncatEntity extends PathfinderMob {
    static final int NONE=0, STRETCH=1, GROOM=2, POUNCE=3, BLESS=4;
    private static final EntityDataAccessor<Integer> ACTION=SynchedEntityData.defineId(MooncatEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> START=SynchedEntityData.defineId(MooncatEntity.class,EntityDataSerializers.INT);
    static final int BLESS_DURATION=40, BLESS_IMPACT=18;
    private static final EntityDataAccessor<Integer> RECIPIENT=SynchedEntityData.defineId(MooncatEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> HEALED=SynchedEntityData.defineId(MooncatEntity.class,EntityDataSerializers.BOOLEAN);
    int recipient(){return entityData.get(RECIPIENT);}
    boolean healed(){return entityData.get(HEALED);}
    void blessingRecipient(int id){entityData.set(RECIPIENT,id);}
    void blessingImpact(){entityData.set(HEALED,true);}
    private int quiet,next=100,sequence;
    private double px,py,pz;
    private boolean sampled;
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder){
        super.defineSynchedData(builder);builder.define(ACTION,NONE);builder.define(START,0);builder.define(RECIPIENT,-1);builder.define(HEALED,false);
    }
    int idleAction(){return entityData.get(ACTION);}
    int idleElapsed(){return Math.max(0,(int)level().getGameTime()-entityData.get(START));}
    void beginCombat(int action){
        entityData.set(HEALED,false);
        entityData.set(START,(int)level().getGameTime());
        entityData.set(ACTION,action);
    }
    void endCombat(){
        if(idleAction()>=POUNCE)entityData.set(ACTION,NONE);
        entityData.set(RECIPIENT,-1);entityData.set(HEALED,false);quiet=0;
    }
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
        if(idleAction()>=POUNCE)return; // Combat controller owns the cast lifecycle.
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
