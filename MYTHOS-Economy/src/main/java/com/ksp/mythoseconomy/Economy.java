package com.ksp.mythoseconomy;

import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

@Mod("mythoseconomy")
public final class Economy {
    private static final org.slf4j.Logger LOG=com.mojang.logging.LogUtils.getLogger();
    private static final UUID CONSOLE=new UUID(0,0);
    private Ledger ledger;
    public Economy(){
        var bus=MinecraftForge.EVENT_BUS;
        bus.addListener(this::start);bus.addListener(this::stop);bus.addListener(this::commands);
    }
    private void start(ServerStartedEvent e){
        EconomyApi.detach();ledger=null;
        try{
            ledger=new Ledger(e.getServer().getWorldPath(LevelResource.ROOT).resolve("mythos-economy.json"),ex->LOG.error("Economy storage failure",ex));
            EconomyApi.attach(e.getServer(),ledger);
            LOG.info("MYTHOS Economy 0.2.0 ready");
        }catch(Exception ex){LOG.error("MYTHOS Economy locked: cannot load ledger",ex);}
    }
    private void stop(ServerStoppingEvent e){EconomyApi.detach();ledger=null;}
    private int fail(CommandSourceStack s,String m){s.sendFailure(Component.literal(m));return 0;}
    private int ok(CommandSourceStack s,String m){s.sendSuccess(()->Component.literal(m),false);return 1;}
    private boolean ready(){return ledger!=null&&ledger.available();}
    private static String gold(long value){return String.format(Locale.ROOT,"%,d Gold",value);}
    private int balance(CommandSourceStack s,ServerPlayer p){return ready()?ok(s,p.getScoreboardName()+": "+gold(ledger.balance(p.getUUID()))):fail(s,"Economy unavailable.");}
    private int history(CommandSourceStack s,ServerPlayer p){
        if(!ready())return fail(s,"Economy unavailable.");
        var entries=ledger.recent(p.getUUID(),10);
        ok(s,"Recent transactions for "+p.getScoreboardName()+" (maximum 10)");
        for(var e:entries){
            boolean incoming=p.getUUID().toString().equals(e.to());
            ok(s,"#"+e.sequence()+" "+(incoming?"+":"-")+gold(e.amount())+" | "+e.kind()+" | "+e.reason());
        }
        return 1;
    }
    private int pay(CommandSourceStack s,ServerPlayer from,ServerPlayer to,long amount){
        if(!ready())return fail(s,"Economy unavailable.");
        if(from==to)return fail(s,"Cannot pay yourself.");
        var receipt=ledger.transfer(from.getUUID(),to.getUUID(),amount,"Player payment");
        if(!receipt.success())return fail(s,receipt.message());
        to.sendSystemMessage(Component.literal("Received "+gold(amount)+" from "+from.getScoreboardName()));
        return ok(s,"Paid "+gold(amount)+" to "+to.getScoreboardName()+" (#"+receipt.sequence()+")");
    }
    private int admin(CommandSourceStack s,ServerPlayer p,long amount,String reason,boolean mint){
        if(!ready())return fail(s,"Economy unavailable.");
        UUID actor=s.getEntity() instanceof ServerPlayer player?player.getUUID():CONSOLE;
        var receipt=mint?ledger.mint(actor,p.getUUID(),amount,reason):ledger.burn(actor,p.getUUID(),amount,reason);
        if(!receipt.success())return fail(s,receipt.message());
        LOG.info("Economy admin transaction #{} actor={} target={} mint={} amount={} reason={}",receipt.sequence(),actor,p.getUUID(),mint,amount,reason);
        return ok(s,"Transaction #"+receipt.sequence()+" committed. Balance: "+gold(ledger.balance(p.getUUID())));
    }
    private void commands(RegisterCommandsEvent e){
        var root=Commands.literal("economy").executes(c->balance(c.getSource(),c.getSource().getPlayerOrException()));
        root.then(Commands.literal("balance").executes(c->balance(c.getSource(),c.getSource().getPlayerOrException())).then(Commands.argument("player",EntityArgument.player()).requires(s->s.hasPermission(2)).executes(c->balance(c.getSource(),EntityArgument.getPlayer(c,"player")))));
        root.then(Commands.literal("history").executes(c->history(c.getSource(),c.getSource().getPlayerOrException())).then(Commands.argument("player",EntityArgument.player()).requires(s->s.hasPermission(2)).executes(c->history(c.getSource(),EntityArgument.getPlayer(c,"player")))));
        root.then(Commands.literal("pay").then(Commands.argument("player",EntityArgument.player()).then(Commands.argument("amount",LongArgumentType.longArg(1,Ledger.MAX_BALANCE)).executes(c->pay(c.getSource(),c.getSource().getPlayerOrException(),EntityArgument.getPlayer(c,"player"),LongArgumentType.getLong(c,"amount"))))));
        for(boolean mint:new boolean[]{true,false})root.then(Commands.literal(mint?"grant":"take").requires(s->s.hasPermission(2)).then(Commands.argument("player",EntityArgument.player()).then(Commands.argument("amount",LongArgumentType.longArg(1,Ledger.MAX_BALANCE)).then(Commands.argument("reason",StringArgumentType.greedyString()).executes(c->admin(c.getSource(),EntityArgument.getPlayer(c,"player"),LongArgumentType.getLong(c,"amount"),StringArgumentType.getString(c,"reason"),mint))))));
        var node=e.getDispatcher().register(root);
        e.getDispatcher().register(Commands.literal("gold").executes(c->balance(c.getSource(),c.getSource().getPlayerOrException())));
        e.getDispatcher().register(Commands.literal("balance").executes(c->balance(c.getSource(),c.getSource().getPlayerOrException())));
        e.getDispatcher().register(Commands.literal("pay").redirect(node.getChild("pay")));
    }
}
