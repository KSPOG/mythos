package com.ksp.mythosequipment;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import java.util.*;
import com.ksp.mythosequipment.EquipmentRules.*;
import com.ksp.mythosequipment.EquipmentRules.Tier;

@Mod(MythosEquipment.ID)
public final class MythosEquipment {
    static final String ID="mythosequipment";
    static final DeferredRegister<Item> ITEMS=DeferredRegister.create(Registries.ITEM,ID);
    static final DeferredRegister<ArmorMaterial> MATERIALS=DeferredRegister.create(Registries.ARMOR_MATERIAL,ID);
    static final DeferredRegister<CreativeModeTab> TABS=DeferredRegister.create(Registries.CREATIVE_MODE_TAB,ID);
    static final Map<String,RegistryObject<Item>> GEAR=new LinkedHashMap<>();
    private static final Map<String,RegistryObject<ArmorMaterial>> ARMOR=new HashMap<>();
    static {
        for(ClassSet c:ClassSet.values())for(Tier t:Tier.values()){
            String key=key(c,t);
            ARMOR.put(key,MATERIALS.register(key,()->new ArmorMaterial(
                Map.of(ArmorItem.Type.HELMET,2,ArmorItem.Type.CHESTPLATE,4,ArmorItem.Type.LEGGINGS,3,ArmorItem.Type.BOOTS,2),
                12,SoundEvents.ARMOR_EQUIP_IRON,()->Ingredient.of(Items.IRON_INGOT),
                List.of(new ArmorMaterial.Layer(ResourceLocation.fromNamespaceAndPath(ID,key))),
                t==Tier.DRAGON?2F:0F,0F)));
        }
        for(Definition d:EquipmentRules.CATALOG)GEAR.put(d.id(),ITEMS.register(d.id(),()->create(d)));
        TABS.register("gear",()->CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.mythosequipment.gear"))
            .icon(()->new ItemStack(GEAR.get("dragon_warrior_weapon").get()))
            .displayItems((params,out)->GEAR.values().forEach(item->out.accept(item.get()))).build());
    }
    public MythosEquipment(){
        IEventBus bus=FMLJavaModLoadingContext.get().getModEventBus();
        MATERIALS.register(bus);ITEMS.register(bus);TABS.register(bus);
        MinecraftForge.EVENT_BUS.addListener(EquipmentPresentation::tooltip);
        EquipmentLifecycle.register();
    }
    private static String key(ClassSet c,Tier t){return t.name().toLowerCase(Locale.ROOT)+"_"+c.id;}
    private static Item create(Definition d){
        Item.Properties p=new Item.Properties().stacksTo(1);
        if(d.slot()==Slot.SHIELD)return new ShieldItem(p.durability(336+128*d.tier().ordinal()));
        if(d.slot()==Slot.WEAPON){
            if(d.kind()==WeaponKind.BOW)return new BowItem(p.durability(384+256*d.tier().ordinal()));
            if(d.kind()==WeaponKind.CROSSBOW)return new CrossbowItem(p.durability(465+256*d.tier().ordinal()));
            return new SwordItem(Tiers.IRON,p.attributes(SwordItem.createAttributes(Tiers.IRON,d.kind().damage+d.tier().ordinal(),d.kind().speed-4F)));
        }
        ArmorItem.Type type=switch(d.slot()){
            case HEAD->ArmorItem.Type.HELMET;case CHEST->ArmorItem.Type.CHESTPLATE;
            case LEGS->ArmorItem.Type.LEGGINGS;case FEET->ArmorItem.Type.BOOTS;default->throw new IllegalStateException();
        };
        Holder<ArmorMaterial> material=ARMOR.get(key(d.owner(),d.tier())).getHolder().orElseThrow();
        return new ArmorItem(material,type,p.durability(type.getDurability(15+10*d.tier().ordinal())));
    }
    static Definition definition(ItemStack stack){
        if(stack.isEmpty())return null;
        for(Definition d:EquipmentRules.CATALOG)if(stack.is(GEAR.get(d.id()).get()))return d;
        return null;
    }
}
