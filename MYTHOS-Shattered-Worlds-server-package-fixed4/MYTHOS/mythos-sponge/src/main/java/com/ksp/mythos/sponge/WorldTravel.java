package com.ksp.mythos.sponge;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.kyori.adventure.text.Component;
import org.apache.logging.log4j.Logger;
import org.spongepowered.api.ResourceKey;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.Command;
import org.spongepowered.api.command.CommandCause;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.parameter.Parameter;
import org.spongepowered.api.data.Keys;
import org.spongepowered.api.entity.living.player.server.ServerPlayer;
import org.spongepowered.api.scheduler.Task;
import org.spongepowered.api.world.border.WorldBorder;
import org.spongepowered.api.world.server.ServerLocation;
import org.spongepowered.api.world.server.ServerWorld;
import org.spongepowered.api.world.server.WorldTemplate;
import org.spongepowered.api.world.teleport.TeleportHelperFilters;
import org.spongepowered.api.world.teleport.TeleportHelperFilter;
import org.spongepowered.api.util.Tristate;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.math.vector.Vector3i;
import org.spongepowered.plugin.PluginContainer;

final class WorldTravel {
    static final String MANAGE = "mythos.command.worldmanage";

    record Destination(ResourceKey world, Vector3i position) {
        String description() {
            return this.world.asString() + (this.position == null ? " (world spawn)"
                    : " " + this.position.x() + " " + this.position.y() + " " + this.position.z());
        }
    }

    private final PluginContainer plugin;
    private final Logger logger;
    private final Map<ResourceKey, CompletableFuture<ServerWorld>> loading = new HashMap<>();
    private final Map<UUID, ServerPlayer> teleporting = new HashMap<>();

    WorldTravel(final PluginContainer plugin, final Logger logger) {
        this.plugin = plugin;
        this.logger = logger;
    }

    Command.Parameterized worldsCommand() {
        final var argument = Parameter.string().key("world").build();
        return Command.builder()
                .addChild(Command.builder().addParameter(argument).executor(context -> {
                    if (!canManage(context.cause())) return denied();
                    final String id = context.requireOne(argument);
                    if (!id.matches("[a-z][a-z0-9_]{0,31}")) {
                        return error("Use a short world ID such as chapter_one.");
                    }
                    final ResourceKey key = ResourceKey.of("mythos", id);
                    prepare(key, true, context::sendMessage);
                    return CommandResult.success();
                }).build(), "create")
                .addChild(Command.builder().addParameter(argument).executor(context -> {
                    if (!canManage(context.cause())) return denied();
                    final var key = parseKey(context.requireOne(argument));
                    if (key.isEmpty() || !known(key.get())) return error("Unknown world. Use /mythosworld to list world keys.");
                    prepare(key.get(), false, context::sendMessage);
                    return CommandResult.success();
                }).build(), "load")
                .executor(context -> {
                    final var worlds = Sponge.server().worldManager();
                    final var keys = new java.util.TreeSet<String>();
                    worlds.worldKeys().forEach(key -> keys.add(key.asString()));
                    worlds.worlds().forEach(world -> keys.add(world.key().asString()));
                    context.sendMessage(Component.text("Worlds: " + String.join(", ", keys)));
                    return CommandResult.success();
                }).build();
    }

    Command.Parameterized teleportCommand() {
        final var world = Parameter.string().key("world").build();
        final var player = Parameter.player().key("player").optional().build();
        return Command.builder().addParameter(world).addParameter(player).executor(context -> {
            if (!canManage(context.cause())) return denied();
            final var key = parseKey(context.requireOne(world));
            if (key.isEmpty() || !known(key.get())) return error("Unknown world. Use /mythosworld to list world keys.");
            final var recipient = context.one(player).or(() -> context.cause().first(ServerPlayer.class));
            if (recipient.isEmpty()) return error("Console usage: mythosworldtp <world> <online-player>");
            teleport(recipient.get(), new Destination(key.get(), null), context::sendMessage);
            return CommandResult.success();
        }).build();
    }

    private void prepare(final ResourceKey key, final boolean create, final Consumer<Component> reply) {
        reply.accept(Component.text("Preparing " + key.asString() + "…"));
        load(key, create).whenComplete((world, failure) -> onServer(() -> {
            if (failure != null) {
                this.logger.error("Could not prepare world {}", key, failure);
                reply.accept(Component.text("World preparation failed. Check the server log."));
            } else {
                reply.accept(Component.text("World ready: " + key.asString()));
            }
        }));
    }

    void teleport(final ServerPlayer player, final Destination target, final Consumer<Component> reply) {
        final var start = player.serverLocation();
        teleport(player, target, reply, current -> current.worldKey().equals(start.worldKey())
                && current.position().distanceSquared(start.position()) <= 16, arrival -> true, 16, 8, true);
    }

    void teleport(final ServerPlayer player, final Destination target, final Consumer<Component> reply,
                  final Predicate<ServerLocation> stillValid, final Predicate<ServerLocation> validArrival,
                  final int height, final int radius, final boolean announce) {
        final UUID id = player.uniqueId();
        if (this.teleporting.putIfAbsent(id, player) != null) {
            reply.accept(Component.text("A world teleport is already in progress."));
            return;
        }
        final var future = load(target.world(), false);
        final boolean ready = future.isDone();
        future.whenComplete((world, failure) -> {
            final Runnable finish = () -> {
                if (!this.teleporting.remove(id, player)) return;
                if (Sponge.server().player(id).orElse(null) != player) return;
                if (failure != null) {
                    this.logger.warn("Could not load teleport destination {}", target.world(), failure);
                    reply.accept(Component.text("That world is unavailable."));
                    return;
                }
                if (!stillValid.test(player.serverLocation())) {
                    if (announce) reply.accept(Component.text("Teleport cancelled because you moved away."));
                    return;
                }
                final Vector3i pos = target.position() == null ? world.properties().spawnPosition() : target.position();
                final var destination = ServerLocation.of(world, pos.x() + 0.5, pos.y(), pos.z() + 0.5);
                final var arrivalFilter = new TeleportHelperFilter() {
                    @Override
                    public Tristate isValidLocation(final ServerWorld candidateWorld, final Vector3i candidate) {
                        final var location = ServerLocation.of(candidateWorld, candidate.x() + 0.5,
                                candidate.y(), candidate.z() + 0.5);
                        return validArrival.test(location) && insideBorder(candidateWorld, location)
                                ? Tristate.TRUE : Tristate.FALSE;
                    }

                    @Override
                    public boolean isSafeFloorMaterial(final BlockState state) {
                        return true;
                    }

                    @Override
                    public boolean isSafeBodyMaterial(final BlockState state) {
                        return true;
                    }
                };
                final var safe = Sponge.server().teleportHelper().findSafeLocation(destination, height, radius, 2,
                        TeleportHelperFilters.DEFAULT.get(), TeleportHelperFilters.NO_PORTAL.get(), arrivalFilter);
                if (safe.isEmpty() || !player.setLocation(safe.get())) {
                    reply.accept(Component.text("No safe arrival was available at " + target.description() + "."));
                    return;
                }
                if (announce) reply.accept(Component.text("Arrived in " + target.world().asString() + "."));
            };
            if (ready) finish.run();
            else onServer(finish);
        });
    }

    void preload(final ResourceKey key) {
        load(key, false).exceptionally(error -> {
            this.logger.warn("Could not preload portal world {}", key, error);
            return null;
        });
    }

    void forget(final UUID id) {
        this.teleporting.remove(id);
    }

    private static boolean insideBorder(final ServerWorld world, final ServerLocation location) {
        final WorldBorder border = world.properties().worldBorder();
        final var center = border.center();
        final var pos = location.position();
        final double radius = border.diameter() / 2.0;
        return Math.abs(pos.x() - center.x()) < radius && Math.abs(pos.z() - center.y()) < radius;
    }

    private CompletableFuture<ServerWorld> load(final ResourceKey key, final boolean create) {
        final var worlds = Sponge.server().worldManager();
        final var pending = this.loading.get(key);
        if (pending != null) return pending;
        final var loaded = worlds.world(key);
        if (loaded.isPresent()) return CompletableFuture.completedFuture(loaded.get());
        try {
            final var future = create && !worlds.worldExists(key)
                    ? worlds.loadWorld(WorldTemplate.builder().key(key).add(Keys.IS_LOAD_ON_STARTUP, true).build())
                    : worlds.loadWorld(key);
            this.loading.put(key, future);
            future.whenComplete((world, failure) -> onServer(() -> this.loading.remove(key, future)));
            return future;
        } catch (RuntimeException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    private void onServer(final Runnable task) {
        Sponge.server().scheduler().submit(Task.builder().plugin(this.plugin).execute(task).build());
    }

    static boolean known(final ResourceKey key) {
        final var worlds = Sponge.server().worldManager();
        return worlds.world(key).isPresent() || worlds.worldExists(key) || worlds.worldKeys().contains(key);
    }

    static Optional<ResourceKey> parseKey(final String value) {
        if (!value.matches("[a-z0-9_][a-z0-9_.-]*:[a-z0-9_][a-z0-9_./-]*")
                || value.contains("..") || value.contains("//") || value.endsWith("/")) {
            return Optional.empty();
        }
        return Optional.of(ResourceKey.resolve(value));
    }

    static boolean canManage(final CommandCause cause) {
        return cause.subject() == Sponge.systemSubject() || cause.hasPermission(MANAGE);
    }

    static CommandResult denied() {
        return error("You need the MYTHOS Owner world management permission.");
    }

    static CommandResult error(final String message) {
        return CommandResult.error(Component.text(message));
    }
}
