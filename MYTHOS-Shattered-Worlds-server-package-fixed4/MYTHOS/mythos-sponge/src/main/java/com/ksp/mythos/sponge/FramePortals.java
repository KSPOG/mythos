package com.ksp.mythos.sponge;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import org.apache.logging.log4j.Logger;
import org.spongepowered.api.ResourceKey;
import org.spongepowered.api.command.Command;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.parameter.Parameter;
import org.spongepowered.api.entity.living.player.server.ServerPlayer;
import org.spongepowered.api.world.server.ServerLocation;
import org.spongepowered.math.vector.Vector3i;

final class FramePortals {
    private record Frame(ResourceKey world, Vector3i low, Vector3i high, String peer, Vector3i arrival) {
        static Frame between(final ResourceKey world, final Vector3i a, final Vector3i b) {
            final Vector3i low = new Vector3i(Math.min(a.x(), b.x()), Math.min(a.y(), b.y()), Math.min(a.z(), b.z()));
            final Vector3i high = new Vector3i(Math.max(a.x(), b.x()), Math.max(a.y(), b.y()), Math.max(a.z(), b.z()));
            final long dx = (long) high.x() - low.x(), dy = (long) high.y() - low.y(), dz = (long) high.z() - low.z();
            final long width = Math.max(dx, dz);
            if ((dx == 0) == (dz == 0) || width < 2 || width > 32 || dy < 3 || dy > 32) {
                throw new IllegalArgumentException("Select diagonal outer corners of a vertical frame with a walkable opening (maximum 33 blocks across or tall).");
            }
            return new Frame(world, low, high, null, null);
        }

        Frame link(final String name) {
            return new Frame(this.world, this.low, this.high, name, this.arrival);
        }

        Frame withArrival(final Vector3i position) {
            if (position != null && Math.abs((long) depth(position) - depth(this.low)) < 2) {
                throw new IllegalArgumentException("Stand at least two blocks out from the frame, on the side players should use.");
            }
            return new Frame(this.world, this.low, this.high, this.peer, position);
        }

        private int depth(final Vector3i position) {
            return this.low.z() == this.high.z() ? position.z() : position.x();
        }

        boolean contains(final ServerLocation location) {
            if (!this.world.equals(location.worldKey())) return false;
            final var pos = location.blockPosition();
            final boolean alongX = this.low.z() == this.high.z();
            final int across = alongX ? pos.x() : pos.z();
            if (across <= (alongX ? this.low.x() : this.low.z())
                    || across >= (alongX ? this.high.x() : this.high.z())
                    || pos.y() >= this.high.y()) return false;

            final long offset = (long) depth(pos) - depth(this.low);
            if (this.arrival == null) return offset == 0 && pos.y() > this.low.y();
            final int front = Integer.compare(depth(this.arrival), depth(this.low));
            // A backed gate can stop the player's body a block before the framed plane.
            return pos.y() >= this.low.y() && (offset == 0 || offset == front);
        }

        boolean overlaps(final Frame other) {
            return this.world.equals(other.world)
                    && this.low.x() <= other.high.x() && this.high.x() >= other.low.x()
                    && this.low.y() <= other.high.y() && this.high.y() >= other.low.y()
                    && this.low.z() <= other.high.z() && this.high.z() >= other.low.z();
        }

        WorldTravel.Destination exit() {
            if (this.arrival != null) return new WorldTravel.Destination(this.world, this.arrival);
            final int x = this.low.x() + (this.high.x() - this.low.x()) / 2;
            final int z = this.low.z() + (this.high.z() - this.low.z()) / 2;
            return new WorldTravel.Destination(this.world, new Vector3i(x + (this.low.x() == this.high.x() ? 1 : 0),
                    this.low.y() + 1, z + (this.low.z() == this.high.z() ? 1 : 0)));
        }
    }

    private record Selection(String name, ServerLocation first) {
    }

    private final Path file;
    private final WorldTravel travel;
    private final Logger logger;
    private final Map<String, Frame> frames = new LinkedHashMap<>();
    private final Map<UUID, Selection> selecting = new HashMap<>();
    private final Map<UUID, Frame> occupied = new HashMap<>();

    FramePortals(final Path file, final WorldTravel travel, final Logger logger) throws IOException {
        this.file = file;
        this.travel = travel;
        this.logger = logger;
        if (Files.notExists(file)) return;
        final Properties data = new Properties();
        try (var reader = Files.newBufferedReader(file)) {
            data.load(reader);
        }
        for (final String id : data.stringPropertyNames().stream().sorted().toList()) {
            try {
                final String[] parts = data.getProperty(id).split(" ", -1);
                if (!validId(id) || (parts.length != 8 && parts.length != 11)) throw new IllegalArgumentException();
                final Frame frame = Frame.between(ResourceKey.resolve(parts[0]), position(parts, 1), position(parts, 4));
                if (this.frames.values().stream().anyMatch(frame::overlaps)) throw new IllegalArgumentException("Overlapping frames");
                this.frames.put(id, frame.link(parts[7].equals("-") ? null : parts[7])
                        .withArrival(parts.length == 11 ? position(parts, 8) : null));
            } catch (RuntimeException exception) {
                throw new IOException("Invalid portal " + id + " in " + file, exception);
            }
        }
        for (final var entry : this.frames.entrySet()) {
            final String peer = entry.getValue().peer();
            if (peer != null && (peer.equals(entry.getKey()) || !this.frames.containsKey(peer)
                    || !entry.getKey().equals(this.frames.get(peer).peer()))) {
                throw new IOException("Invalid portal pair for " + entry.getKey() + " in " + file);
            }
        }
    }

    void preloadWorlds() {
        this.frames.values().stream().filter(frame -> frame.peer() != null)
                .map(Frame::world).distinct().forEach(this.travel::preload);
    }

    Command.Parameterized command() {
        final var arguments = Parameter.remainingJoinedStrings().key("arguments").optional().build();
        return Command.builder().addParameter(arguments).executor(context -> {
            if (!WorldTravel.canManage(context.cause())) return WorldTravel.denied();
            final String input = context.one(arguments).orElse("").strip();
            if (input.isEmpty() || input.equalsIgnoreCase("list")) {
                if (this.frames.isEmpty()) context.sendMessage(Component.text("No portal frames defined."));
                this.frames.forEach((id, frame) -> context.sendMessage(Component.text(id + " at "
                        + frame.world().asString() + " " + format(frame.low()) + " → " + format(frame.high())
                        + (frame.peer() == null ? " (unlinked)" : " — paired with " + frame.peer())
                        + (frame.arrival() == null ? "" : " | arrival " + format(frame.arrival())))));
                return CommandResult.success();
            }
            final String[] words = normalize(input).split("\\s+");
            final var player = context.cause().first(ServerPlayer.class);
            final String action = words[0];
            final int expected = switch (action) {
                case "define", "setarrival", "unlink", "remove" -> 2;
                case "link" -> 3;
                case "cancel" -> 1;
                default -> 0;
            };
            if (words.length != expected) {
                return WorldTravel.error("Usage: /mythosportal [define <frame> | link <frame> <frame> | setarrival <frame> | unlink <frame> | remove <frame> | cancel]");
            }
            return switch (action) {
                case "define" -> define(words[1], player.orElse(null), context::sendMessage);
                case "setarrival" -> setArrival(words[1], player.orElse(null), context::sendMessage);
                case "link" -> pair(words[1], words[2], context::sendMessage);
                case "unlink", "remove" -> disconnect(words[1], action.equals("remove"), context::sendMessage);
                case "cancel" -> {
                    player.ifPresent(user -> this.selecting.remove(user.uniqueId()));
                    context.sendMessage(Component.text("Portal frame selection cleared."));
                    yield CommandResult.success();
                }
                default -> throw new IllegalStateException("Unrecognized portal action " + action);
            };
        }).build();
    }

    private CommandResult define(final String id, final ServerPlayer player, final Consumer<Component> reply) {
        if (player == null) return WorldTravel.error("Define a portal frame in game.");
        if (!validId(id)) return WorldTravel.error("Use an ID such as chapter_gate.");
        this.selecting.put(player.uniqueId(), new Selection(id, null));
        reply.accept(Component.text("With an empty main hand, right-click a lower outer corner and the opposite upper outer corner."));
        return CommandResult.success();
    }

    private CommandResult setArrival(final String id, final ServerPlayer player, final Consumer<Component> reply) {
        if (player == null) return WorldTravel.error("Set a portal arrival in game.");
        final Frame frame = this.frames.get(id);
        if (frame == null) return WorldTravel.error("Unknown frame " + id + ".");
        final ServerLocation location = player.serverLocation();
        if (!frame.world().equals(location.worldKey())) {
            return WorldTravel.error("Stand in the same world as frame " + id + ".");
        }
        final Frame updatedFrame;
        try {
            updatedFrame = frame.withArrival(location.blockPosition());
        } catch (IllegalArgumentException exception) {
            return WorldTravel.error(exception.getMessage());
        }
        if (this.frames.values().stream().anyMatch(other -> other.contains(location))) {
            return WorldTravel.error("Choose an arrival spot outside other portal openings.");
        }

        final var updated = new LinkedHashMap<>(this.frames);
        updated.put(id, updatedFrame);
        if (!save(updated)) return saveError();
        reply.accept(Component.text("Saved " + id + " arrival and entrance side at "
                + format(updatedFrame.arrival()) + "."));
        return CommandResult.success();
    }

    private CommandResult pair(final String from, final String to, final Consumer<Component> reply) {
        if (from.equals(to) || !this.frames.containsKey(from) || !this.frames.containsKey(to)) {
            return WorldTravel.error("Choose two different defined portal frames.");
        }
        final var updated = new LinkedHashMap<>(this.frames);
        unlink(updated, from);
        unlink(updated, to);
        updated.put(from, updated.get(from).link(to));
        updated.put(to, updated.get(to).link(from));
        if (!save(updated)) return saveError();
        preloadWorlds();
        reply.accept(Component.text("Paired " + from + " ↔ " + to + ". Walk through either opening."));
        return CommandResult.success();
    }

    private CommandResult disconnect(final String id, final boolean remove, final Consumer<Component> reply) {
        if (!this.frames.containsKey(id)) return WorldTravel.error("Unknown frame " + id + ".");
        final var updated = new LinkedHashMap<>(this.frames);
        unlink(updated, id);
        if (remove) updated.remove(id);
        if (!save(updated)) return saveError();
        reply.accept(Component.text((remove ? "Removed frame " : "Unlinked frame ") + id + "."));
        return CommandResult.success();
    }

    boolean select(final ServerPlayer player, final ServerLocation block) {
        final UUID id = player.uniqueId();
        final Selection selection = this.selecting.get(id);
        if (selection == null) return false;
        if (!player.hasPermission(WorldTravel.MANAGE)) {
            this.selecting.remove(id);
            return false;
        }
        if (selection.first() == null) {
            this.selecting.put(id, new Selection(selection.name(), block));
            player.sendMessage(Component.text("First corner saved. Right-click the opposite outer corner."));
            return true;
        }
        if (!selection.first().worldKey().equals(block.worldKey())) {
            player.sendMessage(Component.text("Both corners must be in the same world."));
            return true;
        }
        try {
            final Frame frame = Frame.between(block.worldKey(), selection.first().blockPosition(), block.blockPosition());
            if (this.frames.entrySet().stream().anyMatch(entry -> !entry.getKey().equals(selection.name())
                    && entry.getValue().overlaps(frame))) {
                player.sendMessage(Component.text("That frame overlaps another portal."));
                return true;
            }
            final var updated = new LinkedHashMap<>(this.frames);
            final Frame old = updated.get(selection.name());
            final Frame linked = frame.link(old == null ? null : old.peer());
            updated.put(selection.name(), old != null && old.world().equals(frame.world())
                    && old.low().equals(frame.low()) && old.high().equals(frame.high())
                    ? linked.withArrival(old.arrival()) : linked);
            if (save(updated)) {
                this.selecting.remove(id);
                if (old != null && old.peer() != null) preloadWorlds();
                player.sendMessage(Component.text("Saved frame " + selection.name() + "."));
            } else {
                saveError().errorMessage().ifPresent(player::sendMessage);
            }
        } catch (IllegalArgumentException exception) {
            player.sendMessage(Component.text(exception.getMessage()));
        }
        return true;
    }

    void check(final ServerPlayer player) {
        final UUID id = player.uniqueId();
        final ServerLocation location = player.serverLocation();
        final Frame previous = this.occupied.get(id);
        if (previous != null && previous.contains(location) && this.frames.containsValue(previous)) return;
        this.occupied.remove(id);
        for (final var entry : this.frames.entrySet()) {
            final Frame source = entry.getValue();
            if (!source.contains(location)) continue;
            this.occupied.put(id, source);
            final Frame peer = this.frames.get(source.peer());
            if (peer == null) return;
            final String name = entry.getKey();
            final int search = peer.arrival() == null ? 2 : 0;
            this.travel.teleport(player, peer.exit(), player::sendMessage,
                    current -> source.contains(current) && source.equals(this.frames.get(name))
                            && peer.equals(this.frames.get(source.peer())),
                    arrival -> this.frames.values().stream().noneMatch(frame -> frame.contains(arrival)),
                    search, search, false);
            return;
        }
    }

    void forget(final UUID id) {
        this.selecting.remove(id);
        this.occupied.remove(id);
    }

    private static void unlink(final Map<String, Frame> frames, final String id) {
        final Frame frame = frames.get(id);
        if (frame.peer() != null) frames.put(frame.peer(), frames.get(frame.peer()).link(null));
        frames.put(id, frame.link(null));
    }

    private boolean save(final Map<String, Frame> updated) {
        final Properties data = new Properties();
        updated.forEach((name, frame) -> data.setProperty(name, frame.world().asString() + " " + format(frame.low())
                + " " + format(frame.high()) + " " + (frame.peer() == null ? "-" : frame.peer())
                + (frame.arrival() == null ? "" : " " + format(frame.arrival()))));
        try {
            Files.createDirectories(this.file.getParent());
            final Path temp = this.file.resolveSibling(this.file.getFileName() + ".tmp");
            try (var writer = Files.newBufferedWriter(temp)) {
                data.store(writer, "MYTHOS portal frames");
            }
            try {
                Files.move(temp, this.file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temp, this.file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            this.logger.error("Could not save MYTHOS portal frames", exception);
            return false;
        }
        this.frames.clear();
        this.frames.putAll(updated);
        return true;
    }

    private static CommandResult saveError() {
        return WorldTravel.error("Could not save the portal frames. Check the server log.");
    }

    private static String normalize(final String value) {
        return value.toLowerCase(java.util.Locale.ROOT);
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
