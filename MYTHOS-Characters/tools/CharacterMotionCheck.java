package com.ksp.mythoscharacters;
public final class CharacterMotionCheck {
    static void require(boolean test,String message){if(!test)throw new AssertionError(message);}
    public static void main(String[] args){
        for(var state:CharacterMotion.State.values())for(int i=0;i<2000;i++){
            float age=i*.1F,phase=i*.15F;
            var p=CharacterMotion.clip(state,phase,.9F,age,(i%100)/100F);
            require(p.equals(CharacterMotion.clip(state,phase,.9F,age,(i%100)/100F)),"Nondeterministic clip");
            var a=CharacterMotion.aroundPelvis(0,0,0,p);var b=CharacterMotion.aroundPelvis(5,2,0,p);
            float x=a.x()-b.x(),y=a.y()-b.y(),z=a.z()-b.z();
            require(Math.abs(x*x+y*y+z*z-29)<.01,"Shoulder distance drift");
            require(p.rightKnee()>=0&&p.rightKnee()<=1.5F&&p.leftKnee()>=0&&p.leftKnee()<=1.5F,"Knee limits");
            var q=CharacterMotion.bend(1,3,0,.9F,5.5F);
            require(Math.abs(q.y()-3)<.001,"Thigh drift");
            var n=CharacterMotion.bendNormal(0,0,-1,1.3F,.6F);
            require(Math.abs(n.x()*n.x()+n.y()*n.y()+n.z()*n.z()-1)<.001,"Normal length");
        }
        require(CharacterMotion.select(true,false,true,1,0,false)==CharacterMotion.State.RUN,"Sprint selection");
        require(CharacterMotion.select(false,false,false,0,.2F,false)==CharacterMotion.State.RISE,"Jump selection");
        require(CharacterMotion.select(true,true,false,0,0,false)==CharacterMotion.State.CROUCH,"Crouch selection");
        var controller=new CharacterMotion.Controller();
        controller.update(0,0,0,true,false,false,0,false);
        var previous=controller.update(1,0,0,false,false,false,.2F,false);
        for(int i=2;i<40;i++){
            var next=controller.update(i,i*.3F,.8F,i>=12,false,true,i<5?.2F:-.2F,false);
            require(Math.abs(next.rightHip()-previous.rightHip())<1.5F,"Abrupt transition");
            previous=next;
        }
        require(controller.update(40,0,0,true,false,false,0,true).equals(CharacterMotion.Pose.zero()),"Reserved action not neutral");
        System.out.println("18000 clip samples, joint bounds, normal lengths and jump/landing transitions passed");
    }
}
