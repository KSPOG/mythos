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
final class DragonMeshModel extends EntityModel<DragonEntity> {
    private static final Surface[] BODY = surfaces(112,72, 64,40, 32,20, 16,10);
    private static final Surface[] DETAIL = surfaces(64,44, 40,24, 20,12, 10,6);
    private static final Surface[] SMALL = surfaces(40,24, 24,16, 12,8, 6,4);
    private static final Surface[] ACCENT = surfaces(12,8, 8,6, 6,4, 4,3);
    private static final int[] SIDES = {-1, 1};
    private int lod;
    private static final int EMBER = 4;
    private static final int FULL_LIGHT = 0xF000F0;

    private static Surface[] surfaces(int... dimensions) {
        Surface[] result = new Surface[dimensions.length / 2];
        for (int i=0; i<result.length; i++)
            result[i] = new Surface(dimensions[i*2], dimensions[i*2+1]);
        return result;
    }
    private static final int FUR = 0, MANE = 1, GOLD = 2, EAR = 3, EYE = 4, TIP = 5;
    // Dragon-specific material atlas; ember surfaces use full packed light.
    private static final int[][] PALETTE = {{8,8},{24,8},{40,8},{56,8},{8,24},{24,24}};
    private float swing, amount, age, yaw, pitch;

    @Override
    public void setupAnim(DragonEntity entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        double distance = Minecraft.getInstance().gameRenderer.getMainCamera()
            .getPosition().distanceToSqr(entity.position());
        lod = distance <= 25 ? 0 : distance <= 225 ? 1 : distance <= 900 ? 2 : 3;
        swing = limbSwing;
        amount = Mth.clamp(limbSwingAmount, 0, 1);
        age = ageInTicks;
        yaw = Mth.clamp(netHeadYaw, -45, 45);
        pitch = Mth.clamp(headPitch, -30, 30);
    }

    @Override
    public void renderToBuffer(PoseStack stack, VertexConsumer vertices, int light, int overlay, int color) {
        Painter p = new Painter(stack, vertices, light, overlay, color, lod);
        stack.pushPose();
        stack.translate(0, Mth.sin(age * 0.12F) * 0.008F, 0);
        // Long reptilian torso, plated belly and curved neck.
        p.shape(BODY,0,15.2F,1,2.65F,2.9F,7.2F,FUR);
        p.shape(DETAIL,0,14.5F,-3.5F,3.1F,3.2F,3.8F,FUR);
        p.shape(DETAIL,0,17.4F,-1,1.95F,1.35F,5.5F,MANE);
        for(int plate=0;plate<6;plate++) {
            p.shape(ACCENT,0,18.25F,-4+plate*1.15F,1.7F,0.35F,0.45F,MANE);
            p.shape(ACCENT,0,18.42F,-3.5F+plate*1.15F,1.35F,0.12F,0.13F,EMBER);
        }
        p.bone(0,14,-4,0,10,-7,1.85F,FUR);
        p.bone(0,10,-7,0,8,-9,1.35F,FUR);
        p.bone(0,13.5F,-5.4F,0,9.1F,-8.2F,0.45F,MANE);
        stack.pushPose();
        p.translate(0,8,-9);
        stack.mulPose(Axis.YP.rotationDegrees(yaw));
        stack.mulPose(Axis.XP.rotationDegrees(pitch));
        p.shape(DETAIL,0,0,0,1.9F,1.55F,2.7F,FUR);
        p.taper(0,0.15F,-0.7F,0,0.65F,-5.5F,1.55F,0.66F,FUR);
        p.shape(SMALL,0,0.55F,-4.8F,0.95F,0.57F,1.0F,FUR);
        p.taper(0,-1.1F,-0.3F,0,-0.5F,-4.9F,0.75F,0.25F,MANE);
        stack.pushPose();
        p.translate(0,1.6F,-1.8F);
        stack.mulPose(Axis.XP.rotation(Mth.sin(age*0.12F)*0.05F+0.08F));
        p.taper(0,-0.15F,0.8F,0,0.1F,-3.9F,1.25F,0.38F,MANE);
        p.shape(ACCENT,0,-0.28F,-2.0F,0.7F,0.12F,1.6F,EMBER);
        stack.popPose();
        for(int side:SIDES){
            p.shape(ACCENT,side*1.67F,-0.52F,-1.55F,0.22F,0.23F,0.55F,EMBER);
            p.shape(ACCENT,side*1.86F,-0.53F,-1.55F,0.045F,0.18F,0.07F,FUR);
            p.taper(side*1.38F,-0.9F,-2.4F,side*1.75F,-0.98F,-0.3F,0.31F,0.14F,FUR);
            p.shape(ACCENT,side*0.77F,0.08F,-5.1F,0.18F,0.11F,0.24F,FUR);
            p.taper(side*1.15F,-0.9F,1.1F,side*2.05F,-2.5F,3.2F,0.53F,0.25F,GOLD);
            p.taper(side*2.05F,-2.5F,3.2F,side*2.45F,-2.9F,5.2F,0.25F,0.025F,GOLD);
            p.taper(side*1.5F,0.45F,0.7F,side*2.3F,0.1F,2.6F,0.31F,0.025F,GOLD);
            for(int tooth=0;tooth<4;tooth++)
                p.taper(side*(1.1F-tooth*0.12F),0.95F,-1.8F-tooth*0.72F,
                    side*(1.1F-tooth*0.12F),1.42F,-1.95F-tooth*0.72F,0.14F,0.015F,GOLD);
        }
        stack.popPose();

        // Four articulated legs and talons.
        for(int i=0;i<4;i++){
            float x=i%2==0?-2.35F:2.35F,z=i<2?-3.7F:5;
            float stride=Mth.cos(swing*0.8F+((i==0||i==3)?0:Mth.PI))*0.55F*amount;
            stack.pushPose();
            p.translate(x,15.5F,z);
            stack.mulPose(Axis.XP.rotation(stride));
            p.bone(0,0,0,0,3.2F,i<2?-0.3F:1.0F,0.93F,FUR);
            p.translate(0,3.2F,i<2?-0.3F:1.0F);
            stack.mulPose(Axis.XP.rotation(Math.max(0,-stride)*0.7F));
            p.bone(0,0,0,0,3.7F,-0.5F,0.52F,FUR);
            p.shape(SMALL,0,4.3F,-0.65F,0.9F,0.65F,1.35F,FUR);
            for(int claw=0;claw<3;claw++)
                p.taper((claw-1)*0.5F,4.35F,-1.5F,(claw-1)*0.57F,4.75F,-2.45F,0.19F,0.02F,GOLD);
            stack.popPose();
        }

        // Dorsal spines, overlapping scales and segmented serpentine tail.
        for(int i=0;i<7;i++)
            p.taper(0,12.5F+i*0.03F,-5+i*1.6F,0,10.2F+i*0.24F,-3.9F+i*1.6F,0.55F,0.025F,GOLD);
        if(lod<=1) for(int side:SIDES) for(int row=0;row<3;row++) for(int col=0;col<8;col++)
            {
                float x=side*(2.57F-row*0.18F),y=14.0F+row*0.85F,z=-4+col*1.25F;
                p.taper(x,y,z,x*1.03F,y+0.14F,z+0.83F,0.49F,0.08F,FUR);
                if((row+col)%3==0) p.shape(ACCENT,x*1.035F,y+0.32F,z+0.45F,0.08F,0.12F,0.33F,EMBER);
            }
        stack.pushPose();
        p.translate(0,15.2F,7);
        for(int i=0;i<12;i++){
            stack.mulPose(Axis.YP.rotation(Mth.sin(age*0.09F-i*0.45F)*0.15F));
            stack.mulPose(Axis.XP.rotation(i==0?0.1F:-0.05F));
            float radius=1.45F-i*0.105F;
            p.taper(0,0,-0.12F,0,0,2.4F,radius,radius-0.105F,FUR);
            p.taper(0,-radius,0.6F,0,-radius*1.8F,1.5F,0.25F,0.02F,GOLD);
            if(i%2==0) p.shape(ACCENT,0,-radius,1.8F,0.18F,0.08F,0.3F,EMBER);
            p.translate(0,0,2.25F);
        }
        p.taper(0,0,-0.2F,0,0,2.6F,0.65F,0.02F,GOLD);
        p.taper(0,-0.35F,0.25F,0,-0.1F,2.1F,0.28F,0.02F,EMBER);
        stack.popPose();

        // Bat-like wings: articulated roots, bones and actual two-sided membranes.
        for(int side:SIDES){
            stack.pushPose();
            p.translate(side*2.15F,12.7F,-3);
            stack.mulPose(Axis.YP.rotation(side*(0.65F-amount*0.48F)));
            stack.mulPose(Axis.ZP.rotation(side*(-0.48F+Mth.sin(age*0.13F)*(0.035F+amount*0.18F))));
            p.bone(0,0,0,side*5,-3,-2,0.62F,FUR);
            p.taper(side*5,-3,-2,side*15,-4,-1,0.48F,0.05F,FUR);
            p.taper(side*5,-3,-2,side*17,0,6,0.29F,0.02F,GOLD);
            p.taper(side*5,-3,-2,side*12,1,12,0.26F,0.02F,FUR);
            p.taper(side*5,-3,-2,side*5,2,10,0.23F,0.02F,FUR);
            float[] root={0,0,0}, elbow={side*5,-3,-2}, tip={side*15,-4,-1},
                finger={side*17,0,6}, rear={side*12,1,12}, inner={side*5,2,10};
            p.membrane(root,elbow,inner);
            p.membrane(elbow,tip,new float[]{side*11,-1,2});
            p.membrane(elbow,new float[]{side*11,-1,2},finger);
            p.membrane(elbow,finger,new float[]{side*10,0,6});
            p.membrane(elbow,new float[]{side*10,0,6},rear);
            p.membrane(elbow,rear,new float[]{side*6,1,6});
            p.membrane(elbow,new float[]{side*6,1,6},inner);
            p.membrane(root,inner,new float[]{0,1,7});
            stack.popPose();
        }
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

        void bone(float ax,float ay,float az,float bx,float by,float bz,float radius,int material) {
            float dx=bx-ax,dy=by-ay,dz=bz-az;
            float length=(float)Math.sqrt(dx*dx+dy*dy+dz*dz);
            stack.pushPose();
            translate((ax+bx)/2,(ay+by)/2,(az+bz)/2);
            stack.mulPose(new org.joml.Quaternionf().rotationTo(0,0,1,dx/length,dy/length,dz/length));
            shape(DETAIL,0,0,0,radius,radius,length/2+radius,material);
            stack.popPose();
        }

        // Tapered axial tube with a pointed end instead of overlapping spheres.
        void taper(float ax,float ay,float az,float bx,float by,float bz,float r0,float r1,int material) {
            float dx=bx-ax,dy=by-ay,dz=bz-az;
            float length=(float)Math.sqrt(dx*dx+dy*dy+dz*dz);
            if(length<0.001F)return;
            stack.pushPose();
            translate(ax,ay,az);
            stack.mulPose(new org.joml.Quaternionf().rotationTo(0,0,1,dx/length,dy/length,dz/length));
            int sides=lod==0?32:lod==1?20:lod==2?12:6;
            float slope=(r0-r1)/length;
            float norm=(float)Math.sqrt(1+slope*slope);
            for(int i=0;i<sides;i++){
                for(int corner=0;corner<4;corner++){
                    boolean end=corner>=2;
                    int index=i+((corner==1||corner==2)?1:0);
                    float angle=index*Mth.TWO_PI/sides, c=Mth.cos(angle), si=Mth.sin(angle);
                    float radius=end?r1:r0;
                    emit(c*radius,si*radius,end?length:0,c/norm,si/norm,slope/norm,material);
                }
            }
            stack.popPose();
        }

        void emit(float x,float y,float z,float nx,float ny,float nz,int material) {
            int[] sample=PALETTE[material];
            vertices.addVertex(stack.last(),x/16,y/16,z/16).setColor(color)
                .setUv((sample[0]+0.5F)/64,(sample[1]+0.5F)/64)
                .setOverlay(overlay).setLight(material==EMBER?FULL_LIGHT:light)
                .setNormal(stack.last(),nx,ny,nz);
        }

        void membrane(float[] a,float[] b,float[] c) {
            // Entity render type uses quads; repeat the third vertex to encode a triangle.
            float ax=b[0]-a[0],ay=b[1]-a[1],az=b[2]-a[2];
            float bx=c[0]-a[0],by=c[1]-a[1],bz=c[2]-a[2];
            float nx=ay*bz-az*by,ny=az*bx-ax*bz,nz=ax*by-ay*bx;
            float len=(float)Math.sqrt(nx*nx+ny*ny+nz*nz);
            if(len<0.001F)return;
            for(int face=0;face<2;face++){
                float sign=face==0?1:-1;
                float[][] points=face==0?new float[][]{a,b,c,c}:new float[][]{a,c,b,b};
                for(float[] v:points)
                    vertices.addVertex(stack.last(),v[0]/16,v[1]/16,v[2]/16).setColor(color)
                        .setUv(56.5F/64,24.5F/64).setOverlay(overlay).setLight(light)
                        .setNormal(stack.last(),sign*nx/len,sign*ny/len,sign*nz/len);
            }
        }

        void shape(Surface[] levels,float x,float y,float z,float rx,float ry,float rz,int material) {
            Surface surface = levels[lod];
            stack.pushPose();
            translate(x,y,z);
            stack.scale(rx/16F,ry/16F,rz/16F);
            var pose=stack.last();
            int[] sample=PALETTE[material];
            float u=(sample[0]+0.5F)/64F, v=(sample[1]+0.5F)/64F;
            // Positions also form unit-sphere normals. PoseStack handles inverse
            // scale when transforming normals for ellipsoids.
            for (int i=0;i<surface.vertices.length;i+=3) {
                float nx=surface.vertices[i], ny=surface.vertices[i+1], nz=surface.vertices[i+2];
                vertices.addVertex(pose,nx,ny,nz).setColor(color).setUv(u,v)
                    .setOverlay(overlay).setLight(material==EMBER?FULL_LIGHT:light).setNormal(pose,nx,ny,nz);
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
