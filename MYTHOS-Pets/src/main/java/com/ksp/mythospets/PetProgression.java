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
    record Stats(int level,float attack,float healing,float agility,float haste,float duration){}
    static Stats stats(long xp){
        int l=level(xp),n=l-1;
        return new Stats(l,1+n*0.025F,1+n*0.025F,1+n*0.0075F,1+n*0.006F,1+n*0.012F);
    }
    static int cooldown(int base,Stats s){return Math.max(20,Math.round(base/s.haste()));}
    static int duration(int base,Stats s){return Math.round(base*s.duration());}
    static long add(long xp,int amount){return Math.min(MAX_XP,xp+Math.max(0,amount));}
}
