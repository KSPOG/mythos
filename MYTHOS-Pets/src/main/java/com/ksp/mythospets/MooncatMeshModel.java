package com.ksp.mythospets;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.EntityModel;
import net.minecraft.util.Mth;

/**
 * Smooth, articulated polygon mesh. Geometry is cached in local space and
 * rendered using the vanilla entity vertex format (four vertices per face).
 * All positions below are in Minecraft model pixels, with Y increasing down.
 */
final class MooncatMeshModel extends EntityModel<MooncatEntity> {
    private static final Surface BODY = new Surface(32, 20);
    private static final Surface DETAIL = new Surface(20, 12);
    private static final Surface SMALL = new Surface(12, 8);
    private static final int FUR = 0, MANE = 1, GOLD = 2, EAR = 3, EYE = 4, TIP = 5;
    // Coordinates sample the existing embedded Mooncat palette.
    private static final int[][] PALETTE = {{8,8},{8,24},{24,35},{5,35},{40,9},{25,52}};
    private float swing, amount, age, yaw, pitch;

    @Override
    public void setupAnim(MooncatEntity entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        swing = limbSwing;
        amount = Mth.clamp(limbSwingAmount, 0, 1);
        age = ageInTicks;
        yaw = Mth.clamp(netHeadYaw, -45, 45);
        pitch = Mth.clamp(headPitch, -30, 30);
    }

    @Override
    public void renderToBuffer(PoseStack stack, VertexConsumer vertices, int light, int overlay, int color) {
        Painter p = new Painter(stack, vertices, light, overlay, color);
        stack.pushPose();
        stack.translate(0, Mth.sin(age * 0.12F) * 0.008F, 0);
        // Overlapping curved surfaces produce a rounded feline silhouette.
        p.shape(BODY, 0, 16, 1, 3.3F, 3.1F, 6.1F, FUR);
        p.shape(BODY, 0, 15.2F, -3.2F, 3.5F, 3.8F, 3.8F, MANE);
        p.shape(BODY, 0, 16.6F, 5, 3.4F, 3.2F, 3.5F, FUR);
        p.shape(DETAIL, 0, 18, 0, 2.8F, 1.8F, 4.7F, MANE);

        stack.pushPose();
        p.translate(0, 12.5F, -5.4F);
        stack.mulPose(Axis.YP.rotationDegrees(yaw));
        stack.mulPose(Axis.XP.rotationDegrees(pitch));
        p.shape(BODY, 0, -0.8F, -0.3F, 3.5F, 3.2F, 3.4F, FUR);
        p.shape(DETAIL, 0, 1.1F, -2.5F, 2.3F, 1.8F, 1.9F, MANE);
        for (int side : new int[]{-1, 1}) {
            p.shape(DETAIL, side*2.1F, 0.5F, -1.4F, 1.7F, 1.9F, 2.1F, FUR);
            p.shape(DETAIL, side*1.65F, -0.9F, -3.05F, 0.85F, 0.75F, 0.44F, GOLD);
            p.shape(SMALL, side*1.65F, -0.9F, -3.35F, 0.62F, 0.58F, 0.29F, EYE);
            p.shape(SMALL, side*1.65F, -0.9F, -3.59F, 0.16F, 0.43F, 0.10F, FUR);
            p.shape(SMALL, side*1.86F, -1.09F, -3.55F, 0.12F, 0.12F, 0.10F, TIP);
            p.shape(DETAIL, side*0.9F, 1.1F, -3.5F, 1.12F, 0.93F, 0.75F, MANE);
            stack.pushPose();
            p.translate(side*2.3F, -2.8F, 0.2F);
            stack.mulPose(Axis.ZP.rotationDegrees(side*18F));
            p.shape(DETAIL, 0, -1.5F, 0, 1.15F, 2.2F, 0.85F, FUR);
            p.shape(SMALL, 0, -1.55F, -0.62F, 0.72F, 1.48F, 0.31F, EAR);
            p.shape(SMALL, 0, -3.25F, 0.02F, 0.40F, 0.75F, 0.36F, TIP);
            stack.popPose();
        }
        p.shape(SMALL, 0, 0.63F, -4.2F, 0.65F, 0.35F, 0.35F, EAR);
        p.shape(DETAIL, 0, -2.15F, -2.95F, 0.62F, 0.84F, 0.28F, GOLD);
        p.shape(SMALL, 0, -2.15F, -3.2F, 0.28F, 0.46F, 0.17F, EYE);
        stack.popPose();

        // Independent shoulder/hip, knee, and paw articulation.
        for (int i=0; i<4; i++) {
            float x = i%2==0 ? -2.15F : 2.15F;
            float z = i<2 ? -3.4F : 5.1F;
            float phase = (i==0 || i==3) ? 0 : Mth.PI;
            float stride = Mth.cos(swing*0.8F+phase)*0.65F*amount;
            stack.pushPose();
            p.translate(x, 17, z);
            stack.mulPose(Axis.XP.rotation(stride));
            p.shape(DETAIL, 0, 1.2F, 0, 1.4F, 2.6F, 1.45F, FUR);
            p.translate(0, 3.1F, 0);
            stack.mulPose(Axis.XP.rotation(Math.max(0, -stride)*0.7F));
            p.shape(DETAIL, 0, 1.3F, 0, 0.91F, 1.8F, 0.96F, FUR);
            p.shape(DETAIL, 0, 2.85F, -0.55F, 1.23F, 0.85F, 1.55F, TIP);
            stack.popPose();
        }

        // Six overlapping tapered tail segments with a traveling idle wave.
        stack.pushPose();
        p.translate(0, 15.5F, 7);
        for (int i=0; i<6; i++) {
            stack.mulPose(Axis.YP.rotation(Mth.sin(age*0.09F-i*0.55F)*0.11F));
            stack.mulPose(Axis.XP.rotation(i==0 ? 0.48F : 0.12F));
            float radius = 1.2F-i*0.105F;
            p.shape(DETAIL, 0, 0, 1.0F, radius, radius, 1.55F, i>=4 ? TIP : FUR);
            p.translate(0, 0, 1.85F);
        }
        stack.popPose();
        stack.popPose();
    }

    private static final class Painter {
        private final PoseStack stack;
        private final VertexConsumer vertices;
        private final int light, overlay, color;

        Painter(PoseStack stack, VertexConsumer vertices, int light, int overlay, int color) {
            this.stack=stack; this.vertices=vertices; this.light=light; this.overlay=overlay; this.color=color;
        }

        void translate(float x, float y, float z) { stack.translate(x/16F,y/16F,z/16F); }

        void shape(Surface surface,float x,float y,float z,float rx,float ry,float rz,int material) {
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
