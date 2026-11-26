package com.ksp.mythoscharacters;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import java.util.*;
final class CinematicPlayerModel extends PlayerModel<AbstractClientPlayer> {
    private final boolean slim;
    private final Map<Integer,List<CharacterMesh.Part>> meshes=new HashMap<>();
    private int lod, physique;
    private boolean hair;
    private float rightKnee,leftKnee,rightElbow,leftElbow;
    private final Map<AbstractClientPlayer,CharacterMotion.Controller> controllers=new WeakHashMap<>();
    CinematicPlayerModel(ModelPart root,boolean slim){super(root,slim);this.slim=slim;}
    @Override public void setupAnim(AbstractClientPlayer entity,float swing,float amount,float age,float yaw,float pitch){
        // PlayerModel instances are reused for many entities and frames. Reset every
        // mutable pose before vanilla animation, including angles vanilla may not reset.
        head.resetPose();hat.resetPose();body.resetPose();
        rightArm.resetPose();leftArm.resetPose();rightLeg.resetPose();leftLeg.resetPose();
        jacket.resetPose();rightSleeve.resetPose();leftSleeve.resetPose();rightPants.resetPose();leftPants.resetPose();
        super.setupAnim(entity,swing,amount,age,yaw,pitch);
        rightKnee=leftKnee=rightElbow=leftElbow=0;
        var mc=Minecraft.getInstance();
        double distance=mc.gameRenderer.getMainCamera().getPosition().distanceToSqr(entity.position());
        lod=CharacterOptions.lod(distance);
        boolean local=entity==mc.player;
        physique=local?CharacterOptions.physique.ordinal():0;
        hair=local&&CharacterOptions.hair;
        if(CharacterOptions.enabled&&CharacterOptions.motion){
            boolean reserved=entity.isUsingItem()||entity.isPassenger()||entity.isSwimming()||entity.isFallFlying()||attackTime>0||entity.isVisuallySwimming()||entity.isSleeping()||entity.onClimbable();
            var pose=controllers.computeIfAbsent(entity,e->new CharacterMotion.Controller()).update(age,swing,amount,entity.onGround(),entity.isCrouching(),entity.isSprinting(),(float)entity.getDeltaMovement().y,reserved);
            if(!reserved){
                boolean armoredLegs=!entity.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS).isEmpty()||!entity.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET).isEmpty();
                boolean armoredArms=!entity.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).isEmpty();
                boolean emptyHands=entity.getMainHandItem().isEmpty()&&entity.getOffhandItem().isEmpty();
                // Use neutral model pivots; vanilla crouch offsets must not be applied twice.
                body.resetPose();rightArm.resetPose();leftArm.resetPose();rightLeg.resetPose();leftLeg.resetPose();
                float headX=head.xRot,headY=head.yRot;head.resetPose();head.xRot=headX;head.yRot=headY;
                // Armored legs stay rigid until equipment supports weighted deformation.
                if(armoredLegs){
                    pose=new CharacterMotion.Pose(pose.chestYaw(),pose.chestRoll(),pose.chestPitch(),pose.headRoll(),0,
                        pose.rightHip()*.72F,pose.leftHip()*.72F,0,0,pose.rightShoulder(),pose.leftShoulder(),pose.rightElbow(),pose.leftElbow(),pose.armSpread());
                }
                moveUpper(body,pose);moveUpper(head,pose);moveUpper(rightArm,pose);moveUpper(leftArm,pose);
                head.xRot-=pose.chestPitch()*.8F;head.zRot+=pose.headRoll();
                rightArm.xRot+=pose.rightShoulder();leftArm.xRot+=pose.leftShoulder();
                rightArm.zRot+=pose.armSpread();leftArm.zRot-=pose.armSpread();
                rightLeg.y+=pose.drop();leftLeg.y+=pose.drop();
                rightLeg.xRot=pose.rightHip();leftLeg.xRot=pose.leftHip();
                rightKnee=pose.rightKnee();leftKnee=pose.leftKnee();
                if(!armoredArms&&emptyHands){rightElbow=pose.rightElbow();leftElbow=pose.leftElbow();}
            }
            hat.copyFrom(head);jacket.copyFrom(body);rightSleeve.copyFrom(rightArm);leftSleeve.copyFrom(leftArm);rightPants.copyFrom(rightLeg);leftPants.copyFrom(leftLeg);
        }else controllers.remove(entity);
    }
    private static void moveUpper(ModelPart part,CharacterMotion.Pose pose){
        var p=CharacterMotion.aroundPelvis(part.x,part.y,part.z,pose);
        part.x=p.x();part.y=p.y();part.z=p.z();
        part.xRot+=pose.chestPitch();part.yRot+=pose.chestYaw();part.zRot+=pose.chestRoll();
    }
    @Override public void renderToBuffer(PoseStack stack,VertexConsumer consumer,int light,int overlay,int color){
        if(!CharacterOptions.enabled){super.renderToBuffer(stack,consumer,light,overlay,color);return;}
        ModelPart[] bones={head,body,rightArm,leftArm,rightLeg,leftLeg};
        ModelPart[] outer={hat,jacket,rightSleeve,leftSleeve,rightPants,leftPants};
        for(var part:meshes.computeIfAbsent(lod+4*physique+(hair?12:0),k->CharacterMesh.build(slim,lod,physique,hair))){
            ModelPart bone=bones[part.bone()];
            if(!bone.visible||(part.overlay()&&!outer[part.bone()].visible))continue;
            stack.pushPose();
            bone.translateAndRotate(stack);
            float bend=switch(part.bone()){case 2->rightElbow;case 3->leftElbow;case 4->rightKnee;case 5->leftKnee;default->0;};
            float joint=part.bone()==2||part.bone()==3?4.3F:5.5F;
            for(var q:part.quads()){
                emit(stack,consumer,q.a(),light,overlay,color,bend,joint);emit(stack,consumer,q.b(),light,overlay,color,bend,joint);
                emit(stack,consumer,q.c(),light,overlay,color,bend,joint);emit(stack,consumer,q.d(),light,overlay,color,bend,joint);
            }
            stack.popPose();
        }
    }
    private static void emit(PoseStack stack,VertexConsumer out,CharacterMesh.Vertex v,int light,int overlay,int color,float bend,float joint){
        var p=v.position();var n=v.normal();
        if(bend!=0&&p.y()>joint-.55F){
            var q=CharacterMotion.bend(p.x(),p.y(),p.z(),bend,joint);
            var normal=CharacterMotion.bendNormal(n.x(),n.y(),n.z(),bend,CharacterMotion.jointWeight(p.y(),joint));
            p=new CharacterMesh.V(q.x(),q.y(),q.z());n=new CharacterMesh.V(normal.x(),normal.y(),normal.z());
        }
        out.addVertex(stack.last(),p.x()/16,p.y()/16,p.z()/16).setColor(color).setUv(v.u(),v.v())
            .setOverlay(overlay).setLight(light).setNormal(stack.last(),n.x(),n.y(),n.z());
    }
}
