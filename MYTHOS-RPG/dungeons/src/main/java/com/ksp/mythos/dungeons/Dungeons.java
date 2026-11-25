package com.ksp.mythos.dungeons;
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


@Mod("mythos_dungeons") public final class Dungeons{
 public Dungeons(){MinecraftForge.EVENT_BUS.addListener(this::commands);}
 private void commands(RegisterCommandsEvent e){e.getDispatcher().register(Commands.literal("dungeons").executes(c->Core.ok(c.getSource(),"dungeons module installed; encounter/property runtime not implemented yet.")));}
}
