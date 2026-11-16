package com.ksp.mythos.sponge;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.apache.logging.log4j.Logger;
import org.spongepowered.api.ResourceKey;
import org.spongepowered.api.command.Command;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.parameter.Parameter;
import org.spongepowered.api.entity.living.player.server.ServerPlayer;
import org.spongepowered.api.registry.RegistryTypes;
import org.spongepowered.api.world.server.ServerLocation;
import org.spongepowered.math.vector.Vector3i;

final class BlockPortals {
    private record Block(ResourceKey world, Vector3i position, ResourceKey type) {
        static Block at(final ServerLocation location) {
            return new Block(location.worldKey(), location.blockPosition(),
                    location.block().type().key(RegistryTypes.BLOCK_TYPE));
        }

        boolean samePosition(final Block other) {
            return this.world.equals(other.world) && this.position.equals(other.position);
        }
    }

    private record Portal(Block block, WorldTravel.Destination destination) {
    }

    private final Path file;
    private final WorldTravel travel;
    private final Logger logger;
    private final Map<String, Portal> links = new LinkedHashMap<>();
    private final Map<UUID, Map.Entry<String, WorldTravel.Destination>> selecting = new HashMap<>();

    BlockPortals(final Path file, final WorldTravel travel, final Logger logger) throws IOException {
        this.file = file;
        this.travel = travel;
        this.logger = logger;
        if (Files.notExists(file)) return;
        final Properties data = new Properties();
        try (var reader = Files.newBufferedReader(file)) {
            data.load(reader);
        }
        for (final String id : data.stringPropertyNames().stream().sorted().toList()) {
            final String[] parts = data.getProperty(id).split(" ", -1);
            try {
                if (!validId(id) || (parts.length != 6 && parts.length != 9)) throw new IllegalArgumentException();
                final var block = new Block(ResourceKey.resolve(parts[0]), position(parts, 1), ResourceKey.resolve(parts[4]));
                if (this.links.values().stream().anyMatch(portal -> portal.block().samePosition(block))) {
                    throw new IllegalArgumentException("Duplicate source block");
                }
                this.links.put(id, new Portal(block, new WorldTravel.Destination(ResourceKey.resolve(parts[5]),
                        parts.length == 9 ? position(parts, 6) : null)));
            } catch (RuntimeException exception) {
                throw new IOException("Invalid portal " + id + " in " + file, exception);
            }
        }
    }

    Command.Parameterized command() {
        final var name = Parameter.string().key("id").build();
        final var world = Parameter.string().key("world").build();
        final var x = Parameter.integerNumber().key("x").optional().build();
        final var y = Parameter.integerNumber().key("y").optional().build();
        final var z = Parameter.integerNumber().key("z").optional().build();
        final var link = Command.builder().addParameter(name).addParameter(world).addParameter(x).addParameter(y).addParameter(z)
                .executor(context -> {
                    if (!WorldTravel.canManage(context.cause())) return WorldTravel.denied();
                    final var player = context.cause().first(ServerPlayer.class);
                    if (player.isEmpty()) return WorldTravel.error("Run portal link in game to select a block.");
                    final String id = context.requireOne(name).toLowerCase(Locale.ROOT);
                    if (!validId(id)) return WorldTravel.error("Use an ID such as chapter_gate.");
                    final var destination = WorldTravel.parseKey(context.requireOne(world));
                    if (destination.isEmpty() || !WorldTravel.known(destination.get())) {
                        return WorldTravel.error("Unknown world. Use /mythosworld to list world keys.");
                    }
                    final var coordinates = List.of(context.one(x), context.one(y), context.one(z));
                    final long count = coordinates.stream().filter(java.util.Optional::isPresent).count();
                    if (count != 0 && count != 3) return WorldTravel.error("Supply all three arrival coordinates or none.");
                    final Vector3i arrival = count == 0 ? null
                            : new Vector3i(context.requireOne(x), context.requireOne(y), context.requireOne(z));
                    this.selecting.put(player.get().uniqueId(), Map.entry(id,
                            new WorldTravel.Destination(destination.get(), arrival)));
                    context.sendMessage(Component.text("Right-click the block for " + id + " with an empty main hand."));
                    return CommandResult.success();
                }).build();
        final var unlink = Command.builder().addParameter(name).executor(context -> {
            if (!WorldTravel.canManage(context.cause())) return WorldTravel.denied();
            final String id = context.requireOne(name).toLowerCase(Locale.ROOT);
            if (!this.links.containsKey(id)) return WorldTravel.error("Unknown portal " + id + ".");
            if (!save(id, null)) return WorldTravel.error("Could not save the portal. Check the server log.");
            context.sendMessage(Component.text("Removed portal " + id + "."));
            return CommandResult.success();
        }).build();
        final var cancel = Command.builder().executor(context -> {
            context.cause().first(ServerPlayer.class).ifPresent(player -> forget(player.uniqueId()));
            context.sendMessage(Component.text("Portal block selection cleared."));
            return CommandResult.success();
        }).build();
        return Command.builder().addChild(link, "link").addChild(unlink, "unlink").addChild(cancel, "cancel")
                .executor(context -> {
                    if (!WorldTravel.canManage(context.cause())) return WorldTravel.denied();
                    if (this.links.isEmpty()) context.sendMessage(Component.text("No portals are linked."));
                    this.links.forEach((id, portal) -> context.sendMessage(Component.text(id + " at "
                            + portal.block().world().asString() + " " + format(portal.block().position())
                            + " → " + portal.destination().description())));
                    return CommandResult.success();
                }).build();
    }

    boolean interact(final ServerPlayer player, final ServerLocation location) {
        final var selection = this.selecting.remove(player.uniqueId());
        if (selection != null && player.hasPermission(WorldTravel.MANAGE)) {
            final String id = selection.getKey();
            final Block target = Block.at(location);
            if (this.links.entrySet().stream().anyMatch(entry -> !entry.getKey().equals(id)
                    && entry.getValue().block().samePosition(target))) {
                player.sendMessage(Component.text("That block is already linked to another portal."));
            } else if (save(id, new Portal(target, selection.getValue()))) {
                player.sendMessage(Component.text("Linked " + id + " to " + selection.getValue().description() + "."));
            } else {
                player.sendMessage(Component.text("Could not save the portal. Check the server log."));
            }
            return true;
        }

        final var portal = this.links.values().stream().filter(link -> link.block().world().equals(location.worldKey())
                && link.block().position().equals(location.blockPosition())).findFirst();
        if (portal.isEmpty() || !portal.get().block().equals(Block.at(location))) return false;
        this.travel.teleport(player, portal.get().destination(), player::sendMessage);
        return true;
    }

    void forget(final UUID id) {
        this.selecting.remove(id);
    }

    private boolean save(final String id, final Portal portal) {
        final Map<String, Portal> updated = new LinkedHashMap<>(this.links);
        if (portal == null) updated.remove(id);
        else updated.put(id, portal);
        final Properties data = new Properties();
        updated.forEach((name, link) -> data.setProperty(name, link.block().world().asString() + " "
                + format(link.block().position()) + " " + link.block().type().asString() + " "
                + link.destination().world().asString()
                + (link.destination().position() == null ? "" : " " + format(link.destination().position()))));
        try {
            Files.createDirectories(this.file.getParent());
            final Path temp = this.file.resolveSibling(this.file.getFileName() + ".tmp");
            try (var writer = Files.newBufferedWriter(temp)) {
                data.store(writer, "MYTHOS portals");
            }
            try {
                Files.move(temp, this.file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temp, this.file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            this.logger.error("Could not save MYTHOS portal {}", id, exception);
            return false;
        }
        this.links.clear();
        this.links.putAll(updated);
        return true;
    }

    private static boolean validId(final String id) {
        return id.matches("[a-z][a-z0-9_]{0,31}");
    }

    private static Vector3i position(final String[] values, final int offset) {
        return new Vector3i(Integer.parseInt(values[offset]), Integer.parseInt(values[offset + 1]),
                Integer.parseInt(values[offset + 2]));
    }

    private static String format(final Vector3i pos) {
        return pos.x() + " " + pos.y() + " " + pos.z();
    }
}
