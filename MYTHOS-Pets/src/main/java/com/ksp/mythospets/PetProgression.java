package com.ksp.mythospets;

/** Pure progression rules; persisted XP is the single source of truth. */
final class PetProgression {
    static final int MAX_LEVEL=50;
    static long threshold(int level){
        long n=Math.max(0,Math.min(MAX_LEVEL,level)-1);
        return 50*n*(n+1);
    }
    static final long MAX_XP=threshold(MAX_LEVEL);
    static int level(long xp){
        int lo=1,hi=MAX_LEVEL;
        while(lo<hi){int mid=(lo+hi+1)/2;if(threshold(mid)<=xp)lo=mid;else hi=mid-1;}
        return lo;
    }
    enum Role {
        DPS(.035F,.010F,.009F,.008F,.009F),
        HEALER(.012F,.040F,.006F,.009F,.016F),
        GUARDIAN(.015F,.020F,.005F,.006F,.025F),
        SUPPORT(.018F,.022F,.010F,.012F,.020F),
        CONTROL(.023F,.012F,.008F,.009F,.025F);
        final float attack,healing,agility,haste,duration;
        Role(float a,float h,float g,float c,float d){attack=a;healing=h;agility=g;haste=c;duration=d;}
    }
    record Stats(int level,float attack,float healing,float agility,float haste,float duration){}
    static Stats stats(long xp,Role role){
        int l=level(xp),n=l-1;
        return new Stats(l,1+n*role.attack,1+n*role.healing,1+n*role.agility,1+n*role.haste,1+n*role.duration);
    }
    static int cooldown(int base,Stats s){return Math.max(20,Math.round(base/s.haste()));}
    static int duration(int base,Stats s){return Math.round(base*s.duration());}
    static long add(long xp,int amount){return Math.min(MAX_XP,xp+Math.max(0,amount));}
}
