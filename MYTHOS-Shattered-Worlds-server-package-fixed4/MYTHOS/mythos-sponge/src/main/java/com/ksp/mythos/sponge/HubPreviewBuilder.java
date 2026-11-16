package com.ksp.mythos.sponge;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;
import java.util.zip.GZIPInputStream;
import net.kyori.adventure.text.Component;
import org.apache.logging.log4j.Logger;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.command.Command;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.parameter.Parameter;
import org.spongepowered.api.entity.living.player.server.ServerPlayer;
import org.spongepowered.api.scheduler.ScheduledTask;
import org.spongepowered.api.registry.RegistryTypes;
import org.spongepowered.api.scheduler.Task;
import org.spongepowered.api.util.Ticks;
import org.spongepowered.api.world.BlockChangeFlags;
import org.spongepowered.api.world.server.ServerWorld;
import org.spongepowered.math.vector.Vector3i;
import org.spongepowered.plugin.PluginContainer;

final class HubPreviewBuilder {
    private static final int BATCH_SIZE = 2048;
    private static final long BATCH_NANOS = 8_000_000;
    private enum Stage { CHECK, PLACE, VERIFY }

    private record Block(Vector3i offset, BlockState state, String specification) {
        boolean matches(final BlockState current) {
            if (!current.type().equals(this.state.type())) return false;
            // Fence connections, stair shape, and leaf distance can change after a neighbor is placed.
            final int propertiesStart = this.specification.indexOf('[');
            if (propertiesStart < 0) return true;
            final String serialized = current.asString();
            final int start = serialized.indexOf('[');
            if (start < 0) return false;
            final var actual = List.of(serialized.substring(start + 1, serialized.length() - 1).split(","));
            for (final String property : this.specification.substring(propertiesStart + 1,
                    this.specification.length() - 1).split(",")) {
                if (!actual.contains(property)) return false;
            }
            return true;
        }
    }

    private final PluginContainer plugin;
    private final Logger logger;
    private List<Block> blueprint;
    private ScheduledTask active;

    HubPreviewBuilder(final PluginContainer plugin, final Logger logger) {
        this.plugin = plugin;
        this.logger = logger;
    }

    Command.Parameterized command() {
        final var arguments = Parameter.remainingJoinedStrings().key("site").optional().build();
        return Command.builder().addParameter(arguments).executor(context -> {
            if (!WorldTravel.canManage(context.cause())
                    && !context.cause().hasPermission(SpawnHub.MANAGE)) {
                return WorldTravel.error("You need the MYTHOS Owner hub build permission.");
            }
            if (this.active != null) return WorldTravel.error("A hub preview build is already running.");

            final String input = context.one(arguments).orElse("").strip();
            final ServerWorld world;
            final Vector3i anchor;
            if (input.isEmpty()) {
                final var player = context.cause().first(ServerPlayer.class);
                if (player.isEmpty()) return WorldTravel.error("Console usage: buildhub <loaded-world> <x> <y> <z>");
                world = player.get().world();
                anchor = player.get().serverLocation().blockPosition().sub(0, 1, 0);
            } else {
                final String[] parts = input.split("\\s+");
                if (parts.length != 4) return WorldTravel.error("Usage: /buildhub [<loaded-world> <x> <y> <z>]");
                final var target = WorldTravel.parseKey(parts[0]).flatMap(Sponge.server().worldManager()::world);
                if (target.isEmpty()) return WorldTravel.error("World is not loaded. Use /mythosworld load first.");
                world = target.get();
                try {
                    anchor = new Vector3i(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
                } catch (NumberFormatException exception) {
                    return WorldTravel.error("Use whole block coordinates for the anchor.");
                }
            }

            // Validate using long arithmetic before adding offsets to user coordinates.
            final var min = world.min();
            final var max = world.max();
            final var border = world.properties().worldBorder();
            final var center = border.center();
            final double radius = border.diameter() / 2;
            if ((long) anchor.x() - 68 < min.x() || (long) anchor.x() + 70 > max.x()
                    || (long) anchor.y() - 32 < min.y() || (long) anchor.y() + 48 > max.y()
                    || (long) anchor.z() - 56 < min.z() || (long) anchor.z() + 71 > max.z()
                    || anchor.x() - 68.0 < center.x() - radius || anchor.x() + 71.0 > center.x() + radius
                    || anchor.z() - 56.0 < center.y() - radius || anchor.z() + 72.0 > center.y() + radius) {
                return WorldTravel.error("The Skyreach footprint would extend outside the world bounds or border.");
            }

            try {
                if (this.blueprint == null) this.blueprint = loadBlueprint();
            } catch (IOException | IllegalArgumentException exception) {
                this.logger.error("Could not load the Skyreach blueprint", exception);
                return WorldTravel.error("The Skyreach blueprint could not be loaded. See the server log.");
            }
            final var build = new Placement(world, anchor, context::sendMessage);
            this.active = Sponge.server().scheduler().submit(Task.builder().plugin(this.plugin)
                    .interval(Ticks.of(1)).execute(build::tick).build(), "MYTHOS Skyreach preview");
            build.report("Checking " + this.blueprint.size() + " blocks in " + world.key().asString()
                    + " at " + coordinates(anchor) + ".");
            return CommandResult.success();
        }).build();
    }

    private final class Placement {
        private final ServerWorld world;
        private final Vector3i anchor;
        private final Consumer<Component> reply;
        private int next;
        private int progress = 25;
        private Stage stage = Stage.CHECK;

        Placement(final ServerWorld world, final Vector3i anchor, final Consumer<Component> reply) {
            this.world = world;
            this.anchor = anchor;
            this.reply = reply;
        }

        void tick(final ScheduledTask task) {
            try {
                if (!this.world.isLoaded()) {
                    stop(task, "Stopped because the destination world was unloaded.");
                    return;
                }

                final long start = System.nanoTime();
                for (int count = 0; count < BATCH_SIZE && this.next < blueprint.size(); count++) {
                    final Block block = blueprint.get(this.next);
                    final Vector3i pos = this.anchor.add(block.offset());
                    final BlockState current = this.world.block(pos);
                    if (!block.matches(current)) {
                        if (this.stage == Stage.VERIFY || !isAir(current)) {
                            stop(task, mismatch(pos, block, current, this.stage == Stage.CHECK
                                    ? "Site check blocked; no preview blocks were placed"
                                    : "Build incomplete; previously placed blocks were kept"));
                            return;
                        }
                        if (this.stage == Stage.PLACE) {
                            this.world.setBlock(pos, block.state(), BlockChangeFlags.NOTIFY_CLIENTS);
                            final var actual = this.world.block(pos);
                            if (!block.matches(actual)) {
                                stop(task, mismatch(pos, block, actual, "Block placement did not stick"));
                                return;
                            }
                        }
                    }
                    this.next++;
                    if (System.nanoTime() - start >= BATCH_NANOS) break;
                }
                final int percent = this.next * 100 / blueprint.size();
                if (percent >= this.progress && percent < 100) {
                    final String step = switch (this.stage) {
                        case CHECK -> "Site check";
                        case PLACE -> "Placement";
                        case VERIFY -> "Verification";
                    };
                    report(step + " " + percent + "%.");
                    this.progress += 25;
                }
                if (this.next < blueprint.size()) return;
                if (this.stage != Stage.VERIFY) {
                    this.stage = this.stage == Stage.CHECK ? Stage.PLACE : Stage.VERIFY;
                    this.next = 0;
                    this.progress = 25;
                    report(this.stage == Stage.PLACE ? "Site check complete. Placing Skyreach."
                            : "Placement finished. Verifying every planned block.");
                    return;
                }
                final var arrival = this.anchor.add(0, 1, 11);
                stop(task, "Complete. Preview arrival: " + coordinates(arrival) + " in " + this.world.key().asString() + ".");
            } catch (RuntimeException exception) {
                logger.error("Skyreach build stopped at block {}", this.next, exception);
                stop(task, "Build stopped. Check the server log for details.");
            }
        }

        private String mismatch(final Vector3i pos, final Block block, final BlockState actual, final String detail) {
            return detail + " at " + coordinates(pos) + " in " + this.world.key().asString()
                    + ". Expected " + block.specification() + ", found " + actual.type().key(RegistryTypes.BLOCK_TYPE).asString()
                    + " (" + actual.asString() + ").";
        }

        void report(final String message) {
            logger.info("[Skyreach] {}", message);
            this.reply.accept(Component.text("[Skyreach] " + message));
        }

        void stop(final ScheduledTask task, final String message) {
            task.cancel();
            active = null;
            report(message);
        }
    }

    private static boolean isAir(final BlockState state) {
        return state.type() == BlockTypes.AIR.get() || state.type() == BlockTypes.CAVE_AIR.get()
                || state.type() == BlockTypes.VOID_AIR.get();
    }

    private static String coordinates(final Vector3i position) {
        return position.x() + " " + position.y() + " " + position.z();
    }

    private static List<Block> loadBlueprint() throws IOException {
        final var stream = HubPreviewBuilder.class.getResourceAsStream("/skyreach-v2.mcfunction.gz");
        if (stream == null) throw new IOException("Missing Skyreach blueprint");
        final var blocks = new ArrayList<Block>();
        final var palette = new HashMap<String, BlockState>();
        try (stream; var reader = new BufferedReader(new InputStreamReader(new GZIPInputStream(stream), StandardCharsets.UTF_8))) {
            for (String line; (line = reader.readLine()) != null;) {
                final String[] words = line.split(" ");
                final int start = offset(words[1]);
                final int y = offset(words[2]);
                final int z = offset(words[3]);
                final int end = words[0].equals("fill") ? offset(words[4]) : start;
                final var state = palette.computeIfAbsent(words[words.length - 1], BlockState::fromString);
                for (int x = start; x <= end; x++) {
                    blocks.add(new Block(new Vector3i(x, y, z), state, words[words.length - 1]));
                }
            }
        }
        if (blocks.size() != 122316) throw new IOException("Unexpected Skyreach blueprint size: " + blocks.size());
        return List.copyOf(blocks);
    }

    private static int offset(final String token) {
        return token.length() == 1 ? 0 : Integer.parseInt(token.substring(1));
    }
}
