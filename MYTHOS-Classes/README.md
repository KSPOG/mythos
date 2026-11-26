# MYTHOS Classes 0.1.3

Server-only Forge 1.21.1 / 52.1.5 / Java 21 mod. No custom registries, client code or network channels. Vanilla client compatibility intended, pending runtime validation. Install JAR on server only.

## Commands
- /class list
- /class choose <id> (one-time selection)
- /class info
- /class set <player> <id> (permission 2)
- /class clear <player> (permission 2)

IDs: warrior, heavy_knight, ranger, mage, rogue, cleric, necromancer.

Class identity persists in world/mythos-classes.json with atomic writes. No weapon assignments, restrictions on vanilla items, placeholder weapons or combat abilities.

## Integration
Future MYTHOS equipment mods define their own allowed class IDs. On the logical server thread use ClassApi.available(), ClassApi.classId(UUID), ClassApi.validClass(String), and ClassApi.canUse(UUID, Collection<String>). Empty collection means unrestricted, null or invalid policy denied. Restricted equipment denies unassigned players or unavailable storage. Equipment mods must actually call the API and enforce equip/attack/use restrictions in their server handlers. No restrictions are registered by this foundation. Class selection is persistent and administrators can change it. CI checks class IDs; runtime compatibility and persistence must be tested.

## Stats and allocation (0.1.1)
Each class starts with a budget of 60 base stats (strength, defense, vitality, dexterity, intelligence, spirit) and 5 unspent points. One point increases one selected stat by one. Effective stat cap 100 each; lifetime earned point budget capped at 10000. Existing class-only saves migrate automatically with five starting points. Class reassignment refunds allocations, preserves earned budget; clearing class deletes that profile and budget. Same-class reassignment preserves allocations. All writes are atomic and rollback in memory on save failure.

- /class preview <id>
- /class stats [player] (other player requires permission 2)
- /class allocate <stat> <amount>
- /class points grant <player> <amount> (permission 2)
- /class points reset <player> (permission 2, refunds all allocated points)

ClassApi.stats(UUID) returns an immutable snapshot of base, allocated and total stats plus earned/unspent points. ClassApi.grantPoints(UUID,int) is a trusted server-thread reward hook for future progression mods. No vanilla attributes or combat modifiers; stats are authoritative data for future integration. Vanilla equipment is untouched. CI checks stat budgets and identifiers; live persistence and allocation require runtime verification.

## Player progression (0.1.2)
Independent MYTHOS XP, level 1–50, cumulative threshold 100*(L-1)*L; maximum 245000 XP. Direct player-attributed hostile Monster kills grant 50 XP in survival/adventure, excluding tagged MYTHOS pets/mounts and allied entities. No PvP, creative or spectator rewards. Pet-attributed kills and environmental kills without player attribution are not counted. No anti-farm/spawner differentiation yet. Every gained level yields 3 allocatable points, derived from XP rather than separately incremented. At level 50 the starting+level budget is 152 before bonus grants. Existing saves default to level 1, retain all allocations and granted points. Class changes retain XP and refund allocations; clear removes profile including XP. Bonus grant budget remains capped at 10000 including starting points, separate from the 147 level points.

- /class level [player] (other player permission 2)
- /class xp (view own progression)
- /class xp grant <player> <amount> (permission 2)

ClassApi.progression(UUID) and ClassApi.grantXp(UUID,int) support future quests/dungeons. API returns actual award, level changes and points; caller handles notification for external grants. No client networking, no modifications to vanilla XP or attributes. CI tests progression boundaries; runtime death attribution, persistence and vanilla client compatibility still require verification.

## Level nameplates (0.1.3)
Players with a selected class display their MYTHOS level as a gray suffix: Player [Lv. 50]. Vanilla scoreboard teams provide overhead nameplates and tab-list formatting without any required client mod. Refresh on login, class assignment/clear and local level-up; reconcile external API rewards every 20 server ticks. Unassigned players have no suffix. Reserved owned teams are mythos_lv_1 through mythos_lv_50, with explicit display-name ownership marker. Startup/shutdown remove only owned level teams. Players already in another scoreboard team are left untouched to avoid overwriting rank/clan membership; their level suffix is not displayed until a compatible team integration is developed. Vanilla supports only one scoreboard team per player. Chat formatting is not independently overridden. Runtime nameplate/tab rendering and other scoreboard integrations require testing.
