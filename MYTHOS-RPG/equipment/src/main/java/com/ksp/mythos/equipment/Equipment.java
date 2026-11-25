package com.ksp.mythos.equipment;
import com.ksp.mythos.core.Core;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import com.mojang.brigadier.arguments.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;


@Mod("mythos_equipment") public final class Equipment{
 public Equipment(){MinecraftForge.EVENT_BUS.addListener(this::commands);}
 private void commands(RegisterCommandsEvent e){e.getDispatcher().register(Commands.literal("rpgitem").requires(s->s.hasPermission(2)).then(Commands.literal("roll").then(Commands.argument("level",IntegerArgumentType.integer(1,100)).executes(c->{var s=c.getSource();int l=IntegerArgumentType.getInteger(c,"level");var p=s.getPlayerOrException();var r=new Random();var item=new com.google.gson.JsonObject();String id=UUID.randomUUID().toString();item.addProperty("owner",Core.key(p));item.addProperty("template","arcane_bow");item.addProperty("level",l);item.addProperty("class","ranger");item.addProperty("damage",l*4+r.nextInt(l*2+1));item.addProperty("dexterity",1+r.nextInt(l));item.addProperty("criticalChance",r.nextInt(19));Core.transaction(()->{Core.state.items.put(id,item);Core.profile(p).vault.add(id);});return Core.ok(s,"Rolled virtual Arcane Bow "+id+" "+item);}))));}

}
