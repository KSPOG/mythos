package com.ksp.mythoscharacters;

/** Stateless secondary animation. Every frame starts from vanilla's freshly evaluated pose. */
final class CharacterMotion {
    record Pose(float chestYaw,float chestRoll,float chestPitch,float headRoll,float armPitch,float armRoll,float rightKnee,float leftKnee){}
    record Point(float x,float y,float z){}
    static Pose sample(float swing,float amount,float age,boolean constrained){
        if(constrained)return new Pose(0,0,0,0,0,0,0,0);
        float speed=Math.max(0,Math.min(1,amount));
        float phase=swing*.6662F;
        float wave=(float)Math.sin(phase),stride=(float)Math.cos(phase),breath=(float)Math.sin(age*.075F);
        return new Pose(.055F*wave*speed,.028F*wave*speed,.025F*speed+.009F*breath,
            -.012F*wave*speed,.018F*breath*(1-speed),.025F*(1-speed),
            .30F*speed*Math.max(0,stride),.30F*speed*Math.max(0,-stride));
    }
    /** Rotate a joint around the pelvis so the shoulders cannot drift away from the trunk. */
    static Point aroundPelvis(float x,float y,float z,Pose pose){
        float py=y-12F;
        float c=(float)Math.cos(pose.chestPitch),s=(float)Math.sin(pose.chestPitch);
        float yy=c*py-s*z,zz=s*py+c*z;
        c=(float)Math.cos(pose.chestYaw);s=(float)Math.sin(pose.chestYaw);
        float xx=c*x+s*zz;zz=-s*x+c*zz;
        c=(float)Math.cos(pose.chestRoll);s=(float)Math.sin(pose.chestRoll);
        return new Point(c*xx-s*yy,12F+s*xx+c*yy,zz);
    }
    /** A continuous knee bend for unarmored legs; leaves the thigh and hip unchanged. */
    static Point bend(float x,float y,float z,float angle,float knee){
        float t=Math.max(0,Math.min(1,(y-knee)/2F));
        float a=angle*t*t*(3-2*t),c=(float)Math.cos(a),s=(float)Math.sin(a);
        return new Point(x,knee+c*(y-knee)-s*z,s*(y-knee)+c*z);
    }
}
