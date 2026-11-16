package com.ksp.mythos.sponge;

import com.ksp.mythos.core.character.CharacterClass;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.spongepowered.api.ResourceKey;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.command.Command;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.entity.living.player.server.ServerPlayer;
import org.spongepowered.api.world.server.ServerLocation;
import org.spongepowered.math.vector.Vector3i;

final class SpawnHub {
    static final String MANAGE = "mythos.command.buildhub";
    private static final ResourceKey OVERWORLD = ResourceKey.minecraft("overworld");
    private static final int RADIUS = 10;
    private static final int PROTECTION_HEIGHT = 6;

    private record Site(ResourceKey world, Vector3i center) {
        boolean contains(final ServerLocation location) {
            final Vector3i block = location.blockPosition();
            return this.world.equals(location.worldKey())
                    && Math.abs((long) block.x() - this.center.x()) <= RADIUS
                    && Math.abs((long) block.z() - this.center.z()) <= RADIUS
                    && block.y() >= this.center.y() - 1 && block.y() <= this.center.y() + PROTECTION_HEIGHT;
        }

        Optional<ServerLocation> arrival() {
            return Sponge.server().worldManager().world(this.world).map(world ->
                    ServerLocation.of(world, this.center.x() + 0.5, this.center.y(), this.center.z() + 0.5));
        }
    }

    private final Path file;
    private Site site;

    SpawnHub(final Path file) throws IOException {
        this.file = file;
        if (Files.notExists(file)) return;
        final Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file)) {
            properties.load(reader);
        }
        try {
            this.site = new Site(ResourceKey.resolve(properties.getProperty("world")), new Vector3i(
                    Integer.parseInt(properties.getProperty("x")),
                    Integer.parseInt(properties.getProperty("y")),
                    Integer.parseInt(properties.getProperty("z"))));
        } catch (RuntimeException exception) {
            throw new IOException("Invalid MYTHOS spawn hub configuration at " + file, exception);
        }
    }

    boolean contains(final ServerLocation location) {
        return this.site != null && this.site.contains(location);
    }

    boolean configured() {
        return this.site != null;
    }

    boolean arrive(final ServerPlayer player) {
        return this.site != null && this.site.arrival()
                .filter(location -> location.add(0, -1, 0).block().type() == BlockTypes.LODESTONE.get()
                        && isAir(location) && isAir(location.add(0, 1, 0)))
                .filter(player::setLocation).isPresent();
    }

    static CommandResult showClasses(final ServerPlayer player, final Optional<CharacterClass> selected) {
        player.sendMessage(Component.text("MYTHOS Classes", NamedTextColor.GOLD));
        player.sendMessage(selected.map(character -> Component.text("Your class is " + character.displayName() + "."))
                .orElseGet(() -> Component.text("Click a class to preview your selection in chat. Press Enter to confirm.")));
        for (final CharacterClass character : CharacterClass.values()) {
            Component name = Component.text(character.displayName(), NamedTextColor.AQUA);
            if (selected.isEmpty()) {
                name = name.clickEvent(ClickEvent.suggestCommand("/mythosclass " + character.id()))
                        .hoverEvent(HoverEvent.showText(Component.text("Select " + character.displayName())));
            }
            player.sendMessage(name.append(Component.text("  " + switch (character) {
                case WARRIOR -> "Frontline fighter with swords, axes and polearms";
                case BERSERKER -> "Heavy hits with greatswords, greataxes and hammers";
                case RANGER -> "Ranged combat with bows and crossbows";
                case ASSASSIN -> "Fast attacks with daggers and dual blades";
                case MAGE -> "Spellcasting with staffs and wands";
                case CLERIC -> "Recovery magic and holy weapons";
                case PALADIN -> "Defensive combat with swords, shields and maces";
                case SUMMONER -> "Magic from summoning staffs and catalysts";
            }, NamedTextColor.GRAY)));
        }
        return CommandResult.success();
    }

    Command.Parameterized buildCommand() {
        return Command.builder().permission(MANAGE).executor(context -> {
            final Optional<ServerPlayer> player = context.cause().first(ServerPlayer.class);
            if (player.isEmpty()) return CommandResult.error(Component.text("Run /mythosbuildhub in game at the hub site."));
            if (configured()) return CommandResult.error(Component.text("A spawn hub is already configured."));
            final ServerLocation origin = player.get().serverLocation();
            final ServerLocation center = origin.withPosition(origin.blockPosition().toDouble());
            try {
                final CommandResult result = build(center);
                if (result.isSuccess()) {
                    context.sendMessage(Component.text("MYTHOS spawn hub built. Use /mythoshub to visit."));
                }
                return result;
            } catch (IOException exception) {
                return CommandResult.error(Component.text("Could not save the hub settings: " + exception.getMessage()));
            }
        }).build();
    }

    Command.Parameterized exitCommand() {
        return Command.builder().executor(context -> {
            final Optional<ServerPlayer> player = context.cause().first(ServerPlayer.class);
            if (player.isEmpty() || !contains(player.get().serverLocation())) {
                return CommandResult.error(Component.text("Use /mythosleavehub while inside the hub."));
            }
            final var server = Sponge.server();
            final var overworld = server.worldManager().world(OVERWORLD);
            if (overworld.isEmpty()) return CommandResult.error(Component.text("The survival world is unavailable."));
            final var world = overworld.get();
            final var spawn = world.properties().spawnPosition();
            final var destination = ServerLocation.of(world, spawn.x() + 0.5, spawn.y(), spawn.z() + 0.5);
            if (server.teleportHelper().findSafeLocation(destination)
                    .filter(location -> !contains(location)).filter(player.get()::setLocation).isEmpty()) {
                return CommandResult.error(Component.text("The survival spawn is unavailable. Contact staff."));
            }
            return CommandResult.success();
        }).build();
    }

    private CommandResult build(final ServerLocation center) throws IOException {
        final var overworld = Sponge.server().worldManager().world(OVERWORLD);
        if (overworld.isEmpty()) return CommandResult.error(Component.text("The survival world is unavailable."));
        final var survivalSpawn = overworld.get().properties().spawnPosition();
        if (!center.worldKey().equals(OVERWORLD)) {
            return CommandResult.error(Component.text("Build the hub in the Overworld."));
        }
        if (Math.abs((long) center.blockPosition().x() - survivalSpawn.x()) <= RADIUS + 10
                && Math.abs((long) center.blockPosition().z() - survivalSpawn.z()) <= RADIUS + 10) {
            return CommandResult.error(Component.text("Build the hub at least 21 blocks to the side of the survival spawn."));
        }
        final Map<ServerLocation, BlockState> blocks = new LinkedHashMap<>();
        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int z = -RADIUS; z <= RADIUS; z++) {
                final int edge = Math.max(Math.abs(x), Math.abs(z));
                for (int y = -1; y <= 3; y++) {
                    final ServerLocation block = center.add(x, y, z);
                    if (!block.world().contains(block.blockPosition()) || !isAir(block)) {
                        return CommandResult.error(Component.text("The hub needs a clear 21×21 area from one block below you "
                                + "to three blocks above. Blocked at " + block.blockPosition() + "."));
                    }
                }
                final BlockState floor = edge == RADIUS ? BlockTypes.DEEPSLATE_TILES.get().defaultState()
                        : (Math.abs(x) <= 1 || Math.abs(z) <= 1) ? BlockTypes.POLISHED_ANDESITE.get().defaultState()
                        : BlockTypes.POLISHED_DEEPSLATE.get().defaultState();
                blocks.put(center.add(x, -1, z), floor);
                if (edge == RADIUS) {
                    blocks.put(center.add(x, 0, z), BlockTypes.LIGHT_BLUE_STAINED_GLASS.get().defaultState());
                    blocks.put(center.add(x, 1, z), BlockTypes.LIGHT_BLUE_STAINED_GLASS.get().defaultState());
                }
            }
        }
        for (int offset = -7; offset <= 7; offset += 7) {
            blocks.put(center.add(offset, -1, 2), BlockTypes.SEA_LANTERN.get().defaultState());
            blocks.put(center.add(2, -1, offset), BlockTypes.SEA_LANTERN.get().defaultState());
        }
        blocks.put(center.add(0, -1, 0), BlockTypes.LODESTONE.get().defaultState());
        for (final int x : new int[] {-7, 7}) {
            for (final int z : new int[] {-7, 7}) {
                blocks.put(center.add(x, -1, z), BlockTypes.SEA_LANTERN.get().defaultState());
                blocks.put(center.add(x, 0, z), BlockTypes.POLISHED_BASALT.get().defaultState());
                blocks.put(center.add(x, 1, z), BlockTypes.POLISHED_BASALT.get().defaultState());
                blocks.put(center.add(x, 2, z), BlockTypes.SEA_LANTERN.get().defaultState());
            }
        }
        final var placed = new LinkedHashMap<ServerLocation, BlockState>();
        try {
            for (final var entry : blocks.entrySet()) {
                final ServerLocation location = entry.getKey();
                final BlockState original = location.block();
                if (!location.setBlock(entry.getValue())) {
                    return CommandResult.error(Component.text("Hub placement was blocked at " + location.blockPosition() + "."));
                }
                placed.put(location, original);
            }
            final Site built = new Site(center.worldKey(), center.blockPosition());
            save(built);
            this.site = built;
        } finally {
            if (!configured()) {
                for (final var entry : placed.reversed().entrySet()) entry.getKey().setBlock(entry.getValue());
            }
        }
        return CommandResult.success();
    }

    private void save(final Site built) throws IOException {
        final Properties properties = new Properties();
        properties.setProperty("world", built.world().asString());
        properties.setProperty("x", Integer.toString(built.center().x()));
        properties.setProperty("y", Integer.toString(built.center().y()));
        properties.setProperty("z", Integer.toString(built.center().z()));
        Files.createDirectories(this.file.getParent());
        final Path temp = this.file.resolveSibling("spawn-hub.properties.tmp");
        try (Writer writer = Files.newBufferedWriter(temp)) {
            properties.store(writer, "MYTHOS spawn hub");
        }
        try {
            Files.move(temp, this.file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(temp, this.file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static boolean isAir(final ServerLocation location) {
        final var type = location.block().type();
        return type == BlockTypes.AIR.get() || type == BlockTypes.CAVE_AIR.get() || type == BlockTypes.VOID_AIR.get();
    }
}
