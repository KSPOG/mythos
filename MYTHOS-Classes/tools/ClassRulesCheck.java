package com.ksp.mythosclasses;
public final class ClassRulesCheck {
    public static void main(String[] args){
        if(ClassRules.Role.values().length!=7)throw new AssertionError("seven classes");
        for(var role:ClassRules.Role.values()){
            if(ClassRules.Role.parse(role.id())!=role)throw new AssertionError("role roundtrip");
        }
        if(ClassRules.Role.parse("heavy_knight")!=ClassRules.Role.HEAVY_KNIGHT)throw new AssertionError("heavy knight");
        if(ClassRules.Role.parse(null)!=null||ClassRules.Role.parse("invalid")!=null)throw new AssertionError("invalid class");
        System.out.println("Class identity checks passed");
    }
}
