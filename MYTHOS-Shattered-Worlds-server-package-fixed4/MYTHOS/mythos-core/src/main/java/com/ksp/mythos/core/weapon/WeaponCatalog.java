package com.ksp.mythos.core.weapon;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Server-owned registry of MYTHOS weapon IDs and their categories. */
public final class WeaponCatalog {
    private final Map<String, WeaponDefinition> weapons = new ConcurrentHashMap<>();

    public void register(final WeaponDefinition definition) {
        final WeaponDefinition previous = this.weapons.putIfAbsent(definition.id(), definition);
        if (previous != null) throw new IllegalStateException("Duplicate weapon ID: " + definition.id());
    }

    public Optional<WeaponDefinition> find(final String itemId) {
        return Optional.ofNullable(this.weapons.get(itemId));
    }

    public Optional<WeaponCategory> categoryOf(final String itemId) {
        return this.find(itemId).map(WeaponDefinition::category);
    }
}
