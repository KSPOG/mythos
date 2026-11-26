# MYTHOS Classes 0.1.0

Server-only Forge 1.21.1 / 52.1.5 / Java 21 mod. No custom registries, client code or network channels. Vanilla client compatibility intended, pending runtime validation. Install JAR on server only.

## Commands
- /class list
- /class choose <id> (one-time selection)
- /class info
- /class set <player> <id> (permission 2)
- /class clear <player> (permission 2)

IDs: warrior, heavy_knight, ranger, mage, rogue, cleric, necromancer.

Class identity persists in world/mythos-classes.json with atomic writes. No weapon assignments, restrictions on vanilla items, placeholder weapons, stats, XP or combat abilities.

## Integration
Future MYTHOS equipment mods define their own allowed class IDs. On the logical server thread use ClassApi.available(), ClassApi.classId(UUID), ClassApi.validClass(String), and ClassApi.canUse(UUID, Collection<String>). Empty collection means unrestricted, null or invalid policy denied. Restricted equipment denies unassigned players or unavailable storage. Equipment mods must actually call the API and enforce equip/attack/use restrictions in their server handlers. No restrictions are registered by this foundation. Class selection is persistent and administrators can change it. CI checks class IDs; runtime compatibility and persistence must be tested.
