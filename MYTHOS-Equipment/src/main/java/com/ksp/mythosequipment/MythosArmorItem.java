package com.ksp.mythosequipment;

import net.minecraft.core.Holder;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import java.util.function.Consumer;
import com.ksp.mythosequipment.EquipmentRules.Definition;

final class MythosArmorItem extends ArmorItem {
    private final Definition definition;
    MythosArmorItem(Holder<ArmorMaterial> material,Type type,Properties properties,Definition definition){
        super(material,type,properties);this.definition=definition;
    }
    @Override public void initializeClient(Consumer<IClientItemExtensions> consumer){
        consumer.accept(new ArmorVisuals(definition));
    }
}
