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

## Spawn hub

The [project checklist](CHECKLIST.md) tracks the chapter and launch milestones.

The Owner rank can build the Rift Sanctum, a 21×21 arrival plaza with a floating amethyst shard, blackstone arches, lit paths and eight interactive class shrines. If the ranks were already imported, grant the build permission once from the server console.

```text
lp group owner permission set mythos.command.buildhub true
```

1. Join in Creative mode and hover above clear ground or water in the Overworld. Choose an open 21×21 area with your feet at the intended arrival height. The plaza needs air from one block below your feet through three blocks above it, plus overhead clearance for the shard and arches up to nine blocks above your feet. Build at least 21 blocks to one side of the Overworld spawn so the survival exit is outside the hub.
2. Run `/mythosbuildhub` once. If the site is obstructed, the command reports the blocked coordinates before placing anything. The build saves the site to `server/config/mythos/spawn-hub.properties`.
3. Run `/mythoshub` to arrive at the lodestone. First-time players arrive there automatically. `/mythosclasses` shows class descriptions and lets new players prepare a selection in chat before they confirm it with Enter.
4. Click **Explore the survival world** or run `/mythosleavehub` to go to a safe spot near the existing Overworld spawn.

If you already built the original plaza, use `/mythoshub` followed by `/mythosupgradehub` as Owner. This adds the Sanctum structures in place. The command checks each destination before building and reports coordinates if a decoration obstructs the upgrade. Right-click a class shrine to see its class preview.

If the game reports an unknown command, try `/mythosclasses`. If that is also unknown, rebuild and restart from this project folder. The startup log should include `MYTHOS hub build command registered`. If the build command reports a missing permission, use the LuckPerms command above from the server console.

The plaza protects players from damage, blocks hostile spawns, and restricts block changes within the plaza. Owners with the build permission can decorate it. Keep the center lodestone and the two air blocks above it clear for arrivals.

For a live test, use a fresh vanilla Java 1.21.1 account to verify first arrival, class selection and the survival exit. Try breaking a plaza block and taking damage with a regular account. Rejoin after exploring to verify that returning players keep their location. Restart the server and check that `/mythoshub` still works.

## Worlds and portal frames

Worlds are separate dimensions on the same SpongeForge server. Players keep their inventory and MYTHOS class between worlds. The Owner rank can manage worlds and portal links. If you previously imported the ranks, grant the permission from the server console.

```text
lp group owner permission set mythos.command.worldmanage true
```

| Command | What it does |
| --- | --- |
| `/mythosworld` | List world keys |
| `/mythosworld create chapter_one` | Create or load a normal-terrain world named `mythos:chapter_one` |
| `/mythosworld load mythos:chapter_one` | Load an existing world |
| `/top` or `/mythostop` | Owner-only. Move up to the surface in your current world. If the current column is unsafe, search within 16 blocks |
| `/mythosworldtp mythos:chapter_one` | Teleport yourself to a safe spot near that world's spawn |
| `/mythosworldtp minecraft:overworld KiritoSanPlays` | Teleport an online player to the Overworld spawn area |
| `/mythosportal define chapter_gate` | Select the frame in your current world |
| `/mythosportal link chapter_gate chapter_return` | Pair two defined frames in both directions |
| `/mythosportal` | List frame positions and links |
| `/mythosportal setarrival chapter_gate` | Set the arrival block, front side, and facing for this frame |
| `/mythosportal unlink chapter_gate` | Disconnect both ends and keep the frames |
| `/mythosportal remove chapter_gate` | Remove a frame's registration |
| `/mythosportal cancel` | Cancel corner selection |

Build a vertical rectangular frame in each world with an open passage at least one block wide and two blocks high. Run `/mythosportal define <name>` at each frame and right-click the **lower outside corner** and **opposite upper outside corner** with an empty main hand. Run the link command after defining both. Players travel by walking into either opening. Keep a safe floor and two blocks of headroom beside the destination frame. Arrivals are placed beside the frame to prevent an immediate return trip.

For a gate with a backdrop, stand on solid ground at least two blocks in front of the frame, look in the direction players should face, then run `/mythosportal setarrival <frame>`. Players arriving at that frame use the saved block and view direction, and walking up to its front triggers it at ground level. Keep the landing block clear with room to stand. Set this separately for each end that needs a front-side arrival.

Frame pairs are saved in `server/config/mythos/frame-portals.properties`. Use the define command with the same name to move or resize an existing frame while retaining its link. Earlier single-block links need to be defined again as frame pairs. World management and world teleport commands remain available from the console, where commands omit the slash and teleport requires a player name.

For a live check, pair two frames across worlds, walk through and return, then restart and verify the pairing persists. Test an obstructed exit and confirm a regular player can travel while Owner commands remain restricted.

## Skyreach v2 test build

Build the MYTHOS plugin and restart the server. From the server console, use this command to place the preview in a clear, loaded test world:

```text
buildhub mythos:c1 0 160 0
```

Owners can also run `/buildhub` in game while flying one block above the intended center floor, or supply the world key and coordinates. The console logs the site check, placement progress, and the arrival coordinates on completion. At the example anchor, the arrival is `0 161 11`. The footprint spans X −68 to 70, Y 128 to 208, and Z −56 to 71. Existing blocks at planned coordinates must be air or match the blueprint. If placement stops after the site check, completed batches can remain in the world.

This is a preview structure. Use existing MYTHOS commands to define gate frames and pairs. The [Skyreach v2 datapack](datapacks/MYTHOS-Skyreach-v2.zip) includes the gate coordinates for the example anchor.

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

Staff with `mythos.command.givearmor` can use `/mythosgivearmor <online-player> <armor-id>`. Any class can wear these sets. The themes below describe their intended playstyles. Each piece has its own enchantment. Leather colours show on vanilla clients.

| Set | Item IDs | Base and specialty |
| --- | --- | --- |
| Warden | `warden_helm`, `warden_chestplate`, `warden_leggings`, `warden_boots` | Iron, general defence |
| Riftweave | `riftweave_hood`, `riftweave_tunic`, `riftweave_leggings`, `riftweave_boots` | Purple leather, protection from fire and projectiles |
| Ironbastion | `ironbastion_helm`, `ironbastion_chestplate`, `ironbastion_leggings`, `ironbastion_boots` | Iron, frontline defence and blast protection |
| Ashforged | `ashforged_helm`, `ashforged_chestplate`, `ashforged_leggings`, `ashforged_boots` | Iron, protection from fire and blasts |
| Trailwarden | `trailwarden_hood`, `trailwarden_tunic`, `trailwarden_leggings`, `trailwarden_boots` | Green leather, protection from arrows and falls |
| Nightveil | `nightveil_hood`, `nightveil_tunic`, `nightveil_leggings`, `nightveil_boots` | Dark leather, Swift Sneak leggings |
| Frostglass | `frostglass_hood`, `frostglass_tunic`, `frostglass_leggings`, `frostglass_boots` | Light blue leather, protection from arrows and blasts |
| Dawnweave | `dawnweave_hood`, `dawnweave_tunic`, `dawnweave_leggings`, `dawnweave_boots` | Cream leather, general defence and fire protection |
| Sunward | `sunward_helm`, `sunward_chestplate`, `sunward_leggings`, `sunward_boots` | Gold, Thorns chestplate |
| Gravebloom | `gravebloom_hood`, `gravebloom_tunic`, `gravebloom_leggings`, `gravebloom_boots` | Teal leather, Respiration and Depth Strider |

#### Class-themed armour

These additional sets cover heavy defence, environmental protection, stealth, and travel. Players can mix pieces across sets.

| Theme | Set | Base | Item IDs | Specialty |
| --- | --- | --- | --- | --- |
| Warrior | Rampart | Diamond | `rampart_helm`, `rampart_chestplate`, `rampart_leggings`, `rampart_boots` | Blast-resistant heavy armour |
| Warrior | Stonewatch | Chainmail | `stonewatch_helm`, `stonewatch_chestplate`, `stonewatch_leggings`, `stonewatch_boots` | Projectile and fall protection |
| Warrior | Bridgeguard | Iron | `bridgeguard_helm`, `bridgeguard_chestplate`, `bridgeguard_leggings`, `bridgeguard_boots` | General defence with Depth Strider |
| Berserker | Cinderplate | Netherite | `cinderplate_helm`, `cinderplate_chestplate`, `cinderplate_leggings`, `cinderplate_boots` | Heavy armour for fire and explosions |
| Berserker | Siegebreaker | Diamond | `siegebreaker_helm`, `siegebreaker_chestplate`, `siegebreaker_leggings`, `siegebreaker_boots` | Blast protection and durable boots |
| Berserker | Redfang | Leather | `redfang_hood`, `redfang_tunic`, `redfang_leggings`, `redfang_boots` | Crimson leather with Thorns |
| Ranger | Hawkeye | Leather | `hawkeye_hood`, `hawkeye_tunic`, `hawkeye_leggings`, `hawkeye_boots` | Archer’s leather for projectile-heavy fights |
| Ranger | Highpine | Chainmail | `highpine_helm`, `highpine_chestplate`, `highpine_leggings`, `highpine_boots` | Fall protection for high ground |
| Ranger | Marshstalker | Leather | `marshstalker_hood`, `marshstalker_tunic`, `marshstalker_leggings`, `marshstalker_boots` | Respiration and Depth Strider for wetlands |
| Assassin | Duskthread | Leather | `duskthread_hood`, `duskthread_tunic`, `duskthread_leggings`, `duskthread_boots` | Swift Sneak III and Feather Falling IV |
| Assassin | Whispersteel | Chainmail | `whispersteel_helm`, `whispersteel_chestplate`, `whispersteel_leggings`, `whispersteel_boots` | Chainmail with Swift Sneak |
| Assassin | Siltstep | Leather | `siltstep_hood`, `siltstep_tunic`, `siltstep_leggings`, `siltstep_boots` | Stealth near and under water |
| Mage | Stormsilk | Leather | `stormsilk_hood`, `stormsilk_tunic`, `stormsilk_leggings`, `stormsilk_boots` | Lavender leather for blasts and arrows |
| Mage | Runebound | Chainmail | `runebound_helm`, `runebound_chestplate`, `runebound_leggings`, `runebound_boots` | Mixed defence and boot durability |
| Mage | Emberweave | Leather | `emberweave_hood`, `emberweave_tunic`, `emberweave_leggings`, `emberweave_boots` | Orange leather for fire protection |
| Cleric | Ivorymantle | Leather | `ivorymantle_hood`, `ivorymantle_tunic`, `ivorymantle_leggings`, `ivorymantle_boots` | Pale leather for general defence |
| Cleric | Pilgrim | Chainmail | `pilgrim_helm`, `pilgrim_chestplate`, `pilgrim_leggings`, `pilgrim_boots` | Durable travel gear with water mobility |
| Cleric | Hallowmist | Leather | `hallowmist_hood`, `hallowmist_tunic`, `hallowmist_leggings`, `hallowmist_boots` | Light teal leather for underwater travel |
| Paladin | Oathplate | Diamond | `oathplate_helm`, `oathplate_chestplate`, `oathplate_leggings`, `oathplate_boots` | Heavy general defence with Thorns |
| Paladin | Dawnshield | Iron | `dawnshield_helm`, `dawnshield_chestplate`, `dawnshield_leggings`, `dawnshield_boots` | Defence against mixed threats |
| Paladin | Gilded Vow | Golden | `gilded_vow_helm`, `gilded_vow_chestplate`, `gilded_vow_leggings`, `gilded_vow_boots` | Gold armour with Thorns III and fire protection |
| Summoner | Boneweave | Leather | `boneweave_hood`, `boneweave_tunic`, `boneweave_leggings`, `boneweave_boots` | Bone-coloured leather and blast protection |
| Summoner | Wraithmesh | Chainmail | `wraithmesh_helm`, `wraithmesh_chestplate`, `wraithmesh_leggings`, `wraithmesh_boots` | Fire, blast and sneak utility |
| Summoner | Abyssal | Leather | `abyssal_hood`, `abyssal_tunic`, `abyssal_leggings`, `abyssal_boots` | Deep-blue leather for underwater combat |

### Give every weapon and armour piece

Owners can run `/mythosgiveallgear` in game for themselves or `/mythosgiveallgear <online-player>` to specify a recipient. From the server console, run `mythosgiveallgear <online-player>` while the recipient is online. The command gives one of each configured weapon and each MYTHOS armour piece in named shulker boxes. The default catalog has 168 pieces (32 weapons and 136 armour pieces), so the recipient needs seven empty inventory slots. Place and open the boxes to retrieve the gear.

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
5. As Owner, give the full gear set and place all seven crates. Check that six contain 27 pieces each and the last contains six, and that a weapon retains its name, spell, and stats. Test with six empty slots and verify no crate is delivered. Confirm a Tester cannot run the bulk command.

## Build

Requires Java 21. Run `build.bat` on Windows or use Gradle 8.10.2+:

```powershell
gradle clean build
```

Core policy tests can also be run with the Gradle task `:mythos-core:policyTest`. The Sponge adapter requires access to `https://repo.spongepowered.org/repository/maven-public/`.
