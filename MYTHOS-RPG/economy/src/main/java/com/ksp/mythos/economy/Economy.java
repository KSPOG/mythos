package com.ksp.mythos.economy;
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


@Mod("mythos_economy") public final class Economy{
 public Economy(){MinecraftForge.EVENT_BUS.addListener(this::commands);}
 private void commands(RegisterCommandsEvent e){e.getDispatcher().register(Commands.literal("gold").executes(c->Core.ok(c.getSource(),"Gold: "+Core.profile(c.getSource().getPlayerOrException()).gold)).then(Commands.literal("grant").requires(s->s.hasPermission(2)).then(Commands.argument("player",EntityArgument.player()).then(Commands.argument("amount",LongArgumentType.longArg(1,1000000000)).executes(c->{var p=EntityArgument.getPlayer(c,"player");long n=LongArgumentType.getLong(c,"amount");Core.transaction(()->{var q=Core.profile(p);q.gold=Math.addExact(q.gold,n);Core.state.ledger.add("grant "+Core.key(p)+" "+n);});return 1;})))));}
}
