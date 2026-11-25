package com.ksp.mythos.monsters;
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


@Mod("mythos_monsters") public final class Monsters{
 public Monsters(){MinecraftForge.EVENT_BUS.addListener(this::commands);}
 private void commands(RegisterCommandsEvent e){e.getDispatcher().register(Commands.literal("monster").requires(s->s.hasPermission(2)).then(Commands.literal("mark").then(Commands.argument("target",EntityArgument.entity()).then(Commands.argument("level",IntegerArgumentType.integer(1,100)).executes(c->{var s=c.getSource();var en=EntityArgument.getEntity(c,"target");if(!(en instanceof net.minecraft.world.entity.monster.Monster m))return Core.fail(s,"Select a hostile monster.");int l=IntegerArgumentType.getInteger(c,"level");var d=m.getPersistentData();d.putInt("mythos_level",l);d.putLong("mythos_xp",Math.round(10*Math.pow(l,1.5)));double hp=Math.min(1024,20+5*Math.pow(l,1.1));var h=m.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);if(h!=null){h.setBaseValue(hp);m.setHealth(m.getMaxHealth());}var a=m.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);if(a!=null)a.setBaseValue(2+l*.8);m.setCustomName(Component.literal(m.getType().getDescription().getString()+"[Lv. "+l+"]"));m.setCustomNameVisible(true);return Core.ok(s,"Marked RPG monster level "+l);})))));}
}
