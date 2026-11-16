package com.ksp.mythos.core.weapon;

import java.util.Objects;

/** Immutable definition for a server-registered weapon. */
public record WeaponDefinition(
        String id,
        WeaponCategory category,
        int requiredLevel,
        int requiredMastery,
        double baseDamage
) {
    public WeaponDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(category, "category");
        if (!id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
            throw new IllegalArgumentException("Weapon ID must be a namespaced resource location: " + id);
        }
        if (requiredLevel < 1 || requiredMastery < 0 || baseDamage < 0 || !Double.isFinite(baseDamage)) {
            throw new IllegalArgumentException("Invalid weapon progression or damage values");
        }
    }
}
