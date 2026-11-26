package com.ksp.mythosclasses;
public final class ClassRulesCheck {
    public static void main(String[] args){
        if(ClassRules.Role.values().length!=7)throw new AssertionError("seven classes");
        for(var role:ClassRules.Role.values()){
            if(ClassRules.Role.parse(role.id())!=role)throw new AssertionError("role roundtrip");
        }
        if(ClassRules.Role.parse("heavy_knight")!=ClassRules.Role.HEAVY_KNIGHT)throw new AssertionError("heavy knight");
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
        System.out.println("Class identity and base stat checks passed");
    }
}
