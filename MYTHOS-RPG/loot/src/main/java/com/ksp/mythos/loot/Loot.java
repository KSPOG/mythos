package com.ksp.mythos.loot;
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


@Mod("mythos_loot") public final class Loot{
 public Loot(){MinecraftForge.EVENT_BUS.addListener(this::commands);}
 private void commands(RegisterCommandsEvent e){e.getDispatcher().register(Commands.literal("lootroll").requires(s->s.hasPermission(2)).executes(c->{double x=new Random().nextDouble()*100;String[] names={"junk","common","uncommon","rare","epic","legendary","mythic","divine","transcendent"};double[] weights={30,30,20,12,5,2.5,.45,.045,.005};int i=0;while(i<weights.length-1&&x>=weights[i])x-=weights[i++];return Core.ok(c.getSource(),"Preview roll: "+names[i]);}));}
}
