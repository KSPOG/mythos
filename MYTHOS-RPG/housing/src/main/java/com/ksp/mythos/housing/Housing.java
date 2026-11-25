package com.ksp.mythos.housing;
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


@Mod("mythos_housing") public final class Housing{
 public Housing(){MinecraftForge.EVENT_BUS.addListener(this::commands);}
 private void commands(RegisterCommandsEvent e){e.getDispatcher().register(Commands.literal("housing").executes(c->Core.ok(c.getSource(),"housing module installed; encounter/property runtime not implemented yet.")));}
}
