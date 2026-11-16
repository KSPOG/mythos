package com.ksp.mythos.core.weapon;

import com.ksp.mythos.core.character.CharacterClass;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** Single authoritative class-to-weapon policy. Changes here affect equip and attack checks. */
public final class ClassWeaponRules {
    private static final Map<CharacterClass, Set<WeaponCategory>> ALLOWED = new EnumMap<>(CharacterClass.class);

    static {
        ALLOWED.put(CharacterClass.WARRIOR, EnumSet.of(
                WeaponCategory.SWORD, WeaponCategory.GREATSWORD, WeaponCategory.AXE, WeaponCategory.POLEARM, WeaponCategory.SHIELD));
        ALLOWED.put(CharacterClass.BERSERKER, EnumSet.of(
                WeaponCategory.GREATAXE, WeaponCategory.GREATSWORD, WeaponCategory.HAMMER));
        ALLOWED.put(CharacterClass.RANGER, EnumSet.of(
                WeaponCategory.BOW, WeaponCategory.SHORTBOW, WeaponCategory.CROSSBOW));
        ALLOWED.put(CharacterClass.ASSASSIN, EnumSet.of(
                WeaponCategory.DAGGER, WeaponCategory.DUAL_BLADES));
        ALLOWED.put(CharacterClass.MAGE, EnumSet.of(
                WeaponCategory.STAFF, WeaponCategory.WAND, WeaponCategory.SPELLBOOK, WeaponCategory.CATALYST));
        ALLOWED.put(CharacterClass.CLERIC, EnumSet.of(
                WeaponCategory.HOLY_STAFF, WeaponCategory.MACE, WeaponCategory.RELIC, WeaponCategory.SHIELD));
        ALLOWED.put(CharacterClass.PALADIN, EnumSet.of(
                WeaponCategory.SWORD, WeaponCategory.SHIELD, WeaponCategory.HOLY_WEAPON, WeaponCategory.MACE));
        ALLOWED.put(CharacterClass.SUMMONER, EnumSet.of(
                WeaponCategory.SUMMONING_STAFF, WeaponCategory.CATALYST, WeaponCategory.RELIC));
    }

    private ClassWeaponRules() { }

    public static boolean allows(final CharacterClass characterClass, final WeaponCategory category) {
        return characterClass != null && category != null
                && ALLOWED.getOrDefault(characterClass, Set.of()).contains(category);
    }

    public static Set<WeaponCategory> allowedFor(final CharacterClass characterClass) {
        return Set.copyOf(ALLOWED.getOrDefault(characterClass, Set.of()));
    }
}
