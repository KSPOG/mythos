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

        @Override public void render(MooncatEntity entity,float entityYaw,float partialTick,PoseStack stack,MultiBufferSource buffers,int light){
            super.render(entity,entityYaw,partialTick,stack,buffers,light);
            if(entity.idleAction()!=MooncatEntity.BLESS)return;
            float t=entity.idleElapsed()+partialTick;
            float weight=Mth.clamp(t/9F,0,1)*Mth.clamp((MooncatEntity.BLESS_DURATION-t)/10F,0,1);
            if(weight<=0)return;
            var out=buffers.getBuffer(RenderType.lightning());
            // Render in world-aligned entity-relative coordinates; no model rotation.
            halo(out,stack,Vec3.ZERO,0.64,0.05,t,weight,false,entity.healed());
            halo(out,stack,new Vec3(0,0.38,0),0.48,0.02,t+7,weight*0.8F,false,entity.healed());
            var recipient=entity.level().getEntity(entity.recipient());
            // Do not draw distant recipients from a culled cat renderer.
            // Server particles remain visible independently at the owner.
            if(recipient instanceof net.minecraft.world.entity.player.Player player&&player.distanceToSqr(entity)<256){
                Vec3 offset=new Vec3(Mth.lerp(partialTick,player.xOld,player.getX())-Mth.lerp(partialTick,entity.xOld,entity.getX()),
                    Mth.lerp(partialTick,player.yOld,player.getY())-Mth.lerp(partialTick,entity.yOld,entity.getY()),
                    Mth.lerp(partialTick,player.zOld,player.getZ())-Mth.lerp(partialTick,entity.zOld,entity.getZ()));
                halo(out,stack,offset,0.94,0.06,t,weight,true,entity.healed());
                halo(out,stack,offset.add(0,1.0,0),0.76,0.02,t+9,weight*0.7F,true,entity.healed());
            }
        }
        private static void halo(VertexConsumer out,PoseStack stack,Vec3 center,double radius,double height,float t,float weight,boolean owner,boolean healed){
            double pulse=healed?Math.max(0,1-Math.abs(t-MooncatEntity.BLESS_IMPACT)/9.0):0;
            double r=radius*(0.70+0.30*weight+0.12*pulse);
            int alpha=(int)(weight*(105+75*pulse));
            ring(out,stack,center.add(0,height,0),r,0.035,0,t,alpha,110,210,255);
            ring(out,stack,center.add(0,height+0.012,0),r*0.81,0.018,0,t,-1+alpha,205,235,255);
            // Rotating constellation diamonds around the lunar circle.
            for(int i=0;i<8;i++){
                double a=i*Math.PI/4+t*0.065;
                Vec3 c=center.add(Math.cos(a)*r,height+0.025,Math.sin(a)*r);
                Vec3 radial=new Vec3(Math.cos(a),0,Math.sin(a)).scale(0.115);
                Vec3 tangent=new Vec3(-Math.sin(a),0,Math.cos(a)).scale(0.055);
                quad(out,stack,c.add(radial),c.add(tangent),c.subtract(radial),c.subtract(tangent),190,230,255,alpha);
            }
            // Two rising silver-blue helices, represented by connected ribbons.
            for(int strand=0;strand<2;strand++)for(int i=0;i<24;i++){
                double f=i/24.0,g=(i+1)/24.0;
                double a=t*0.14+strand*Math.PI+f*Math.PI*2,b=t*0.14+strand*Math.PI+g*Math.PI*2;
                double h=owner?0.95:0.48;
                Vec3 v=center.add(Math.cos(a)*r,height+f*h,Math.sin(a)*r);
                Vec3 w=center.add(Math.cos(b)*r,height+g*h,Math.sin(b)*r);
                quad(out,stack,v.add(0,0.025,0),w.add(0,0.025,0),w.add(0,-0.025,0),v.add(0,-0.025,0),100,180,255,(int)(alpha*0.65*(1-f)));
            }
            if(pulse>0)ring(out,stack,center.add(0,height+0.08,0),radius*(1.0+(1-pulse)*0.8),0.045,0,t,(int)(pulse*weight*175),220,245,255);
        }
        private static void ring(VertexConsumer out,PoseStack stack,Vec3 c,double r,double width,double phase,float t,int alpha,int red,int green,int blue){
            for(int i=0;i<64;i++){
                double a=i*Math.PI/32+phase,b=(i+1)*Math.PI/32+phase;
                Vec3 v=new Vec3(Math.cos(a),0,Math.sin(a)),w=new Vec3(Math.cos(b),0,Math.sin(b));
                quad(out,stack,c.add(v.scale(r-width)),c.add(w.scale(r-width)),c.add(w.scale(r+width)),c.add(v.scale(r+width)),red,green,blue,alpha);
            }
        }
        private static void quad(VertexConsumer out,PoseStack stack,Vec3 a,Vec3 b,Vec3 c,Vec3 d,int r,int g,int blue,int alpha){
            for(Vec3 v:new Vec3[]{a,b,c,d})out.addVertex(stack.last().pose(),(float)v.x,(float)v.y,(float)v.z).setColor(r,g,blue,Mth.clamp(alpha,0,255));
        }
        @Override public ResourceLocation getTextureLocation(MooncatEntity entity) { return TEXTURE; }
    }
}
