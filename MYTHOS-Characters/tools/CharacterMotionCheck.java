package com.ksp.mythoscharacters;
public final class CharacterMotionCheck {
    public static void main(String[] args){
        for(int i=0;i<10000;i++){
            float swing=i*.1F,age=i*.25F;
            var pose=CharacterMotion.sample(swing,1,age,false);
            if(!pose.equals(CharacterMotion.sample(swing,1,age,false)))throw new AssertionError("Non-deterministic pose");
            var a=CharacterMotion.aroundPelvis(0,0,0,pose);
            var b=CharacterMotion.aroundPelvis(5,2,0,pose);
            float dx=a.x()-b.x(),dy=a.y()-b.y(),dz=a.z()-b.z();
            if(Math.abs(dx*dx+dy*dy+dz*dz-29)>.01F)throw new AssertionError("Shoulder distance changed");
            var hip=CharacterMotion.aroundPelvis(0,12,0,pose);
            if(Math.abs(hip.y()-12)>.0001F)throw new AssertionError("Pelvis moved");
            if(Math.abs(pose.chestRoll())>.05F||pose.rightKnee()>.31F||pose.leftKnee()>.31F)throw new AssertionError("Motion exceeded safe bounds");
            var thigh=CharacterMotion.bend(1,3,0,.3F,5.5F);
            if(Math.abs(thigh.y()-3)>.0001F)throw new AssertionError("Thigh drift");
        }
        System.out.println("10000 stateless motion samples and joint-distance checks passed");
    }
}
