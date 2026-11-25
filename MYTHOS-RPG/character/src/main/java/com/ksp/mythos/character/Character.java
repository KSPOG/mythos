package com.ksp.mythos.character;
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


@Mod("mythos_character") public final class Character{
 public Character(){MinecraftForge.EVENT_BUS.addListener(this::commands);}
 private void commands(RegisterCommandsEvent e){
e.getDispatcher().register(Commands.literal("stats").executes(c->{var p=c.getSource().getPlayerOrException();var q=Core.profile(p);return Core.ok(c.getSource(),"Level "+q.level+" | EXP "+q.xp+"/"+Core.required(q.level)+" | Unspent "+q.points+" | "+q.attrs);})
.then(Commands.literal("allocate").then(Commands.argument("attribute",StringArgumentType.word()).suggests((c,b)->net.minecraft.commands.SharedSuggestionProvider.suggest(Core.ATTR,b)).then(Commands.argument("points",IntegerArgumentType.integer(1,495)).executes(c->{var s=c.getSource();var p=s.getPlayerOrException();String a=StringArgumentType.getString(c,"attribute");int n=IntegerArgumentType.getInteger(c,"points");if(!Arrays.asList(Core.ATTR).contains(a)||Core.profile(p).points<n)return Core.fail(s,"Invalid attribute or insufficient points");Core.transaction(()->{var q=Core.profile(p);q.points-=n;q.attrs.merge(a,n,Integer::sum);});Core.notifyProfile(p);return Core.ok(s,"Allocated "+n+" "+a);})))));
e.getDispatcher().register(Commands.literal("level").executes(c->{var q=Core.profile(c.getSource().getPlayerOrException());return Core.ok(c.getSource(),"Level "+q.level+" | EXP "+q.xp);}).then(Commands.literal("grantxp").requires(s->s.hasPermission(2)).then(Commands.argument("player",EntityArgument.player()).then(Commands.argument("xp",LongArgumentType.longArg(1,100000000)).executes(c->{Core.award(EntityArgument.getPlayer(c,"player"),LongArgumentType.getLong(c,"xp"));return 1;})))));
}
}
