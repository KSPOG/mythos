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
import org.spongepowered.api.data.type.HandTypes;
import org.spongepowered.api.entity.living.player.server.ServerPlayer;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.entity.AttackEntityEvent;
import org.spongepowered.api.event.entity.ChangeEntityEquipmentEvent;
import org.spongepowered.api.event.lifecycle.RegisterCommandEvent;
import org.spongepowered.api.event.lifecycle.StartedEngineEvent;
import org.spongepowered.api.item.ItemType;
import org.spongepowered.api.registry.RegistryTypes;
import org.spongepowered.plugin.PluginContainer;
import org.spongepowered.plugin.builtin.jvm.Plugin;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

/** SpongeForge adapter. Gameplay decisions are delegated to mythos-core. */
@Plugin("mythos")
public final class MythosPlugin {
    private static final Parameter.Value<String> CLASS_ARGUMENT = Parameter.string().key("class").build();

    private final Logger logger;
    private final PluginContainer container;
    private final WeaponCatalog weapons = new WeaponCatalog();
    private final WeaponPolicy weaponPolicy = new WeaponPolicy(this.weapons);
    private ClassStore classStore;

    @Inject
    public MythosPlugin(final PluginContainer container, final Logger logger) {
        this.logger = logger;
        this.container = container;
        this.registerStarterWeapons();
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
        final Command.Parameterized chooseClass = Command.builder()
                .addParameter(CLASS_ARGUMENT)
                .executor(context -> {
                    final Optional<ServerPlayer> player = context.cause().first(ServerPlayer.class);
                    if (player.isEmpty()) {
                        context.sendMessage(Component.text("This command can only be used by a player."));
                        return CommandResult.success();
                    }
                    final Optional<CharacterClass> parsed = CharacterClass.fromId(context.requireOne(CLASS_ARGUMENT));
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
    }

    /** Prevent equipping a registered MYTHOS weapon that the player's class cannot use. */
    @Listener
    public void onEquipmentChange(final ChangeEntityEquipmentEvent event) {
        if (!(event.entity() instanceof ServerPlayer player) || this.classStore == null) return;
        final ItemType replacementType = event.transaction().finalReplacement().type();
        final String itemId = replacementType.key(RegistryTypes.ITEM_TYPE).asString();
        final Optional<CharacterClass> characterClass = this.classStore.find(player.uniqueId());
        if (this.isRegisteredWeapon(itemId)
                && !this.weaponPolicy.evaluate(characterClass.orElse(null), itemId, WeaponPolicy.Action.EQUIP).allowed()) {
            event.setCancelled(true);
            player.sendMessage(Component.text("Your class cannot equip that weapon."));
        }
    }

    /** Damage validation is repeated at the attack event to prevent equipment/UI bypasses. */
    @Listener
    public void onAttack(final AttackEntityEvent event) {
        if (this.classStore == null) return;
        final Optional<ServerPlayer> attacker = event.cause().first(ServerPlayer.class);
        if (attacker.isEmpty()) return;
        final ServerPlayer player = attacker.get();
        final String itemId = player.itemInHand(HandTypes.MAIN_HAND.get()).type()
                .key(RegistryTypes.ITEM_TYPE).asString();
        if ("minecraft:air".equals(itemId)) return;
        if (!this.isRegisteredWeapon(itemId)) {
            event.setCancelled(true);
            player.sendMessage(Component.text("That item is not a registered MYTHOS weapon."));
            return;
        }
        final CharacterClass characterClass = this.classStore.find(player.uniqueId()).orElse(null);
        if (!this.weaponPolicy.evaluate(characterClass, itemId, WeaponPolicy.Action.ATTACK).allowed()) {
            event.setCancelled(true);
            player.sendMessage(Component.text("Your class cannot attack with that weapon."));
        }
    }

    private boolean isRegisteredWeapon(final String itemId) {
        return this.weapons.find(itemId).isPresent();
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

        this.weapons.register(new WeaponDefinition("mythos:dawnbringer", WeaponCategory.GREATSWORD, 45, 20, 85));
        this.weapons.register(new WeaponDefinition("mythos:frontier_longbow", WeaponCategory.BOW, 30, 10, 42));
        this.weapons.register(new WeaponDefinition("mythos:ember_staff", WeaponCategory.STAFF, 20, 5, 25));
        this.weapons.register(new WeaponDefinition("mythos:assassins_edge", WeaponCategory.DAGGER, 25, 10, 31));
        this.weapons.register(new WeaponDefinition("mythos:clerics_relic", WeaponCategory.RELIC, 20, 5, 18));
        this.weapons.register(new WeaponDefinition("mythos:paladins_oath", WeaponCategory.HOLY_WEAPON, 35, 15, 52));
        this.weapons.register(new WeaponDefinition("mythos:war_sunderer", WeaponCategory.GREATAXE, 40, 18, 78));
        this.weapons.register(new WeaponDefinition("mythos:summoners_focus", WeaponCategory.SUMMONING_STAFF, 30, 12, 24));
    }
}
