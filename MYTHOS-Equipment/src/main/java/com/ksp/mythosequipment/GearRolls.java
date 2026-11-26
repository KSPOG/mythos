package com.ksp.mythosequipment;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import java.util.*;
import com.ksp.mythosequipment.EquipmentRules.*;

final class GearRolls {
    private static final String KEY="mythos_equipment_roll";
    record Roll(Rarity rarity,Map<Stat,Integer> stats){}
    static Optional<Roll> read(ItemStack stack,Definition d){
        var data=stack.get(DataComponents.CUSTOM_DATA);
        if(data==null)return Optional.empty();
        CompoundTag root=data.copyTag();
        if(!root.contains(KEY,10))return Optional.empty();
        CompoundTag roll=root.getCompound(KEY);
        if(!d.id().equals(roll.getString("item")))return Optional.empty();
        try{
            Rarity rarity=Rarity.valueOf(roll.getString("rarity"));
            var values=new EnumMap<Stat,Integer>(Stat.class);
            CompoundTag stats=roll.getCompound("stats");
            for(String key:stats.getAllKeys()){
                if(!stats.contains(key,3))return Optional.empty();
                values.put(Stat.valueOf(key),stats.getInt(key));
            }
            return EquipmentRules.valid(d,rarity,values)?Optional.of(new Roll(rarity,Collections.unmodifiableMap(values))):Optional.empty();
        }catch(IllegalArgumentException ex){return Optional.empty();}
    }
    static Roll ensure(ItemStack stack,Definition d){
        return read(stack,d).orElseGet(()->generate(stack,d,null));
    }
    static Roll generate(ItemStack stack,Definition d,Rarity forced){
        var rng=java.util.concurrent.ThreadLocalRandom.current();
        Rarity rarity=forced==null?Rarity.random(rng):forced;
        var values=EquipmentRules.roll(d,rarity,rng);
        var data=stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag root=data==null?new CompoundTag():data.copyTag();
        CompoundTag roll=new CompoundTag(),stats=new CompoundTag();
        roll.putString("item",d.id());roll.putString("rarity",rarity.name());
        values.forEach((stat,value)->stats.putInt(stat.name(),value));
        roll.put("stats",stats);root.put(KEY,roll);stack.set(DataComponents.CUSTOM_DATA,CustomData.of(root));
        return new Roll(rarity,values);
    }
}
