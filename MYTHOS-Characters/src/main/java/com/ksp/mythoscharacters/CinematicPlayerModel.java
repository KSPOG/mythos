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
    CinematicPlayerModel(ModelPart root,boolean slim){super(root,slim);this.slim=slim;}
    @Override public void setupAnim(AbstractClientPlayer entity,float swing,float amount,float age,float yaw,float pitch){
        super.setupAnim(entity,swing,amount,age,yaw,pitch);
        var mc=Minecraft.getInstance();
        double distance=mc.gameRenderer.getMainCamera().getPosition().distanceToSqr(entity.position());
        lod=CharacterOptions.lod(distance);
        boolean local=entity==mc.player;
        physique=local?CharacterOptions.physique.ordinal():0;
        hair=local&&CharacterOptions.hair;
        if(CharacterOptions.motion){
            var pose=CharacterMotion.sample(swing,amount,age,entity.isUsingItem()||entity.isPassenger()||entity.isSwimming()||entity.isFallFlying()||entity.isCrouching()||attackTime>0);
            body.yRot+=pose.chestYaw();body.zRot+=pose.chestRoll();head.zRot+=pose.headRoll();
            rightArm.zRot+=pose.armRoll();leftArm.zRot-=pose.armRoll();
            rightLeg.zRot+=pose.legRoll();leftLeg.zRot-=pose.legRoll();
            hat.copyFrom(head);jacket.copyFrom(body);rightSleeve.copyFrom(rightArm);leftSleeve.copyFrom(leftArm);rightPants.copyFrom(rightLeg);leftPants.copyFrom(leftLeg);
        }
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
            for(var q:part.quads()){
                emit(stack,consumer,q.a(),light,overlay,color);emit(stack,consumer,q.b(),light,overlay,color);
                emit(stack,consumer,q.c(),light,overlay,color);emit(stack,consumer,q.d(),light,overlay,color);
            }
            stack.popPose();
        }
    }
    private static void emit(PoseStack stack,VertexConsumer out,CharacterMesh.Vertex v,int light,int overlay,int color){
        var p=v.position();var n=v.normal();
        out.addVertex(stack.last(),p.x()/16,p.y()/16,p.z()/16).setColor(color).setUv(v.u(),v.v())
            .setOverlay(overlay).setLight(light).setNormal(stack.last(),n.x(),n.y(),n.z());
    }
}
