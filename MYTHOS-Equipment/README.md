# MYTHOS Equipment 0.1.0

Forge 1.21.1 / 52.1.5 / Java 21. Equipment JAR on client AND server; MYTHOS Classes 0.1.4 on server. Stacked PR depends on Classes PR #63.

120 items: eight classes, three tiers (Adventurer level 1, Veteran level 20, Dragon level 40), one weapon and four wearable armor pieces per tier/class. Procedural 32x32 inventory sprites and standard humanoid armor textures, not custom high-polygon geometry. Ranger uses BowItem; all other weapons currently use SwordItem as a melee foundation, including staff and caster shapes. Dedicated magical/ranged/curse attacks, custom animation and bow draw model overrides are deferred.

Rarity is independent of tier: Common 45% (1 stat), Uncommon 28% (2), Rare 16% (3), Epic 8% (4), Legendary 2.5% (5), Mythical 0.5% (6). Each has an increasing bonus to tier roll range (+0/1/2/4/7/10). Armor always rolls DEF, weapons their primary class stat, remaining distinct stats weighted toward class. Rolls stored in vanilla custom data on each stack and synchronized to clients. Server initializes unrolled items in player inventory, command grants roll immediately. No reroll command. No natural loot or crafting acquisition yet.

Server checks class and level; incompatible equipment is removed to inventory or dropped if full. Vanilla gear unrestricted. Server applies transient attributes for rolled ATK (+0.25 attack per point), STR (+0.15 attack), DEF (+0.25 armor), VIT (+0.5 max health), DEX (+0.002 movement speed). INT/SPI are stored but do not yet affect combat. These bonuses are separate from base class stats; ranged attack scaling and spell integration not implemented. Native base armor/weapon attributes remain. Tick-based enforcement and projectile gates need runtime bypass testing. No assertion of secure client creative item data against privileged creative players.

Commands:
- /equipment list
- /equipment give <player> <item> [rarity] (permission 2)
- /equipment give @s dragon_warrior_chest mythical
- /equipment give @s adventurer_ranger_weapon

CI checks the catalog, all rarity roll ranges, generated assets and compile. Client/server runtime verification required.
