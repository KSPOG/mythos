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
import org.spongepowered.api.command.Command;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.parameter.Parameter;
import org.spongepowered.api.data.Keys;
import org.spongepowered.api.data.type.HandTypes;
import org.spongepowered.api.entity.living.player.server.ServerPlayer;
import org.spongepowered.api.event.EventContextKeys;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.block.InteractBlockEvent;
import org.spongepowered.api.event.cause.entity.damage.source.DamageSource;
import org.spongepowered.api.event.entity.AttackEntityEvent;
import org.spongepowered.api.event.item.inventory.InteractItemEvent;
import org.spongepowered.api.event.item.inventory.UseItemStackEvent;
import org.spongepowered.api.event.lifecycle.RegisterCommandEvent;
import org.spongepowered.api.event.lifecycle.StartedEngineEvent;
import org.spongepowered.api.item.ItemType;
import org.spongepowered.api.item.ItemTypes;
import org.spongepowered.api.item.enchantment.Enchantment;
import org.spongepowered.api.item.enchantment.EnchantmentTypes;
import org.spongepowered.api.item.inventory.ItemStack;
import org.spongepowered.api.item.inventory.ItemStackLike;
import org.spongepowered.api.item.inventory.transaction.InventoryTransactionResult;
import org.spongepowered.api.registry.DefaultedRegistryReference;
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

    @Inject
    public MythosPlugin(final PluginContainer container, final Logger logger) {
        this.logger = logger;
        this.container = container;
    }

    @Listener
    public void onServerStarted(final StartedEngineEvent<Server> event) {
        try {
            this.classStore = new ClassStore(Path.of("config", "mythos", "character-classes.properties"));
            this.logger.info("MYTHOS core loaded. {} starter classes registered.", CharacterClass.values().length);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not initialize MYTHOS character storage", exception);
        }
    }

    @Listener
    public void onRegisterCommands(final RegisterCommandEvent<Command.Parameterized> event) {
        if (this.weapons.find("minecraft:bow").isEmpty()) {
            this.registerStarterWeapons();
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
                            final CharacterClass current = this.classStore.find(player.get().uniqueId()).orElseThrow();
                            context.sendMessage(Component.text("Your class is already " + current.displayName()
                                    + ". Class changes are disabled in this milestone."));
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
        event.register(this.container, giveGearCommand(false), "mythosgiveweapon");
        event.register(this.container, giveGearCommand(true), "mythosgivearmor");
        event.register(this.container, Command.builder().executor(context -> context.cause().first(ServerPlayer.class)
                .map(this::castHeldSpell)
                .orElseGet(() -> CommandResult.error(Component.text("Only a player can cast a spell."))))
                .build(), "mythoscast");
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
                            : Arrays.stream(StarterWeapon.WEAPONS)
                                    .filter(weapon -> weapon.id().equals(id) || ("mythos:" + id).equals(weapon.id()))
                                    .findFirst().flatMap(StarterWeapon::create);
                    if (item.isEmpty()) {
                        final String available = armor
                                ? Arrays.stream(MythosArmor.values()).map(MythosArmor::id).collect(Collectors.joining(", "))
                                : Arrays.stream(StarterWeapon.WEAPONS).map(StarterWeapon::id).collect(Collectors.joining(", "));
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
        final Optional<StarterWeapon> heldWeapon = starterWeapon(stack);
        if (heldWeapon.isEmpty()) {
            return CommandResult.error(Component.text("Hold a MYTHOS weapon in your main hand to cast its spell."));
        }
        final StarterWeapon weapon = heldWeapon.get();
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
        if (isRestrictedUse(event.cause().first(ServerPlayer.class), event.itemStack())) {
            event.setCancelled(true);
        }
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
        return starterWeapon(stack).map(StarterWeapon::id)
                .orElseGet(() -> stack.type().key(RegistryTypes.ITEM_TYPE).asString());
    }

    private static Optional<StarterWeapon> starterWeapon(final ItemStackLike stack) {
        if (stack.isEmpty()) return Optional.empty();
        final int model = stack.get(Keys.CUSTOM_MODEL_DATA).orElse(0);
        if (model == 0) return Optional.empty();
        return Arrays.stream(StarterWeapon.WEAPONS)
                .filter(weapon -> weapon.model() == model && weapon.base().get() == stack.type())
                .findFirst();
    }

    private record StarterWeapon(String id, String name, DefaultedRegistryReference<ItemType> base,
                                 int model, WeaponCategory category, ClassSpell spell) {
        private static final StarterWeapon[] WEAPONS = {
            new StarterWeapon("mythos:dawnbringer", "Dawnbringer", ItemTypes.IRON_SWORD, 71001,
                    WeaponCategory.GREATSWORD, ClassSpell.BULWARK),
            new StarterWeapon("mythos:frontier_longbow", "Frontier Longbow", ItemTypes.BOW, 71002,
                    WeaponCategory.BOW, ClassSpell.WINDSTEP),
            new StarterWeapon("mythos:ember_staff", "Ember Staff", ItemTypes.BLAZE_ROD, 71003,
                    WeaponCategory.STAFF, ClassSpell.EMBER_BOLT),
            new StarterWeapon("mythos:assassins_edge", "Assassin's Edge", ItemTypes.STONE_SWORD, 71004,
                    WeaponCategory.DAGGER, ClassSpell.VEILSTEP),
            new StarterWeapon("mythos:clerics_relic", "Cleric's Relic", ItemTypes.GOLDEN_SWORD, 71005,
                    WeaponCategory.HOLY_STAFF, ClassSpell.MEND),
            new StarterWeapon("mythos:paladins_oath", "Paladin's Oath", ItemTypes.IRON_SWORD, 71006,
                    WeaponCategory.HOLY_WEAPON, ClassSpell.AEGIS),
            new StarterWeapon("mythos:war_sunderer", "War Sunderer", ItemTypes.IRON_AXE, 71007,
                    WeaponCategory.GREATAXE, ClassSpell.BATTLE_FURY),
            new StarterWeapon("mythos:summoners_focus", "Summoner's Focus", ItemTypes.BONE, 71008,
                    WeaponCategory.SUMMONING_STAFF, ClassSpell.SPIRIT_ASCENT)
        };

        Optional<ItemStack> create() {
            final ItemStack stack = ItemStack.of(this.base);
            if (!stack.offer(Keys.CUSTOM_MODEL_DATA, this.model).isSuccessful()
                    || !stack.offer(Keys.CUSTOM_NAME, Component.text(this.name)).isSuccessful()
                    || !stack.offer(Keys.LORE, List.of(Component.text(this.spell.displayName() + " • /mythoscast"))).isSuccessful()) {
                return Optional.empty();
            }

            if (this.category == WeaponCategory.STAFF || this.category == WeaponCategory.SUMMONING_STAFF) {
                return stack.offer(Keys.MAX_STACK_SIZE, 1).isSuccessful() ? Optional.of(stack) : Optional.empty();
            }
            final var enchantment = this.category == WeaponCategory.BOW ? EnchantmentTypes.POWER : EnchantmentTypes.SHARPNESS;
            return stack.offer(Keys.APPLIED_ENCHANTMENTS, List.of(Enchantment.of(enchantment, 1))).isSuccessful()
                    ? Optional.of(stack) : Optional.empty();
        }
    }

    private void registerStarterWeapons() {
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

        for (final StarterWeapon weapon : StarterWeapon.WEAPONS) {
            this.weapons.register(new WeaponDefinition(weapon.id(), weapon.category(), 1, 0, 1));
        }
    }
}
