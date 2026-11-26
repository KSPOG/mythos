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
    private float rightKnee,leftKnee;
    CinematicPlayerModel(ModelPart root,boolean slim){super(root,slim);this.slim=slim;}
    @Override public void setupAnim(AbstractClientPlayer entity,float swing,float amount,float age,float yaw,float pitch){
        // PlayerModel instances are reused for many entities and frames. Reset every
        // mutable pose before vanilla animation, including angles vanilla may not reset.
        head.resetPose();hat.resetPose();body.resetPose();
        rightArm.resetPose();leftArm.resetPose();rightLeg.resetPose();leftLeg.resetPose();
        jacket.resetPose();rightSleeve.resetPose();leftSleeve.resetPose();rightPants.resetPose();leftPants.resetPose();
        super.setupAnim(entity,swing,amount,age,yaw,pitch);
        rightKnee=leftKnee=0;
        var mc=Minecraft.getInstance();
        double distance=mc.gameRenderer.getMainCamera().getPosition().distanceToSqr(entity.position());
        lod=CharacterOptions.lod(distance);
        boolean local=entity==mc.player;
        physique=local?CharacterOptions.physique.ordinal():0;
        hair=local&&CharacterOptions.hair;
        if(CharacterOptions.enabled&&CharacterOptions.motion){
            boolean constrained=entity.isUsingItem()||entity.isPassenger()||entity.isSwimming()||entity.isFallFlying()||entity.isCrouching()||attackTime>0||entity.isVisuallySwimming();
            var pose=CharacterMotion.sample(swing,amount,age,constrained);
            if(!constrained){
                // Vanilla chest, shoulders and head share a stable upper-body frame.
                moveUpper(body,pose);moveUpper(head,pose);moveUpper(rightArm,pose);moveUpper(leftArm,pose);
                head.zRot+=pose.headRoll();
                rightArm.xRot+=pose.armPitch();leftArm.xRot-=pose.armPitch();
                rightArm.zRot+=pose.armRoll();leftArm.zRot-=pose.armRoll();
                if(entity.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS).isEmpty()&&entity.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET).isEmpty()){
                    rightKnee=pose.rightKnee();leftKnee=pose.leftKnee();
                }
            }
            hat.copyFrom(head);jacket.copyFrom(body);rightSleeve.copyFrom(rightArm);leftSleeve.copyFrom(leftArm);rightPants.copyFrom(rightLeg);leftPants.copyFrom(leftLeg);
        }
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
            float bend=part.bone()==4?rightKnee:part.bone()==5?leftKnee:0;
            for(var q:part.quads()){
                emit(stack,consumer,q.a(),light,overlay,color,bend);emit(stack,consumer,q.b(),light,overlay,color);
                emit(stack,consumer,q.c(),light,overlay,color,bend);emit(stack,consumer,q.d(),light,overlay,color,bend);
            }
            stack.popPose();
        }
    }
    private static void emit(PoseStack stack,VertexConsumer out,CharacterMesh.Vertex v,int light,int overlay,int color,float bend){
        var p=v.position();var n=v.normal();
        if(bend!=0&&p.y()>5.5F){
            var q=CharacterMotion.bend(p.x(),p.y(),p.z(),bend,5.5F);
            float t=Math.max(0,Math.min(1,(p.y()-5.5F)/2F)),a=bend*t*t*(3-2*t),c=(float)Math.cos(a),s=(float)Math.sin(a);
            p=new CharacterMesh.V(q.x(),q.y(),q.z());n=new CharacterMesh.V(n.x(),c*n.y()-s*n.z(),s*n.y()+c*n.z());
        }
        out.addVertex(stack.last(),p.x()/16,p.y()/16,p.z()/16).setColor(color).setUv(v.u(),v.v())
            .setOverlay(overlay).setLight(light).setNormal(stack.last(),n.x(),n.y(),n.z());
    }
}
