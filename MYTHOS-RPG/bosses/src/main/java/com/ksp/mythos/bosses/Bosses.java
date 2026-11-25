package com.ksp.mythos.bosses;
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


@Mod("mythos_bosses") public final class Bosses{
 public Bosses(){MinecraftForge.EVENT_BUS.addListener(this::commands);}
 private void commands(RegisterCommandsEvent e){e.getDispatcher().register(Commands.literal("boss").requires(s->s.hasPermission(2)).then(Commands.literal("inspect").then(Commands.argument("target",EntityArgument.entity()).executes(c->{var t=EntityArgument.getEntity(c,"target");return Core.ok(c.getSource(),"NPC level: "+t.getPersistentData().getInt("mythos_level")+"; boss phase engine not active");}))));}
}
