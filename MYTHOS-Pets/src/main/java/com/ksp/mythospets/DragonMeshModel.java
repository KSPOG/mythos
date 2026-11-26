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
    private float breath, blink, walkBob;
    private float rearPose, roarPose, wingSpread, wingBeat, wingLag, crouch, rearReach, rearLook, rearClaw, rearTail;
    private static final float REAR_ANGLE=1.48F;
    private static float track(float t,float... keys){
        if(t<=keys[0])return keys[1];
        for(int i=2;i<keys.length;i+=2)if(t<=keys[i])return Mth.lerp(ease((t-keys[i-2])/(keys[i]-keys[i-2])),keys[i-1],keys[i+1]);
        return keys[keys.length-1];
    }

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
        float offset=(entity.getId()%31)*0.37F;
        breath=Mth.sin(age*0.105F+offset);
        float blinkTime=(age+entity.getId()%97)%113;
        blink=1-envelope(blinkTime,5,2,2)*0.94F;
        walkBob=0.16F*amount*Mth.cos(swing*1.6F);
        yaw = Mth.clamp(netHeadYaw, -45, 45);
        pitch = Mth.clamp(headPitch, -30, 30);
        int action=entity.idleAction();
        float t=entity.idleElapsed()+Mth.clamp(ageInTicks-entity.tickCount,0,1);
        float still=1-ease(amount/0.25F);
        rearPose=0;roarPose=0;wingSpread=0;wingBeat=0;wingLag=0;crouch=0;rearReach=0;rearLook=0;rearClaw=0;rearTail=0;
        switch(action) {
            case DragonEntity.FLAP -> {
                wingSpread=envelope(t,64,12,14)*still;
                float beat=envelope(t-10,44,5,8)*still;
                wingBeat=beat*Mth.sin((t-10)*Mth.TWO_PI/16);
                wingLag=beat*Mth.sin((t-13)*Mth.TWO_PI/16);
            }
            case DragonEntity.REAR -> {
                // Authored channels, staggered in time: gather, thrust, settle,
                // display, controlled descent, impact and recovery.
                rearPose=track(t,0,0,16,0,34,1,42,0.94F,54,1,102,1,112,0.92F,138,0,160,0)*still;
                crouch=track(t,0,0,12,1,23,0,126,0,140,1,160,0)*still;
                rearReach=track(t,0,0,14,0,30,1,48,0.44F,78,0.67F,104,0.42F,130,1,145,0,160,0)*still;
                rearLook=track(t,0,0,14,-12,34,-18,54,9,80,-7,110,4,140,0,160,0)*still;
                rearClaw=track(t,0,0,30,1,55,0.25F,86,0.8F,118,0.25F,144,0,160,0)*still;
                rearTail=track(t,0,0,15,0.6F,36,1,100,0.8F,140,0,160,0)*still;
                wingSpread=track(t,0,0,12,0.2F,34,1,52,0.86F,85,1,113,0.85F,140,0.25F,160,0)*still;
                wingBeat=(track(t,0,0,18,-0.5F,30,0.85F,43,-0.25F,57,0,112,-0.38F,132,0.5F,150,0)*still);
                wingLag=track(t,0,0,21,-0.35F,34,0.7F,48,-0.2F,62,0,117,-0.25F,137,0.38F,155,0)*still;
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
        stack.translate(0, (breath*0.035F+walkBob)/16F*(1-rearPose), 0);
        float tilt=rearPose*REAR_ANGLE;
        // Shift the hips forward over the hind feet; lower the pelvis during anticipation.
        float hipDrop=0.85F*crouch+0.38F*rearPose;
        float hipShift=-rearPose*0.8F;
        p.translate(0,hipDrop,hipShift);
        // Raise chest about the hind hip; the rear feet remain in world-facing stance.
        p.translate(0,15.5F,5);
        stack.mulPose(Axis.XP.rotation(-tilt));
        p.translate(0,-15.5F,-5);
        // Long reptilian torso, plated belly and curved neck.
        stack.pushPose();
        p.translate(0,15,0);
        stack.scale(1+breath*0.016F+roarPose*0.025F,1+breath*0.012F+roarPose*0.02F,1);
        p.translate(0,-15,0);
        p.hull(anatomy.torso,FUR);
        p.scales(anatomy.torso,112,64);
        p.hull(anatomy.belly,MANE);
        stack.popPose();
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
        stack.mulPose(Axis.YP.rotationDegrees(yaw+Mth.sin(age*0.035F)*3F*(1-amount)*(1-rearPose)));
        stack.mulPose(Axis.XP.rotationDegrees(pitch*(1-rearPose)+rearPose*62F-roarPose*18F+rearLook+Mth.sin(swing*0.8F)*amount*2.5F));
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
        if(roarPose>0.08F){
            p.plate(0,-0.22F,-2.1F,0.56F,0.12F,1.2F,EAR);
            for(int side:SIDES)for(int tooth=0;tooth<4;tooth++)
                p.taper(side*(0.96F-tooth*0.10F),-0.19F,-1.4F-tooth*0.62F,side*(0.94F-tooth*0.10F),-0.65F,-1.5F-tooth*0.62F,0.12F,0.018F,GOLD);
        }
        stack.popPose();
        for(int side:SIDES){
            p.plate(side*1.78F,-0.55F,-1.48F,0.15F,0.16F*blink,0.39F,EMBER);
            p.plate(side*1.91F,-0.55F,-1.48F,0.025F,0.14F*blink,0.055F,FUR);
            p.taper(side*1.38F,-0.9F,-2.4F,side*1.75F,-0.98F,-0.3F,0.31F,0.14F,FUR);
            p.plate(side*0.81F,-0.03F,-4.35F,0.18F,0.11F,0.24F,FUR);
            p.plate(side*1.32F,-0.78F,1.10F,0.91F,0.70F,1.05F,FUR);
            p.horn(side);
            for(int ridge=0;ridge<2;ridge++) {
                float z=1.2F+ridge*0.72F;
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
            float gait=swing*0.8F+(i==0?0:i==1?Mth.PI:i==2?Mth.PI*1.5F:Mth.PI*0.5F);
            float stride=Mth.cos(gait)*0.48F*amount;
            float liftStep=Math.max(0,Mth.sin(gait))*amount*(1-rearPose);
            stack.pushPose();
            p.translate(x,15.5F,z);
            // Hind legs cancel body pitch, while forelegs reach ahead and curl at the elbow.
            float upper=i<2?rearPose*0.82F+rearReach*0.58F:0;
            float knee=i<2?Math.max(0,-stride)*0.7F-rearPose*1.35F+rearReach*0.35F:Math.max(0,-stride)*0.7F;
            float ankle=0;
            knee-=liftStep*(i<2?0.58F:0.42F);
            if(i>=2){
                // Solve in unrotated model coordinates. Target is the rest ankle
                // at (6.85,0.35) relative to the original hip.
                float weight=ease((rearPose+crouch)*8)*(1-ease(amount/0.25F));
                float ty=6.85F-hipDrop-(breath*0.035F+walkBob)*(1-rearPose),tz=0.35F-hipShift;
                float l1=(float)Math.hypot(3.2,1),l2=(float)Math.hypot(3.65,-0.65);
                float distance=Math.min((float)Math.hypot(ty,tz),l1+l2-0.001F);
                float bend=-(float)Math.acos(Mth.clamp((distance*distance-l1*l1-l2*l2)/(2*l1*l2),-1,1));
                float first=(float)(Math.atan2(tz,ty)-Math.atan2(l2*Math.sin(bend),l1+l2*Math.cos(bend)));
                float worldUpper=first-(float)Math.atan2(1,3.2);
                float solvedKnee=bend+(float)Math.atan2(1,3.2)-(float)Math.atan2(-0.65,3.65);
                upper=tilt+Mth.lerp(weight,stride,worldUpper)-stride;
                knee=Mth.lerp(weight,knee,solvedKnee);
                ankle=-(stride+upper-tilt+knee)*weight;
            }
            stack.mulPose(Axis.XP.rotation(stride+upper));
            p.plate(0,0.8F,0,i<2?1.45F:1.7F,2.15F,i<2?1.6F:2.35F,FUR);
            p.taper(0,0,0,0,3.2F,i<2?-0.5F:1.0F,i<2?1.05F:1.35F,0.72F,FUR);
            p.translate(0,3.2F,i<2?-0.3F:1.0F);
            stack.mulPose(Axis.XP.rotation(knee));
            p.plate(0,0,0,0.82F,0.85F,0.92F,FUR);
            p.taper(0,0,0,0,3.65F,-0.65F,0.70F,0.46F,FUR);
            // Counter-rotate hind paws independently of knee bend.
            if(i>=2){
                p.translate(0,3.65F,-0.65F);
                stack.mulPose(Axis.XP.rotation(ankle));
                p.translate(0,-3.65F,0.65F);
            }
            if(i<2){
                p.translate(0,3.65F,-0.65F);
                stack.mulPose(Axis.XP.rotation(rearClaw*0.48F));
                p.translate(0,-3.65F,0.65F);
            }
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
        stack.mulPose(Axis.XP.rotation(tilt-rearPose*0.12F+rearTail*0.22F));
        for(int i=0;i<12;i++){
            stack.mulPose(Axis.YP.rotation(Mth.sin(age*0.075F-i*0.48F)*(0.045F+amount*0.07F)+Mth.sin(swing*0.45F-i*0.32F)*amount*0.025F));
            stack.mulPose(Axis.XP.rotation(i==0?0.1F:-0.05F-rearTail*0.012F));
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
            p.membraneTension=wingSpread*0.6F+Math.abs(wingBeat)*0.35F;
            p.membranePhase=age*0.22F+side*0.25F;
            float idle=Mth.sin(age*0.09F+side*0.4F)*0.025F;
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
        private float membraneTension,membranePhase;
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
                    float variation=Mth.sin(row*1.73F+col*2.37F);
                    float height=0.048F+0.025F*variation;
                    u+=0.12F*variation/cols;
                    v+=0.10F*Mth.cos(row*2.13F-col*1.79F)/rows;
                    float[][] outer=new float[6][],inner=new float[6][];
                    for(int k=0;k<6;k++){
                        float angle=k*Mth.TWO_PI/6;
                        float du=Mth.cos(angle)*0.47F*(1+0.12F*variation)/cols,dv=Mth.sin(angle)*0.47F*(1-0.1F*variation)/rows;
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

        // A single swept horn surface, using continuous frames and UVs.
        void horn(int side) {
            float[][] control={{side*1.28F,-0.75F,1.10F},{side*1.94F,-1.85F,1.65F},
                {side*2.40F,-3.0F,2.38F},{side*2.48F,-4.18F,3.20F},
                {side*2.22F,-5.10F,4.20F},{side*1.85F,-5.58F,5.10F}};
            float[] radius={0.92F,0.76F,0.55F,0.34F,0.16F,0.008F};
            int segments=lod==0?64:lod==1?40:lod==2?24:12;
            int sides=lod==0?32:lod==1?24:lod==2?16:8;
            float[][][] rings=new float[segments+1][sides][3];
            float[][][] normals=new float[segments+1][sides][3];
            for(int i=0;i<=segments;i++){
                float t=i/(float)segments;
                float[] center=hornCenter(control,t);
                float[] before=hornCenter(control,Math.max(0,t-0.001F));
                float[] after=hornCenter(control,Math.min(1,t+0.001F));
                org.joml.Vector3f tangent=new org.joml.Vector3f(after[0]-before[0],after[1]-before[1],after[2]-before[2]).normalize();
                org.joml.Vector3f axis=new org.joml.Vector3f(1,0,0);
                axis.sub(new org.joml.Vector3f(tangent).mul(axis.dot(tangent))).normalize();
                org.joml.Vector3f binormal=new org.joml.Vector3f(tangent).cross(axis).normalize();
                float f=t*(radius.length-1);
                int k=Math.min((int)f,radius.length-2);
                float rad=Mth.lerp(f-k,radius[k],radius[k+1]);
                for(int j=0;j<sides;j++){
                    float angle=j*Mth.TWO_PI/sides;
                    float ridge=1+0.035F*Mth.cos(angle*5+0.4F*t)*(1-t);
                    org.joml.Vector3f normal=new org.joml.Vector3f(axis).mul(Mth.cos(angle))
                        .add(new org.joml.Vector3f(binormal).mul(Mth.sin(angle)));
                    rings[i][j]=new float[]{center[0]+normal.x*rad*ridge,center[1]+normal.y*rad*ridge,center[2]+normal.z*rad*ridge};
                    normals[i][j]=new float[]{normal.x,normal.y,normal.z};
                }
            }
            for(int i=0;i<segments;i++)for(int j=0;j<sides;j++){
                int k=(j+1)%sides;
                hornVertex(rings[i][j],normals[i][j],j/(float)sides,i/(float)segments);
                hornVertex(rings[i][k],normals[i][k],(j+1)/(float)sides,i/(float)segments);
                hornVertex(rings[i+1][k],normals[i+1][k],(j+1)/(float)sides,(i+1)/(float)segments);
                hornVertex(rings[i+1][j],normals[i+1][j],j/(float)sides,(i+1)/(float)segments);
            }
            float[] end=hornCenter(control,1);
            for(int j=0;j<sides;j++)face(end,rings[segments][j],rings[segments][(j+1)%sides],rings[segments][(j+1)%sides],GOLD);
        }

        private static float[] hornCenter(float[][] points,float t){
            float f=t*(points.length-1);
            int i=Math.min((int)f,points.length-2);
            float u=f-i,u2=u*u,u3=u2*u;
            float[] a=points[Math.max(0,i-1)],b=points[i],c=points[i+1],d=points[Math.min(points.length-1,i+2)];
            float[] result=new float[3];
            for(int k=0;k<3;k++)result[k]=0.5F*((2*b[k])+(-a[k]+c[k])*u+
                (2*a[k]-5*b[k]+4*c[k]-d[k])*u2+(-a[k]+3*b[k]-3*c[k]+d[k])*u3);
            return result;
        }

        private void hornVertex(float[] p,float[] n,float u,float v){
            emitUv(p[0],p[1],p[2],n[0],n[1],n[2],GOLD,u,v);
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
            float bend=Math.min(0.75F,len*0.017F)*(1-0.62F*membraneTension);
            float[][][] points=new float[steps+1][][];
            for(int i=0;i<=steps;i++){
                points[i]=new float[steps-i+1][];
                for(int j=0;j<=steps-i;j++){
                    float u=i/(float)steps,v=j/(float)steps,w=1-u-v;
                    points[i][j]=new float[]{w*a[0]+u*b[0]+v*c[0],
                        w*a[1]+u*b[1]+v*c[1]+27*u*v*w*bend*(1+0.12F*Mth.sin(18*u+12*v+membranePhase)),
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
