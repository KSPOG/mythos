package com.ksp.mythospets;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.Mth;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "mythospets", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class PetClient {
    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(PetEntities.DRAGON.get(), DragonRenderer::new);
        event.registerEntityRenderer(PetEntities.MOONCAT.get(), MooncatRenderer::new);
    }

    private static final class DragonRenderer extends MobRenderer<DragonEntity, DragonMeshModel> {
        DragonRenderer(EntityRendererProvider.Context context) {
            super(context, new DragonMeshModel(), 0.4F);
        }
        @Override public void render(DragonEntity entity,float entityYaw,float partialTick,PoseStack stack,MultiBufferSource buffers,int light){
            super.render(entity,entityYaw,partialTick,stack,buffers,light);
            float length=entity.streamLength();
            Vec3 aim=entity.breathAim().normalize();
            if(length<=0.03F||aim.lengthSqr()<0.5)return;
            Vec3 start=entity.mouth().subtract(entity.position());
            Vec3 right=aim.cross(new Vec3(0,1,0)).normalize();
            if(right.lengthSqr()<0.1)right=new Vec3(1,0,0);
            Vec3 up=right.cross(aim).normalize();
            var out=buffers.getBuffer(RenderType.lightning());
            float age=entity.tickCount+partialTick;
            // Three translucent, animated volume shells. Unlike particles,
            // the flame has continuous connected geometry along its full length.
            for(int shell=0;shell<3;shell++){
                int segments=18,sides=12;
                for(int i=0;i<segments;i++)for(int j=0;j<sides;j++){
                    flameVertex(out,stack,start,aim,right,up,length,age,shell,i/(float)segments,j/(float)sides);
                    flameVertex(out,stack,start,aim,right,up,length,age,shell,(i+1)/(float)segments,j/(float)sides);
                    flameVertex(out,stack,start,aim,right,up,length,age,shell,(i+1)/(float)segments,(j+1)/(float)sides);
                    flameVertex(out,stack,start,aim,right,up,length,age,shell,i/(float)segments,(j+1)/(float)sides);
                }
            }
        }
        private static void flameVertex(VertexConsumer out,PoseStack stack,Vec3 start,Vec3 aim,Vec3 right,Vec3 up,float length,float age,int shell,float t,float u){
            double angle=u*Math.PI*2;
            double ripple=1+0.14*Math.sin(t*25-age*0.85+angle*3)+0.09*Math.sin(t*48-age*1.2-angle*5);
            double radius=(0.065+t*length*0.16)*ripple*(shell==0?0.35:shell==1?0.68:1);
            radius*=1-0.65*Math.pow(t,8);
            Vec3 v=start.add(aim.scale(t*length)).add(right.scale(Math.cos(angle)*radius)).add(up.scale(Math.sin(angle)*radius));
            int alpha=(int)((shell==0?190:shell==1?105:48)*(1-t*0.75)*(0.85+0.15*Math.sin(age*0.8-t*16)));
            int red=255,green=shell==0?232:shell==1?125:45,blue=shell==0?125:shell==1?18:4;
            out.addVertex(stack.last().pose(),(float)v.x,(float)v.y,(float)v.z).setColor(red,green,blue,Math.max(0,alpha));
        }
        @Override public ResourceLocation getTextureLocation(DragonEntity entity) {
            return ResourceLocation.fromNamespaceAndPath("mythospets", "textures/entity/ember_dragon.png");
        }
    }

    private static final class MooncatRenderer extends MobRenderer<MooncatEntity, MooncatMeshModel> {
        private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath("mythospets", "textures/entity/mooncat.png");

        MooncatRenderer(EntityRendererProvider.Context context) {
            super(context, new MooncatMeshModel(), 0.3F);
        }

        @Override public ResourceLocation getTextureLocation(MooncatEntity entity) { return TEXTURE; }
    }
}
