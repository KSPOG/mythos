package com.ksp.mythos.core.combat;

import com.ksp.mythos.core.character.CharacterClass;
import com.ksp.mythos.core.weapon.ClassWeaponRules;
import com.ksp.mythos.core.weapon.WeaponCatalog;
import com.ksp.mythos.core.weapon.WeaponCategory;
import java.util.Objects;

/** Fail-closed policy for all MYTHOS-registered weapons. */
public final class WeaponPolicy {
    public enum Action { EQUIP, ATTACK, USE }
    public record Decision(boolean allowed, String reason) { }

    private final WeaponCatalog catalog;

    public WeaponPolicy(final WeaponCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
    }

    public Decision evaluate(final CharacterClass characterClass, final String itemId, final Action action) {
        Objects.requireNonNull(action, "action");
        if (characterClass == null) return new Decision(false, "Choose a class before using MYTHOS weapons.");
        final WeaponCategory category = this.catalog.categoryOf(itemId).orElse(null);
        if (category == null) return new Decision(true, "Item is not a MYTHOS-registered weapon.");
        if (!ClassWeaponRules.allows(characterClass, category)) {
            return new Decision(false, "Your " + characterClass.displayName()
                    + " class cannot " + action.name().toLowerCase() + " " + category.name().toLowerCase() + ".");
        }
        return new Decision(true, "Allowed.");
    }
}
