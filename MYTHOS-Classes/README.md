# MYTHOS Classes 0.1.1

Server-only Forge 1.21.1 / 52.1.5 / Java 21 mod. No custom registries, client code or network channels. Vanilla client compatibility intended, pending runtime validation. Install JAR on server only.

## Commands
- /class list
- /class choose <id> (one-time selection)
- /class info
- /class set <player> <id> (permission 2)
- /class clear <player> (permission 2)

IDs: warrior, heavy_knight, ranger, mage, rogue, cleric, necromancer.

Class identity persists in world/mythos-classes.json with atomic writes. No weapon assignments, restrictions on vanilla items, placeholder weapons, XP or combat abilities.

## Integration
Future MYTHOS equipment mods define their own allowed class IDs. On the logical server thread use ClassApi.available(), ClassApi.classId(UUID), ClassApi.validClass(String), and ClassApi.canUse(UUID, Collection<String>). Empty collection means unrestricted, null or invalid policy denied. Restricted equipment denies unassigned players or unavailable storage. Equipment mods must actually call the API and enforce equip/attack/use restrictions in their server handlers. No restrictions are registered by this foundation. Class selection is persistent and administrators can change it. CI checks class IDs; runtime compatibility and persistence must be tested.

## Stats and allocation (0.1.1)
Each class starts with a budget of 60 base stats (strength, defense, vitality, dexterity, intelligence, spirit) and 5 unspent points. One point increases one selected stat by one. Effective stat cap 100 each; lifetime earned point budget capped at 10000. Existing class-only saves migrate automatically with five starting points. Class reassignment refunds allocations, preserves earned budget; clearing class deletes that profile and budget. Same-class reassignment preserves allocations. All writes are atomic and rollback in memory on save failure.

- /class preview <id>
- /class stats [player] (other player requires permission 2)
- /class allocate <stat> <amount>
- /class points grant <player> <amount> (permission 2)
- /class points reset <player> (permission 2, refunds all allocated points)

ClassApi.stats(UUID) returns an immutable snapshot of base, allocated and total stats plus earned/unspent points. ClassApi.grantPoints(UUID,int) is a trusted server-thread reward hook for future progression mods. No automatic XP, leveling, rewards, vanilla attributes or combat modifiers; stats are authoritative data for future integration. Vanilla equipment is untouched. CI checks stat budgets and identifiers; live persistence and allocation require runtime verification.
