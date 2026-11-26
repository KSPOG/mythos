package com.ksp.mythosclasses;

import java.util.Locale;

final class ClassRules {
    enum Role {
        WARRIOR("Warrior"), HEAVY_KNIGHT("Heavy Knight"), RANGER("Ranger"),
        MAGE("Mage"), ROGUE("Rogue"), CLERIC("Cleric"), NECROMANCER("Necromancer");
        final String label;
        Role(String label){this.label=label;}
        String id(){return name().toLowerCase(Locale.ROOT);}
        static Role parse(String id){
            if(id==null)return null;
            try{return valueOf(id.toUpperCase(Locale.ROOT));}catch(IllegalArgumentException ex){return null;}
        }
    }
}
