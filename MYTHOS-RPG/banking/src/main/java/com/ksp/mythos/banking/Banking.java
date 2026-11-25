package com.ksp.mythos.banking;
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


@Mod("mythos_banking") public final class Banking{
 public Banking(){MinecraftForge.EVENT_BUS.addListener(this::commands);}
 private int move(CommandSourceStack s,long n,boolean deposit)throws Exception{var p=s.getPlayerOrException();var q=Core.profile(p);if((deposit?q.gold:q.bankGold)<n)return Core.fail(s,"Insufficient gold");Core.transaction(()->{var a=Core.profile(p);if(deposit){a.gold-=n;a.bankGold=Math.addExact(a.bankGold,n);}else{a.bankGold-=n;a.gold=Math.addExact(a.gold,n);}Core.state.ledger.add("bank "+Core.key(p)+" "+(deposit?n:-n));});return Core.ok(s,"Bank: "+Core.profile(p).bankGold);}
private void commands(RegisterCommandsEvent e){e.getDispatcher().register(Commands.literal("bank").executes(c->Core.ok(c.getSource(),"Bank gold: "+Core.profile(c.getSource().getPlayerOrException()).bankGold)).then(Commands.literal("deposit").then(Commands.argument("amount",LongArgumentType.longArg(1)).executes(c->{try{return move(c.getSource(),LongArgumentType.getLong(c,"amount"),true);}catch(Exception x){return Core.fail(c.getSource(),"Bank operation failed");}}))).then(Commands.literal("withdraw").then(Commands.argument("amount",LongArgumentType.longArg(1)).executes(c->{try{return move(c.getSource(),LongArgumentType.getLong(c,"amount"),false);}catch(Exception x){return Core.fail(c.getSource(),"Bank operation failed");}}))));}
}
