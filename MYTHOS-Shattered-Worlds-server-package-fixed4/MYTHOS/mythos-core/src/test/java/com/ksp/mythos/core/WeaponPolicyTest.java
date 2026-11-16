package com.ksp.mythos.core;

import com.ksp.mythos.core.character.CharacterClass;
import com.ksp.mythos.core.combat.WeaponPolicy;
import com.ksp.mythos.core.weapon.WeaponCatalog;
import com.ksp.mythos.core.weapon.WeaponCategory;
import com.ksp.mythos.core.weapon.WeaponDefinition;

/** Dependency-free executable regression tests. Run with assertions enabled. */
public final class WeaponPolicyTest {
    public static void main(String[] args) {
        final WeaponCatalog catalog = new WeaponCatalog();
        catalog.register(new WeaponDefinition("mythos:dawnbringer", WeaponCategory.GREATSWORD, 45, 20, 85));
        catalog.register(new WeaponDefinition("mythos:longbow_of_the_frontier", WeaponCategory.BOW, 30, 10, 42));
        catalog.register(new WeaponDefinition("mythos:ember_staff", WeaponCategory.STAFF, 20, 5, 25));
        final WeaponPolicy policy = new WeaponPolicy(catalog);

        assert policy.evaluate(CharacterClass.WARRIOR, "mythos:dawnbringer", WeaponPolicy.Action.EQUIP).allowed();
        assert policy.evaluate(CharacterClass.WARRIOR, "mythos:dawnbringer", WeaponPolicy.Action.ATTACK).allowed();
        assert !policy.evaluate(CharacterClass.MAGE, "mythos:dawnbringer", WeaponPolicy.Action.EQUIP).allowed();
        assert !policy.evaluate(CharacterClass.MAGE, "mythos:dawnbringer", WeaponPolicy.Action.ATTACK).allowed();
        assert policy.evaluate(CharacterClass.RANGER, "mythos:longbow_of_the_frontier", WeaponPolicy.Action.EQUIP).allowed();
        assert !policy.evaluate(CharacterClass.ASSASSIN, "mythos:longbow_of_the_frontier", WeaponPolicy.Action.ATTACK).allowed();
        assert policy.evaluate(CharacterClass.MAGE, "mythos:ember_staff", WeaponPolicy.Action.EQUIP).allowed();
        assert !policy.evaluate(null, "mythos:ember_staff", WeaponPolicy.Action.EQUIP).allowed();
        assert policy.evaluate(CharacterClass.MAGE, "minecraft:bread", WeaponPolicy.Action.ATTACK).allowed();

        boolean duplicateRejected = false;
        try {
            catalog.register(new WeaponDefinition("mythos:dawnbringer", WeaponCategory.SWORD, 1, 0, 1));
        } catch (IllegalStateException expected) {
            duplicateRejected = true;
        }
        assert duplicateRejected;
        assert com.ksp.mythos.core.weapon.ClassWeaponRules.allows(CharacterClass.WARRIOR, WeaponCategory.POLEARM);
        assert com.ksp.mythos.core.weapon.ClassWeaponRules.allows(CharacterClass.BERSERKER, WeaponCategory.HAMMER);
        assert com.ksp.mythos.core.weapon.ClassWeaponRules.allows(CharacterClass.RANGER, WeaponCategory.CROSSBOW);
        assert com.ksp.mythos.core.weapon.ClassWeaponRules.allows(CharacterClass.ASSASSIN, WeaponCategory.DAGGER);
        assert com.ksp.mythos.core.weapon.ClassWeaponRules.allows(CharacterClass.MAGE, WeaponCategory.WAND);
        assert com.ksp.mythos.core.weapon.ClassWeaponRules.allows(CharacterClass.CLERIC, WeaponCategory.RELIC);
        assert com.ksp.mythos.core.weapon.ClassWeaponRules.allows(CharacterClass.PALADIN, WeaponCategory.HOLY_WEAPON);
        assert com.ksp.mythos.core.weapon.ClassWeaponRules.allows(CharacterClass.SUMMONER, WeaponCategory.SUMMONING_STAFF);
        System.out.println("MYTHOS weapon policy tests passed (17 assertions + duplicate-ID guard).");
    }
}
