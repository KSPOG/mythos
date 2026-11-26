package com.ksp.mythoscharacters;

/** Procedural locomotion rig with distinct clips and a per-entity transition controller. */
final class CharacterMotion {
    enum State { IDLE, WALK, RUN, RISE, FALL, LAND, CROUCH, CROUCH_WALK, RESERVED }
    record Point(float x,float y,float z){}
    record Pose(float chestYaw,float chestRoll,float chestPitch,float headRoll,float drop,
                float rightHip,float leftHip,float rightKnee,float leftKnee,
                float rightShoulder,float leftShoulder,float rightElbow,float leftElbow,float armSpread){
        static Pose zero(){return new Pose(0,0,0,0,0,0,0,0,0,0,0,0,0,0);}
        Pose blend(Pose b,float t){return new Pose(
            mix(chestYaw,b.chestYaw,t),mix(chestRoll,b.chestRoll,t),mix(chestPitch,b.chestPitch,t),mix(headRoll,b.headRoll,t),mix(drop,b.drop,t),
            mix(rightHip,b.rightHip,t),mix(leftHip,b.leftHip,t),mix(rightKnee,b.rightKnee,t),mix(leftKnee,b.leftKnee,t),
            mix(rightShoulder,b.rightShoulder,t),mix(leftShoulder,b.leftShoulder,t),mix(rightElbow,b.rightElbow,t),mix(leftElbow,b.leftElbow,t),mix(armSpread,b.armSpread,t));}
    }
    static float mix(float a,float b,float t){return a+(b-a)*t;}
    static float clamp(float t){return Math.max(0,Math.min(1,t));}
    static float smooth(float t){t=clamp(t);return t*t*(3-2*t);}
    static State select(boolean ground,boolean crouch,boolean sprint,float speed,float vy,boolean reserved){
        if(reserved)return State.RESERVED;
        if(!ground)return vy>.015F?State.RISE:State.FALL;
        if(crouch)return speed>.07F?State.CROUCH_WALK:State.CROUCH;
        if(speed<=.07F)return State.IDLE;
        return sprint?State.RUN:State.WALK;
    }
    static Pose clip(State state,float phase,float speed,float age,float landing){
        speed=clamp(speed);
        float a=(float)Math.cos(phase),b=(float)Math.sin(phase),breath=(float)Math.sin(age*.075F);
        float liftR=Math.max(0,b),liftL=Math.max(0,-b);
        return switch(state){
            case RESERVED -> Pose.zero();
            case IDLE -> new Pose(.012F*breath,.008F*breath,.012F*breath,0,0,0,0,0,0,.035F,-.035F,-.10F,-.10F,.045F);
            case WALK -> new Pose(.095F*a*speed,.045F*b*speed,.07F, -.022F*b,.18F*Math.abs(b),
                .58F*a*speed,-.58F*a*speed,.70F*liftR*speed,.70F*liftL*speed,-.50F*a*speed,.50F*a*speed,-.20F,-.20F,.055F);
            case RUN -> new Pose(.14F*a,.055F*b,.24F,-.025F*b,.38F*Math.abs(b),
                .92F*a,-.92F*a,.99F*liftR,.99F*liftL,-.77F*a,.77F*a,-.85F,-.85F,.09F);
            case RISE -> new Pose(.02F,0,.16F,0,.25F,-.48F,-.28F,.72F,.58F,-.60F,-.45F,-.55F,-.65F,.12F);
            case FALL -> new Pose(0,0,.08F,0,.18F,-.25F,-.35F,.46F,.58F,-.22F,-.35F,-.44F,-.44F,.22F);
            case LAND -> {
                float w=(float)Math.sin(Math.PI*clamp(landing));
                yield new Pose(0,0,.14F+.38F*w,0,1.65F*w,-.72F*w,-.72F*w,1.38F*w,1.38F*w,-.35F*w,-.35F*w,-.45F*w,-.45F*w,.12F);
            }
            case CROUCH,CROUCH_WALK -> {
                float stride=state==State.CROUCH_WALK?.30F*a*speed:0;
                yield new Pose(.045F*stride,.035F*b*speed,.40F,0,2.1F,-.68F+stride,-.68F-stride,
                    1.15F+.24F*liftR*speed,1.15F+.24F*liftL*speed,-.30F-stride,-.30F+stride,-.52F,-.52F,.08F);
            }
        };
    }
    static final class Controller {
        private State state=State.RESERVED;
        private Pose from=Pose.zero(),last=Pose.zero();
        private float changeAge=Float.NaN,lastAge=Float.NaN,landAge=-1000;
        private boolean wasGround=true;
        private boolean initialized;
        Pose update(float age,float swing,float amount,boolean ground,boolean crouch,boolean sprint,float vy,boolean reserved){
            if(!initialized||age<lastAge||age-lastAge>20){initialized=true;state=select(ground,crouch,sprint,amount,vy,reserved);from=last=clip(state,swing*.6662F,amount,age,0);changeAge=age-10;wasGround=ground;landAge=-1000;}
            if(ground&&!wasGround&&!reserved)landAge=age;
            wasGround=ground;lastAge=age;
            State target=select(ground,crouch,sprint,amount,vy,reserved);
            if(ground&&!reserved&&age-landAge<5F)target=State.LAND;
            if(reserved){state=target;from=last=Pose.zero();changeAge=age;return last;}
            if(target!=state){from=last;state=target;changeAge=age;}
            Pose to=clip(state,swing*.6662F,amount,age,(age-landAge)/5F);
            last=from.blend(to,smooth((age-changeAge)/(state==State.LAND?1.5F:3.5F)));
            return last;
        }
    }
    static Point aroundPelvis(float x,float y,float z,Pose pose){
        float py=y-12F;
        float c=(float)Math.cos(pose.chestPitch),s=(float)Math.sin(pose.chestPitch);
        float yy=c*py-s*z,zz=s*py+c*z;
        c=(float)Math.cos(pose.chestYaw);s=(float)Math.sin(pose.chestYaw);
        float xx=c*x+s*zz;zz=-s*x+c*zz;
        c=(float)Math.cos(pose.chestRoll);s=(float)Math.sin(pose.chestRoll);
        return new Point(c*xx-s*yy,12F+s*xx+c*yy+pose.drop,zz);
    }
    static float jointWeight(float y,float joint){return smooth((y-joint+.55F)/1.1F);}
    /** Two-bone linear blend skinning around elbow or knee; shared vertices remain continuous. */
    static Point bend(float x,float y,float z,float angle,float joint){
        float w=jointWeight(y,joint),c=(float)Math.cos(angle),s=(float)Math.sin(angle),dy=y-joint;
        return new Point(x,mix(y,joint+c*dy-s*z,w),mix(z,s*dy+c*z,w));
    }
    static Point bendNormal(float x,float y,float z,float angle,float weight){
        float c=(float)Math.cos(angle),s=(float)Math.sin(angle);
        float yy=mix(y,c*y-s*z,weight),zz=mix(z,s*y+c*z,weight);
        float len=(float)Math.sqrt(x*x+yy*yy+zz*zz);
        return len<1e-6F?new Point(x,y,z):new Point(x/len,yy/len,zz/len);
    }
}
