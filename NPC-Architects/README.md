# NPC Architects 0.1.2 — experimental source prototype
Forge 1.21.1-52.1.5 / Java 21. No custom client content. Vanilla connection not tested.

## Build
Install Java 21 and obtain the official Forge 1.21.1-52.1.5 MDK from https://files.minecraftforge.net/net/minecraftforge/forge/index_1.21.1.html . Extract its Gradle wrapper (`gradlew`, `gradlew.bat`, `gradle/wrapper/`) into this directory. Use the unmodified MDK wrapper. Run `gradlew.bat build` (Windows) or `./gradlew build` (Linux). Install the generated JAR from `build/libs` on a test Forge server only. The Forge 52 port requires a successful CI build and in-game testing.

## Test
Use an operator on a flat, empty test world. `/architect house 7 7` spawns one builder and creates a house three blocks southeast of your feet. `/architect status`, `/architect pause 1`, `/architect resume 1`, `/architect cancel 1`. Cancel does not undo blocks or despawn the worker. Jobs are saved in the world directory. Keep chunks loaded. A blocked job can be resumed after clearing obstructions or moving the worker. Missing workers do not respawn. Vanilla villager AI may compete with navigation; scaffolding is not implemented and high blocks may stall. No claim integration, line-of-sight enforcement, material consumption, schematic import or custom GUI. Use backups; do not deploy on a production world.

## Diagnostics
`/architect ping` is available to all players; construction and status remain operator level 2. The server log reports initialization, command registration and startup. `pack.mcmeta` covers Minecraft 1.21.1 data (48) and resource (34) pack formats.
