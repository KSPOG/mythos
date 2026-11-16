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
    GRAVEBLOOM_BOOTS("Gravebloom Boots", ItemTypes.LEATHER_BOOTS, 71194, EnchantmentTypes.DEPTH_STRIDER, 2, 0x405552),

    RAMPART_HELM("Rampart Helm", ItemTypes.DIAMOND_HELMET, 71201, EnchantmentTypes.PROTECTION, 3, 0),
    RAMPART_CHESTPLATE("Rampart Chestplate", ItemTypes.DIAMOND_CHESTPLATE, 71202, EnchantmentTypes.BLAST_PROTECTION, 3, 0),
    RAMPART_LEGGINGS("Rampart Leggings", ItemTypes.DIAMOND_LEGGINGS, 71203, EnchantmentTypes.PROTECTION, 2, 0),
    RAMPART_BOOTS("Rampart Boots", ItemTypes.DIAMOND_BOOTS, 71204, EnchantmentTypes.FEATHER_FALLING, 2, 0),

    STONEWATCH_HELM("Stonewatch Helm", ItemTypes.CHAINMAIL_HELMET, 71211, EnchantmentTypes.PROTECTION, 2, 0),
    STONEWATCH_CHESTPLATE("Stonewatch Chestplate", ItemTypes.CHAINMAIL_CHESTPLATE, 71212, EnchantmentTypes.PROJECTILE_PROTECTION, 3, 0),
    STONEWATCH_LEGGINGS("Stonewatch Leggings", ItemTypes.CHAINMAIL_LEGGINGS, 71213, EnchantmentTypes.PROJECTILE_PROTECTION, 2, 0),
    STONEWATCH_BOOTS("Stonewatch Boots", ItemTypes.CHAINMAIL_BOOTS, 71214, EnchantmentTypes.FEATHER_FALLING, 3, 0),

    BRIDGEGUARD_HELM("Bridgeguard Helm", ItemTypes.IRON_HELMET, 71221, EnchantmentTypes.PROTECTION, 2, 0),
    BRIDGEGUARD_CHESTPLATE("Bridgeguard Chestplate", ItemTypes.IRON_CHESTPLATE, 71222, EnchantmentTypes.PROTECTION, 2, 0),
    BRIDGEGUARD_LEGGINGS("Bridgeguard Leggings", ItemTypes.IRON_LEGGINGS, 71223, EnchantmentTypes.BLAST_PROTECTION, 2, 0),
    BRIDGEGUARD_BOOTS("Bridgeguard Boots", ItemTypes.IRON_BOOTS, 71224, EnchantmentTypes.DEPTH_STRIDER, 2, 0),

    CINDERPLATE_HELM("Cinderplate Helm", ItemTypes.NETHERITE_HELMET, 71231, EnchantmentTypes.FIRE_PROTECTION, 3, 0),
    CINDERPLATE_CHESTPLATE("Cinderplate Chestplate", ItemTypes.NETHERITE_CHESTPLATE, 71232, EnchantmentTypes.FIRE_PROTECTION, 4, 0),
    CINDERPLATE_LEGGINGS("Cinderplate Leggings", ItemTypes.NETHERITE_LEGGINGS, 71233, EnchantmentTypes.BLAST_PROTECTION, 3, 0),
    CINDERPLATE_BOOTS("Cinderplate Boots", ItemTypes.NETHERITE_BOOTS, 71234, EnchantmentTypes.FEATHER_FALLING, 2, 0),

    SIEGEBREAKER_HELM("Siegebreaker Helm", ItemTypes.DIAMOND_HELMET, 71241, EnchantmentTypes.BLAST_PROTECTION, 3, 0),
    SIEGEBREAKER_CHESTPLATE("Siegebreaker Chestplate", ItemTypes.DIAMOND_CHESTPLATE, 71242, EnchantmentTypes.BLAST_PROTECTION, 4, 0),
    SIEGEBREAKER_LEGGINGS("Siegebreaker Leggings", ItemTypes.DIAMOND_LEGGINGS, 71243, EnchantmentTypes.PROTECTION, 2, 0),
    SIEGEBREAKER_BOOTS("Siegebreaker Boots", ItemTypes.DIAMOND_BOOTS, 71244, EnchantmentTypes.UNBREAKING, 3, 0),

    REDFANG_HOOD("Redfang Hood", ItemTypes.LEATHER_HELMET, 71251, EnchantmentTypes.PROTECTION, 2, 0x8C3030),
    REDFANG_TUNIC("Redfang Tunic", ItemTypes.LEATHER_CHESTPLATE, 71252, EnchantmentTypes.THORNS, 2, 0x8C3030),
    REDFANG_LEGGINGS("Redfang Leggings", ItemTypes.LEATHER_LEGGINGS, 71253, EnchantmentTypes.FIRE_PROTECTION, 2, 0x8C3030),
    REDFANG_BOOTS("Redfang Boots", ItemTypes.LEATHER_BOOTS, 71254, EnchantmentTypes.FEATHER_FALLING, 3, 0x8C3030),

    HAWKEYE_HOOD("Hawkeye Hood", ItemTypes.LEATHER_HELMET, 71261, EnchantmentTypes.PROJECTILE_PROTECTION, 3, 0x8F7443),
    HAWKEYE_TUNIC("Hawkeye Tunic", ItemTypes.LEATHER_CHESTPLATE, 71262, EnchantmentTypes.PROJECTILE_PROTECTION, 4, 0x8F7443),
    HAWKEYE_LEGGINGS("Hawkeye Leggings", ItemTypes.LEATHER_LEGGINGS, 71263, EnchantmentTypes.PROJECTILE_PROTECTION, 2, 0x8F7443),
    HAWKEYE_BOOTS("Hawkeye Boots", ItemTypes.LEATHER_BOOTS, 71264, EnchantmentTypes.FEATHER_FALLING, 3, 0x8F7443),

    HIGHPINE_HELM("Highpine Helm", ItemTypes.CHAINMAIL_HELMET, 71271, EnchantmentTypes.PROTECTION, 2, 0),
    HIGHPINE_CHESTPLATE("Highpine Chestplate", ItemTypes.CHAINMAIL_CHESTPLATE, 71272, EnchantmentTypes.PROJECTILE_PROTECTION, 2, 0),
    HIGHPINE_LEGGINGS("Highpine Leggings", ItemTypes.CHAINMAIL_LEGGINGS, 71273, EnchantmentTypes.UNBREAKING, 2, 0),
    HIGHPINE_BOOTS("Highpine Boots", ItemTypes.CHAINMAIL_BOOTS, 71274, EnchantmentTypes.FEATHER_FALLING, 4, 0),

    MARSHSTALKER_HOOD("Marshstalker Hood", ItemTypes.LEATHER_HELMET, 71281, EnchantmentTypes.RESPIRATION, 3, 0x475D2F),
    MARSHSTALKER_TUNIC("Marshstalker Tunic", ItemTypes.LEATHER_CHESTPLATE, 71282, EnchantmentTypes.PROJECTILE_PROTECTION, 2, 0x475D2F),
    MARSHSTALKER_LEGGINGS("Marshstalker Leggings", ItemTypes.LEATHER_LEGGINGS, 71283, EnchantmentTypes.PROTECTION, 2, 0x475D2F),
    MARSHSTALKER_BOOTS("Marshstalker Boots", ItemTypes.LEATHER_BOOTS, 71284, EnchantmentTypes.DEPTH_STRIDER, 3, 0x475D2F),

    DUSKTHREAD_HOOD("Duskthread Hood", ItemTypes.LEATHER_HELMET, 71291, EnchantmentTypes.PROTECTION, 2, 0x312965),
    DUSKTHREAD_TUNIC("Duskthread Tunic", ItemTypes.LEATHER_CHESTPLATE, 71292, EnchantmentTypes.PROTECTION, 2, 0x312965),
    DUSKTHREAD_LEGGINGS("Duskthread Leggings", ItemTypes.LEATHER_LEGGINGS, 71293, EnchantmentTypes.SWIFT_SNEAK, 3, 0x312965),
    DUSKTHREAD_BOOTS("Duskthread Boots", ItemTypes.LEATHER_BOOTS, 71294, EnchantmentTypes.FEATHER_FALLING, 4, 0x312965),

    WHISPERSTEEL_HELM("Whispersteel Helm", ItemTypes.CHAINMAIL_HELMET, 71301, EnchantmentTypes.PROJECTILE_PROTECTION, 2, 0),
    WHISPERSTEEL_CHESTPLATE("Whispersteel Chestplate", ItemTypes.CHAINMAIL_CHESTPLATE, 71302, EnchantmentTypes.PROTECTION, 3, 0),
    WHISPERSTEEL_LEGGINGS("Whispersteel Leggings", ItemTypes.CHAINMAIL_LEGGINGS, 71303, EnchantmentTypes.SWIFT_SNEAK, 2, 0),
    WHISPERSTEEL_BOOTS("Whispersteel Boots", ItemTypes.CHAINMAIL_BOOTS, 71304, EnchantmentTypes.FEATHER_FALLING, 2, 0),

    SILTSTEP_HOOD("Siltstep Hood", ItemTypes.LEATHER_HELMET, 71311, EnchantmentTypes.RESPIRATION, 2, 0x60584A),
    SILTSTEP_TUNIC("Siltstep Tunic", ItemTypes.LEATHER_CHESTPLATE, 71312, EnchantmentTypes.PROTECTION, 2, 0x60584A),
    SILTSTEP_LEGGINGS("Siltstep Leggings", ItemTypes.LEATHER_LEGGINGS, 71313, EnchantmentTypes.SWIFT_SNEAK, 2, 0x60584A),
    SILTSTEP_BOOTS("Siltstep Boots", ItemTypes.LEATHER_BOOTS, 71314, EnchantmentTypes.DEPTH_STRIDER, 3, 0x60584A),

    STORMSILK_HOOD("Stormsilk Hood", ItemTypes.LEATHER_HELMET, 71321, EnchantmentTypes.BLAST_PROTECTION, 3, 0x7A85BC),
    STORMSILK_TUNIC("Stormsilk Tunic", ItemTypes.LEATHER_CHESTPLATE, 71322, EnchantmentTypes.PROJECTILE_PROTECTION, 3, 0x7A85BC),
    STORMSILK_LEGGINGS("Stormsilk Leggings", ItemTypes.LEATHER_LEGGINGS, 71323, EnchantmentTypes.BLAST_PROTECTION, 2, 0x7A85BC),
    STORMSILK_BOOTS("Stormsilk Boots", ItemTypes.LEATHER_BOOTS, 71324, EnchantmentTypes.FEATHER_FALLING, 3, 0x7A85BC),

    RUNEBOUND_HELM("Runebound Helm", ItemTypes.CHAINMAIL_HELMET, 71331, EnchantmentTypes.PROTECTION, 3, 0),
    RUNEBOUND_CHESTPLATE("Runebound Chestplate", ItemTypes.CHAINMAIL_CHESTPLATE, 71332, EnchantmentTypes.BLAST_PROTECTION, 3, 0),
    RUNEBOUND_LEGGINGS("Runebound Leggings", ItemTypes.CHAINMAIL_LEGGINGS, 71333, EnchantmentTypes.PROJECTILE_PROTECTION, 2, 0),
    RUNEBOUND_BOOTS("Runebound Boots", ItemTypes.CHAINMAIL_BOOTS, 71334, EnchantmentTypes.UNBREAKING, 3, 0),

    EMBERWEAVE_HOOD("Emberweave Hood", ItemTypes.LEATHER_HELMET, 71341, EnchantmentTypes.FIRE_PROTECTION, 3, 0xC97432),
    EMBERWEAVE_TUNIC("Emberweave Tunic", ItemTypes.LEATHER_CHESTPLATE, 71342, EnchantmentTypes.FIRE_PROTECTION, 4, 0xC97432),
    EMBERWEAVE_LEGGINGS("Emberweave Leggings", ItemTypes.LEATHER_LEGGINGS, 71343, EnchantmentTypes.FIRE_PROTECTION, 2, 0xC97432),
    EMBERWEAVE_BOOTS("Emberweave Boots", ItemTypes.LEATHER_BOOTS, 71344, EnchantmentTypes.FEATHER_FALLING, 3, 0xC97432),

    IVORYMANTLE_HOOD("Ivorymantle Hood", ItemTypes.LEATHER_HELMET, 71351, EnchantmentTypes.PROTECTION, 3, 0xEFE9D5),
    IVORYMANTLE_TUNIC("Ivorymantle Tunic", ItemTypes.LEATHER_CHESTPLATE, 71352, EnchantmentTypes.PROTECTION, 3, 0xEFE9D5),
    IVORYMANTLE_LEGGINGS("Ivorymantle Leggings", ItemTypes.LEATHER_LEGGINGS, 71353, EnchantmentTypes.PROTECTION, 2, 0xEFE9D5),
    IVORYMANTLE_BOOTS("Ivorymantle Boots", ItemTypes.LEATHER_BOOTS, 71354, EnchantmentTypes.FEATHER_FALLING, 3, 0xEFE9D5),

    PILGRIM_HELM("Pilgrim Helm", ItemTypes.CHAINMAIL_HELMET, 71361, EnchantmentTypes.RESPIRATION, 2, 0),
    PILGRIM_CHESTPLATE("Pilgrim Chestplate", ItemTypes.CHAINMAIL_CHESTPLATE, 71362, EnchantmentTypes.PROTECTION, 2, 0),
    PILGRIM_LEGGINGS("Pilgrim Leggings", ItemTypes.CHAINMAIL_LEGGINGS, 71363, EnchantmentTypes.UNBREAKING, 3, 0),
    PILGRIM_BOOTS("Pilgrim Boots", ItemTypes.CHAINMAIL_BOOTS, 71364, EnchantmentTypes.DEPTH_STRIDER, 2, 0),

    HALLOWMIST_HOOD("Hallowmist Hood", ItemTypes.LEATHER_HELMET, 71371, EnchantmentTypes.RESPIRATION, 3, 0xB4DECF),
    HALLOWMIST_TUNIC("Hallowmist Tunic", ItemTypes.LEATHER_CHESTPLATE, 71372, EnchantmentTypes.PROJECTILE_PROTECTION, 2, 0xB4DECF),
    HALLOWMIST_LEGGINGS("Hallowmist Leggings", ItemTypes.LEATHER_LEGGINGS, 71373, EnchantmentTypes.PROTECTION, 2, 0xB4DECF),
    HALLOWMIST_BOOTS("Hallowmist Boots", ItemTypes.LEATHER_BOOTS, 71374, EnchantmentTypes.DEPTH_STRIDER, 3, 0xB4DECF),

    OATHPLATE_HELM("Oathplate Helm", ItemTypes.DIAMOND_HELMET, 71381, EnchantmentTypes.PROTECTION, 3, 0),
    OATHPLATE_CHESTPLATE("Oathplate Chestplate", ItemTypes.DIAMOND_CHESTPLATE, 71382, EnchantmentTypes.THORNS, 2, 0),
    OATHPLATE_LEGGINGS("Oathplate Leggings", ItemTypes.DIAMOND_LEGGINGS, 71383, EnchantmentTypes.PROTECTION, 3, 0),
    OATHPLATE_BOOTS("Oathplate Boots", ItemTypes.DIAMOND_BOOTS, 71384, EnchantmentTypes.FEATHER_FALLING, 3, 0),

    DAWNSHIELD_HELM("Dawnshield Helm", ItemTypes.IRON_HELMET, 71391, EnchantmentTypes.PROJECTILE_PROTECTION, 2, 0),
    DAWNSHIELD_CHESTPLATE("Dawnshield Chestplate", ItemTypes.IRON_CHESTPLATE, 71392, EnchantmentTypes.PROTECTION, 3, 0),
    DAWNSHIELD_LEGGINGS("Dawnshield Leggings", ItemTypes.IRON_LEGGINGS, 71393, EnchantmentTypes.BLAST_PROTECTION, 3, 0),
    DAWNSHIELD_BOOTS("Dawnshield Boots", ItemTypes.IRON_BOOTS, 71394, EnchantmentTypes.FEATHER_FALLING, 3, 0),

    GILDED_VOW_HELM("Gilded Vow Helm", ItemTypes.GOLDEN_HELMET, 71401, EnchantmentTypes.PROTECTION, 2, 0),
    GILDED_VOW_CHESTPLATE("Gilded Vow Chestplate", ItemTypes.GOLDEN_CHESTPLATE, 71402, EnchantmentTypes.THORNS, 3, 0),
    GILDED_VOW_LEGGINGS("Gilded Vow Leggings", ItemTypes.GOLDEN_LEGGINGS, 71403, EnchantmentTypes.UNBREAKING, 3, 0),
    GILDED_VOW_BOOTS("Gilded Vow Boots", ItemTypes.GOLDEN_BOOTS, 71404, EnchantmentTypes.FIRE_PROTECTION, 3, 0),

    BONEWEAVE_HOOD("Boneweave Hood", ItemTypes.LEATHER_HELMET, 71411, EnchantmentTypes.PROTECTION, 2, 0xC4B092),
    BONEWEAVE_TUNIC("Boneweave Tunic", ItemTypes.LEATHER_CHESTPLATE, 71412, EnchantmentTypes.BLAST_PROTECTION, 3, 0xC4B092),
    BONEWEAVE_LEGGINGS("Boneweave Leggings", ItemTypes.LEATHER_LEGGINGS, 71413, EnchantmentTypes.PROTECTION, 2, 0xC4B092),
    BONEWEAVE_BOOTS("Boneweave Boots", ItemTypes.LEATHER_BOOTS, 71414, EnchantmentTypes.FEATHER_FALLING, 3, 0xC4B092),

    WRAITHMESH_HELM("Wraithmesh Helm", ItemTypes.CHAINMAIL_HELMET, 71421, EnchantmentTypes.FIRE_PROTECTION, 3, 0),
    WRAITHMESH_CHESTPLATE("Wraithmesh Chestplate", ItemTypes.CHAINMAIL_CHESTPLATE, 71422, EnchantmentTypes.BLAST_PROTECTION, 3, 0),
    WRAITHMESH_LEGGINGS("Wraithmesh Leggings", ItemTypes.CHAINMAIL_LEGGINGS, 71423, EnchantmentTypes.SWIFT_SNEAK, 2, 0),
    WRAITHMESH_BOOTS("Wraithmesh Boots", ItemTypes.CHAINMAIL_BOOTS, 71424, EnchantmentTypes.FEATHER_FALLING, 3, 0),

    ABYSSAL_HOOD("Abyssal Hood", ItemTypes.LEATHER_HELMET, 71431, EnchantmentTypes.RESPIRATION, 3, 0x253F62),
    ABYSSAL_TUNIC("Abyssal Tunic", ItemTypes.LEATHER_CHESTPLATE, 71432, EnchantmentTypes.PROTECTION, 3, 0x253F62),
    ABYSSAL_LEGGINGS("Abyssal Leggings", ItemTypes.LEATHER_LEGGINGS, 71433, EnchantmentTypes.PROJECTILE_PROTECTION, 2, 0x253F62),
    ABYSSAL_BOOTS("Abyssal Boots", ItemTypes.LEATHER_BOOTS, 71434, EnchantmentTypes.DEPTH_STRIDER, 3, 0x253F62);

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
