# MYTHOS Equipment 0.1.4

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

## Named sets and signature effects (0.1.1)
24 named sets, three per class; each has a named signature weapon and four named armor pieces. All 120 registry IDs remain unchanged. Set-specific stat focus increases random selection weighting, preserving existing valid rolls, tier ranges and guaranteed stats. The signature effect is shared by each set's five items; only Rare or higher activates it. One effect per player per 200 game ticks on qualifying hostile damage, highest eligible equipped rarity wins, weapon then armor order breaks ties. No stacking and no full-set requirement. Rare through Mythical scale duration (3/4/5/6 seconds), heal (2/3/4/5 HP), burn (2/3/4/5 seconds); Mythical upgrades applicable potion amplifiers. Cooldown resets on server restart, not on swapping items. Effects are implemented with vanilla buffs/debuffs, not dedicated magic projectiles or custom animations. Some sets share underlying effect types with distinct names. Previous CI failed due to ambiguous Tier import; explicit import added. Runtime effect timing, PvP exclusion and equip validation still need testing.

## 0.1.2 visual and weapon expansion
240 registered items (135 weapons, 9 shields, 96 armor pieces), preserving original IDs. Expanded sword, greatsword, shortsword, dagger, axe, greataxe, hammer, mace, staff, wand and crossbow families. Original weapons now have category-specific speed and damage. Shields are real ShieldItems, Warrior/Heavy Knight/Cleric, offhand only; server rejects shields alongside two-handed custom weapons. Weapon models use detailed segmented cuboids and a 256x256 material atlas; armor icons 128x128 and wearable textures 512x256 with corrected vanilla UV mapping. This is detailed Minecraft JSON geometry, not arbitrary polygon meshes. Wearable armor still uses vanilla geometry. Magical weapons remain melee foundations, added weapons reuse class/tier signature effects, and custom arrows, dual wielding, projectile render overrides, full custom armor meshes and runtime visual validation are not yet implemented.

## 0.1.3
375 items, 48 complete class sets across levels 1/10/20/30/40/50; 192 armor pieces, 18 shields, 165 weapons. Added articulated armor model extension and larger inventory weapon render. Six tier stat ranges. Existing added level 10/30/50 weapon rolls outside new ranges may be regenerated on inventory validation. Custom armor model runtime visual validation pending.

## 0.1.4
Fix integrated server crash when Classes is absent. Local creative preview allowed without Classes; survival remains restricted, dedicated servers still require Classes. Includes previous lazy armor model initialization fix. Install matching Equipment JAR on both sides.
