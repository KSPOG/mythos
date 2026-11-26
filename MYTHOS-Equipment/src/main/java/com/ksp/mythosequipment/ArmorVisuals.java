package com.ksp.mythosequipment;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import com.ksp.mythosequipment.EquipmentRules.*;
import java.util.*;

final class ArmorVisuals implements IClientItemExtensions {
    private final java.util.function.Supplier<Definition> definition;
    private PolygonModel model;
    ArmorVisuals(java.util.function.Supplier<Definition> definition){this.definition=definition;}
    private static ModelPart skeleton(){
        var mesh=new MeshDefinition();var root=mesh.getRoot();
        for(String name:List.of("head","hat","body","right_arm","left_arm","right_leg","left_leg"))
            root.addOrReplaceChild(name,CubeListBuilder.create(),net.minecraft.client.model.geom.PartPose.ZERO);
        return LayerDefinition.create(mesh,128,64).bakeRoot();
    }
    private static final class PolygonModel extends HumanoidModel<LivingEntity>{
        private final List<List<ArmorPolygon.Piece>> levels;
        private int lod;
        PolygonModel(Definition d){super(skeleton());levels=List.of(ArmorPolygon.build(d,0),ArmorPolygon.build(d,1),ArmorPolygon.build(d,2));}
        @Override public void renderToBuffer(PoseStack stack,VertexConsumer out,int light,int overlay,int color){
            ModelPart[] bones={head,body,rightArm,leftArm,rightLeg,leftLeg};
            for(var piece:levels.get(lod)){
                ModelPart bone=bones[piece.bone()];if(!bone.visible)continue;
                stack.pushPose();bone.translateAndRotate(stack);
                for(var f:piece.faces()){
                    vertex(stack,out,f.a(),f,light,overlay,color,.1F,.1F);
                    vertex(stack,out,f.b(),f,light,overlay,color,.9F,.1F);
                    vertex(stack,out,f.c(),f,light,overlay,color,.9F,.9F);
                    vertex(stack,out,f.c(),f,light,overlay,color,.9F,.9F);
                }
                stack.popPose();
            }
        }
        private static void vertex(PoseStack stack,VertexConsumer out,ArmorPolygon.V v,ArmorPolygon.Face f,int light,int overlay,int color,float u,float w){
            out.addVertex(stack.last(),v.x()/16,v.y()/16,v.z()/16).setColor(color)
                .setUv((f.material()+u)/8F,w*.5F).setOverlay(overlay).setLight(light).setNormal(stack.last(),f.nx(),f.ny(),f.nz());
        }
    }
    @Override public HumanoidModel<?> getHumanoidArmorModel(LivingEntity entity,ItemStack stack,EquipmentSlot slot,HumanoidModel<?> original){
        if(model==null){Definition d=definition.get();if(d==null)return original;model=new PolygonModel(d);}
        double distance=Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().distanceToSqr(entity.position());
        model.lod=distance<=64?0:distance<=256?1:2;
        model.head.copyFrom(original.head);model.hat.copyFrom(original.hat);model.body.copyFrom(original.body);
        model.rightArm.copyFrom(original.rightArm);model.leftArm.copyFrom(original.leftArm);
        model.rightLeg.copyFrom(original.rightLeg);model.leftLeg.copyFrom(original.leftLeg);
        model.setAllVisible(false);
        switch(slot){case HEAD->model.head.visible=true;case CHEST->{model.body.visible=true;model.rightArm.visible=true;model.leftArm.visible=true;}case LEGS,FEET->{model.rightLeg.visible=true;model.leftLeg.visible=true;}default->{}}
        model.young=original.young;model.crouching=original.crouching;model.riding=original.riding;
        return model;
    }
}
