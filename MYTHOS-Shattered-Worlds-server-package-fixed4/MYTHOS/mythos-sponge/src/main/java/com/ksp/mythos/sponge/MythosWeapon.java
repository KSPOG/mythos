package com.ksp.mythos.sponge;

import com.ksp.mythos.core.weapon.WeaponCategory;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.spongepowered.api.ResourceKey;
import org.spongepowered.api.data.Keys;
import org.spongepowered.api.entity.attribute.AttributeModifier;
import org.spongepowered.api.entity.attribute.AttributeOperations;
import org.spongepowered.api.entity.attribute.type.AttributeTypes;
import org.spongepowered.api.item.ItemType;
import org.spongepowered.api.item.enchantment.Enchantment;
import org.spongepowered.api.item.enchantment.EnchantmentTypes;
import org.spongepowered.api.item.inventory.ItemStack;
import org.spongepowered.api.item.inventory.equipment.EquipmentTypes;
import org.spongepowered.api.registry.RegistryTypes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

record MythosWeapon(String id, String name, String base, WeaponCategory category, ClassSpell spell,
                    int model, double damage, double speed, int durability, int power, Rarity rarity) {
    enum Rarity {
        COMMON(NamedTextColor.WHITE),
        UNCOMMON(NamedTextColor.GREEN),
        RARE(NamedTextColor.AQUA),
        EPIC(NamedTextColor.LIGHT_PURPLE),
        LEGENDARY(NamedTextColor.GOLD);

        private final NamedTextColor color;

        Rarity(final NamedTextColor color) {
            this.color = color;
        }
    }

    static List<MythosWeapon> load(final Path file) {
        try {
            Files.createDirectories(file.getParent());
            if (Files.notExists(file)) {
                try (var defaults = MythosWeapon.class.getResourceAsStream("/mythos-weapons.tsv")) {
                    if (defaults == null) throw new IllegalStateException("Missing MYTHOS weapon defaults");
                    Files.copy(defaults, file);
                }
            }
            final List<MythosWeapon> weapons = new ArrayList<>();
            final var ids = new HashSet<String>();
            final var models = new HashSet<Integer>();
            int line = 0;
            for (final String row : Files.readAllLines(file)) {
                line++;
                if (row.isBlank() || row.startsWith("#")) continue;
                try {
                    final String[] columns = row.split("\t", -1);
                    if (columns.length != 11) throw new IllegalArgumentException("Expected 11 tab-separated fields");
                    final var weapon = new MythosWeapon("mythos:" + columns[0], columns[1], columns[2],
                            readEnum(WeaponCategory.class, columns[3]), readEnum(ClassSpell.class, columns[4]),
                            Integer.parseInt(columns[5]), Double.parseDouble(columns[6]), Double.parseDouble(columns[7]),
                            Integer.parseInt(columns[8]), Integer.parseInt(columns[9]), readEnum(Rarity.class, columns[10]));
                    weapon.validate();
                    if (!ids.add(weapon.id) || !models.add(weapon.model)) throw new IllegalArgumentException("Duplicate ID or model");
                    weapons.add(weapon);
                } catch (IllegalArgumentException exception) {
                    throw new IllegalStateException(file + ":" + line + ": " + exception.getMessage(), exception);
                }
            }
            if (weapons.isEmpty()) throw new IllegalStateException("No custom weapons in " + file);
            return List.copyOf(weapons);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read MYTHOS weapons from " + file, exception);
        }
    }

    private static <E extends Enum<E>> E readEnum(final Class<E> type, final String value) {
        return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
    }

    private void validate() {
        if (!this.id.matches("mythos:[a-z0-9_]+") || this.name.isBlank() || this.model < 1
                || !this.base.matches("[a-z0-9_]+") || this.durability < 0 || this.power < 0 || this.power > 5
                || !Double.isFinite(this.damage) || !Double.isFinite(this.speed)
                || this.damage < 0 || this.damage > 2048 || this.speed < 0 || this.speed > 1024
                || (this.damage > 0 && (this.damage < 1 || this.speed == 0))
                || (this.damage == 0 && this.speed != 0)) {
            throw new IllegalArgumentException("Invalid weapon ID, base, name, model or stats");
        }
        if (this.base.equals("bow") != (this.category == WeaponCategory.BOW || this.category == WeaponCategory.SHORTBOW)
                || (!this.base.equals("bow") && this.power != 0)
                || RegistryTypes.ITEM_TYPE.get().findValue(ResourceKey.minecraft(this.base)).isEmpty()) {
            throw new IllegalArgumentException("Invalid item base for weapon category or bow Power");
        }
    }

    boolean castsOnRightClick() {
        return switch (this.category) {
            case STAFF, SUMMONING_STAFF, HOLY_STAFF, WAND -> true;
            default -> false;
        };
    }

    Optional<ItemStack> create() {
        final ItemType type = RegistryTypes.ITEM_TYPE.get().value(ResourceKey.minecraft(this.base));
        final ItemStack stack = ItemStack.of(type);
        final var title = Component.text(this.name, this.rarity.color).decoration(TextDecoration.ITALIC, false);
        final List<Component> lore = List.of(
                Component.text(this.rarity.name(), this.rarity.color).decoration(TextDecoration.ITALIC, false),
                Component.text(this.spell.displayName() + (castsOnRightClick() ? " • Right-click" : " • /mythoscast")));
        if (!stack.offer(Keys.CUSTOM_MODEL_DATA, this.model).isSuccessful()
                || !stack.offer(Keys.CUSTOM_NAME, title).isSuccessful()
                || !stack.offer(Keys.LORE, lore).isSuccessful()
                // Sponge rejects stack-size writes on damageable items, including swords already limited to one.
                || ((castsOnRightClick() || this.durability > 0) && stack.maxStackQuantity() > 1
                        && !stack.offer(Keys.MAX_STACK_SIZE, 1).isSuccessful())
                || (this.durability > 0 && !stack.offer(Keys.MAX_DURABILITY, this.durability).isSuccessful())
                || (this.power > 0 && !stack.offer(Keys.APPLIED_ENCHANTMENTS,
                        List.of(Enchantment.of(EnchantmentTypes.POWER, this.power))).isSuccessful())) {
            return Optional.empty();
        }
        if (this.damage > 0) {
            // Replace the base item's modifiers for these attributes. Player bases are 1 damage and 4 attacks/sec.
            stack.addAttributeModifier(AttributeTypes.GENERIC_ATTACK_DAMAGE, modifier("base_attack_damage", this.damage - 1),
                    EquipmentTypes.MAINHAND);
            stack.addAttributeModifier(AttributeTypes.GENERIC_ATTACK_SPEED, modifier("base_attack_speed", this.speed - 4),
                    EquipmentTypes.MAINHAND);
        }
        return Optional.of(stack);
    }

    private static AttributeModifier modifier(final String id, final double value) {
        return AttributeModifier.builder()
                .key(ResourceKey.minecraft(id))
                .operation(AttributeOperations.ADDITION)
                .amount(value)
                .build();
    }
}
