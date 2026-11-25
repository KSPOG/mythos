package com.ksp.mythos.parties;
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


@Mod("mythos_parties") public final class Parties{
 public Parties(){MinecraftForge.EVENT_BUS.addListener(this::commands);}
 private final Map<UUID,LinkedHashSet<UUID>> groups=new HashMap<>();private final Map<UUID,UUID> invitations=new HashMap<>();
private UUID leader(UUID id){for(var e:groups.entrySet())if(e.getValue().contains(id))return e.getKey();return null;}
private void commands(RegisterCommandsEvent e){e.getDispatcher().register(Commands.literal("party").executes(c->{UUID id=c.getSource().getPlayerOrException().getUUID();UUID k=leader(id);return Core.ok(c.getSource(),k==null?"No party":"Party: "+groups.get(k));}).then(Commands.literal("create").executes(c->{var id=c.getSource().getPlayerOrException().getUUID();if(leader(id)!=null)return 0;groups.put(id,new LinkedHashSet<>(List.of(id)));return 1;})).then(Commands.literal("invite").then(Commands.argument("player",EntityArgument.player()).executes(c->{var p=c.getSource().getPlayerOrException();var t=EntityArgument.getPlayer(c,"player");if(!groups.containsKey(p.getUUID())||leader(t.getUUID())!=null)return 0;invitations.put(t.getUUID(),p.getUUID());t.sendSystemMessage(Component.literal("Party invitation from "+p.getName().getString()+"; /party accept"));return 1;}))).then(Commands.literal("accept").executes(c->{UUID id=c.getSource().getPlayerOrException().getUUID(),k=invitations.remove(id);var g=groups.get(k);if(g==null||g.size()>=5||leader(id)!=null)return 0;g.add(id);return 1;})).then(Commands.literal("leave").executes(c->{UUID id=c.getSource().getPlayerOrException().getUUID(),k=leader(id);if(k==null)return 0;var g=groups.get(k);g.remove(id);if(k.equals(id)){groups.remove(k);if(!g.isEmpty())groups.put(g.iterator().next(),g);}return 1;})));}
}
