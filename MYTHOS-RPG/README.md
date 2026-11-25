# MYTHOS RPG foundation 0.1.0 — experimental, not production
17 separately packaged modules, shared Core. Install together on Forge 52.1.5 / Java 21 after build verification. No custom registries or client channels. Vanilla join not runtime verified.
## Implemented first slice
- /class choose ranger; /stats; /stats allocate dexterity 5; /level; operator /level grantxp <player> <xp>.
- /monster mark <entity> <level> marks existing hostile monster with level, scaled vanilla health (cap 1024), attack and EXP. Only marked monsters grant RPG EXP; killer attribution via DamageSource, not contribution or party sharing.
- /nameplate uses per-player scoreboard teams, skips conflicting existing teams.
- /rpgitem roll <level> creates persistent virtual bow record in personal vault, not a usable ItemStack. No enforcement or item transfer yet.
- /lootroll previews rarity only, no material rewards.
- /party create/invite/accept/leave: session memory, no reward sharing.
- /clan create/leave: durable owner record; recruitment/ranks unfinished.
- /gold grant <player> <amount> (op); /gold; /bank deposit/withdraw <amount>. Remote gold bank prototype, not item storage.
- /trade request <player>, /trade accept, /trade gold <amount>, /trade confirm (both twice), /trade cancel. GOLD ONLY. Three-block distance at request, accept, every server tick and commit; same dimension, alive, non-spectator. Both confirmations reset on offer change. Locked final stage. Atomic JSON state replacement and transaction ledger. No inventory transfer, combat lockout or dungeon restrictions yet.
- Boss inspect only. Dungeons, raids, housing are explicitly non-functional scaffolds. No protected properties or instance allocation.
## Limitations
This PR is an initial foundation, NOT a complete MMORPG. No custom assets, item combat restrictions, full NPC AI, GUI, production database, personal item banking, clan housing, clan recruitment, boss phases or raid rewards. No PvP/AFK protection. Single-thread server state, synchronous whole-file JSON writes and rollback; use test world, not production. Defaults hardcoded for now. Build and runtime tests pending. Do not enable experimental economy on public server until security and crash recovery tested.
