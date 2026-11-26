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
    private float swing, amount, age, yaw, pitch, breath, blink, stretch, sit, groom, earFlick, pounce, bless, gather, swipe;
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
        pounce=entity.idleAction()==MooncatEntity.POUNCE?envelope(t-4,16,4,8):0;
        gather=entity.idleAction()==MooncatEntity.POUNCE?envelope(t,9,4,3):0;
        swipe=entity.idleAction()==MooncatEntity.POUNCE?envelope(t-4,12,4,6):0;
        bless=entity.idleAction()==MooncatEntity.BLESS?envelope(t,24,9,10):0;
    }

    @Override
    public void renderToBuffer(PoseStack stack, VertexConsumer vertices, int light, int overlay, int color) {
        Painter p = new Painter(stack, vertices, light, overlay, color, lod);
        stack.pushPose();
        stack.translate(0, (breath*0.035F+Mth.cos(swing*1.6F)*amount*0.12F)/16F, 0);
        p.translate(0,stretch*0.65F+sit*0.55F+gather*0.7F-pounce*1.8F,-pounce*1.2F);
        p.translate(0,16,3);stack.mulPose(Axis.XP.rotation(pounce*0.22F-gather*0.12F));p.translate(0,-16,-3);
        // Continuous anatomical torso with distinct rib cage, waist and pelvis.
        stack.pushPose();
        p.translate(0,16,0);
        stack.scale(1+breath*0.025F,1+breath*0.018F,1+stretch*0.12F);
        p.translate(0,-16,0);
        p.torso();
        p.shape(DETAIL,0,15.5F,-3.4F,2.12F,2.55F,2.30F,MANE);
        p.shape(DETAIL,0,17.7F,0,2.15F,1.55F,4.0F,MANE);
        stack.popPose();

        stack.pushPose();
        p.translate(0, 13.25F+stretch*1.6F,-4.65F-stretch*1.15F);
        stack.mulPose(Axis.YP.rotationDegrees(yaw));
        stack.mulPose(Axis.XP.rotationDegrees(pitch+stretch*16+groom*(18+12*Mth.sin(age*0.32F))-bless*18+pounce*9));
        // Domestic feline: broad rounded cranium, short flat muzzle, large
        // forward-facing almond eyes and upright triangular pinnae.
        p.shape(BODY,0,-0.65F,-0.25F,2.85F,2.55F,2.18F,FUR);
        p.shape(DETAIL,0,0.96F,-1.72F,1.53F,0.86F,0.82F,MANE);
        p.shape(DETAIL,0,1.50F,-1.83F,0.94F,0.43F,0.60F,MANE);
        for(int side:SIDES){
            p.shape(DETAIL,side*1.68F,0.25F,-0.72F,1.12F,1.34F,1.32F,FUR);
            stack.pushPose();
            p.translate(side*1.29F,-0.80F,-1.97F);
            stack.mulPose(Axis.YP.rotationDegrees(-side*21F));
            stack.mulPose(Axis.ZP.rotationDegrees(-side*12F));
            p.almond(0,0,0,0.93F,0.55F*blink,0.19F,FUR);
            p.almond(0,0,-0.14F,0.79F,0.43F*blink,0.13F,EYE);
            p.almond(0,0,-0.24F,0.105F,0.38F*blink,0.035F,FUR);
            p.shape(ACCENT,-0.20F,-0.12F*blink,-0.27F,0.075F,0.075F*blink,0.025F,TIP);
            stack.popPose();
            // Two small whisker pads rather than a protruding canine snout.
            p.shape(DETAIL,side*0.65F,0.77F,-2.08F,0.78F,0.56F,0.58F,MANE);
            if(lod<=1)for(int w=0;w<4;w++){
                stack.pushPose();
                p.translate(side*0.94F,0.55F+w*0.19F,-2.42F);
                stack.mulPose(Axis.ZP.rotationDegrees(side*(w-1.5F)*9F));
                stack.mulPose(Axis.YP.rotationDegrees(side*(9F+w*4F)));
                p.shape(ACCENT,side*1.55F,0,0,1.70F,0.027F,0.027F,TIP);
                stack.popPose();
            }
            stack.pushPose();
            p.translate(side*1.86F,-2.25F,0.05F);
            stack.mulPose(Axis.ZP.rotationDegrees(side*(10F+earFlick*12F)));
            p.ear(0,0,0,1.22F,2.24F,0.52F,FUR);
            p.ear(0,-0.19F,-0.38F,0.79F,1.67F,0.15F,EAR);
            stack.popPose();
            // Small swept cheek ruff, not a canine mane.
            if(lod<=1)for(int tuft=0;tuft<3;tuft++){
                stack.pushPose();
                p.translate(side*(2.18F-tuft*0.12F),0.36F+tuft*0.38F,0.42F);
                stack.mulPose(Axis.ZP.rotationDegrees(-side*35F));
                p.shape(ACCENT,side*0.30F,0.30F,0.24F,0.42F,0.70F,0.60F,FUR);
                stack.popPose();
            }
        }
        p.nose();
        p.shape(ACCENT,0,1.02F,-2.56F,0.055F,0.24F,0.035F,FUR);
        for(int side:SIDES){
            stack.pushPose();p.translate(side*0.21F,1.23F,-2.46F);
            stack.mulPose(Axis.ZP.rotationDegrees(side*16F));
            p.shape(ACCENT,0,0,0,0.28F,0.045F,0.035F,FUR);
            stack.popPose();
        }
        p.crescent();
        stack.popPose();

        // Independent shoulder/hip, knee, and paw articulation.
        for (int i=0; i<4; i++) {
            float x = (i%2==0?-1:1)*(i<2?1.62F:1.87F);
            float z = i<2 ? -3.4F : 5.1F;
            float phase = i*Mth.PI*0.5F;
            float stride = Mth.cos(swing*0.8F+phase)*0.55F*amount;
            float lift=Math.max(0,Mth.sin(swing*0.8F+phase))*amount;
            float grooming=i==0?groom:0;
            float attack=i==0?swipe:i==1?swipe*0.65F:-pounce*0.5F;
            stack.pushPose();
            p.translate(x, 16.8F+sit*(i>=2?1.2F:0.15F), z);
            stack.mulPose(Axis.XP.rotation(stride+stretch*(i<2?-0.68F:0.12F)+sit*(i>=2?-0.50F:0)+grooming*0.85F-attack*0.85F+gather*0.2F));
            p.shape(DETAIL,0,1.1F,0,i<2?0.78F:1.18F,2.35F,i<2?0.80F:1.35F,FUR);
            p.translate(0, 3.1F, 0);
            stack.mulPose(Axis.XP.rotation(Math.max(0, -stride)*0.7F-lift*0.48F+stretch*(i<2?0.42F:0)+sit*(i>=2?0.95F:0)-grooming*1.35F+attack*0.45F+gather*0.45F));
            p.shape(DETAIL, 0, 1.3F, 0, 0.67F, 1.8F, 0.72F, FUR);
            p.shape(DETAIL, 0, 2.85F, -0.55F, 0.82F, 0.65F, 0.94F, TIP);
            if(lod<=1)for(int toe=0;toe<3;toe++)
                p.shape(ACCENT,(toe-1)*0.43F,2.98F,-1.24F,0.27F,0.29F,0.38F,TIP);
            stack.popPose();
        }

        // A continuous tapered tail, with a traveling wave and curled seated pose.
        p.tail(age,amount+pounce*0.6F,sit);
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
        void almond(float x,float y,float z,float w,float h,float depth,int mat){
            int n=lod==0?32:lod==1?24:12;
            for(int i=0;i<n;i++){
                float a=i*Mth.TWO_PI/n,b=(i+1)*Mth.TWO_PI/n;
                emit(x,y,z-depth,0,0,-1,mat,0.5F,0.5F);
                almondEdge(x,y,z,w,h,mat,b);
                almondEdge(x,y,z,w,h,mat,a);
                emit(x,y,z-depth,0,0,-1,mat,0.5F,0.5F);
            }
        }
        void almondEdge(float x,float y,float z,float w,float h,int mat,float a){
            float cs=Mth.cos(a),sn=Mth.sin(a);
            emit(x+w*cs,y+h*sn*(0.45F+0.55F*Math.abs(sn)),z,0,0,-1,mat,0.5F+cs*0.45F,0.5F+sn*0.45F);
        }
        void nose(){
            emit(-0.40F,0.48F,-2.62F,0,0,-1,EAR,0,0);
            emit(0,0.86F,-2.68F,0,0,-1,EAR,0.5F,1);
            emit(0.40F,0.48F,-2.62F,0,0,-1,EAR,1,0);
            emit(0,0.40F,-2.66F,0,0,-1,EAR,0.5F,0);
        }
        void torso(){
            float[][] profile={{-7,15.0F,0.04F,0.04F},{-5.8F,15.5F,1.5F,1.8F},{-3.7F,15.8F,2.25F,2.55F},{-1.5F,16,2.40F,2.55F},{1.5F,16.1F,2.05F,2.25F},{3.5F,16.3F,2.1F,2.15F},{5.3F,15.75F,2.50F,2.75F},{7.4F,16,1.6F,1.8F},{8.2F,16,0.04F,0.04F}};
            int rows=new int[]{96,48,24,12}[lod],cols=new int[]{96,48,24,12}[lod];
            for(int i=0;i<rows;i++)for(int j=0;j<cols;j++){
                torsoVertex(profile,i/(float)rows,j/(float)cols);
                torsoVertex(profile,i/(float)rows,(j+1)/(float)cols);
                torsoVertex(profile,(i+1)/(float)rows,(j+1)/(float)cols);
                torsoVertex(profile,(i+1)/(float)rows,j/(float)cols);
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
            emit(r*Mth.cos(a),-2.10F+r*Mth.sin(a),-2.10F,0,0,-1,EYE,0.5F,0.5F);
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
            float y=15.8F-9.0F*Mth.sin(t*1.65F)+sit*5*t;
            float z=7+7.5F*t-2.8F*t*t-sit*4*t*t;
            float angle=u*Mth.TWO_PI,cs=Mth.cos(angle),sn=Mth.sin(angle);
            float r=0.69F*(1-0.80F*t)*Mth.sqrt(Math.max(0.001F,1-t*t))+0.018F;
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
