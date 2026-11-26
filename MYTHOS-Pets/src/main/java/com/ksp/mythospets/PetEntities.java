package com.ksp.mythospets;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.core.registries.Registries;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.eventbus.api.IEventBus;

final class PetEntities {
    private static final DeferredRegister<EntityType<?>> TYPES =
        DeferredRegister.create(Registries.ENTITY_TYPE, "mythospets");
    static final RegistryObject<EntityType<MooncatEntity>> MOONCAT = TYPES.register("mooncat",
        () -> EntityType.Builder.of(MooncatEntity::new, MobCategory.MISC)
            .sized(0.65F, 0.8F).clientTrackingRange(8).updateInterval(2)
            .build("mythospets:mooncat"));

    static final RegistryObject<EntityType<DragonEntity>> DRAGON = TYPES.register("ember_dragon",
        () -> EntityType.Builder.of(DragonEntity::new, MobCategory.MISC)
            .sized(0.9F, 1.0F).clientTrackingRange(8).updateInterval(2)
            .build("mythospets:ember_dragon"));

    static void register(IEventBus bus) {
        TYPES.register(bus);
        bus.addListener(PetEntities::attributes);
    }

    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(DRAGON.get(), Mob.createMobAttributes()
            .add(Attributes.MAX_HEALTH, 20).add(Attributes.MOVEMENT_SPEED, 0.25).build());
        event.put(MOONCAT.get(), Mob.createMobAttributes()
            .add(Attributes.MAX_HEALTH, 20).add(Attributes.MOVEMENT_SPEED, 0.25).build());
    }
}
