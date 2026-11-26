package com.ksp.mythoscharacters;
/** Deterministic secondary motion: avoids altering vanilla gameplay or arm use states. */
final class CharacterMotion {
    record Pose(float chestYaw,float chestRoll,float headRoll,float armRoll,float legRoll){}
    static Pose sample(float swing,float amount,float age,boolean constrained){
        if(constrained)return new Pose(0,0,0,0,0);
        float speed=Math.max(0,Math.min(1,amount));
        float wave=(float)Math.sin(swing*.6662F),breath=(float)Math.sin(age*.075F);
        return new Pose(.075F*wave*speed,.035F*wave*speed+.008F*breath,-.018F*wave*speed,.035F*breath*(1-speed),.018F*wave*speed);
    }
}
