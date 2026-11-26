package com.ksp.mythosclasses;

import java.util.Locale;
import java.util.Set;

final class ClassRules {
    enum Weapon { SWORD, AXE, SHIELD, MACE, BOW, CROSSBOW, TRIDENT, STAFF, DAGGER, HOLY_FOCUS, DARK_FOCUS }
    enum Role {
        WARRIOR("Warrior",Set.of(Weapon.SWORD,Weapon.AXE)),
        HEAVY_KNIGHT("Heavy Knight",Set.of(Weapon.MACE,Weapon.SHIELD)),
        RANGER("Ranger",Set.of(Weapon.BOW,Weapon.CROSSBOW)),
        MAGE("Mage",Set.of(Weapon.STAFF)),
        ROGUE("Rogue",Set.of(Weapon.DAGGER)),
        CLERIC("Cleric",Set.of(Weapon.HOLY_FOCUS)),
        NECROMANCER("Necromancer",Set.of(Weapon.DARK_FOCUS));
        final String label; final Set<Weapon> weapons;
        Role(String label,Set<Weapon> weapons){this.label=label;this.weapons=weapons;}
        String id(){return name().toLowerCase(Locale.ROOT);}
        static Role parse(String id){try{return valueOf(id.toUpperCase(Locale.ROOT));}catch(Exception ex){return null;}}
    }
    static boolean allows(Role role,Weapon weapon){return weapon==null||role!=null&&role.weapons.contains(weapon);}
}
