package com.ksp.mythos.sponge;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.spongepowered.api.data.Keys;
import org.spongepowered.api.data.persistence.DataContainer;
import org.spongepowered.api.data.persistence.DataQuery;
import org.spongepowered.api.item.ItemTypes;
import org.spongepowered.api.item.inventory.ItemStack;
import org.spongepowered.api.registry.RegistryTypes;

final class GearCrates {
    private static final int CAPACITY = 27;
    private static final DataQuery COMPONENTS = DataQuery.of("components");
    private static final DataQuery CONTENTS = COMPONENTS.then("minecraft:container");

    private GearCrates() {
    }

    static int countFor(final int itemCount) {
        return Math.ceilDiv(itemCount, CAPACITY);
    }

    static List<ItemStack> pack(final List<ItemStack> gear) {
        final int count = countFor(gear.size());
        final List<ItemStack> crates = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            final ItemStack box = ItemStack.of(ItemTypes.SHULKER_BOX);
            if (!box.offer(Keys.CUSTOM_NAME, Component.text("MYTHOS Gear " + (index + 1) + "/" + count))
                    .isSuccessful()) {
                throw new IllegalStateException("Could not name MYTHOS gear crate");
            }

            // Sponge's 1.21.1 item serialization preserves the original item components.
            final List<DataContainer> entries = new ArrayList<>(CAPACITY);
            final int start = index * CAPACITY;
            final int end = Math.min(start + CAPACITY, gear.size());
            for (int item = start; item < end; item++) {
                final ItemStack stack = gear.get(item);
                final DataContainer serialized = DataContainer.createNew()
                        .set(DataQuery.of("id"), stack.type().key(RegistryTypes.ITEM_TYPE).asString())
                        .set(DataQuery.of("count"), stack.quantity())
                        .set(COMPONENTS, stack.toContainer().getView(COMPONENTS).orElseThrow());
                entries.add(DataContainer.createNew()
                        .set(DataQuery.of("slot"), item - start)
                        .set(DataQuery.of("item"), serialized));
            }
            final ItemStack packed = ItemStack.builder()
                    .fromContainer(box.toContainer().set(CONTENTS, entries)).build();
            if (packed.isEmpty() || packed.toContainer().getViewList(CONTENTS).orElse(List.of()).size() != entries.size()) {
                throw new IllegalStateException("MYTHOS gear crate contents did not survive serialization");
            }
            crates.add(packed);
        }
        return crates;
    }
}
