package com.ksp.mythosclasses;

import java.util.Locale;
import java.util.Set;

final class ClassRules {
    static final int MAX_LEVEL=50,MAX_STAT=100;
    enum Stat { POWER, VITALITY, AGILITY, SPIRIT }
    enum Weapon { SWORD, AXE, SHIELD, MACE, BOW, CROSSBOW, TRIDENT, STAFF, DAGGER, HOLY_FOCUS, DARK_FOCUS }
    enum Role {
        WARRIOR("Warrior","Rage",Set.of(Weapon.SWORD,Weapon.AXE,Weapon.SHIELD),2,1,0,0,"Cleave","Battle Cry","Whirlwind"),
        HEAVY_KNIGHT("Heavy Knight","Resolve",Set.of(Weapon.MACE,Weapon.SHIELD),1,3,0,0,"Shield Bash","Iron Fortress","Shield Wall"),
        RANGER("Ranger","Focus",Set.of(Weapon.BOW,Weapon.CROSSBOW),1,0,2,0,"Piercing Shot","Quickstep","Volley"),
        MAGE("Mage","Mana",Set.of(Weapon.STAFF),0,0,0,3,"Arcane Bolt","Frost Nova","Arcane Storm"),
        ROGUE("Rogue","Energy",Set.of(Weapon.DAGGER),2,0,2,0,"Backstab","Vanish","Shadow Strike"),
        CLERIC("Cleric","Faith",Set.of(Weapon.HOLY_FOCUS),0,1,0,3,"Heal","Sanctuary","Greater Heal"),
        NECROMANCER("Necromancer","Soul Energy",Set.of(Weapon.DARK_FOCUS),0,0,0,3,"Soul Bolt","Wither Curse","Soul Drain");
        final String label,resource; final Set<Weapon> weapons;
        final int power,vitality,agility,spirit; final String[] moves;
        Role(String label,String resource,Set<Weapon> weapons,int p,int v,int a,int s,String... moves){
            this.label=label;this.resource=resource;this.weapons=weapons;power=p;vitality=v;agility=a;spirit=s;this.moves=moves;
        }
        String id(){return name().toLowerCase(Locale.ROOT);}
        static Role parse(String id){try{return valueOf(id.toUpperCase(Locale.ROOT));}catch(Exception ex){return null;}}
    }
    static long threshold(int level){long n=Math.max(0,Math.min(MAX_LEVEL,level)-1);return 100*n*(n+1);}
    static final long MAX_XP=threshold(MAX_LEVEL);
    static int level(long xp){int l=1;while(l<MAX_LEVEL&&threshold(l+1)<=xp)l++;return l;}
    static int points(long xp){return (level(xp)-1)*3;}
    static int unlock(int slot){return switch(slot){case 1->1;case 2->10;case 3->25;default->Integer.MAX_VALUE;};}
    static int cost(int slot){return switch(slot){case 1->20;case 2->35;case 3->50;default->Integer.MAX_VALUE;};}
    static int cooldown(int slot){return switch(slot){case 1->100;case 2->240;case 3->400;default->Integer.MAX_VALUE;};}
}
