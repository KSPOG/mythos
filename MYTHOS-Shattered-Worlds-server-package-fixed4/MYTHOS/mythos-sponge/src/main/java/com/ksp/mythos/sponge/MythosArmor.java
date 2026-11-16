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
    RIFTWEAVE_BOOTS("Riftweave Boots", ItemTypes.LEATHER_BOOTS, 71114, EnchantmentTypes.FEATHER_FALLING, 0x362751),

    IRONBASTION_HELM("Ironbastion Helm", ItemTypes.IRON_HELMET, 71121, EnchantmentTypes.PROTECTION, 2, 0),
    IRONBASTION_CHESTPLATE("Ironbastion Chestplate", ItemTypes.IRON_CHESTPLATE, 71122, EnchantmentTypes.BLAST_PROTECTION, 2, 0),
    IRONBASTION_LEGGINGS("Ironbastion Leggings", ItemTypes.IRON_LEGGINGS, 71123, EnchantmentTypes.PROTECTION, 1, 0),
    IRONBASTION_BOOTS("Ironbastion Boots", ItemTypes.IRON_BOOTS, 71124, EnchantmentTypes.FEATHER_FALLING, 1, 0),

    ASHFORGED_HELM("Ashforged Helm", ItemTypes.IRON_HELMET, 71131, EnchantmentTypes.FIRE_PROTECTION, 2, 0),
    ASHFORGED_CHESTPLATE("Ashforged Chestplate", ItemTypes.IRON_CHESTPLATE, 71132, EnchantmentTypes.FIRE_PROTECTION, 2, 0),
    ASHFORGED_LEGGINGS("Ashforged Leggings", ItemTypes.IRON_LEGGINGS, 71133, EnchantmentTypes.BLAST_PROTECTION, 1, 0),
    ASHFORGED_BOOTS("Ashforged Boots", ItemTypes.IRON_BOOTS, 71134, EnchantmentTypes.PROTECTION, 1, 0),

    TRAILWARDEN_HOOD("Trailwarden Hood", ItemTypes.LEATHER_HELMET, 71141, EnchantmentTypes.PROJECTILE_PROTECTION, 2, 0x47623A),
    TRAILWARDEN_TUNIC("Trailwarden Tunic", ItemTypes.LEATHER_CHESTPLATE, 71142, EnchantmentTypes.PROJECTILE_PROTECTION, 2, 0x47623A),
    TRAILWARDEN_LEGGINGS("Trailwarden Leggings", ItemTypes.LEATHER_LEGGINGS, 71143, EnchantmentTypes.PROTECTION, 1, 0x47623A),
    TRAILWARDEN_BOOTS("Trailwarden Boots", ItemTypes.LEATHER_BOOTS, 71144, EnchantmentTypes.FEATHER_FALLING, 2, 0x47623A),

    NIGHTVEIL_HOOD("Nightveil Hood", ItemTypes.LEATHER_HELMET, 71151, EnchantmentTypes.PROTECTION, 1, 0x262333),
    NIGHTVEIL_TUNIC("Nightveil Tunic", ItemTypes.LEATHER_CHESTPLATE, 71152, EnchantmentTypes.PROTECTION, 1, 0x262333),
    NIGHTVEIL_LEGGINGS("Nightveil Leggings", ItemTypes.LEATHER_LEGGINGS, 71153, EnchantmentTypes.SWIFT_SNEAK, 1, 0x262333),
    NIGHTVEIL_BOOTS("Nightveil Boots", ItemTypes.LEATHER_BOOTS, 71154, EnchantmentTypes.FEATHER_FALLING, 2, 0x262333),

    FROSTGLASS_HOOD("Frostglass Hood", ItemTypes.LEATHER_HELMET, 71161, EnchantmentTypes.PROTECTION, 1, 0x79BCD6),
    FROSTGLASS_TUNIC("Frostglass Tunic", ItemTypes.LEATHER_CHESTPLATE, 71162, EnchantmentTypes.PROJECTILE_PROTECTION, 2, 0x79BCD6),
    FROSTGLASS_LEGGINGS("Frostglass Leggings", ItemTypes.LEATHER_LEGGINGS, 71163, EnchantmentTypes.BLAST_PROTECTION, 2, 0x79BCD6),
    FROSTGLASS_BOOTS("Frostglass Boots", ItemTypes.LEATHER_BOOTS, 71164, EnchantmentTypes.FEATHER_FALLING, 1, 0x79BCD6),

    DAWNWEAVE_HOOD("Dawnweave Hood", ItemTypes.LEATHER_HELMET, 71171, EnchantmentTypes.PROTECTION, 2, 0xE8D9A8),
    DAWNWEAVE_TUNIC("Dawnweave Tunic", ItemTypes.LEATHER_CHESTPLATE, 71172, EnchantmentTypes.PROTECTION, 2, 0xE8D9A8),
    DAWNWEAVE_LEGGINGS("Dawnweave Leggings", ItemTypes.LEATHER_LEGGINGS, 71173, EnchantmentTypes.FIRE_PROTECTION, 1, 0xE8D9A8),
    DAWNWEAVE_BOOTS("Dawnweave Boots", ItemTypes.LEATHER_BOOTS, 71174, EnchantmentTypes.PROTECTION, 1, 0xE8D9A8),

    SUNWARD_HELM("Sunward Helm", ItemTypes.GOLDEN_HELMET, 71181, EnchantmentTypes.PROTECTION, 2, 0),
    SUNWARD_CHESTPLATE("Sunward Chestplate", ItemTypes.GOLDEN_CHESTPLATE, 71182, EnchantmentTypes.THORNS, 1, 0),
    SUNWARD_LEGGINGS("Sunward Leggings", ItemTypes.GOLDEN_LEGGINGS, 71183, EnchantmentTypes.PROTECTION, 2, 0),
    SUNWARD_BOOTS("Sunward Boots", ItemTypes.GOLDEN_BOOTS, 71184, EnchantmentTypes.UNBREAKING, 2, 0),

    GRAVEBLOOM_HOOD("Gravebloom Hood", ItemTypes.LEATHER_HELMET, 71191, EnchantmentTypes.RESPIRATION, 2, 0x405552),
    GRAVEBLOOM_TUNIC("Gravebloom Tunic", ItemTypes.LEATHER_CHESTPLATE, 71192, EnchantmentTypes.BLAST_PROTECTION, 2, 0x405552),
    GRAVEBLOOM_LEGGINGS("Gravebloom Leggings", ItemTypes.LEATHER_LEGGINGS, 71193, EnchantmentTypes.PROJECTILE_PROTECTION, 1, 0x405552),
    GRAVEBLOOM_BOOTS("Gravebloom Boots", ItemTypes.LEATHER_BOOTS, 71194, EnchantmentTypes.DEPTH_STRIDER, 2, 0x405552);

    private final String displayName;
    private final DefaultedRegistryReference<ItemType> base;
    private final int model;
    private final DefaultedRegistryReference<EnchantmentType> enchantment;
    private final int enchantmentLevel;
    private final int dye;

    MythosArmor(final String displayName, final DefaultedRegistryReference<ItemType> base, final int model,
                final DefaultedRegistryReference<EnchantmentType> enchantment, final int dye) {
        this(displayName, base, model, enchantment, 1, dye);
    }

    MythosArmor(final String displayName, final DefaultedRegistryReference<ItemType> base, final int model,
                final DefaultedRegistryReference<EnchantmentType> enchantment, final int enchantmentLevel, final int dye) {
        this.displayName = displayName;
        this.base = base;
        this.model = model;
        this.enchantment = enchantment;
        this.enchantmentLevel = enchantmentLevel;
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
                || !item.offer(Keys.APPLIED_ENCHANTMENTS, List.of(Enchantment.of(this.enchantment, this.enchantmentLevel))).isSuccessful()
                || (this.dye != 0 && !item.offer(Keys.COLOR, Color.ofRgb(this.dye)).isSuccessful())) {
            return Optional.empty();
        }
        return Optional.of(item);
    }
}
