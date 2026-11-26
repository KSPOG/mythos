package com.ksp.mythosequipment;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import com.ksp.mythosequipment.EquipmentRules.Rarity;

final class EquipmentPresentation {
    static int color(Rarity r){return switch(r){
        case COMMON->0xAAAAAA;case UNCOMMON->0x55FF55;case RARE->0x5599FF;
        case EPIC->0xBB55FF;case LEGENDARY->0xFFAA00;case MYTHICAL->0xFF5555;
    };}
    static void tooltip(ItemTooltipEvent event){
        var d=MythosEquipment.definition(event.getItemStack());if(d==null)return;
        var lines=event.getToolTip();
        var roll=GearRolls.read(event.getItemStack(),d);
        if(roll.isPresent()){
            var r=roll.get();
            lines.add(Component.literal(r.rarity().label()).withStyle(style->style.withColor(color(r.rarity())).withBold(true)));
            r.stats().forEach((stat,value)->lines.add(Component.literal("+"+value+" "+stat.name()).withStyle(ChatFormatting.GREEN)));
        }else lines.add(Component.literal("Unrolled — generated on server").withStyle(ChatFormatting.GRAY));
        lines.add(Component.literal("Class: "+d.owner().id.replace('_',' ')).withStyle(ChatFormatting.GOLD));
        lines.add(Component.literal("Required level: "+d.tier().level).withStyle(ChatFormatting.GRAY));
    }
}
