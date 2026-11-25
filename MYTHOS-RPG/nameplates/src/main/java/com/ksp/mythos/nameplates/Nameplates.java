package com.ksp.mythos.nameplates;
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


@Mod("mythos_nameplates") public final class Nameplates{
 public Nameplates(){MinecraftForge.EVENT_BUS.addListener(this::commands);Core.PROFILE_LISTENERS.add(this::update);MinecraftForge.EVENT_BUS.addListener(this::login);}
 private final Map<UUID,String> owned=new HashMap<>();
private void commands(RegisterCommandsEvent e){e.getDispatcher().register(Commands.literal("nameplate").executes(c->{update(c.getSource().getPlayerOrException());return 1;}));}
private void login(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent e){if(e.getEntity() instanceof ServerPlayer p)update(p);}
private void update(ServerPlayer p){var q=Core.profile(p);var sb=p.getServer().getScoreboard();String name=p.getScoreboardName(),id="ml_"+p.getStringUUID().replace("-","").substring(0,12);var existing=sb.getPlayersTeam(name);if(existing!=null&&!existing.getName().equals(id))return;var t=sb.getPlayerTeam(id);if(t==null)t=sb.addPlayerTeam(id);t.setPlayerSuffix(Component.literal("[Lv. "+q.level+"]").withStyle(net.minecraft.ChatFormatting.GREEN));sb.addPlayerToTeam(name,t);}
}
