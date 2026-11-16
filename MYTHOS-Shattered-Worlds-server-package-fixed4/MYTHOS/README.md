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
2. Run `install.bat`. It downloads the matching Forge installer, installs the dedicated server, and installs SpongeForge and LuckPerms for Sponge into `server\mods`.
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

## Permissions and chat ranks

MYTHOS uses [LuckPerms for Sponge](https://luckperms.net/download) for groups and permissions. The Windows installer adds LuckPerms 5.5.91 for SpongeAPI 12 if there is no LuckPerms JAR in `server/mods`. On an existing server, stop it, run `install.bat` and `build.bat`, then start it again.

The installer stages the group preset as a GZIP archive at `server/luckperms/mythos-ranks.json.gz`. In the **running server console**, run:

```text
lp import "mythos-ranks.json.gz"
```

The import adds the predefined groups and permissions to the current LuckPerms data. On a server with existing groups, check for overlapping names before importing. Existing extra permissions are retained by a normal import. Use the console to check effective permissions if a player already has other groups or OP status.

| Group | Public chat prefix | Permissions included |
| --- | --- | --- |
| `default` | [Member] | Normal gameplay, including class selection and casting with eligible gear |
| `supporter` | [Supporter] | Cosmetic chat prefix only |
| `tester` | [Tester] | Change player classes and issue MYTHOS weapons and armour. Use on a test server. |
| `moderator` | [Moderator] | Kick players |
| `admin` | [Admin] | Moderator permissions, player bans and pardons, ban list, and whitelist management |
| `owner` | [Owner] | Admin and Tester permissions, plus bulk gear issuing and full LuckPerms administration. Only grant to trusted owners. |

Once the import has completed and KiritoSanPlays has joined at least once, assign the owner role from the console:

```text
lp user KiritoSanPlays parent add owner
```

The next public chat message should show `[Owner] KiritoSanPlays`. To assign another rank, use `lp user <name> parent add <group>`. To revoke it, use `lp user <name> parent remove <group>`. Staff prefixes outrank Supporter and Member. Tab-list and overhead names use their existing format.

For a smoke test, confirm an ordinary non-OP player can select a class but cannot run the staff class or item commands. Check that a supporter has the same gameplay permissions, a moderator can kick, an admin can manage the whitelist, and the owner can assign ranks and test equipment. Verify the prefix by sending a public chat message.

## Staff class testing

Grant trusted staff the `mythos.command.setclass` permission. Run `/mythossetclass <online-player> <class>` in game or omit the slash in the server console. For example, `/mythossetclass YourName ranger` switches your own class. The assignment is saved and takes effect immediately for weapon use and spellcasting. Inventory and active spell cooldowns are preserved.

To test access, try the command as a regular player and then as staff. As staff, switch an online player from Ranger to Mage while they carry a bow. Verify the bow stays in their inventory and they can no longer fire it. Restart the server and verify the Mage assignment persists.

## Class-specific weapon enforcement

Any class can pick up, carry, store, or trade weapon items. MYTHOS checks the player's class when they try to use a registered weapon or attack. Bows and crossbows require Ranger. Unknown held items are rejected for melee attacks. Empty-hand attacks are allowed.

### Custom weapons and spells

The catalog has 32 weapons across the eight classes, including four bows, three axes, six swords, eleven staffs or wands, and specialist weapons. Use `/mythosgiveweapon <online-player> <weapon-id>` with `mythos.command.giveweapon`. The server accepts both the short ID and the `mythos:` prefix. The bundled catalog lists every ID and its stats.

| Class | Weapons to try |
| --- | --- |
| Warrior | `sentinels_blade`, `ironroot_axe`, `rampart_glaive`, `dawnbringer` |
| Berserker | `war_sunderer`, `slag_splitter`, `ashfall_claymore`, `quake_maul` |
| Ranger | `frontier_longbow`, `thornwood_shortbow`, `silverleaf_bow`, `nightbriar_bow` |
| Assassin | `assassins_edge`, `viper_fang`, `gloamblade`, `glassneedle` |
| Mage | `ember_staff`, `frostglass_staff`, `starfall_staff`, `cinder_wand` |
| Cleric | `clerics_relic`, `dawn_chalice`, `hymnweaver_staff`, `bellkeeper_mace` |
| Paladin | `paladins_oath`, `sunward_blade`, `oathkeeper`, `vigil_mace` |
| Summoner | `summoners_focus`, `gravebloom_staff`, `echo_scepter`, `riftwood_staff` |

Right-click with a staff or wand in your main hand to cast. For Ember Bolt, aim at a hostile mob within 16 blocks with a clear line of sight. It uses no ammunition. The Ranger bows use vanilla ammunition and draw timing, including the Shortbow. Hold any MYTHOS weapon in your main hand and use `/mythoscast` to invoke its spell. The server checks your class and applies a shared per-player cooldown on a successful cast. Swapping weapons or reconnecting during the cooldown does not reset it. Cooldowns reset on server restart.

### Edit weapon stats and rarity

On first startup MYTHOS writes `server/config/mythos/weapons.tsv` from the [bundled catalog](mythos-sponge/src/main/resources/mythos-weapons.tsv). Edit this file with a tab-aware editor and restart the server. Fields include melee damage, attacks per second, maximum durability, bow Power (0–5), spell and rarity. Melee damage and attack speed are the unbuffed player totals before hit cooldown and target modifiers. Zero leaves the corresponding vanilla attribute or durability unchanged. Spell names reference the built-in spell definitions. Keep existing IDs, model numbers and base item types stable. If you add an entry, give it a new ID and model number and add its matching pack assets.

| Rarity | Name color |
| --- | --- |
| Common | White |
| Uncommon | Green |
| Rare | Aqua |
| Epic | Light purple |
| Legendary | Gold |

Newly issued stacks receive the configured stats and color. Previously issued weapons keep their saved names, stats, and enchantments. Reissue one to apply the current stats and rarity color. Existing model IDs still display the new artwork when the resource pack is loaded.

### Install the custom weapon models

Run `build.bat` and copy `build\resource-packs\MYTHOS-resource-pack.zip` to `%APPDATA%\.minecraft\resourcepacks`. Enable the pack under **Options → Resource Packs** in Minecraft Java 1.21.1. The server-side plugin handles gameplay and class checks. The resource pack supplies each weapon's 3D model and PNG texture, including three draw stages per bow.

To offer the pack to players on join, host the actual ZIP at a direct HTTPS download URL and set these entries in `server/server.properties`. Restart the server after saving.

```properties
resource-pack=https://your-host.example/MYTHOS-resource-pack.zip
resource-pack-sha1=REPLACE_WITH_THE_ZIP_SHA1
```

Get the hash in PowerShell with `(Get-FileHash .\build\resource-packs\MYTHOS-resource-pack.zip -Algorithm SHA1).Hash.ToLowerInvariant()`. Update the hash each time you upload a changed ZIP.

### Armour

Staff with `mythos.command.givearmor` can use `/mythosgivearmor <online-player> <armor-id>`. Both sets can be worn by any class.

| Set | Item IDs | Base |
| --- | --- | --- |
| Warden | `warden_helm`, `warden_chestplate`, `warden_leggings`, `warden_boots` | Iron |
| Riftweave | `riftweave_hood`, `riftweave_tunic`, `riftweave_leggings`, `riftweave_boots` | Purple-dyed leather |

Each piece has a defensive enchantment. Gear uses vanilla item bases and persistent model data. MYTHOS gear is distributed through staff commands.

### Give every weapon and armour piece

Owners can run `/mythosgiveallgear` in game for themselves or `/mythosgiveallgear <online-player>` to specify a recipient. From the server console, run `mythosgiveallgear <online-player>` while the recipient is online. The command gives one of each configured weapon and each MYTHOS armour piece in named shulker boxes. The default catalog has 40 pieces, so the recipient needs two empty inventory slots. Place and open the boxes to retrieve the gear.

The rank preset grants `mythos.command.giveallgear` to Owner. If you already imported the ranks, run this once from the server console.

```text
lp group owner permission set mythos.command.giveallgear true
```

After rebuilding and restarting, look for `MYTHOS bulk gear command registered` in the startup log to confirm that the server loaded the updated plugin.

### In-game smoke test

1. Give a Mage an Ember Staff, aim at a hostile mob and right-click. Check a near and a distant target. Check that a wall blocks the spell and a second successful cast must wait for the cooldown.
2. Trade the staff to a Warrior. Check that it stays in their inventory and casting is denied.
3. Give the Warrior a Frontier Longbow. Move it into and out of a chest, then try to draw it. Give the same bow to a Ranger and verify that it fires with ordinary arrows. With the pack enabled, draw each custom bow and a vanilla bow to verify all models.
4. Give and equip a Riftweave piece. Check its dye and enchantment after reconnecting.
5. As Owner, give the full gear set and place both crates. Check they contain 27 and 13 pieces, and that a weapon retains its name, spell, and stats. Test with one empty slot and verify no crate is delivered. Confirm a Tester cannot run the bulk command.

## Build

Requires Java 21. Run `build.bat` on Windows or use Gradle 8.10.2+:

```powershell
gradle clean build
```

Core policy tests can also be run with the Gradle task `:mythos-core:policyTest`. The Sponge adapter requires access to `https://repo.spongepowered.org/repository/maven-public/`.
