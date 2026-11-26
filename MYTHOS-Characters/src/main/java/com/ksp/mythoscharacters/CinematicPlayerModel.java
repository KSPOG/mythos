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
    private int lod;
    CinematicPlayerModel(ModelPart root,boolean slim){super(root,slim);this.slim=slim;}
    @Override public void setupAnim(AbstractClientPlayer entity,float swing,float amount,float age,float yaw,float pitch){
        super.setupAnim(entity,swing,amount,age,yaw,pitch);
        var mc=Minecraft.getInstance();
        double distance=mc.gameRenderer.getMainCamera().getPosition().distanceToSqr(entity.position());
        lod=distance<=9?0:distance<=64?1:distance<=256?2:3;
    }
    @Override public void renderToBuffer(PoseStack stack,VertexConsumer consumer,int light,int overlay,int color){
        ModelPart[] bones={head,body,rightArm,leftArm,rightLeg,leftLeg};
        ModelPart[] outer={hat,jacket,rightSleeve,leftSleeve,rightPants,leftPants};
        for(var part:meshes.computeIfAbsent(lod,k->CharacterMesh.build(slim,k))){
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
