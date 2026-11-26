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

    private static Surface[] surfaces(int... dimensions) {
        Surface[] result = new Surface[dimensions.length / 2];
        for (int i=0; i<result.length; i++)
            result[i] = new Surface(dimensions[i*2], dimensions[i*2+1]);
        return result;
    }
    private static final int FUR = 0, MANE = 1, GOLD = 2, EAR = 3, EYE = 4, TIP = 5;
    // Coordinates sample the existing embedded Mooncat palette.
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
        p.shape(BODY,0,16,1,3.6F,3.8F,7,FUR);
        p.shape(BODY,0,18,0,3.1F,2.5F,5.8F,MANE);
        p.shape(DETAIL,0,13,-4,2.7F,4,3.1F,FUR);
        p.shape(DETAIL,0,10,-6,2,3.5F,2.5F,FUR);
        stack.pushPose();
        p.translate(0,8,-7);
        stack.mulPose(Axis.YP.rotationDegrees(yaw));
        stack.mulPose(Axis.XP.rotationDegrees(pitch));
        p.shape(BODY,0,0,0,3,2.7F,3.6F,FUR);
        p.shape(DETAIL,0,0.7F,-3.2F,2.2F,1.25F,3.2F,FUR);
        stack.pushPose();
        p.translate(0,1.6F,-1.8F);
        stack.mulPose(Axis.XP.rotation(Mth.sin(age*0.12F)*0.05F+0.08F));
        p.shape(DETAIL,0,0,-2,2,0.7F,2.8F,MANE);
        stack.popPose();
        for(int side:SIDES){
            p.shape(SMALL,side*2.15F,-0.9F,-1.5F,0.82F,0.65F,0.8F,GOLD);
            p.shape(SMALL,side*2.45F,-0.9F,-1.9F,0.47F,0.49F,0.51F,EYE);
            p.shape(SMALL,side*2.65F,-0.9F,-2.15F,0.15F,0.42F,0.16F,FUR);
            p.shape(ACCENT,side*1.1F,0.2F,-5.4F,0.26F,0.16F,0.22F,FUR);
            stack.pushPose();
            p.translate(side*1.9F,-1.8F,1.4F);
            stack.mulPose(Axis.ZP.rotationDegrees(side*30));
            stack.mulPose(Axis.XP.rotationDegrees(-35));
            p.shape(DETAIL,0,-1.5F,0,0.65F,2.2F,0.7F,GOLD);
            p.translate(0,-2.4F,0);
            stack.mulPose(Axis.XP.rotationDegrees(-25));
            p.shape(SMALL,0,-0.7F,0,0.34F,1.25F,0.35F,GOLD);
            stack.popPose();
            for(int tooth=0;tooth<4;tooth++)
                p.shape(ACCENT,side*1.65F,1.65F,-2.6F-tooth*0.7F,0.19F,0.38F,0.2F,GOLD);
        }
        stack.popPose();

        // Four articulated legs and talons.
        for(int i=0;i<4;i++){
            float x=i%2==0?-2.5F:2.5F,z=i<2?-3.2F:5;
            float stride=Mth.cos(swing*0.8F+((i==0||i==3)?0:Mth.PI))*0.55F*amount;
            stack.pushPose();
            p.translate(x,17,z);
            stack.mulPose(Axis.XP.rotation(stride));
            p.shape(DETAIL,0,1,0,1.5F,2.4F,1.6F,FUR);
            p.translate(0,2.8F,0);
            stack.mulPose(Axis.XP.rotation(Math.max(0,-stride)*0.7F));
            p.shape(DETAIL,0,1.1F,0,0.95F,1.6F,1,FUR);
            p.shape(SMALL,0,2.8F,-0.6F,1.3F,0.8F,1.7F,MANE);
            for(int claw=0;claw<3;claw++)
                p.shape(ACCENT,(claw-1)*0.65F,2.9F,-2,0.2F,0.22F,0.65F,GOLD);
            stack.popPose();
        }

        // Dorsal spines, overlapping scales and segmented serpentine tail.
        for(int i=0;i<7;i++)
            p.shape(SMALL,0,12.2F+i*0.12F,-5+i*1.6F,0.55F,1.45F-i*0.09F,0.75F,GOLD);
        if(lod<=1) for(int side:SIDES) for(int row=0;row<3;row++) for(int col=0;col<8;col++)
            p.shape(ACCENT,side*(3.1F-row*0.27F),14.7F+row*0.95F,-4+col*1.25F,
                0.48F,0.52F,0.75F, row==0?MANE:FUR);
        stack.pushPose();
        p.translate(0,16,7);
        for(int i=0;i<9;i++){
            stack.mulPose(Axis.YP.rotation(Mth.sin(age*0.09F-i*0.45F)*0.15F));
            stack.mulPose(Axis.XP.rotation(i==0?0.1F:-0.05F));
            float radius=1.75F-i*0.16F;
            p.shape(DETAIL,0,0,1.2F,radius,radius,1.85F,FUR);
            p.shape(ACCENT,0,-radius,1,0.3F,radius*0.75F,0.5F,GOLD);
            p.translate(0,0,2.25F);
        }
        p.shape(SMALL,0,0,1,0.6F,0.25F,1.6F,GOLD);
        stack.popPose();

        // Bat-like wings: articulated roots, bones and actual two-sided membranes.
        for(int side:SIDES){
            stack.pushPose();
            p.translate(side*2.5F,13,-2);
            stack.mulPose(Axis.ZP.rotation(side*(0.35F+Mth.sin(age*0.22F)*0.48F)));
            p.bone(0,0,0,side*5,-3,-2,0.62F,FUR);
            p.bone(side*5,-3,-2,side*11,-2,-1,0.42F,FUR);
            p.bone(side*5,-3,-2,side*13,0,5,0.26F,GOLD);
            p.bone(side*5,-3,-2,side*9,1,9,0.24F,FUR);
            p.bone(side*5,-3,-2,side*4,1,8,0.24F,FUR);
            float[] root={0,0,0}, elbow={side*5,-3,-2}, tip={side*11,-2,-1},
                finger={side*13,0,5}, rear={side*9,1,9}, inner={side*4,1,8};
            p.membrane(root,elbow,inner);
            p.membrane(elbow,tip,finger);
            p.membrane(elbow,finger,rear);
            p.membrane(elbow,rear,inner);
            p.membrane(root,inner,new float[]{0,1,6});
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
                    .setOverlay(overlay).setLight(light).setNormal(pose,nx,ny,nz);
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
