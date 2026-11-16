# MYTHOS: Shattered Worlds

A server-side Minecraft MMORPG foundation. Target platform: **Minecraft 1.21.1**, **Forge 52.1.5**, **SpongeForge 1.21.1-52.1.5-12.0.3**, **SpongeAPI 12.0.0**, **Java 21**.

## Compatibility baseline

The official Sponge download page currently marks `1.21.1-52.1.5-12.0.3` as the recommended SpongeForge build for Minecraft 1.21.1. SpongeForge requires the matching Forge build. SpongeForge alone can accept vanilla Java clients; any additional mod must be reviewed for client-side requirements.

- Official recommended build: https://dl.spongepowered.org/spongeforge?minecraft=1.21.1
- SpongeForge installation instructions: https://docs.spongepowered.org/stable/en/server/getting-started/implementations/spongeforge.html
- Sponge migration/client compatibility notes: https://docs.spongepowered.org/stable/en/server/getting-started/migrating.html
- Forge downloads: https://files.minecraftforge.net/net/minecraftforge/forge/

## Windows quick start

1. Install a 64-bit Java 21 runtime (not a different major version) and ensure `java` is on `PATH`.
2. Run `install.bat`. It downloads the matching Forge installer, installs the dedicated server, and downloads the official SpongeForge universal JAR into `server\mods`.
3. Run `build.bat`. It downloads Gradle 8.10.2 into the local `.tools` folder if needed, builds the modules and copies `Mythos.jar` into `server\mods`.
4. Read Mojang's EULA at https://www.minecraft.net/eula. If you agree, edit `server\eula.txt` and set `eula=true`. The installer deliberately does not accept it for you.
5. Run `start.bat` (or `server\start.bat`).
6. The server is configured with whitelist enabled. Add your Minecraft account from the server console using `whitelist add <playername>`.

The installer does not open a public firewall port or enable offline mode. Keep `online-mode=true`. Do not expose the server publicly until permissions, backups, firewall rules and moderation have been configured.

## Repository layout

- `mythos-core`: Minecraft-independent domain model and class/weapon policy.
- `mythos-sponge`: SpongeAPI adapter, commands, persistence and server event enforcement.
- `scripts`: Windows installation and build automation.
- `server`: generated dedicated server instance, mods and runtime configuration.

## First milestone scope

- Eight starter classes: Warrior, Berserker, Ranger, Assassin, Mage, Cleric, Paladin and Summoner.
- Central class-to-weapon-category policy.
- Server-owned weapon registry with namespaced IDs.
- One-time `/mythosclass <class>` selection, persisted by UUID in an atomic properties file.
- Server-side validation for weapon interactions and attacks.
- Vanilla weapon categories are registered and checked by the same class policy.
- Dependency-free policy regression tests.

## Class-specific weapon enforcement

Any class can pick up, carry, store, or trade weapon items. MYTHOS checks the player's class when they try to use a registered weapon or attack. Bows and crossbows require Ranger. Unknown held items are rejected for melee attacks. Empty-hand attacks are allowed.

### Starter weapons

Staff with the `mythos.command.giveweapon` permission can issue `/mythosgiveweapon <online-player> <weapon-id>`. Use the short ID or prefix it with `mythos:`. Giving a weapon to a player of any class is allowed.

| Weapon ID | Eligible classes | Spell | Cooldown |
| --- | --- | --- | ---: |
| `dawnbringer` | Warrior, Berserker | Bulwark grants Resistance I for 6 seconds | 20 s |
| `frontier_longbow` | Ranger | Windstep grants Speed II for 6 seconds | 18 s |
| `ember_staff` | Mage | Ember Bolt deals 6 damage to a hostile mob in sight within 16 blocks | 8 s |
| `assassins_edge` | Assassin | Veilstep grants Invisibility for 4 seconds | 20 s |
| `clerics_relic` | Cleric | Mend grants Regeneration II for 5 seconds | 22 s |
| `paladins_oath` | Paladin | Aegis grants Absorption I for 10 seconds | 25 s |
| `war_sunderer` | Berserker | Battle Fury grants Strength I for 6 seconds | 25 s |
| `summoners_focus` | Summoner | Spirit Ascent grants Slow Falling for 8 seconds | 18 s |

Hold the weapon in your main hand and run `/mythoscast`. The server checks your class and starts a shared per-player cooldown on each successful cast. Swapping weapons or reconnecting during the cooldown does not reset it. Cooldowns are in memory and reset on server restart.

### Armour

Staff with `mythos.command.givearmor` can use `/mythosgivearmor <online-player> <armor-id>`. Both sets can be worn by any class.

| Set | Item IDs | Base |
| --- | --- | --- |
| Warden | `warden_helm`, `warden_chestplate`, `warden_leggings`, `warden_boots` | Iron |
| Riftweave | `riftweave_hood`, `riftweave_tunic`, `riftweave_leggings`, `riftweave_boots` | Purple-dyed leather |

Each piece has a defensive enchantment. Gear uses vanilla item bases and persistent model data. The sword and axe weapons have Sharpness I and the longbow has Power I. The staff and focus use their spells for their main effects. The starter gear is distributed through staff commands.

### In-game smoke test

1. Give a Mage an Ember Staff, aim at a hostile mob and cast. Check that a wall blocks the spell and a second successful cast must wait for the cooldown.
2. Trade the staff to a Warrior. Check that it stays in their inventory and casting is denied.
3. Give the Warrior a Frontier Longbow. Move it into and out of a chest, then try to draw it. Give the same bow to a Ranger and verify that it fires.
4. Give and equip a Riftweave piece. Check its dye and enchantment after reconnecting.

## Build

Requires Java 21. Run `build.bat` on Windows or use Gradle 8.10.2+:

```powershell
gradle clean build
```

Core policy tests can also be run with the Gradle task `:mythos-core:policyTest`. The Sponge adapter requires access to `https://repo.spongepowered.org/repository/maven-public/`.
