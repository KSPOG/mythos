package com.ksp.mythos.combat;
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


@Mod("mythos_combat") public final class Combat{
 public Combat(){MinecraftForge.EVENT_BUS.addListener(this::commands);MinecraftForge.EVENT_BUS.addListener(this::death);}
 private void commands(RegisterCommandsEvent e){e.getDispatcher().register(Commands.literal("rpgcombat").executes(c->Core.ok(c.getSource(),"RPG combat: marked hostile NPC kill rewards active.")));}
private void death(net.minecraftforge.event.entity.living.LivingDeathEvent e){if(e.getEntity().level().isClientSide()||e.getEntity() instanceof ServerPlayer)return;var d=e.getEntity().getPersistentData();int l=d.getInt("mythos_level");if(l<=0||!(e.getSource().getEntity() instanceof ServerPlayer p))return;int diff=l-Core.profile(p).level;double f=diff<=-15?0:diff<=-10?.25:diff<=-5?.5:diff<0?.75:diff==0?1:diff<5?1.1:diff<10?1.25:1.5;Core.award(p,Math.round(d.getLong("mythos_xp")*f));}
}
