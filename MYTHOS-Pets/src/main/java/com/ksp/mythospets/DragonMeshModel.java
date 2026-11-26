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
    private static final int[] MEMBRANE_STEPS={24,15,8,3};
    private static final int EMBER = 4;
    private static final int FULL_LIGHT = 0xF000F0;

    private static final int FUR = 0, MANE = 1, GOLD = 2, EAR = 3, EYE = 4, TIP = 5;
    // Dragon-specific material atlas; ember surfaces use full packed light.
    private static final int[][] PALETTE = {{8,8},{24,8},{40,8},{56,8},{8,24},{24,24},{40,24},{56,24}};
    private float swing, amount, age, yaw, pitch;
    private float breath, blink, walkBob;
    private float wingOpen, wingDrive, wingRecovery;
    private float liftHeight, flightTuck, landingFlex, flightPitch;
    private float performanceTime, performanceWeight, foreGesture, foreFollow, wristFollow, wingWrist, wingFinger;
    private float rearPose, roarPose, wingSpread, wingBeat, wingLag, crouch, rearReach, rearLook, rearClaw, rearTail;
    private static final float REAR_ANGLE=1.48F;
    private static float track(float t,float... keys){
        if(t<=keys[0])return keys[1];
        for(int i=2;i<keys.length;i+=2)if(t<=keys[i])return Mth.lerp(smooth((t-keys[i-2])/(keys[i]-keys[i-2])),keys[i-1],keys[i+1]);
        return keys[keys.length-1];
    }

    private static float smooth(float t){t=Mth.clamp(t,0,1);return t*t*t*(t*(6*t-15)+10);}
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
        performanceTime=t;
        performanceWeight=action==DragonEntity.NONE?0:envelope(t,DragonEntity.duration(action),9,12)*still;
        foreGesture=0;foreFollow=0;wristFollow=0;wingWrist=0;wingFinger=0;
        wingOpen=0;wingDrive=0;wingRecovery=0;
        liftHeight=0;flightTuck=0;landingFlex=0;flightPitch=0;
        rearPose=0;roarPose=0;wingSpread=0;wingBeat=0;wingLag=0;crouch=0;rearReach=0;rearLook=0;rearClaw=0;rearTail=0;
        switch(action) {
            case DragonEntity.FLAP -> {
                // One continuous phase clock for all joints. Distal joints
                // respond later in the same cycle rather than stopping at keys.
                float open=smooth((t-4)/18)*smooth((128-t)/25);
                float drive=smooth((t-12)/12)*smooth((113-t)/18);
                float phase=(t-24)*Mth.TWO_PI/24;
                wingOpen=open*still;
                wingSpread=wingOpen;
                wingDrive=drive*(-Mth.cos(phase)+0.10F*Mth.sin(2*phase))*0.90F*still;
                wingRecovery=drive*Mth.sin(phase-0.55F)*0.68F*still;
                wingBeat=wingDrive;
                wingLag=-drive*Mth.cos(phase-0.44F)*still;
                wingWrist=-drive*Mth.cos(phase-0.82F)*still;
                wingFinger=-drive*Mth.cos(phase-1.16F)*still;
                // Render-space lift only: keeps server movement and hitbox intact.
                // Smooth takeoff, hover with tiny stroke-linked bob, flare and landing.
                float hover=smooth((t-29)/23)*smooth((94-t)/18);
                float height=0.48F*smooth((t-23)/29)*smooth((105-t)/29);
                liftHeight=(height+0.025F*hover*Mth.sin(phase-0.6F))*still;
                flightTuck=smooth((t-26)/17)*smooth((102-t)/20)*still;
                landingFlex=smooth((t-103)/5)*smooth((124-t)/16)*still;
                crouch=smooth(t/10)*smooth((24-t)/10)*still;
                flightPitch=(0.09F*smooth((t-15)/10)*smooth((49-t)/20)
                    -0.07F*smooth((t-80)/12)*smooth((110-t)/12))*still;
                foreGesture=wingDrive*0.045F*flightTuck;
                foreFollow=wingLag*0.08F*flightTuck;
                wristFollow=wingWrist*0.08F*flightTuck;
            }
            case DragonEntity.REAR -> {
                wingOpen=track(t,0,0,14,0.28F,34,1,52,0.8F,87,1,115,0.8F,140,0.25F,160,0)*still;
                wingDrive=track(t,0,0,18,-0.65F,30,0.8F,45,-0.23F,65,0,113,-0.35F,132,0.45F,160,0)*still;
                wingRecovery=track(t,0,0,25,0.5F,41,-0.65F,61,0,120,0.4F,143,-0.4F,160,0)*still;
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
                foreGesture=track(t,0,0,14,0,32,0.65F,48,-0.20F,70,0.32F,93,-0.27F,113,0.15F,136,0.5F,153,0)*still;
                foreFollow=track(t,0,0,18,0,36,-0.75F,53,0.14F,76,-0.44F,99,0.22F,119,-0.18F,142,0.28F,157,0)*still;
                wristFollow=track(t,0,0,23,0,41,0.64F,58,-0.24F,81,0.53F,103,-0.19F,124,0.2F,147,-0.25F,160,0)*still;
                wingWrist=track(t,0,0,24,-0.30F,39,0.65F,54,-0.16F,70,0,120,-0.22F,143,0.35F,160,0)*still;
                wingFinger=track(t,0,0,27,-0.24F,43,0.56F,60,-0.12F,76,0,124,-0.18F,147,0.28F,160,0)*still;
            }
            case DragonEntity.ROAR -> {
                wingOpen=track(t,0,0,12,0.35F,25,1,48,1,62,0.4F,72,0)*still;
                wingDrive=track(t,0,0,14,-0.65F,27,0.4F,45,-0.15F,58,0.3F,72,0)*still;
                wingRecovery=track(t,0,0,18,0.4F,33,-0.5F,53,0.15F,72,0)*still;
                rearPose=envelope(t,72,18,17)*0.82F*still;
                roarPose=envelope(t-16,39,10,9)*still;
                wingSpread=envelope(t,72,18,17)*still;
                wingBeat=roarPose*0.16F*Mth.sin(t*0.24F);
                wingLag=roarPose*0.28F*Mth.sin(t*0.24F-0.6F);
                wingWrist=roarPose*0.32F*Mth.sin(t*0.24F-1.0F);
                wingFinger=roarPose*0.25F*Mth.sin(t*0.24F-1.5F);
                foreGesture=roarPose*(0.24F+0.18F*Mth.sin(t*0.21F));
                foreFollow=-roarPose*(0.48F+0.16F*Mth.sin(t*0.21F-0.6F));
                wristFollow=roarPose*0.35F*Mth.sin(t*0.21F-1.1F);
            }
            case DragonEntity.BREATH -> {
                float charge=envelope(t,30,10,9);
                roarPose=envelope(t-5,22,6,8);
                rearLook=track(t,0,0,8,-18,12,8,20,4,30,0);
                crouch=charge*0.42F;wingOpen=charge*0.64F;
                wingDrive=track(t,0,0,8,-0.42F,13,0.55F,30,0);
                wingLag=wingDrive*0.6F;wingWrist=wingDrive*0.3F;
                foreGesture=charge*0.15F;rearTail=charge*0.35F;
            }
            case DragonEntity.GUARD -> {
                float guard=envelope(t,24,9,10);
                rearPose=guard*0.25F;roarPose=guard*0.5F;
                wingOpen=guard;wingDrive=guard*0.2F;
                wingRecovery=guard*0.4F;rearLook=-guard*12;
            }
            default -> {}
        }
    }

    @Override
    public void renderToBuffer(PoseStack stack, VertexConsumer vertices, int light, int overlay, int color) {
        if (anatomy == null) anatomy = Anatomy.load();
        Painter p = new Painter(stack, vertices, light, overlay, color, lod);
        stack.pushPose();
        stack.translate(0, (breath*0.035F+walkBob)/16F*(1-rearPose)-liftHeight, 0);
        float tilt=rearPose*REAR_ANGLE+flightPitch;
        // Shift the hips forward over the hind feet; lower the pelvis during anticipation.
        float hipDrop=0.85F*crouch+0.38F*rearPose+0.65F*landingFlex;
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
            float side=i%2==0?-1:1;
            float gesture=performanceWeight*rearPose*Mth.sin(performanceTime*0.16F+side*0.75F);
            p.translate(x,15.5F,z);
            if(i<2){
                p.translate(side*rearPose*0.18F,gesture*0.12F,-rearPose*0.16F);
                stack.mulPose(Axis.ZP.rotation(side*(rearPose*0.16F+gesture*0.08F)));
                stack.mulPose(Axis.YP.rotation(side*(rearPose*0.10F+gesture*0.06F)));
            }
            // Hind legs cancel body pitch, while forelegs reach ahead and curl at the elbow.
            float upper=i<2?rearPose*0.82F+rearReach*0.58F+foreGesture+gesture*0.13F:0;
            float knee=i<2?Math.max(0,-stride)*0.7F-rearPose*1.35F+rearReach*0.35F+foreFollow+gesture*0.12F:Math.max(0,-stride)*0.7F;
            float ankle=0;
            knee-=liftStep*(i<2?0.58F:0.42F);
            if(i>=2){
                // Solve in unrotated model coordinates. Target is the rest ankle
                // at (6.85,0.35) relative to the original hip.
                float weight=ease((rearPose+crouch+landingFlex)*8)*(1-ease(amount/0.25F))*(1-flightTuck);
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
            // Airborne feet tuck under the body, then extend before contact.
            upper+=flightTuck*(i<2?0.45F:-0.36F);
            knee+=flightTuck*(i<2?-0.72F:0.68F);
            if(i<2){upper+=0.16F*(crouch+landingFlex);knee-=0.32F*(crouch+landingFlex);}
            else ankle-=flightTuck*0.22F;
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
                stack.mulPose(Axis.XP.rotation(rearClaw*0.48F+wristFollow+liftStep*0.26F+flightTuck*0.25F));
                stack.mulPose(Axis.ZP.rotation(side*(rearPose*0.12F+gesture*0.10F)));
                p.translate(0,-3.65F,0.65F);
            }
            p.plate(0,4.0F,-0.85F,1.14F,0.65F,1.55F,FUR);
            // Each digit has an independent base pivot and curls with distal follow-through.
            for(int toe=0;toe<3;toe++){
                float spread=(toe-1);
                stack.pushPose();
                p.translate(spread*0.63F,4.25F,-1.15F);
                float curl=i<2?(rearClaw*0.40F+wristFollow*0.35F+gesture*0.12F):0;
                stack.mulPose(Axis.YP.rotation(spread*(0.08F+rearPose*0.14F)));
                stack.mulPose(Axis.XP.rotation(curl*(1-0.12F*Math.abs(spread))));
                p.plate(0,0,-0.50F,0.38F,0.36F,0.85F,FUR);
                p.taper(0,-0.05F,-0.85F,spread*0.07F,0.40F,-1.60F,0.19F,0.02F,GOLD);
                stack.popPose();
            }
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
        stack.mulPose(Axis.XP.rotation(tilt-rearPose*0.12F+rearTail*0.22F+flightTuck*0.16F));
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

        // Bat-inspired chain: humerus, forearm, wrist and separately bent digits.
        // All membrane patches use the very same boundary points as the bones.
        float motion=amount*amount*(3-2*amount);
        for(int side:SIDES){
            stack.pushPose();
            p.translate(side*2.15F,12.5F,-3.6F);
            float open=Mth.clamp(wingOpen+motion*0.10F,0,1);
            float subtle=Mth.sin(age*0.075F+side*0.31F);
            float asym=0.045F*performanceWeight*Mth.sin(performanceTime*0.13F+side*0.8F);
            float stroke=wingDrive+asym;
            float recovery=wingRecovery;
            float pressure=0.5F*(stroke+(float)Math.sqrt(stroke*stroke+0.0025F));
            p.membraneTension=Mth.clamp(open*0.55F+pressure*0.35F,0,1);
            p.membranePhase=age*0.19F+side*0.23F;
            p.membraneFlutter=0.12F+stroke*stroke*0.30F+recovery*recovery*0.14F;
            // Open out from a genuinely swept, folded rest pose; pitch and roll
            // differ on the power and recovery strokes.
            stack.mulPose(Axis.YP.rotation(side*Mth.lerp(open,0.72F,-0.08F)));
            stack.mulPose(Axis.ZP.rotation(side*(-0.18F+stroke*0.83F-open*0.20F+subtle*0.028F)));
            stack.mulPose(Axis.XP.rotation(stroke*0.19F-recovery*0.15F));
            float[] root={0,0,0};
            float[] elbow=wingPoint(side,root,5,-3,-2,side*(Mth.lerp(open,0.36F,-0.12F)+wingLag*0.12F),side*recovery*0.16F);
            float yaw2=side*(Mth.lerp(open,-1.15F,0.02F)+wingLag*0.32F);
            float roll2=side*(Mth.lerp(open,0.33F,0.04F)+wingLag*0.54F);
            float[] wrist=wingPoint(side,elbow,5.2F,-0.55F,0.15F,yaw2,roll2);
            float fan=Mth.lerp(open,-0.75F,0.08F)+wingWrist*0.34F;
            float distal=wingFinger*0.48F+recovery*0.31F;
            // Two phalanges per long digit: fixed length, independently delayed flex.
            float[][] tip=wingDigit(side,wrist,5.8F,-0.45F,0.85F,yaw2+side*fan,roll2+side*wingWrist*0.36F,distal*0.65F);
            float[][] finger=wingDigit(side,wrist,7.8F,3.55F,7.85F,yaw2+side*fan*0.72F,roll2+side*wingWrist*0.40F,distal);
            float[][] rear=wingDigit(side,wrist,2.8F,4.55F,13.85F,yaw2+side*fan*0.42F,roll2+side*wingWrist*0.28F,distal*0.78F);
            float[][] inner=wingDigit(side,wrist,-3.2F,5.55F,11.85F,yaw2+side*fan*0.22F,roll2+side*wingWrist*0.18F,distal*0.45F);
            float[] notch1=wingNotch(wrist,tip[1],finger[1],0.68F);
            float[] notch2=wingNotch(wrist,finger[1],rear[1],0.72F);
            float[] notch3=wingNotch(wrist,rear[1],inner[1],0.75F);
            float[] anchor={-side*0.8F,2.3F,10};
            p.link(root,elbow,0.58F,0.40F,FUR);
            p.link(elbow,wrist,0.42F,0.28F,FUR);
            p.digit(wrist,tip,0.28F);
            p.digit(wrist,finger,0.25F);
            p.digit(wrist,rear,0.23F);
            p.digit(wrist,inner,0.20F);
            // Each curved finger is a shared patch boundary; adjacent patches
            // meet at its intermediate joint rather than cutting across it.
            p.membrane(root,elbow,anchor);
            p.membrane(elbow,wrist,anchor);
            p.membrane(wrist,inner[0],anchor);
            p.membrane(inner[0],inner[1],anchor);
            p.membrane(wrist,tip[0],finger[0]);
            p.membrane(tip[0],tip[1],notch1);
            p.membrane(tip[0],notch1,finger[0]);
            p.membrane(finger[0],notch1,finger[1]);
            p.membrane(wrist,finger[0],rear[0]);
            p.membrane(finger[0],finger[1],notch2);
            p.membrane(finger[0],notch2,rear[0]);
            p.membrane(rear[0],notch2,rear[1]);
            p.membrane(wrist,rear[0],inner[0]);
            p.membrane(rear[0],rear[1],notch3);
            p.membrane(rear[0],notch3,inner[0]);
            p.membrane(inner[0],notch3,inner[1]);
            stack.popPose();
        }
        stack.popPose();
    }

    private static float[][] wingDigit(int side,float[] wrist,float x,float y,float z,float yaw,float roll,float bend){
        float[] joint=wingPoint(side,wrist,x*0.55F,y*0.55F,z*0.55F,yaw,roll);
        float[] end=wingPoint(side,joint,x*0.45F,y*0.45F,z*0.45F,yaw+side*bend*0.35F,roll+side*bend);
        return new float[][]{joint,end};
    }

    private static float[] wingNotch(float[] pivot,float[] a,float[] b,float pull){
        return new float[]{pivot[0]+((a[0]+b[0])*0.5F-pivot[0])*pull,
            pivot[1]+((a[1]+b[1])*0.5F-pivot[1])*pull,
            pivot[2]+((a[2]+b[2])*0.5F-pivot[2])*pull};
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
        private float membraneTension,membranePhase,membraneFlutter;
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

        void digit(float[] root,float[][] points,float radius){
            link(root,points[0],radius,radius*0.58F,FUR);
            link(points[0],points[1],radius*0.58F,0.018F,FUR);
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
            float bend=Math.min(0.85F,len*0.024F)*(1-0.70F*membraneTension);
            // Orient billow consistently upward in local wing coordinates.
            float sign=ny<0?-1:1;
            nx=nx/len*sign;ny=ny/len*sign;nz=nz/len*sign;
            float[][][] points=new float[steps+1][][];
            for(int i=0;i<=steps;i++){
                points[i]=new float[steps-i+1][];
                for(int j=0;j<=steps-i;j++){
                    float u=i/(float)steps,v=j/(float)steps,w=1-u-v;
                    float wave=27*u*v*w*(bend+membraneFlutter*0.24F*Mth.sin(10*u+8*v-membranePhase));
                    points[i][j]=new float[]{w*a[0]+u*b[0]+v*c[0]+nx*wave,
                        w*a[1]+u*b[1]+v*c[1]+ny*wave,
                        w*a[2]+u*b[2]+v*c[2]+nz*wave};
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
