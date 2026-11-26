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
            // Broken, overlapping flame tongues rather than concentric smooth tubes.
            // Each tongue advects downstream and fades independently.
            double distance=net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().distanceToSqr(entity.position());
            int tongues=distance<=49?16:distance<=196?10:6;
            int longitudinal=distance<=49?8:5;
            int radial=distance<=49?5:4;
            for(int k=0;k<tongues;k++){
                float seed=k*2.399963F;
                float phase=(age*(0.045F+0.009F*(k%5))+k*0.618034F)%1F;
                double begin=phase*0.86;
                double extent=Math.min(1-begin,0.16+0.18*(0.5+0.5*Math.sin(seed)));
                if(extent<0.015)continue;
                for(int i=0;i<longitudinal;i++)for(int j=0;j<radial;j++){
                    tongue(out,stack,start,aim,right,up,length,age,seed,begin,extent,i/(double)longitudinal,j/(double)radial,k%3);
                    tongue(out,stack,start,aim,right,up,length,age,seed,begin,extent,(i+1)/(double)longitudinal,j/(double)radial,k%3);
                    tongue(out,stack,start,aim,right,up,length,age,seed,begin,extent,(i+1)/(double)longitudinal,(j+1)/(double)radial,k%3);
                    tongue(out,stack,start,aim,right,up,length,age,seed,begin,extent,i/(double)longitudinal,(j+1)/(double)radial,k%3);
                }
            }
            // Tight, irregular ignition root; the rest is a fragmented plume.
            for(int i=0;i<10;i++)for(int j=0;j<8;j++){
                tongue(out,stack,start,aim,right,up,length,age,0,0,0.26,i/10.0,j/8.0,0);
                tongue(out,stack,start,aim,right,up,length,age,0,0,0.26,(i+1)/10.0,j/8.0,0);
                tongue(out,stack,start,aim,right,up,length,age,0,0,0.26,(i+1)/10.0,(j+1)/8.0,0);
                tongue(out,stack,start,aim,right,up,length,age,0,0,0.26,i/10.0,(j+1)/8.0,0);
            }
        }
        private static void tongue(VertexConsumer out,PoseStack stack,Vec3 start,Vec3 aim,Vec3 right,Vec3 up,float length,float age,float seed,double begin,double extent,double f,double u,int layer){
            double t=begin+extent*f,angle=u*Math.PI*2;
            double spread=length*t*0.115;
            double drift=seed+age*0.12+t*7;
            double lateral=spread*(0.25+0.65*Math.sin(seed*1.7));
            double envelope=Math.pow(Math.max(0,Math.sin(Math.PI*f)),0.7);
            double ripple=0.75+0.25*Math.sin(angle*3+seed+t*30-age*0.8);
            double radius=(0.035+spread*0.40)*envelope*ripple;
            Vec3 v=start.add(aim.scale(t*length))
                .add(right.scale(Math.cos(drift)*lateral+Math.cos(angle)*radius))
                .add(up.scale(Math.sin(drift)*lateral+Math.sin(angle)*radius))
                .add(0,0.15*t*t,0);
            double heat=Math.max(0,1-t*1.4)*(layer==0?1:0.65);
            int green=(int)(42+153*heat),blue=(int)(8+65*heat);
            int alpha=(int)((layer==0?155:95)*envelope*Math.pow(1-t,0.55)*(0.75+0.25*Math.sin(seed+age*0.7)));
            out.addVertex(stack.last().pose(),(float)v.x,(float)v.y,(float)v.z).setColor(255,green,blue,Mth.clamp(alpha,0,255));
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
