package com.ksp.mythosequipment;

import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Items;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = MythosEquipment.ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class EquipmentClient {
    private EquipmentClient() {}

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MythosEquipment.GEAR.values().forEach(entry -> {
            var item = entry.get();
            if (item instanceof BowItem) {
                ItemProperties.register(item, ResourceLocation.withDefaultNamespace("pulling"),
                    (stack, level, entity, seed) -> entity != null && entity.isUsingItem() && entity.getUseItem() == stack ? 1F : 0F);
                ItemProperties.register(item, ResourceLocation.withDefaultNamespace("pull"),
                    (stack, level, entity, seed) -> entity != null && entity.isUsingItem() && entity.getUseItem() == stack
                        ? Mth.clamp((stack.getUseDuration(entity) - entity.getUseItemRemainingTicks()) / 20F, 0F, 1F) : 0F);
            } else if (item instanceof CrossbowItem) {
                ItemProperties.register(item, ResourceLocation.withDefaultNamespace("pulling"),
                    (stack, level, entity, seed) -> entity != null && entity.isUsingItem() && entity.getUseItem() == stack && !CrossbowItem.isCharged(stack) ? 1F : 0F);
                ItemProperties.register(item, ResourceLocation.withDefaultNamespace("charged"),
                    (stack, level, entity, seed) -> CrossbowItem.isCharged(stack) ? 1F : 0F);
            } else if (item instanceof ShieldItem) {
                ItemProperties.register(item, ResourceLocation.withDefaultNamespace("blocking"),
                    (stack, level, entity, seed) -> entity != null && entity.isUsingItem() && entity.getUseItem() == stack ? 1F : 0F);
            }
        }));
    }
}
