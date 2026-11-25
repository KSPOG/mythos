package com.ksp.mythos.classes;
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


@Mod("mythos_classes") public final class Classes{
 public Classes(){MinecraftForge.EVENT_BUS.addListener(this::commands);}
 private void commands(RegisterCommandsEvent e){e.getDispatcher().register(Commands.literal("class").executes(c->Core.ok(c.getSource(),"Class: "+Core.profile(c.getSource().getPlayerOrException()).clazz)).then(Commands.literal("choose").then(Commands.argument("class",StringArgumentType.word()).suggests((c,b)->net.minecraft.commands.SharedSuggestionProvider.suggest(Core.CLASSES,b)).executes(c->{var s=c.getSource();var p=s.getPlayerOrException();String k=StringArgumentType.getString(c,"class");if(!Core.CLASSES.contains(k)||!Core.profile(p).clazz.isEmpty())return Core.fail(s,"Invalid class or already chosen.");Core.transaction(()->Core.profile(p).clazz=k);return Core.ok(s,"Class selected: "+k);}))));}
}
