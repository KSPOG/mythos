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
        boolean light=switch(d.owner()){case MAGE,NECROMANCER,RANGER,ROGUE,HEXSLINGER,SUMMONER->true;default->false;};
        int tier=d.tier().ordinal();
        float inflate=d.slot()==Slot.LEGS?.35F:.6F;
        MeshDefinition mesh=HumanoidModel.createMesh(new CubeDeformation(inflate),0);
        PartDefinition root=mesh.getRoot();
        PartDefinition head=root.getChild("head"),body=root.getChild("body");
        PartDefinition right=root.getChild("right_arm"),left=root.getChild("left_arm");
        PartDefinition rl=root.getChild("right_leg"),ll=root.getChild("left_leg");
        // Use the existing vanilla atlas islands for trim and shell. Geometry follows the animated parent bones.
        if(d.slot()==Slot.HEAD){
            for(int i=0;i<5;i++){
                float y=-8.5F+i*.52F,w=3.8F+i*.18F;
                add(head,"crown"+i,0,0,-w,y,-w,2*w,.5F,2*w,0);
            }
            for(int side:new int[]{-1,1}){
                float x=side<0?-4.75F:3.65F;
                add(head,"cheek"+side,0,8,x,-5.7F,-4.7F,1.1F,5.2F,2.0F,0);
                if(tier>=2)for(int i=0;i<5;i++)add(head,"crest"+side+"_"+i,8,0,x+side*i*.16F,-8.4F-i*.52F,-1.1F+i*.2F,.85F,.7F,1.4F,0);
            }
            add(head,"brow",8,0,-4.5F,-6.8F,-4.9F,9,1.0F,.8F,0);
        }
        if(d.slot()==Slot.CHEST){
            for(int i=0;i<5;i++){
                float y=.5F+i*2.05F,w=light?3.7F:4.45F-i*.17F;
                add(body,"breast"+i,20,20,-w,y,-3.05F+i*.10F,w*2,1.85F,1.0F,0);
            }
            add(body,"belt",20,24,-4.35F,9.6F,-3.1F,8.7F,1.45F,6.2F,0);
            for(int side:new int[]{-1,1}){
                PartDefinition arm=side<0?right:left;
                float center=side<0?-1:1;
                int plates=light?2:4+tier/2;
                for(int i=0;i<plates;i++){
                    float w=(light?2.6F:3.1F+tier*.12F)-i*.18F;
                    add(arm,"pauldron"+i,40,20,center-w,-2.5F+i*1.05F,-w,2*w,1.0F,2*w,0);
                }
                add(arm,"bracer",40,24,center-2.25F,5.1F,-2.45F,4.5F,3.5F,4.9F,0);
            }
        }
        if(d.slot()==Slot.LEGS||d.slot()==Slot.FEET){
            for(PartDefinition leg:new PartDefinition[]{rl,ll}){
                if(d.slot()==Slot.LEGS){
                    for(int i=0;i<4;i++)add(leg,"thigh"+i,4,20,-2.35F,i*1.3F,-2.65F,4.7F,1.15F,1.0F,0);
                    add(leg,"knee",4,24,-2.1F,4.8F,-3.0F,4.2F,2.15F,1.3F,0);
                }else{
                    for(int i=0;i<4;i++)add(leg,"greave"+i,4,20,-2.25F,5.5F+i*1.25F,-2.7F,4.5F,1.05F,1.05F,0);
                    add(leg,"toe",4,24,-2.6F,10.2F,-3.5F,5.2F,1.8F,5.9F,0);
                }
            }
        }
        model=new HumanoidModel<>(LayerDefinition.create(mesh,64,32).bakeRoot());
    }
    private static void add(PartDefinition p,String name,int u,int v,float x,float y,float z,float w,float h,float depth,float rot){
        p.addOrReplaceChild(name,CubeListBuilder.create().texOffs(u,v).addBox(x,y,z,w,h,depth),net.minecraft.client.model.geom.PartPose.rotation(0,0,rot));
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
