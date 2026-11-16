package com.ksp.mythos.sponge;

import com.google.inject.Inject;
import com.ksp.mythos.core.character.CharacterClass;
import com.ksp.mythos.core.combat.WeaponPolicy;
import com.ksp.mythos.core.weapon.WeaponCatalog;
import com.ksp.mythos.core.weapon.WeaponCategory;
import com.ksp.mythos.core.weapon.WeaponDefinition;
import net.kyori.adventure.text.Component;
import org.apache.logging.log4j.Logger;
import org.spongepowered.api.Server;
import org.spongepowered.api.adventure.ChatTypes;
import org.spongepowered.api.command.Command;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.parameter.Parameter;
import org.spongepowered.api.data.Keys;
import org.spongepowered.api.data.type.HandTypes;
import org.spongepowered.api.entity.living.Hostile;
import org.spongepowered.api.entity.living.player.server.ServerPlayer;
import org.spongepowered.api.event.EventContextKeys;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.block.InteractBlockEvent;
import org.spongepowered.api.event.cause.entity.damage.source.DamageSource;
import org.spongepowered.api.event.entity.AttackEntityEvent;
import org.spongepowered.api.event.entity.InteractEntityEvent;
import org.spongepowered.api.event.item.inventory.InteractItemEvent;
import org.spongepowered.api.event.item.inventory.UseItemStackEvent;
import org.spongepowered.api.event.lifecycle.RegisterCommandEvent;
import org.spongepowered.api.event.lifecycle.StartedEngineEvent;
import org.spongepowered.api.event.message.PlayerChatEvent;
import org.spongepowered.api.item.inventory.ItemStack;
import org.spongepowered.api.item.inventory.ItemStackLike;
import org.spongepowered.api.item.inventory.transaction.InventoryTransactionResult;
import org.spongepowered.api.registry.RegistryTypes;
import org.spongepowered.api.util.Tristate;
import org.spongepowered.plugin.PluginContainer;
import org.spongepowered.plugin.builtin.jvm.Plugin;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/** SpongeForge adapter. Gameplay decisions are delegated to mythos-core. */
@Plugin("mythos")
public final class MythosPlugin {
    private final Logger logger;
    private final PluginContainer container;
    private final WeaponCatalog weapons = new WeaponCatalog();
    private final WeaponPolicy weaponPolicy = new WeaponPolicy(this.weapons);
    private final Map<UUID, Long> spellReadyAt = new HashMap<>();
    private ClassStore classStore;
    private List<MythosWeapon> customWeapons = List.of();

    @Inject
    public MythosPlugin(final PluginContainer container, final Logger logger) {
        this.logger = logger;
        this.container = container;
    }

    @Listener
    public void onServerStarted(final StartedEngineEvent<Server> event) {
        try {
            this.classStore = new ClassStore(Path.of("config", "mythos", "character-classes.properties"));
            this.logger.info("MYTHOS core loaded with {} classes and {} custom weapons.", CharacterClass.values().length, this.customWeapons.size());
        } catch (IOException exception) {
            throw new IllegalStateException("Could not initialize MYTHOS character storage", exception);
        }
    }

    @Listener
    public void onRegisterCommands(final RegisterCommandEvent<Command.Parameterized> event) {
        if (this.weapons.find("minecraft:bow").isEmpty()) {
            this.customWeapons = MythosWeapon.load(Path.of("config", "mythos", "weapons.tsv"));
            this.registerWeapons();
        }
        final Parameter.Value<String> classArgument = Parameter.string().key("class").build();
        final Command.Parameterized chooseClass = Command.builder()
                .addParameter(classArgument)
                .executor(context -> {
                    final Optional<ServerPlayer> player = context.cause().first(ServerPlayer.class);
                    if (player.isEmpty()) {
                        context.sendMessage(Component.text("This command can only be used by a player."));
                        return CommandResult.success();
                    }
                    final Optional<CharacterClass> parsed = CharacterClass.fromId(context.requireOne(classArgument));
                    if (parsed.isEmpty()) {
                        context.sendMessage(Component.text("Unknown class. Available: warrior, berserker, ranger, assassin, mage, cleric, paladin, summoner."));
                        return CommandResult.success();
                    }
                    if (this.classStore == null) {
                        context.sendMessage(Component.text("MYTHOS is still initializing. Try again shortly."));
                        return CommandResult.success();
                    }
                    try {
                        if (!this.classStore.assignIfAbsent(player.get().uniqueId(), parsed.get())) {
                            final CharacterClass current = this.classStore.find(player.get().uniqueId()).orElse(null);
                            if (current == null) {
                                return CommandResult.error(Component.text("Your saved class is invalid. Ask staff to set it."));
                            }
                            context.sendMessage(Component.text("Your class is already " + current.displayName()
                                    + ". Ask staff if you need a class change."));
                        } else {
                            context.sendMessage(Component.text("Your MYTHOS class is now " + parsed.get().displayName() + "."));
                        }
                    } catch (IOException exception) {
                        this.logger.error("Failed to persist class for {}", player.get().uniqueId(), exception);
                        context.sendMessage(Component.text("Could not save your class. Please contact an administrator."));
                    }
                    return CommandResult.success();
                })
                .build();
        event.register(this.container, chooseClass, "mythosclass", "mclass");
        event.register(this.container, setClassCommand(classArgument), "mythossetclass");
        event.register(this.container, giveGearCommand(false), "mythosgiveweapon");
        event.register(this.container, giveGearCommand(true), "mythosgivearmor");
        event.register(this.container, Command.builder().executor(context -> context.cause().first(ServerPlayer.class)
                .map(this::castHeldSpell)
                .orElseGet(() -> CommandResult.error(Component.text("Only a player can cast a spell."))))
                .build(), "mythoscast");
    }

    @Listener
    public void onPublicChat(final PlayerChatEvent.Submit event) {
        if (event.isCancelled() || !event.chatType().location().equals(ChatTypes.CHAT.location())) {
            return;
        }
        event.player()
                .flatMap(player -> player.option("prefix", event.cause()))
                .map(String::strip)
                .filter(prefix -> !prefix.isEmpty())
                .ifPresent(prefix -> event.setSender(Component.text(prefix + " ").append(event.sender())));
    }

    private Command.Parameterized setClassCommand(final Parameter.Value<String> classArgument) {
        final Parameter.Value<ServerPlayer> targetArgument = Parameter.player().key("player").build();
        return Command.builder()
                .permission("mythos.command.setclass")
                .addParameter(targetArgument)
                .addParameter(classArgument)
                .executor(context -> {
                    final Optional<CharacterClass> parsed = CharacterClass.fromId(context.requireOne(classArgument));
                    if (parsed.isEmpty()) {
                        final String classes = Arrays.stream(CharacterClass.values())
                                .map(CharacterClass::id).collect(Collectors.joining(", "));
                        return CommandResult.error(Component.text("Unknown class. Available: " + classes));
                    }
                    if (this.classStore == null) {
                        return CommandResult.error(Component.text("MYTHOS is still initializing. Try again shortly."));
                    }
                    final ServerPlayer target = context.requireOne(targetArgument);
                    final CharacterClass selected = parsed.get();
                    try {
                        this.classStore.assign(target.uniqueId(), selected);
                    } catch (IOException exception) {
                        this.logger.error("Failed to persist staff class assignment for {}", target.uniqueId(), exception);
                        return CommandResult.error(Component.text("Could not save the class change. Check the server log."));
                    }
                    context.sendMessage(Component.text("Set " + target.name() + "'s MYTHOS class to " + selected.displayName() + "."));
                    if (context.cause().first(ServerPlayer.class)
                            .filter(sender -> sender.uniqueId().equals(target.uniqueId())).isEmpty()) {
                        target.sendMessage(Component.text("Your MYTHOS class is now " + selected.displayName() + "."));
                    }
                    return CommandResult.success();
                })
                .build();
    }

    private Command.Parameterized giveGearCommand(final boolean armor) {
        final Parameter.Value<ServerPlayer> targetArgument = Parameter.player().key("player").build();
        final Parameter.Value<String> itemArgument = Parameter.string().key("item").build();
        return Command.builder()
                .permission(armor ? "mythos.command.givearmor" : "mythos.command.giveweapon")
                .addParameter(targetArgument)
                .addParameter(itemArgument)
                .executor(context -> {
                    final ServerPlayer target = context.requireOne(targetArgument);
                    final String id = context.requireOne(itemArgument).toLowerCase(Locale.ROOT);
                    final Optional<ItemStack> item = armor
                            ? MythosArmor.find(id).flatMap(MythosArmor::create)
                            : this.customWeapons.stream()
                                    .filter(weapon -> weapon.id().equals(id) || ("mythos:" + id).equals(weapon.id()))
                                    .findFirst().flatMap(MythosWeapon::create);
                    if (item.isEmpty()) {
                        final String available = armor
                                ? Arrays.stream(MythosArmor.values()).map(MythosArmor::id).collect(Collectors.joining(", "))
                                : this.customWeapons.stream().map(MythosWeapon::id).collect(Collectors.joining(", "));
                        return CommandResult.error(Component.text("Could not create that item. Available IDs: " + available));
                    }
                    if (target.inventory().offer(item.get()).type() != InventoryTransactionResult.Type.SUCCESS) {
                        return CommandResult.error(Component.text("Could not deliver the item. Check the player's inventory space."));
                    }
                    context.sendMessage(Component.text("Gave " + id + " to " + target.name() + "."));
                    return CommandResult.success();
                })
                .build();
    }

    private CommandResult castHeldSpell(final ServerPlayer player) {
        final ItemStack stack = player.itemInHand(HandTypes.MAIN_HAND.get());
        final Optional<MythosWeapon> heldWeapon = customWeapon(stack);
        if (heldWeapon.isEmpty()) {
            return CommandResult.error(Component.text("Hold a MYTHOS weapon in your main hand to cast its spell."));
        }
        return castSpell(player, heldWeapon.get());
    }

    private CommandResult castSpell(final ServerPlayer player, final MythosWeapon weapon) {
        final var decision = this.weaponPolicy.evaluate(characterClass(player), weapon.id(), WeaponPolicy.Action.USE);
        if (!decision.allowed()) {
            return CommandResult.error(Component.text(decision.reason()));
        }

        final long now = System.nanoTime();
        final long remaining = this.spellReadyAt.getOrDefault(player.uniqueId(), now) - now;
        if (remaining > 0) {
            final long seconds = TimeUnit.NANOSECONDS.toSeconds(remaining - 1) + 1;
            return CommandResult.error(Component.text("Your spell is ready in " + seconds + "s."));
        }

        final ClassSpell spell = weapon.spell();
        if (!spell.apply(player)) {
            return CommandResult.error(Component.text(spell == ClassSpell.EMBER_BOLT
                    ? "Aim at a hostile mob within 16 blocks with a clear line of sight."
                    : "That spell could not improve your active effect."));
        }
        this.spellReadyAt.values().removeIf(readyAt -> now - readyAt >= 0);
        this.spellReadyAt.put(player.uniqueId(), now + spell.cooldown().toNanos());
        player.sendMessage(Component.text("Cast " + spell.displayName() + "."));
        return CommandResult.success();
    }

    // Inventory transactions are unrestricted. Only actual weapon use is blocked.
    @Listener
    public void onItemInteraction(final InteractItemEvent.Secondary event) {
        final Optional<ServerPlayer> player = event.cause().first(ServerPlayer.class);
        if (event.isCancelled() || player.isEmpty()) return;
        final boolean mainHand = event.context().get(EventContextKeys.USED_HAND).orElse(null) == HandTypes.MAIN_HAND.get();
        if ((mainHand && tryCastStaff(player.get(), event.itemStack())) || isRestrictedUse(player, event.itemStack())) {
            event.setCancelled(true);
        }
    }

    @Listener
    public void onEntityInteraction(final InteractEntityEvent.Secondary event) {
        if (event.isCancelled() || event.context().get(EventContextKeys.USED_HAND).orElse(null) != HandTypes.MAIN_HAND.get()) {
            return;
        }
        final Optional<ServerPlayer> player = event.cause().first(ServerPlayer.class);
        if (player.isPresent() && event.entity() instanceof Hostile
                && tryCastStaff(player.get(), player.get().itemInHand(HandTypes.MAIN_HAND.get()))) {
            event.setCancelled(true);
        }
    }

    private boolean tryCastStaff(final ServerPlayer caster, final ItemStackLike stack) {
        final var weapon = customWeapon(stack).filter(MythosWeapon::castsOnRightClick);
        if (weapon.isEmpty()) return false;

        // A click can send both entity-interaction and use-item packets. The shared spell cooldown
        // prevents both events (or a command and a click) from applying a successful cast twice.
        castSpell(caster, weapon.get()).errorMessage().ifPresent(caster::sendActionBar);
        return true;
    }

    @Listener
    public void onBlockInteraction(final InteractBlockEvent.Secondary event) {
        final Optional<ServerPlayer> player = event.cause().first(ServerPlayer.class);
        if (player.isEmpty()) return;
        final ServerPlayer user = player.get();
        final ItemStackLike stack = event.context().get(EventContextKeys.USED_ITEM)
                .<ItemStackLike>map(item -> item)
                .orElseGet(() -> user.itemInHand(event.context().get(EventContextKeys.USED_HAND)
                        .orElseGet(HandTypes.MAIN_HAND::get)));
        if (isRestrictedUse(player, stack)) {
            // Keep the block interaction available, including opening a trade chest.
            event.setUseItemResult(Tristate.FALSE);
        }
    }

    @Listener
    public void onStartUsingItem(final UseItemStackEvent.Start event) {
        if (isRestrictedUse(event.cause().first(ServerPlayer.class), event.itemStackInUse())) {
            event.setCancelled(true);
        }
    }

    @Listener
    public void onReleaseItem(final UseItemStackEvent.Stop event) {
        if (isRestrictedUse(event.cause().first(ServerPlayer.class), event.itemStackInUse())) {
            event.setCancelled(true);
        }
    }

    private boolean isRestrictedUse(final Optional<ServerPlayer> player, final ItemStackLike stack) {
        if (player.isEmpty() || stack.isEmpty()) return false;
        final String id = weaponId(stack);
        if (this.weapons.find(id).isEmpty()) return false;
        final ServerPlayer user = player.get();
        if (this.weaponPolicy.evaluate(characterClass(user), id, WeaponPolicy.Action.USE).allowed()) {
            return false;
        }
        user.sendMessage(Component.text("Your class cannot use that weapon."));
        return true;
    }

    @Listener
    public void onAttack(final AttackEntityEvent event) {
        final var attacker = event.cause().first(DamageSource.class)
                .flatMap(DamageSource::source)
                .filter(ServerPlayer.class::isInstance)
                .map(ServerPlayer.class::cast);
        if (attacker.isEmpty()) return;
        final ServerPlayer player = attacker.get();
        final ItemStack stack = player.itemInHand(HandTypes.MAIN_HAND.get());
        if (stack.isEmpty()) return;
        final String id = weaponId(stack);
        if (this.weapons.find(id).isEmpty()) {
            event.setCancelled(true);
            player.sendMessage(Component.text("That item is not a registered MYTHOS weapon."));
        } else if (!this.weaponPolicy.evaluate(characterClass(player), id, WeaponPolicy.Action.ATTACK).allowed()) {
            event.setCancelled(true);
            player.sendMessage(Component.text("Your class cannot attack with that weapon."));
        }
    }

    private CharacterClass characterClass(final ServerPlayer player) {
        return this.classStore == null ? null : this.classStore.find(player.uniqueId()).orElse(null);
    }

    private String weaponId(final ItemStackLike stack) {
        return customWeapon(stack).map(MythosWeapon::id)
                .orElseGet(() -> stack.type().key(RegistryTypes.ITEM_TYPE).asString());
    }

    private Optional<MythosWeapon> customWeapon(final ItemStackLike stack) {
        if (stack.isEmpty()) return Optional.empty();
        final int model = stack.get(Keys.CUSTOM_MODEL_DATA).orElse(0);
        if (model == 0) return Optional.empty();
        return this.customWeapons.stream()
                .filter(weapon -> weapon.model() == model && weapon.base().equals(stack.type().key(RegistryTypes.ITEM_TYPE).value()))
                .findFirst();
    }

    private void registerWeapons() {
        // Vanilla weapon bases are registered so class rules apply to ordinary weapons too.
        for (String material : new String[] {"wooden", "stone", "iron", "golden", "diamond", "netherite"}) {
            this.weapons.register(new WeaponDefinition("minecraft:" + material + "_sword", WeaponCategory.SWORD, 1, 0, 1));
            this.weapons.register(new WeaponDefinition("minecraft:" + material + "_axe", WeaponCategory.AXE, 1, 0, 1));
        }
        this.weapons.register(new WeaponDefinition("minecraft:bow", WeaponCategory.BOW, 1, 0, 1));
        this.weapons.register(new WeaponDefinition("minecraft:crossbow", WeaponCategory.CROSSBOW, 1, 0, 1));
        this.weapons.register(new WeaponDefinition("minecraft:trident", WeaponCategory.POLEARM, 1, 0, 1));
        this.weapons.register(new WeaponDefinition("minecraft:mace", WeaponCategory.HAMMER, 1, 0, 1));
        this.weapons.register(new WeaponDefinition("minecraft:shield", WeaponCategory.SHIELD, 1, 0, 1));

        for (final MythosWeapon weapon : this.customWeapons) {
            this.weapons.register(new WeaponDefinition(weapon.id(), weapon.category(), 1, 0, weapon.damage()));
        }
    }
}
