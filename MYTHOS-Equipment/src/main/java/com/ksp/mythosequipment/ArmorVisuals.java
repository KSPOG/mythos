package com.ksp.mythosequipment;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import com.ksp.mythosequipment.EquipmentRules.*;

final class ArmorVisuals implements IClientItemExtensions {
    private final java.util.function.Supplier<Definition> definition;
    private HumanoidModel<LivingEntity> model;
    ArmorVisuals(java.util.function.Supplier<Definition> definition){this.definition=definition;}
    private void bake(Definition d){
        // Empty articulated skeleton: no vanilla armor shell underneath the custom pieces.
        MeshDefinition mesh=new MeshDefinition();
        PartDefinition root=mesh.getRoot();
        var empty=CubeListBuilder.create();
        var head=root.addOrReplaceChild("head",empty,net.minecraft.client.model.geom.PartPose.ZERO);
        root.addOrReplaceChild("hat",CubeListBuilder.create(),net.minecraft.client.model.geom.PartPose.ZERO);
        var body=root.addOrReplaceChild("body",CubeListBuilder.create(),net.minecraft.client.model.geom.PartPose.ZERO);
        var right=root.addOrReplaceChild("right_arm",CubeListBuilder.create(),net.minecraft.client.model.geom.PartPose.offset(-5,2,0));
        var left=root.addOrReplaceChild("left_arm",CubeListBuilder.create(),net.minecraft.client.model.geom.PartPose.offset(5,2,0));
        var rl=root.addOrReplaceChild("right_leg",CubeListBuilder.create(),net.minecraft.client.model.geom.PartPose.offset(-1.9F,12,0));
        var ll=root.addOrReplaceChild("left_leg",CubeListBuilder.create(),net.minecraft.client.model.geom.PartPose.offset(1.9F,12,0));
        int t=d.tier().ordinal();
        boolean heavy=d.owner()==ClassSet.HEAVY_KNIGHT||d.owner()==ClassSet.WARRIOR||d.owner()==ClassSet.CLERIC;
        float shoulder=switch(d.owner()){case HEAVY_KNIGHT->3.8F;case WARRIOR->3.35F;case CLERIC->3.0F;case ROGUE->2.25F;default->2.65F;};
        if(d.slot()==Slot.HEAD){
            // Beveled helmet: radial panels rather than an inflated head cube.
            for(int k=0;k<12;k++){
                double a=k*Math.PI/6;
                panel(head,"shell"+k,0,32,(float)Math.sin(a)*4.1F,-8.1F,(float)Math.cos(a)*4.1F,2.28F,2.5F,.7F,0,(float)a,0);
                if(k<4||k>8)panel(head,"neck"+k,16,32,(float)Math.sin(a)*4.25F,-5.8F,(float)Math.cos(a)*4.25F,2.3F,4.9F,.75F,0,(float)a,0);
            }
            for(int k=0;k<5;k++){
                float w=4.15F-k*.56F;
                panel(head,"cap"+k,32,32,0,-8.4F-k*.31F,0,w*2,.5F,w*2,0,0,0);
            }
            for(int side:new int[]{-1,1}){
                panel(head,"cheek"+side,16,32,side*3.65F,-5.7F,-4.1F,1.1F,4.9F,.95F,0,0,side*.22F);
                if(t>=2)for(int k=0;k<4;k++)panel(head,"crest"+side+"_"+k,48,32,side*(3.6F+k*.23F),-8.7F-k*.62F,-.7F,.85F,1.1F,2.3F,0,0,side*.35F);
                if(d.owner()==ClassSet.NECROMANCER||d.owner()==ClassSet.SUMMONER)for(int k=0;k<4;k++)panel(head,"horn"+side+"_"+k,64,32,side*(3.5F+k*.65F),-8.5F-k*.65F,.8F-k*.18F,1.15F-k*.16F,1.25F,1.15F-k*.16F,0,0,side*.55F);
            }
            panel(head,"brow",48,32,0,-7.1F,-4.5F,8.2F,.65F,.8F,0,0,0);
        }
        if(d.slot()==Slot.CHEST){
            for(int k=0;k<6;k++){
                float y=k*1.8F,w=(heavy?4.7F:4.25F)-k*.15F;
                for(int side:new int[]{-1,1})panel(body,"breast"+side+"_"+k,0,32,side*w*.48F,y,-2.65F,w,1.95F,1.05F,-.08F,side*.19F,side*.07F);
                panel(body,"back"+k,16,32,0,y,2.3F,w*1.9F,1.95F,.95F,.08F,0,0);
            }
            for(int side:new int[]{-1,1}){
                panel(body,"flank"+side,16,32,side*4.15F,2,0,1.1F,8.8F,4.2F,0,0,side*.06F);
                var arm=side<0?right:left;
                float cx=side<0?-1:1;
                for(int k=0;k<4+t/2;k++){
                    float w=shoulder-k*.19F;
                    if(d.owner()==ClassSet.ROGUE&&side>0)w*=.8F;
                    for(int a=0;a<8;a++){
                        double angle=a*Math.PI/4;
                        panel(arm,"pauldron"+k+"_"+a,32,32,cx+(float)Math.sin(angle)*w,-2.7F+k*.88F,(float)Math.cos(angle)*w,2*w*.47F,1.15F,.68F,0,(float)angle,0);
                    }
                }
                for(int k=0;k<4;k++)panel(arm,"bracer"+k,16,32,cx,5.3F+k*1.05F,0,4.5F-k*.1F,1.2F,4.8F-k*.13F,0,0,0);
                if(!heavy)panel(body,"robe"+side,80,32,side*2.1F,10,-2.1F,3.65F,5.0F,.65F,-.12F,0,side*.11F);
            }
            panel(body,"belt",48,32,0,10.1F,0,8.8F,1.45F,5.8F,0,0,0);
            panel(body,"medallion",64,32,0,2.5F,-3.55F,1.7F+t*.18F,1.7F+t*.18F,.7F,0,0,.785F);
        }
        if(d.slot()==Slot.LEGS||d.slot()==Slot.FEET){
            for(var leg:new PartDefinition[]{rl,ll}){
                int start=d.slot()==Slot.LEGS?0:6,end=d.slot()==Slot.LEGS?7:12;
                for(int k=start;k<end;k++)for(int a=0;a<8;a++){
                    double angle=a*Math.PI/4;float w=2.45F+(heavy?.23F:0);
                    panel(leg,"leg"+k+"_"+a,k%2==0?0:16,32,(float)Math.sin(angle)*w,k,(float)Math.cos(angle)*w,w*.85F,1.12F,.66F,0,(float)angle,0);
                }
                if(d.slot()==Slot.LEGS)panel(leg,"knee",48,32,0,4.8F,-2.8F,3.8F,2.6F,1.2F,0,0,0);
                else{
                    for(int k=0;k<4;k++)panel(leg,"toe"+k,k%2==0?32:48,32,0,10.1F+k*.25F,-1.0F-k*.35F,4.8F-k*.25F,.9F,5.7F-k*.15F,0,0,0);
                }
            }
        }
        model=new HumanoidModel<>(LayerDefinition.create(mesh,128,64).bakeRoot());
    }
    private static void panel(PartDefinition parent,String name,int u,int v,float x,float y,float z,float w,float h,float depth,float rx,float ry,float rz){
        parent.addOrReplaceChild(name,CubeListBuilder.create().texOffs(u,v).addBox(-w/2,0,-depth/2,w,h,depth),net.minecraft.client.model.geom.PartPose.offsetAndRotation(x,y,z,rx,ry,rz));
    }
    @Override public HumanoidModel<?> getHumanoidArmorModel(LivingEntity entity,ItemStack stack,EquipmentSlot slot,HumanoidModel<?> original){
        if(model==null){
            Definition d=definition.get();
            if(d==null)return original;
            bake(d);
        }
        model.head.copyFrom(original.head);model.hat.copyFrom(original.hat);model.body.copyFrom(original.body);
        model.rightArm.copyFrom(original.rightArm);model.leftArm.copyFrom(original.leftArm);
        model.rightLeg.copyFrom(original.rightLeg);model.leftLeg.copyFrom(original.leftLeg);
        model.setAllVisible(false);
        switch(slot){case HEAD->{model.head.visible=true;model.hat.visible=true;}case CHEST->{model.body.visible=true;model.rightArm.visible=true;model.leftArm.visible=true;}case LEGS->{model.body.visible=true;model.rightLeg.visible=true;model.leftLeg.visible=true;}case FEET->{model.rightLeg.visible=true;model.leftLeg.visible=true;}default->{}}
        model.young=original.young;model.crouching=original.crouching;model.riding=original.riding;
        return model;
    }
}
