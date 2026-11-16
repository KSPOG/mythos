package com.ksp.mythos.sponge;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import org.spongepowered.api.data.Keys;
import org.spongepowered.api.item.ItemType;
import org.spongepowered.api.item.ItemTypes;
import org.spongepowered.api.item.enchantment.Enchantment;
import org.spongepowered.api.item.enchantment.EnchantmentType;
import org.spongepowered.api.item.enchantment.EnchantmentTypes;
import org.spongepowered.api.item.inventory.ItemStack;
import org.spongepowered.api.registry.DefaultedRegistryReference;
import org.spongepowered.api.util.Color;

enum MythosArmor {
    WARDEN_HELM("Warden Helm", ItemTypes.IRON_HELMET, 71101, EnchantmentTypes.PROTECTION, 0),
    WARDEN_CHESTPLATE("Warden Chestplate", ItemTypes.IRON_CHESTPLATE, 71102, EnchantmentTypes.PROTECTION, 0),
    WARDEN_LEGGINGS("Warden Leggings", ItemTypes.IRON_LEGGINGS, 71103, EnchantmentTypes.BLAST_PROTECTION, 0),
    WARDEN_BOOTS("Warden Boots", ItemTypes.IRON_BOOTS, 71104, EnchantmentTypes.FEATHER_FALLING, 0),
    RIFTWEAVE_HOOD("Riftweave Hood", ItemTypes.LEATHER_HELMET, 71111, EnchantmentTypes.PROJECTILE_PROTECTION, 0x493271),
    RIFTWEAVE_TUNIC("Riftweave Tunic", ItemTypes.LEATHER_CHESTPLATE, 71112, EnchantmentTypes.FIRE_PROTECTION, 0x593c89),
    RIFTWEAVE_LEGGINGS("Riftweave Leggings", ItemTypes.LEATHER_LEGGINGS, 71113, EnchantmentTypes.FIRE_PROTECTION, 0x35234d),
    RIFTWEAVE_BOOTS("Riftweave Boots", ItemTypes.LEATHER_BOOTS, 71114, EnchantmentTypes.FEATHER_FALLING, 0x362751);

    private final String displayName;
    private final DefaultedRegistryReference<ItemType> base;
    private final int model;
    private final DefaultedRegistryReference<EnchantmentType> enchantment;
    private final int dye;

    MythosArmor(final String displayName, final DefaultedRegistryReference<ItemType> base, final int model,
                final DefaultedRegistryReference<EnchantmentType> enchantment, final int dye) {
        this.displayName = displayName;
        this.base = base;
        this.model = model;
        this.enchantment = enchantment;
        this.dye = dye;
    }

    String id() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    static Optional<MythosArmor> find(final String id) {
        final String shortId = id.startsWith("mythos:") ? id.substring("mythos:".length()) : id;
        return Arrays.stream(values()).filter(armor -> armor.id().equals(shortId)).findFirst();
    }

    Optional<ItemStack> create() {
        final ItemStack item = ItemStack.of(this.base);
        if (!item.offer(Keys.CUSTOM_NAME, Component.text(this.displayName)).isSuccessful()
                || !item.offer(Keys.CUSTOM_MODEL_DATA, this.model).isSuccessful()
                || !item.offer(Keys.APPLIED_ENCHANTMENTS, List.of(Enchantment.of(this.enchantment, 1))).isSuccessful()
                || (this.dye != 0 && !item.offer(Keys.COLOR, Color.ofRgb(this.dye)).isSuccessful())) {
            return Optional.empty();
        }
        return Optional.of(item);
    }
}
