package com.ksp.mythospets;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.resources.ResourceLocation;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Angular articulated mesh with connected bevel rings and hard normals,
 * rendered using the vanilla entity vertex format (four vertices per face).
 * All positions below are in Minecraft model pixels, with Y increasing down.
 */
final class DragonMeshModel extends EntityModel<DragonEntity> {
    private static final int[] SIDES = {-1, 1};
    private int lod;
    private static final ResourceLocation ANATOMY = ResourceLocation.fromNamespaceAndPath("mythospets", "meshes/ember_dragon.anatomy");
    private Anatomy anatomy;
    private static final int[] SCALE_ROWS={96,48,24,0};
    private static final int[] SCALE_COLUMNS={64,40,20,0};
    private static final int[] MEMBRANE_STEPS={32,20,10,3};
    private static final int EMBER = 4;
    private static final int FULL_LIGHT = 0xF000F0;

    private static final int FUR = 0, MANE = 1, GOLD = 2, EAR = 3, EYE = 4, TIP = 5;
    // Dragon-specific material atlas; ember surfaces use full packed light.
    private static final int[][] PALETTE = {{8,8},{24,8},{40,8},{56,8},{8,24},{24,24},{40,24},{56,24}};
    private float swing, amount, age, yaw, pitch;
    private float rearPose, roarPose, wingSpread, wingBeat, wingLag;

    private static float ease(float t) { t=Mth.clamp(t,0,1); return t*t*(3-2*t); }
    private static float envelope(float t,float duration,float rise,float fall) {
        return ease(t/rise)*ease((duration-t)/fall);
    }

    @Override
    public void setupAnim(DragonEntity entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        double distance = Minecraft.getInstance().gameRenderer.getMainCamera()
            .getPosition().distanceToSqr(entity.position());
        lod = distance <= 25 ? 0 : distance <= 100 ? 1 : distance <= 400 ? 2 : 3;
        swing = limbSwing;
        amount = Mth.clamp(limbSwingAmount, 0, 1);
        age = ageInTicks;
        yaw = Mth.clamp(netHeadYaw, -45, 45);
        pitch = Mth.clamp(headPitch, -30, 30);
        int action=entity.idleAction();
        float t=entity.idleElapsed()+Mth.clamp(ageInTicks-entity.tickCount,0,1);
        float still=1-ease(amount/0.25F);
        rearPose=0;roarPose=0;wingSpread=0;wingBeat=0;wingLag=0;
        switch(action) {
            case DragonEntity.FLAP -> {
                wingSpread=envelope(t,64,12,14)*still;
                float beat=envelope(t-10,44,5,8)*still;
                wingBeat=beat*Mth.sin((t-10)*Mth.TWO_PI/16);
                wingLag=beat*Mth.sin((t-13)*Mth.TWO_PI/16);
            }
            case DragonEntity.REAR -> {
                rearPose=envelope(t,84,20,20)*still;
                wingSpread=rearPose*0.72F;
                wingBeat=rearPose*0.18F*Mth.sin(t*0.18F);
                wingLag=rearPose*0.12F*Mth.sin(t*0.18F-0.6F);
            }
            case DragonEntity.ROAR -> {
                rearPose=envelope(t,72,18,17)*0.82F*still;
                roarPose=envelope(t-16,39,10,9)*still;
                wingSpread=envelope(t,72,18,17)*still;
                wingBeat=roarPose*0.16F*Mth.sin(t*0.24F);
                wingLag=roarPose*0.12F*Mth.sin(t*0.24F-0.6F);
            }
            default -> {}
        }
    }

    @Override
    public void renderToBuffer(PoseStack stack, VertexConsumer vertices, int light, int overlay, int color) {
        if (anatomy == null) anatomy = Anatomy.load();
        Painter p = new Painter(stack, vertices, light, overlay, color, lod);
        stack.pushPose();
        stack.translate(0, Mth.sin(age * 0.12F) * 0.008F, 0);
        // Raise chest about the hind hip; the rear feet remain in world-facing stance.
        p.translate(0,15.5F,5);
        stack.mulPose(Axis.XP.rotation(-rearPose*0.95F));
        p.translate(0,-15.5F,-5);
        // Long reptilian torso, plated belly and curved neck.
        p.hull(anatomy.torso,FUR);
        p.scales(anatomy.torso,112,64);
        p.hull(anatomy.belly,MANE);
        for(int plate=0;plate<6;plate++) {
            p.plate(0,18.25F,-4+plate*1.15F,1.7F,0.35F,0.45F,MANE);
            p.plate(0,18.42F,-3.5F+plate*1.15F,1.35F,0.12F,0.13F,EMBER);
        }
        p.hull(anatomy.neck,FUR);
        p.scales(anatomy.neck,64,48);
        for(int i=0;i<4;i++){
            p.plate(0,9.3F+i*1.2F,-9+i*1.15F,0.8F+i*0.12F,0.25F,0.64F,MANE);
        }
        stack.pushPose();
        p.translate(0,8,-9);
        stack.mulPose(Axis.YP.rotationDegrees(yaw));
        stack.mulPose(Axis.XP.rotationDegrees(pitch*(1-roarPose)+roarPose*18F));
        // Connected angular skull, wedge muzzle, raised cheek and crown ridges.
        p.hull(anatomy.skull,FUR);
        p.scales(anatomy.skull,40,32);
        p.plate(0,-1.2F,-1.1F,0.64F,0.44F,1.75F,MANE);
        for(int side:SIDES)
            p.plate(side*1.47F,0.22F,0.1F,0.46F,0.73F,1.6F,FUR);
        stack.pushPose();
        p.translate(0,0.85F,-0.6F);
        stack.mulPose(Axis.XP.rotation(-0.025F+Mth.sin(age*0.08F)*0.012F+roarPose*0.70F));
        p.hull(anatomy.jaw,FUR);
        p.scales(anatomy.jaw,24,24);
        p.plate(0,0.03F,-2.6F,0.42F,0.05F,0.9F,MANE);
        stack.popPose();
        for(int side:SIDES){
            p.plate(side*1.78F,-0.55F,-1.48F,0.15F,0.16F,0.39F,EMBER);
            p.plate(side*1.91F,-0.55F,-1.48F,0.025F,0.14F,0.055F,FUR);
            p.taper(side*1.38F,-0.9F,-2.4F,side*1.75F,-0.98F,-0.3F,0.31F,0.14F,FUR);
            p.plate(side*0.81F,-0.03F,-4.35F,0.18F,0.11F,0.24F,FUR);
            p.horn(side);
            for(int ridge=0;ridge<5;ridge++) {
                float z=0.7F+ridge*0.48F;
                p.taper(side*(1.4F-ridge*0.10F),-1.0F,z,side*(1.9F-ridge*0.13F),-2.1F+ridge*0.09F,z+0.9F,0.36F,0.025F,GOLD);
            }
            p.taper(side*1.5F,0.45F,0.7F,side*2.3F,0.1F,2.6F,0.31F,0.025F,GOLD);
            for(int tooth=0;tooth<3;tooth++)
                p.taper(side*(1.1F-tooth*0.12F),0.95F,-1.8F-tooth*0.72F,
                    side*(1.1F-tooth*0.12F),1.15F,-1.95F-tooth*0.72F,0.14F,0.015F,GOLD);
        }
        stack.popPose();

        // Four articulated legs and talons.
        for(int i=0;i<4;i++){
            float x=i%2==0?-2.35F:2.35F,z=i<2?-3.7F:5;
            float stride=Mth.cos(swing*0.8F+((i==0||i==3)?0:Mth.PI))*0.55F*amount;
            stack.pushPose();
            p.translate(x,15.5F,z);
            stack.mulPose(Axis.XP.rotation(stride+(i<2?rearPose*0.45F:rearPose*0.95F)));
            p.plate(0,0.8F,0,i<2?1.45F:1.7F,2.15F,i<2?1.6F:2.35F,FUR);
            p.taper(0,0,0,0,3.2F,i<2?-0.5F:1.0F,i<2?1.05F:1.35F,0.72F,FUR);
            p.translate(0,3.2F,i<2?-0.3F:1.0F);
            stack.mulPose(Axis.XP.rotation(Math.max(0,-stride)*0.7F+(i<2?-rearPose*0.8F:0)));
            p.plate(0,0,0,0.82F,0.85F,0.92F,FUR);
            p.taper(0,0,0,0,3.65F,-0.65F,0.70F,0.46F,FUR);
            p.plate(0,4.0F,-0.85F,1.14F,0.65F,1.55F,FUR);
            for(int toe=0;toe<3;toe++)p.plate((toe-1)*0.63F,4.25F,-1.65F,0.38F,0.36F,0.85F,FUR);
            for(int claw=0;claw<3;claw++)
                p.taper((claw-1)*0.63F,4.2F,-2.0F,(claw-1)*0.70F,4.65F,-2.75F,0.19F,0.02F,GOLD);
            stack.popPose();
        }

        // Dorsal spines, overlapping scales and segmented serpentine tail.
        for(int i=0;i<7;i++)
            p.taper(0,12.5F+i*0.03F,-5+i*1.6F,0,10.2F+i*0.24F,-3.9F+i*1.6F,0.55F,0.025F,GOLD);
        if(lod<=1) for(int side:SIDES) for(int row=0;row<3;row++) for(int col=0;col<8;col++)
            {
                float x=side*(2.57F-row*0.18F),y=14.0F+row*0.85F,z=-4+col*1.25F;
                p.taper(x,y,z,x*1.03F,y+0.14F,z+0.83F,0.49F,0.08F,FUR);
                if((row+col)%3==0) p.plate(x*1.035F,y+0.32F,z+0.45F,0.08F,0.12F,0.33F,EMBER);
            }
        stack.pushPose();
        p.translate(0,15.2F,7);
        stack.mulPose(Axis.XP.rotation(rearPose*0.95F));
        for(int i=0;i<12;i++){
            stack.mulPose(Axis.YP.rotation(Mth.sin(age*0.09F-i*0.45F)*0.15F));
            stack.mulPose(Axis.XP.rotation(i==0?0.1F:-0.05F));
            float radius=1.45F-i*0.105F;
            p.taper(0,0,-0.12F,0,0,2.4F,radius,radius-0.105F,FUR);
            p.taper(0,-radius,0.6F,0,-radius*1.8F,1.5F,0.25F,0.02F,GOLD);
            if(i%2==0) p.plate(0,-radius,1.8F,0.18F,0.08F,0.3F,EMBER);
            p.translate(0,0,2.25F);
        }
        p.taper(0,0,-0.2F,0,0,2.6F,0.65F,0.02F,GOLD);
        p.taper(0,-0.35F,0.25F,0,-0.1F,2.1F,0.28F,0.02F,EMBER);
        stack.popPose();

        // Shoulder yaw and lift, delayed elbow flex, and continuously attached membrane fingers.
        float motion=amount*amount*(3-2*amount);
        float phase=swing*0.55F;
        for(int side:SIDES){
            stack.pushPose();
            p.translate(side*2.15F,12.5F,-3.6F);
            float idle=Mth.sin(age*0.09F)*0.025F;
            stack.mulPose(Axis.YP.rotation(side*(-0.55F+motion*0.40F+wingSpread*0.65F)));
            stack.mulPose(Axis.ZP.rotation(side*(-0.18F+idle+motion*0.31F*Mth.sin(phase)-wingSpread*0.30F+wingBeat*0.75F)));
            float[] root={0,0,0}, elbow={side*5,-3,-2};
            float flex=side*(-0.48F+motion*0.34F+motion*0.16F*Mth.sin(phase-0.8F)+wingSpread*0.48F+wingLag*0.22F);
            float lift=side*(0.12F+motion*0.22F*Mth.sin(phase-0.65F)+wingLag*0.48F);
            float[] tip=wingPoint(side,elbow,11,-1,1,flex,lift);
            float[] finger=wingPoint(side,elbow,13,3,8,flex,lift);
            float[] rear=wingPoint(side,elbow,8,4,14,flex,lift);
            float[] inner=wingPoint(side,elbow,2,5,12,flex,lift);
            float[] notch1=wingPoint(side,elbow,7,2,4,flex,lift);
            float[] notch2=wingPoint(side,elbow,6,3,8,flex,lift);
            float[] notch3=wingPoint(side,elbow,2,4,8,flex,lift);
            p.link(root,elbow,0.58F,0.40F,FUR);
            p.link(elbow,tip,0.42F,0.025F,FUR);
            p.link(elbow,finger,0.27F,0.025F,GOLD);
            p.link(elbow,rear,0.23F,0.025F,FUR);
            p.link(elbow,inner,0.20F,0.025F,FUR);
            p.membrane(root,elbow,inner);
            p.membrane(elbow,tip,notch1);
            p.membrane(elbow,notch1,finger);
            p.membrane(elbow,finger,notch2);
            p.membrane(elbow,notch2,rear);
            p.membrane(elbow,rear,notch3);
            p.membrane(elbow,notch3,inner);
            p.membrane(root,inner,new float[]{-side*0.8F,2.3F,10});
            stack.popPose();
        }
        stack.popPose();
    }

    private static float[] wingPoint(int side,float[] pivot,float x,float y,float z,float yaw,float roll) {
        x*=side;
        float rx=x*Mth.cos(yaw)+z*Mth.sin(yaw);
        float rz=-x*Mth.sin(yaw)+z*Mth.cos(yaw);
        return new float[]{pivot[0]+rx*Mth.cos(roll)-y*Mth.sin(roll),
            pivot[1]+rx*Mth.sin(roll)+y*Mth.cos(roll),pivot[2]+rz};
    }

    /** Editable anatomical cross sections in model pixels; loaded once per renderer. */
    private record Anatomy(float[][] torso,float[][] belly,float[][] neck,float[][] skull,float[][] jaw) {
        static Anatomy load() {
            var sections=new java.util.HashMap<String,List<float[]>>();
            String section=null;
            try(var reader=new BufferedReader(new InputStreamReader(
                Minecraft.getInstance().getResourceManager().getResourceOrThrow(ANATOMY).open(),StandardCharsets.UTF_8))) {
                for(String line;(line=reader.readLine())!=null;) {
                    line=line.strip();
                    if(line.isEmpty()||line.startsWith("#"))continue;
                    if(line.startsWith("[")) {
                        section=line.substring(1,line.length()-1);
                        if(sections.putIfAbsent(section,new ArrayList<>())!=null)throw new IllegalArgumentException("Duplicate section "+section);
                    } else {
                        if(section==null)throw new IllegalArgumentException("Missing section");
                        String[] fields=line.split("\\s+");
                        if(fields.length!=4)throw new IllegalArgumentException("Expected z y width height");
                        float[] ring=new float[4];
                        for(int i=0;i<4;i++){ring[i]=Float.parseFloat(fields[i]);if(!Float.isFinite(ring[i]))throw new IllegalArgumentException("Nonfinite ring");}
                        if(ring[2]<=0||ring[3]<=0)throw new IllegalArgumentException("Invalid radius");
                        List<float[]> rows=sections.get(section);
                        if(!rows.isEmpty()&&rows.get(rows.size()-1)[0]>=ring[0])throw new IllegalArgumentException("Unordered rings");
                        rows.add(ring);
                    }
                }
                return new Anatomy(required(sections,"torso"),required(sections,"belly"),required(sections,"neck"),required(sections,"skull"),required(sections,"jaw"));
            } catch(Exception e){throw new IllegalStateException("Cannot load embedded Ember Dragon anatomy",e);}
        }
        private static float[][] required(java.util.Map<String,List<float[]>> sections,String name) {
            List<float[]> rows=sections.get(name);
            if(rows==null||rows.size()<2||rows.size()>128)throw new IllegalArgumentException("Invalid section "+name);
            return rows.toArray(float[][]::new);
        }
    }

    private static final class Painter {
        private final PoseStack stack;
        private final VertexConsumer vertices;
        private final int light, overlay, color, lod;
        private static final java.util.Map<float[][],float[][][]> SCALE_CACHE = new java.util.WeakHashMap<>();

        Painter(PoseStack stack, VertexConsumer vertices, int light, int overlay, int color, int lod) {
            this.stack=stack; this.vertices=vertices; this.light=light; this.overlay=overlay; this.color=color; this.lod=lod;
        }

        void translate(float x, float y, float z) { stack.translate(x/16F,y/16F,z/16F); }

        void bone(float ax,float ay,float az,float bx,float by,float bz,float radius,int material) {
            taper(ax,ay,az,bx,by,bz,radius,radius*0.72F,material);
        }

        void link(float[] a,float[] b,float r0,float r1,int material) {
            taper(a[0],a[1],a[2],b[0],b[1],b[2],r0,r1,material);
        }

        void taper(float ax,float ay,float az,float bx,float by,float bz,float r0,float r1,int material) {
            float dx=bx-ax,dy=by-ay,dz=bz-az;
            float length=(float)Math.sqrt(dx*dx+dy*dy+dz*dz);
            if(length<0.001F)return;
            stack.pushPose();
            translate(ax,ay,az);
            stack.mulPose(new org.joml.Quaternionf().rotationTo(0,0,1,dx/length,dy/length,dz/length));
            hull(new float[][]{{0,0,r0,r0},{length,0,r1,r1}},material);
            stack.popPose();
        }

        // Connected anatomy with a finer curved profile, preserving dorsal ridges.
        void hull(float[][] rings,int material) {
            int count=rings.length;
            int sides=count>=5?(lod==0?32:lod==1?24:lod==2?16:8):8;
            float[][][] points=new float[count][sides][3];
            for(int i=0;i<count;i++) for(int j=0;j<sides;j++) {
                float angle=-Mth.HALF_PI+j*Mth.TWO_PI/sides;
                float x=Mth.cos(angle),y=Mth.sin(angle);
                float power=count>=5?0.76F:0.58F;
                x=Math.copySign((float)Math.pow(Math.abs(x),power),x);
                y=Math.copySign((float)Math.pow(Math.abs(y),power),y);
                points[i][j]=new float[]{x*rings[i][2],rings[i][1]+y*rings[i][3],rings[i][0]};
            }
            for(int i=0;i<count-1;i++) for(int j=0;j<sides;j++){
                int k=(j+1)%sides;
                float u0=j/(float)sides,u1=(j+1)/(float)sides;
                float v0=i/(float)(count-1),v1=(i+1)/(float)(count-1);
                texturedFace(points[i][j],points[i][k],points[i+1][k],points[i+1][j],material,u0,v0,u1,v1);
            }
            float[] first={0,rings[0][1],rings[0][0]};
            float[] last={0,rings[count-1][1],rings[count-1][0]};
            for(int j=0;j<sides;j++){
                int k=(j+1)%sides;
                face(first,points[0][k],points[0][j],points[0][j],material);
                face(last,points[count-1][j],points[count-1][k],points[count-1][k],material);
            }
        }

        // Surface-conforming raised scales. Each scale is a six-sided dome with
        // a second ring and central ridge, 18 nondegenerate triangles.
        // Static geometry is cached per anatomy section and LOD.
        void scales(float[][] rings,int fullRows,int fullCols) {
            if(lod==3)return;
            float[][][] levels=SCALE_CACHE.computeIfAbsent(rings,key->new float[3][][]);
            float[][] mesh=levels[lod];
            if(mesh==null){
                int rows=fullRows*SCALE_ROWS[lod]/96;
                int cols=fullCols*SCALE_COLUMNS[lod]/64;
                mesh=new float[rows*cols*13][];
                int index=0;
                for(int row=0;row<rows;row++)for(int col=0;col<cols;col++){
                    float u=(col+0.5F*(row%2)+0.5F)/cols;
                    float v=(row+0.5F)/rows;
                    float height=0.075F+0.045F*Mth.sin(row*1.7F+col*2.3F);
                    float[][] outer=new float[6][],inner=new float[6][];
                    for(int k=0;k<6;k++){
                        float angle=k*Mth.TWO_PI/6;
                        float du=Mth.cos(angle)*0.47F/cols,dv=Mth.sin(angle)*0.47F/rows;
                        outer[k]=scalePoint(rings,u+du,v+dv,0.012F);
                        inner[k]=scalePoint(rings,u+du*0.52F,v+dv*0.52F,height*0.7F);
                    }
                    float[] top=scalePoint(rings,u,v,height);
                    // Six outer, six inner and one apex positions.
                    for(float[] point:outer)mesh[index++]=point;
                    for(float[] point:inner)mesh[index++]=point;
                    mesh[index++]=top;
                }
                levels[lod]=mesh;
            }
            for(int i=0;i<mesh.length;i+=13){
                for(int k=0;k<6;k++){
                    int n=(k+1)%6;
                    texturedFace(mesh[i+k],mesh[i+n],mesh[i+6+n],mesh[i+6+k],FUR,0.35F,0.35F,0.65F,0.65F);
                    texturedFace(mesh[i+6+k],mesh[i+6+n],mesh[i+12],mesh[i+12],FUR,0.40F,0.40F,0.60F,0.60F);
                }
            }
        }

        private static float[] scalePoint(float[][] rings,float u,float v,float rise){
            float t=Mth.clamp(v,0,1)*(rings.length-1);
            int a=Math.min((int)t,rings.length-2);
            float f=t-a;
            float z=Mth.lerp(f,rings[a][0],rings[a+1][0]);
            float cy=Mth.lerp(f,rings[a][1],rings[a+1][1]);
            float rx=Mth.lerp(f,rings[a][2],rings[a+1][2]);
            float ry=Mth.lerp(f,rings[a][3],rings[a+1][3]);
            float angle=-Mth.HALF_PI+u*Mth.TWO_PI;
            float x=Mth.cos(angle),y=Mth.sin(angle);
            return new float[]{Math.copySign((float)Math.pow(Math.abs(x),0.76F),x)*(rx+rise),
                cy+Math.copySign((float)Math.pow(Math.abs(y),0.76F),y)*(ry+rise),z};
        }

        void horn(int side) {
            // Continuous curved horn; rings overlap slightly at articulated bends.
            float[][] points={{side*1.25F,-0.95F,0.85F},{side*1.85F,-2.0F,1.45F},
                {side*2.15F,-3.15F,2.0F},{side*2.1F,-4.3F,2.85F},
                {side*1.78F,-5.1F,3.7F},{side*1.5F,-5.55F,4.55F}};
            float[] radii={0.77F,0.63F,0.46F,0.30F,0.14F,0.018F};
            for(int i=0;i<points.length-1;i++)link(points[i],points[i+1],radii[i],radii[i+1],GOLD);
        }

        void plate(float x,float y,float z,float rx,float ry,float rz,int material){
            stack.pushPose();
            translate(x,y,z);
            hull(new float[][]{{-rz,0,rx*0.28F,ry*0.28F},
                {-rz*0.42F,0,rx,ry},{rz*0.42F,0,rx*0.8F,ry*0.8F},
                {rz,0,rx*0.10F,ry*0.10F}},material);
            stack.popPose();
        }

        void face(float[] a,float[] b,float[] c,float[] d,int material) {
            float ux=b[0]-a[0],uy=b[1]-a[1],uz=b[2]-a[2];
            float vx=c[0]-a[0],vy=c[1]-a[1],vz=c[2]-a[2];
            float nx=uy*vz-uz*vy,ny=uz*vx-ux*vz,nz=ux*vy-uy*vx;
            float length=(float)Math.sqrt(nx*nx+ny*ny+nz*nz);
            if(length<0.00001F)return;
            for(float[] v:new float[][]{a,b,c,d})emit(v[0],v[1],v[2],nx/length,ny/length,nz/length,material);
        }

        void texturedFace(float[] a,float[] b,float[] c,float[] d,int material,float u0,float v0,float u1,float v1) {
            float ux=b[0]-a[0],uy=b[1]-a[1],uz=b[2]-a[2];
            float vx=c[0]-a[0],vy=c[1]-a[1],vz=c[2]-a[2];
            float nx=uy*vz-uz*vy,ny=uz*vx-ux*vz,nz=ux*vy-uy*vx;
            float length=(float)Math.sqrt(nx*nx+ny*ny+nz*nz);
            if(length<0.00001F)return;
            float[][] points={a,b,c,d};
            float[] us={u0,u1,u1,u0},vs={v0,v0,v1,v1};
            for(int i=0;i<4;i++)emitUv(points[i][0],points[i][1],points[i][2],nx/length,ny/length,nz/length,material,us[i],vs[i]);
        }

        void emitUv(float x,float y,float z,float nx,float ny,float nz,int material,float u,float v) {
            int[] sample=PALETTE[material];
            float baseU=(sample[0]-8)/64F,baseV=(sample[1]-8)/64F;
            vertices.addVertex(stack.last(),x/16,y/16,z/16).setColor(color)
                .setUv(baseU+(0.002F+0.246F*u),baseV+(0.002F+0.246F*v))
                .setOverlay(overlay).setLight(material==EMBER?FULL_LIGHT:light)
                .setNormal(stack.last(),nx,ny,nz);
        }

        void emit(float x,float y,float z,float nx,float ny,float nz,int material) {
            int[] sample=PALETTE[material];
            vertices.addVertex(stack.last(),x/16,y/16,z/16).setColor(color)
                .setUv((sample[0]+0.5F)/64,(sample[1]+0.5F)/64)
                .setOverlay(overlay).setLight(material==EMBER?FULL_LIGHT:light)
                .setNormal(stack.last(),nx,ny,nz);
        }

        // Curved membrane patch: boundaries remain fixed to the animated bones.
        void membrane(float[] a,float[] b,float[] c) {
            int steps=MEMBRANE_STEPS[lod];
            float ux=b[0]-a[0],uy=b[1]-a[1],uz=b[2]-a[2];
            float vx=c[0]-a[0],vy=c[1]-a[1],vz=c[2]-a[2];
            float nx=uy*vz-uz*vy,ny=uz*vx-ux*vz,nz=ux*vy-uy*vx;
            float len=(float)Math.sqrt(nx*nx+ny*ny+nz*nz);
            if(len<0.001F)return;
            float bend=Math.min(0.75F,len*0.017F);
            float[][][] points=new float[steps+1][][];
            for(int i=0;i<=steps;i++){
                points[i]=new float[steps-i+1][];
                for(int j=0;j<=steps-i;j++){
                    float u=i/(float)steps,v=j/(float)steps,w=1-u-v;
                    points[i][j]=new float[]{w*a[0]+u*b[0]+v*c[0],
                        w*a[1]+u*b[1]+v*c[1]+27*u*v*w*bend*(1+0.15F*Mth.sin(18*u+12*v)),
                        w*a[2]+u*b[2]+v*c[2]};
                }
            }
            for(int i=0;i<steps;i++)for(int j=0;j<steps-i;j++){
                membraneTriangle(points[i][j],points[i+1][j],points[i][j+1]);
                if(j<steps-i-1)membraneTriangle(points[i+1][j],points[i+1][j+1],points[i][j+1]);
            }
        }

        void membraneFace(float[] a,float[] b,float[] c) {
            float ux=b[0]-a[0],uy=b[1]-a[1],uz=b[2]-a[2];
            float vx=c[0]-a[0],vy=c[1]-a[1],vz=c[2]-a[2];
            float nx=uy*vz-uz*vy,ny=uz*vx-ux*vz,nz=ux*vy-uy*vx;
            float len=(float)Math.sqrt(nx*nx+ny*ny+nz*nz);
            if(len<0.00001F)return;
            for(float[] q:new float[][]{a,b,c,c})
                emitUv(q[0],q[1],q[2],nx/len,ny/len,nz/len,7,
                    Mth.clamp(Math.abs(q[0])/24,0,1),Mth.clamp((q[2]+4)/24,0,1));
        }

        void membraneTriangle(float[] a,float[] b,float[] c){
            membraneFace(a,b,c);
            membraneFace(a,c,b);
        }

    }
}
