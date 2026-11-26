package com.ksp.mythosclasses;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.network.chat.Component;
import com.ksp.mythosclasses.ClassRules.*;

final class ClassWeapons {
    private static final String MARK="mythos_class_weapon";
    static Weapon family(ItemStack stack){
        if(stack.isEmpty())return null;
        var data=stack.get(DataComponents.CUSTOM_DATA);
        if(data!=null){
            String id=data.copyTag().getString(MARK);
            if(!id.isEmpty())try{
                Weapon w=Weapon.valueOf(id);
                if(switch(w){
                    case STAFF -> stack.is(Items.BLAZE_ROD)||stack.is(Items.STICK);
                    case DAGGER -> stack.is(Items.IRON_SWORD);
                    case HOLY_FOCUS -> stack.is(Items.AMETHYST_SHARD)||stack.is(Items.MACE);
                    case DARK_FOCUS -> stack.is(Items.ECHO_SHARD)||stack.is(Items.BLAZE_ROD);
                    default -> false;
                })return w;
            }catch(IllegalArgumentException ignored){}
        }
        Item item=stack.getItem();
        if(item instanceof SwordItem)return Weapon.SWORD;
        if(item instanceof AxeItem)return Weapon.AXE;
        if(item instanceof BowItem)return Weapon.BOW;
        if(item instanceof CrossbowItem)return Weapon.CROSSBOW;
        if(item instanceof TridentItem)return Weapon.TRIDENT;
        if(stack.is(Items.SHIELD))return Weapon.SHIELD;
        if(stack.is(Items.MACE))return Weapon.MACE;
        return null;
    }
    static boolean allowed(Role role,ItemStack stack){
        Weapon w=family(stack);
        return w==null||role!=null&&role.weapons.contains(w);
    }
    static ItemStack starter(Role role){
        return switch(role){
            case WARRIOR -> new ItemStack(Items.STONE_SWORD);
            case HEAVY_KNIGHT -> new ItemStack(Items.SHIELD);
            case RANGER -> new ItemStack(Items.BOW);
            case MAGE -> focus(Items.BLAZE_ROD,Weapon.STAFF,"Apprentice Staff");
            case ROGUE -> focus(Items.IRON_SWORD,Weapon.DAGGER,"Rogue Dagger");
            case CLERIC -> focus(Items.AMETHYST_SHARD,Weapon.HOLY_FOCUS,"Holy Focus");
            case NECROMANCER -> focus(Items.ECHO_SHARD,Weapon.DARK_FOCUS,"Soul Focus");
        };
    }
    private static ItemStack focus(Item item,Weapon w,String name){
        ItemStack stack=new ItemStack(item);CompoundTag tag=new CompoundTag();tag.putString(MARK,w.name());
        stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));
        stack.set(DataComponents.CUSTOM_NAME,Component.literal(name));
        return stack;
    }
}
