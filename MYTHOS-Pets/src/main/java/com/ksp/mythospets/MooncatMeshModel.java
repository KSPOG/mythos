package com.ksp.mythospets;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

/**
 * Smooth, articulated polygon mesh. Geometry is cached in local space and
 * rendered using the vanilla entity vertex format (four vertices per face).
 * All positions below are in Minecraft model pixels, with Y increasing down.
 */
final class MooncatMeshModel extends EntityModel<MooncatEntity> {
    private static final Surface[] BODY = surfaces(112,72, 64,40, 32,20, 16,10);
    private static final Surface[] DETAIL = surfaces(64,44, 40,24, 20,12, 10,6);
    private static final Surface[] SMALL = surfaces(40,24, 24,16, 12,8, 6,4);
    private static final Surface[] ACCENT = surfaces(12,8, 8,6, 6,4, 4,3);
    private static final int[] SIDES = {-1, 1};
    private int lod;

    private static Surface[] surfaces(int... dimensions) {
        Surface[] result = new Surface[dimensions.length / 2];
        for (int i=0; i<result.length; i++)
            result[i] = new Surface(dimensions[i*2], dimensions[i*2+1]);
        return result;
    }
    private static final int FUR = 0, MANE = 1, GOLD = 2, EAR = 3, EYE = 4, TIP = 5;
    // Coordinates sample the existing embedded Mooncat palette.
    private static final int[][] PALETTE = {{8,8},{8,24},{24,35},{5,35},{40,9},{25,52}};
    private float swing, amount, age, yaw, pitch, breath, blink, stretch, sit, groom, earFlick;
    private static float smooth(float t){t=Mth.clamp(t,0,1);return t*t*t*(t*(6*t-15)+10);}
    private static float envelope(float t,float length,float rise,float fall){return smooth(t/rise)*smooth((length-t)/fall);}

    @Override
    public void setupAnim(MooncatEntity entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        double distance = Minecraft.getInstance().gameRenderer.getMainCamera()
            .getPosition().distanceToSqr(entity.position());
        lod = distance <= 25 ? 0 : distance <= 225 ? 1 : distance <= 900 ? 2 : 3;
        swing = limbSwing;
        amount = Mth.clamp(limbSwingAmount, 0, 1);
        age = ageInTicks;
        yaw = Mth.clamp(netHeadYaw, -45, 45);
        pitch = Mth.clamp(headPitch, -30, 30);
        breath=Mth.sin(age*0.105F+entity.getId()*0.31F);
        blink=1-0.96F*envelope((age+entity.getId()%83)%97,6,2,3);
        earFlick=envelope((age+entity.getId()%59)%73,13,3,7);
        float still=1-smooth(amount/0.25F);
        float t=entity.idleElapsed()+Mth.clamp(age-entity.tickCount,0,1);
        stretch=entity.idleAction()==MooncatEntity.STRETCH?envelope(t,100,26,32)*still:0;
        sit=entity.idleAction()==MooncatEntity.GROOM?envelope(t,140,25,30)*still:0;
        groom=sit*envelope(t-25,80,15,15);
    }

    @Override
    public void renderToBuffer(PoseStack stack, VertexConsumer vertices, int light, int overlay, int color) {
        Painter p = new Painter(stack, vertices, light, overlay, color, lod);
        stack.pushPose();
        stack.translate(0, (breath*0.035F+Mth.cos(swing*1.6F)*amount*0.12F)/16F, 0);
        p.translate(0,stretch*0.65F+sit*0.55F,0);
        // Continuous anatomical torso with distinct rib cage, waist and pelvis.
        stack.pushPose();
        p.translate(0,16,0);
        stack.scale(1+breath*0.025F,1+breath*0.018F,1+stretch*0.12F);
        p.translate(0,-16,0);
        p.torso();
        p.shape(DETAIL,0,15.5F,-3.4F,2.55F,2.9F,2.8F,MANE);
        p.shape(DETAIL,0,17.7F,0,2.15F,1.55F,4.0F,MANE);
        stack.popPose();

        stack.pushPose();
        p.translate(0, 12.9F+stretch*1.6F,-5.0F-stretch*1.15F);
        stack.mulPose(Axis.YP.rotationDegrees(yaw));
        stack.mulPose(Axis.XP.rotationDegrees(pitch+stretch*16+groom*(18+12*Mth.sin(age*0.32F))));
        p.shape(BODY, 0, -0.8F, -0.3F, 2.65F, 2.45F, 2.85F, FUR);
        p.shape(DETAIL, 0, 1.1F, -2.5F, 1.85F, 1.3F, 1.55F, MANE);
        for (int side : SIDES) {
            p.shape(DETAIL, side*1.65F, 0.25F, -1.2F, 1.15F, 1.35F, 1.65F, FUR);
            p.shape(DETAIL, side*1.32F, -0.85F, -2.62F, 0.76F, 0.48F*blink, 0.40F, GOLD);
            p.shape(SMALL, side*1.32F, -0.85F, -2.90F, 0.59F, 0.40F*blink, 0.25F, EYE);
            p.shape(SMALL, side*1.32F, -0.85F, -3.11F, 0.13F, 0.33F*blink, 0.08F, FUR);
            p.shape(SMALL, side*1.49F, -0.99F, -3.11F, 0.09F, 0.09F*blink, 0.07F, TIP);
            p.shape(DETAIL, side*0.9F, 1.1F, -3.5F, 1.12F, 0.93F, 0.75F, MANE);
            // Three individual tapered whiskers per cheek, close and medium LOD.
            if (lod <= 1) for (int w=0; w<3; w++) {
                stack.pushPose();
                p.translate(side*1.45F, 0.65F+w*0.38F, -3.55F);
                stack.mulPose(Axis.ZP.rotationDegrees(side*(w-1)*13F));
                stack.mulPose(Axis.YP.rotationDegrees(side*12F));
                p.shape(ACCENT, side*1.45F, 0, 0, 1.65F, 0.045F, 0.045F, TIP);
                stack.popPose();
            }
            stack.pushPose();
            p.translate(side*1.75F, -2.35F, 0.2F);
            stack.mulPose(Axis.ZP.rotationDegrees(side*(18F+earFlick*18F)));
            p.ear(0,-0.1F,0,1.12F,2.95F,0.72F,FUR);
            p.ear(0,-0.38F,-0.48F,0.72F,2.0F,0.22F,EAR);
            p.ear(0,-2.5F,0.02F,0.23F,0.85F,0.22F,TIP);
            stack.popPose();
        }
        // Layered cheek fur, tapering towards the neck.
        if (lod <= 1) for (int side : SIDES) for (int tuft=0; tuft<4; tuft++) {
            stack.pushPose();
            p.translate(side*(2.5F+tuft*0.08F), -0.15F+tuft*0.65F, 0.8F);
            stack.mulPose(Axis.ZP.rotationDegrees(-side*30F));
            p.shape(ACCENT, side*0.55F, 0.55F, 0.55F, 0.55F, 1.35F, 1.1F, MANE);
            stack.popPose();
        }
        p.shape(SMALL, 0, 0.63F, -4.2F, 0.48F, 0.26F, 0.28F, EAR);
        p.crescent();
        stack.popPose();

        // Independent shoulder/hip, knee, and paw articulation.
        for (int i=0; i<4; i++) {
            float x = i%2==0 ? -2.15F : 2.15F;
            float z = i<2 ? -3.4F : 5.1F;
            float phase = i*Mth.PI*0.5F;
            float stride = Mth.cos(swing*0.8F+phase)*0.55F*amount;
            float lift=Math.max(0,Mth.sin(swing*0.8F+phase))*amount;
            float grooming=i==0?groom:0;
            stack.pushPose();
            p.translate(x, 16.8F+sit*(i>=2?1.2F:0.15F), z);
            stack.mulPose(Axis.XP.rotation(stride+stretch*(i<2?-0.68F:0.12F)+sit*(i>=2?-0.50F:0)+grooming*0.85F));
            p.shape(DETAIL, 0, 1.2F, 0, 1.13F, 2.35F, 1.15F, FUR);
            p.translate(0, 3.1F, 0);
            stack.mulPose(Axis.XP.rotation(Math.max(0, -stride)*0.7F-lift*0.48F+stretch*(i<2?0.42F:0)+sit*(i>=2?0.95F:0)-grooming*1.35F));
            p.shape(DETAIL, 0, 1.3F, 0, 0.67F, 1.8F, 0.72F, FUR);
            p.shape(DETAIL, 0, 2.85F, -0.55F, 0.98F, 0.65F, 1.22F, TIP);
            if (lod <= 1) for (int claw=0; claw<3; claw++)
                p.shape(ACCENT, (claw-1)*0.48F, 3.15F, -1.85F, 0.15F, 0.19F, 0.42F, GOLD);
            stack.popPose();
        }

        // A continuous tapered tail, with a traveling wave and curled seated pose.
        p.tail(age,amount,sit);
        stack.popPose();
    }

    private static final class Painter {
        private final PoseStack stack;
        private final VertexConsumer vertices;
        private final int light, overlay, color, lod;

        Painter(PoseStack stack, VertexConsumer vertices, int light, int overlay, int color, int lod) {
            this.stack=stack; this.vertices=vertices; this.light=light; this.overlay=overlay; this.color=color; this.lod=lod;
        }

        void translate(float x, float y, float z) { stack.translate(x/16F,y/16F,z/16F); }

        void emit(float x,float y,float z,float nx,float ny,float nz,int mat,float u,float v){
            var pose=stack.last();
            vertices.addVertex(pose,x/16,y/16,z/16).setColor(color)
                .setUv(mat%4*0.25F+0.003F+0.244F*u,mat/4*0.25F+0.003F+0.244F*v)
                .setOverlay(overlay).setLight(mat==EYE?0xF000F0:light).setNormal(pose,nx,ny,nz);
        }
        void torso(){
            float[][] profile={{-7,15.0F,0.04F,0.04F},{-5.8F,15.5F,1.5F,1.8F},{-3.7F,15.8F,2.65F,2.8F},{-1.5F,16,2.65F,2.65F},{1.5F,16.1F,2.05F,2.25F},{3.5F,16.3F,2.1F,2.15F},{5.3F,16.1F,2.65F,2.55F},{7.4F,16,1.6F,1.8F},{8.2F,16,0.04F,0.04F}};
            int rows=new int[]{96,48,24,12}[lod],cols=new int[]{96,48,24,12}[lod];
            for(int i=0;i<rows;i++)for(int j=0;j<cols;j++){
                torsoVertex(profile,i/(float)rows,j/(float)cols);
                torsoVertex(profile,(i+1)/(float)rows,j/(float)cols);
                torsoVertex(profile,(i+1)/(float)rows,(j+1)/(float)cols);
                torsoVertex(profile,i/(float)rows,(j+1)/(float)cols);
            }
        }
        void torsoVertex(float[][] p,float t,float u){
            float f=t*(p.length-1);int i=Math.min((int)f,p.length-2);float k=f-i;
            float[] a=p[Math.max(0,i-1)],b=p[i],c=p[i+1],d=p[Math.min(p.length-1,i+2)];
            float z=Mth.lerp(k,b[0],c[0]),y=curve(a[1],b[1],c[1],d[1],k);
            float rx=Math.max(0.025F,curve(a[2],b[2],c[2],d[2],k)),ry=Math.max(0.025F,curve(a[3],b[3],c[3],d[3],k));
            float angle=u*Mth.TWO_PI,cs=Mth.cos(angle),sn=Mth.sin(angle);
            emit(rx*cs,y+ry*sn,z,cs,sn,0,FUR,u,t);
        }
        float curve(float a,float b,float c,float d,float t){return 0.5F*(2*b+(-a+c)*t+(2*a-5*b+4*c-d)*t*t+(-a+3*b-3*c+d)*t*t*t);}
        void ear(float x,float y,float z,float width,float height,float depth,int mat){
            int n=new int[]{24,16,10,6}[lod];
            for(int i=0;i<n;i++)for(int j=0;j<n;j++){
                earVertex(x,y,z,width,height,depth,mat,i/(float)n,j/(float)n);
                earVertex(x,y,z,width,height,depth,mat,i/(float)n,(j+1)/(float)n);
                earVertex(x,y,z,width,height,depth,mat,(i+1)/(float)n,(j+1)/(float)n);
                earVertex(x,y,z,width,height,depth,mat,(i+1)/(float)n,j/(float)n);
            }
        }
        void earVertex(float x,float y,float z,float w,float h,float d,int mat,float t,float u){
            float a=u*Mth.TWO_PI,cs=Mth.cos(a),sn=Mth.sin(a),r=(1-t)*0.96F+0.015F;
            emit(x+w*r*cs,y-h*t,z+d*r*sn,cs,-0.2F,sn,mat,u,t);
        }
        void crescent(){
            int steps=36;
            for(int i=0;i<steps;i++){
                float a=-1.15F+i*4.8F/steps,b=-1.15F+(i+1)*4.8F/steps;
                crescentVertex(a,0);crescentVertex(b,0);crescentVertex(b,1);crescentVertex(a,1);
            }
        }
        void crescentVertex(float a,int inner){
            float r=inner==0?0.48F:0.48F-0.22F*Mth.sin((a+1.15F)/4.8F*Mth.PI);
            emit(r*Mth.cos(a),-1.85F+r*Mth.sin(a),-2.78F,0,0,-1,EYE,0.5F,0.5F);
        }
        void tail(float age,float motion,float sit){
            int rows=new int[]{80,48,24,12}[lod],cols=new int[]{32,20,12,8}[lod];
            for(int i=0;i<rows;i++)for(int j=0;j<cols;j++){
                tailVertex(age,motion,sit,i/(float)rows,j/(float)cols);
                tailVertex(age,motion,sit,i/(float)rows,(j+1)/(float)cols);
                tailVertex(age,motion,sit,(i+1)/(float)rows,(j+1)/(float)cols);
                tailVertex(age,motion,sit,(i+1)/(float)rows,j/(float)cols);
            }
        }
        void tailVertex(float age,float motion,float sit,float t,float u){
            float x=(0.65F+motion*0.6F)*t*Mth.sin(age*0.08F-t*5)+sit*3.8F*t*t;
            float y=15.8F-5.2F*Mth.sin(t*1.8F)+sit*3*t;
            float z=7+10.5F*t-sit*5*t*t;
            float angle=u*Mth.TWO_PI,cs=Mth.cos(angle),sn=Mth.sin(angle);
            float r=0.83F*(1-t)+0.045F;
            emit(x+r*cs,y+r*sn,z,cs,sn,0,t>0.78F?TIP:FUR,u,t);
        }

        void shape(Surface[] levels,float x,float y,float z,float rx,float ry,float rz,int material) {
            Surface surface = levels[lod];
            stack.pushPose();
            translate(x,y,z);
            stack.scale(rx/16F,ry/16F,rz/16F);
            var pose=stack.last();
            int[] sample=PALETTE[material];
            float u0=material%4*0.25F, v0=material/4*0.25F;
            // Positions also form unit-sphere normals. PoseStack handles inverse
            // scale when transforming normals for ellipsoids.
            for (int i=0;i<surface.vertices.length;i+=3) {
                float nx=surface.vertices[i], ny=surface.vertices[i+1], nz=surface.vertices[i+2];
                float u=u0+0.003F+0.244F*(float)(Math.atan2(nz,nx)/(2*Math.PI)+0.5),v=v0+0.003F+0.244F*(float)(Math.acos(Mth.clamp(ny,-1,1))/Math.PI);
                vertices.addVertex(pose,nx,ny,nz).setColor(color).setUv(u,v)
                    .setOverlay(overlay).setLight(material==EYE?0xF000F0:light).setNormal(pose,nx,ny,nz);
            }
            stack.popPose();
        }
    }

    private static final class Surface {
        final float[] vertices;
        Surface(int slices,int stacks) {
            vertices=new float[slices*stacks*4*3];
            int i=0;
            for(int row=0;row<stacks;row++) {
                for(int col=0;col<slices;col++) {
                    i=point(i,row,col,slices,stacks);
                    i=point(i,row,col+1,slices,stacks);
                    i=point(i,row+1,col+1,slices,stacks);
                    i=point(i,row+1,col,slices,stacks);
                }
            }
        }

        private int point(int i,int row,int col,int slices,int stacks) {
            double theta=Math.PI*row/stacks, phi=2*Math.PI*col/slices;
            vertices[i++]=(float)(Math.sin(theta)*Math.cos(phi));
            vertices[i++]=(float)Math.cos(theta);
            vertices[i++]=(float)(Math.sin(theta)*Math.sin(phi));
            return i;
        }
    }
}
