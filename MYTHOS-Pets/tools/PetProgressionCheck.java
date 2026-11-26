package com.ksp.mythospets;
public final class PetProgressionCheck {
    private static void check(boolean condition){if(!condition)throw new AssertionError("Progression invariant failed");}
    public static void main(String[] args){
        check(PetProgression.threshold(1)==0);
        check(PetProgression.threshold(2)==100);
        check(PetProgression.MAX_XP==122500);
        for(var role:PetProgression.Role.values())for(int l=2;l<=50;l++){
            long xp=PetProgression.threshold(l);
            check(PetProgression.level(xp)==l);
            check(PetProgression.level(xp-1)==l-1);
            var a=PetProgression.stats(xp-1,role);var b=PetProgression.stats(xp,role);
            check(b.attack()>a.attack()&&b.healing()>a.healing()&&b.agility()>a.agility()&&b.haste()>a.haste()&&b.duration()>a.duration());
            check(PetProgression.cooldown(120,b)<=PetProgression.cooldown(120,a));
        }
        check(PetProgression.add(122490,25)==122500);
        check(PetProgression.add(100,-1)==100);
        check(PetProgression.level(0)==1);
        check(PetProgression.stats(0,PetProgression.Role.DPS).attack()==1F);
        check(PetProgression.cooldown(120,PetProgression.stats(0,PetProgression.Role.DPS))==120);
        check(PetProgression.duration(100,PetProgression.stats(0,PetProgression.Role.DPS))==100);
        check(PetProgression.stats(122500,PetProgression.Role.DPS).attack()>PetProgression.stats(122500,PetProgression.Role.HEALER).attack());
        check(PetProgression.stats(122500,PetProgression.Role.HEALER).healing()>PetProgression.stats(122500,PetProgression.Role.DPS).healing());
        System.out.println("All progression invariants passed");
    }
}
