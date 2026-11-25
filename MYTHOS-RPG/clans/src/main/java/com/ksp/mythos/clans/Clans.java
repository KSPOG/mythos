package com.ksp.mythos.clans;
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


@Mod("mythos_clans") public final class Clans{
 public Clans(){MinecraftForge.EVENT_BUS.addListener(this::commands);}
 private void commands(RegisterCommandsEvent e){e.getDispatcher().register(Commands.literal("clan").executes(c->Core.ok(c.getSource(),"Clan: "+Core.profile(c.getSource().getPlayerOrException()).clan)).then(Commands.literal("create").then(Commands.argument("name",StringArgumentType.word()).executes(c->{var s=c.getSource();var p=s.getPlayerOrException();String n=StringArgumentType.getString(c,"name"),k=n.toLowerCase(Locale.ROOT);if(!n.matches("[A-Za-z0-9_]{3,20}")||!Core.profile(p).clan.isEmpty()||Core.state.clans.containsKey(k))return Core.fail(s,"Invalid or occupied clan");Core.transaction(()->{var clan=new Core.Clan();clan.name=n;clan.tag=n.substring(0,Math.min(4,n.length())).toUpperCase(Locale.ROOT);clan.owner=Core.key(p);clan.members.add(Core.key(p));Core.state.clans.put(k,clan);Core.profile(p).clan=k;});return Core.ok(s,"Created "+n);}))).then(Commands.literal("leave").executes(c->{var s=c.getSource();var p=s.getPlayerOrException();String k=Core.profile(p).clan;var clan=Core.state.clans.get(k);if(clan==null||clan.owner.equals(Core.key(p)))return Core.fail(s,"Owner cannot leave; transfer/disband not available yet.");Core.transaction(()->{Core.state.clans.get(k).members.remove(Core.key(p));Core.profile(p).clan="";});return 1;})));}
}
