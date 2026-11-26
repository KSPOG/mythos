package com.ksp.mythosclasses;
public final class ClassRulesCheck {
    public static void main(String[] args){
        if(ClassRules.Role.values().length!=9)throw new AssertionError("nine classes");
        if(ClassRules.Role.parse("summoner")!=ClassRules.Role.SUMMONER)throw new AssertionError("summoner");
        for(var role:ClassRules.Role.values()){
            if(ClassRules.Role.parse(role.id())!=role)throw new AssertionError("role roundtrip");
        }
        if(ClassRules.Role.parse("heavy_knight")!=ClassRules.Role.HEAVY_KNIGHT)throw new AssertionError("heavy knight");
        if(ClassRules.Role.parse("hexslinger")!=ClassRules.Role.HEXSLINGER)throw new AssertionError("hexslinger");
        if(ClassRules.Role.parse(null)!=null||ClassRules.Role.parse("invalid")!=null)throw new AssertionError("invalid class");
        for(var role:ClassRules.Role.values()){
            int total=0;
            for(var stat:ClassRules.Stat.values()){
                int value=role.base(stat);if(value<=0||value>ClassRules.MAX_STAT)throw new AssertionError("base stat range");total+=value;
                if(ClassRules.Stat.parse(stat.id())!=stat)throw new AssertionError("stat roundtrip");
            }
            if(total!=60)throw new AssertionError("unequal base stat budget: "+role);
        }
        if(ClassRules.START_POINTS!=5)throw new AssertionError("starting point budget");
        if(ClassRules.MAX_LEVEL!=50||ClassRules.MAX_XP!=245000||ClassRules.threshold(2)!=200)throw new AssertionError("level cap or curve");
        for(int l=2;l<=50;l++){
            int xp=ClassRules.threshold(l);
            if(ClassRules.level(xp)!=l||ClassRules.level(xp-1)!=l-1)throw new AssertionError("XP boundary");
            if(ClassRules.levelPoints(xp)!=(l-1)*3)throw new AssertionError("level points");
        }
        if(ClassRules.level(0)!=1||ClassRules.levelPoints(0)!=0||ClassRules.levelPoints(245000)!=147)throw new AssertionError("progression");
        System.out.println("Class identity, base stats and progression checks passed");
    }
}
