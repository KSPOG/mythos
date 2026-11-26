package com.ksp.mythosclasses;
public final class ClassRulesCheck {
    public static void main(String[] args){
        for(var role:ClassRules.Role.values()){
            if(ClassRules.Role.parse(role.id())!=role)throw new AssertionError("role roundtrip");
            for(var weapon:ClassRules.Weapon.values())
                if(ClassRules.allows(role,weapon)!=role.weapons.contains(weapon))throw new AssertionError("authorization");
            if(!ClassRules.allows(role,null))throw new AssertionError("ordinary items");
        }
        for(var weapon:ClassRules.Weapon.values())if(ClassRules.allows(null,weapon))throw new AssertionError("unassigned");
        if(ClassRules.allows(ClassRules.Role.CLERIC,ClassRules.Weapon.BOW))throw new AssertionError("cleric bow");
        if(!ClassRules.allows(ClassRules.Role.RANGER,ClassRules.Weapon.BOW))throw new AssertionError("ranger bow");
        System.out.println("Class authorization checks passed");
    }
}
