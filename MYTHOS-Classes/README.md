# MYTHOS Classes 0.1.0

Server-only Forge 1.21.1 / 52.1.5 / Java 21 mod. No custom registries, client code or network channels. Vanilla client compatibility is intended, not yet runtime verified. Install JAR on server only.

Class identity and weapon authorization only. No XP, stats, abilities, armor restrictions or talent trees.

## Commands

- /class list
- /class choose <id> (one-time selection)
- /class info
- /class set <player> <id> (permission 2)
- /class clear <player> (permission 2)
- /class giveweapon <player> <id> (permission 2, sample vanilla-backed weapon)

IDs: warrior, heavy_knight, ranger, mage, rogue, cleric, necromancer.

## Weapon rules

Warrior: swords and axes. Heavy Knight: mace and shield. Ranger: bows and crossbows. Mage: STAFF. Rogue: DAGGER. Cleric: HOLY_FOCUS. Necromancer: DARK_FOCUS. Trident denied for all until assigned. Ordinary non-weapon items unrestricted. Custom weapons use server-issued minecraft:custom_data field mythos_class_weapon with exact uppercase family name and supported vanilla base item (STAFF: blaze rod or stick; DAGGER: iron sword; HOLY_FOCUS: amethyst shard or mace; DARK_FOCUS: echo shard or blaze rod). Ordinary renamed items do not gain authorization. Custom weapon systems must integrate this marker or their own authorization; arbitrary modded weapons not automatically classified. No custom spells provided.

Restricted held weapons moved to empty non-selected inventory slot; if full, remain held but interaction/combat blocked. No confiscation. Attack, right-click item/block, projectile spawn and direct damage gates. Existing projectiles and third-party custom attack paths require integration testing. Class data stored atomically in world/mythos-classes.json. Unassigned players cannot use recognized weapons. Class assignment is persistent; admin can change it. CI verifies rule matrix, not gameplay.

