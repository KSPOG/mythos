package com.ksp.mythos.core.character;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/** Playable MYTHOS combat classes. IDs are stable persistence identifiers. */
public enum CharacterClass {
    WARRIOR("warrior", "Warrior"),
    BERSERKER("berserker", "Berserker"),
    RANGER("ranger", "Ranger"),
    ASSASSIN("assassin", "Assassin"),
    MAGE("mage", "Mage"),
    CLERIC("cleric", "Cleric"),
    PALADIN("paladin", "Paladin"),
    SUMMONER("summoner", "Summoner");

    private final String id;
    private final String displayName;

    CharacterClass(final String id, final String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public String id() { return this.id; }
    public String displayName() { return this.displayName; }

    public static Optional<CharacterClass> fromId(final String id) {
        if (id == null) return Optional.empty();
        return Arrays.stream(values())
                .filter(value -> value.id.equals(id.trim().toLowerCase(Locale.ROOT)))
                .findFirst();
    }
}
