package com.ksp.mythosclasses;

import java.util.Locale;

final class ClassRules {
    static final int START_POINTS=5, MAX_POINTS=10000, MAX_STAT=100;
    enum Stat {
        STRENGTH, DEFENSE, VITALITY, DEXTERITY, INTELLIGENCE, SPIRIT;
        String id(){return name().toLowerCase(Locale.ROOT);}
        static Stat parse(String id){if(id==null)return null;try{return valueOf(id.toUpperCase(Locale.ROOT));}catch(IllegalArgumentException ex){return null;}}
    }
    enum Role {
        WARRIOR("Warrior",15,12,12,10,5,6),
        HEAVY_KNIGHT("Heavy Knight",10,18,16,5,5,6),
        RANGER("Ranger",10,7,10,18,7,8),
        MAGE("Mage",5,6,9,8,20,12),
        ROGUE("Rogue",12,6,9,20,6,7),
        CLERIC("Cleric",5,9,12,6,10,18),
        NECROMANCER("Necromancer",5,7,10,6,18,14);
        final String label; private final int[] base;
        Role(String label,int... base){this.label=label;this.base=base;}
        int base(Stat stat){return base[stat.ordinal()];}
        String id(){return name().toLowerCase(Locale.ROOT);}
        static Role parse(String id){if(id==null)return null;try{return valueOf(id.toUpperCase(Locale.ROOT));}catch(IllegalArgumentException ex){return null;}}
    }
}
