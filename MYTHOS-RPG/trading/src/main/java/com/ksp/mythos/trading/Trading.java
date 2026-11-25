package com.ksp.mythos.trading;
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


@Mod("mythos_trading") public final class Trading{
 public Trading(){MinecraftForge.EVENT_BUS.addListener(this::commands);MinecraftForge.EVENT_BUS.addListener(this::tick);}
 private static final class Trade{UUID a,b;long ga,gb;int stage=1;Set<UUID> accepted=new HashSet<>();long expiry;}
private final Map<UUID,Trade> active=new HashMap<>();private final Map<UUID,UUID> requests=new HashMap<>();private final Map<UUID,Long> expires=new HashMap<>();
private void close(Trade t){active.remove(t.a);active.remove(t.b);}
private ServerPlayer online(UUID id){return Core.server.getPlayerList().getPlayer(id);}
private boolean valid(Trade t){var a=online(t.a);var b=online(t.b);return a!=null&&b!=null&&Core.near(a,b)&&System.currentTimeMillis()<t.expiry;}
private void tick(net.minecraftforge.event.TickEvent.ServerTickEvent.Post e){for(var t:new HashSet<>(active.values()))if(!valid(t))close(t);expires.entrySet().removeIf(x->{if(x.getValue()<System.currentTimeMillis()){requests.remove(x.getKey());return true;}return false;});}
private int confirm(CommandSourceStack s)throws com.mojang.brigadier.exceptions.CommandSyntaxException{var p=s.getPlayerOrException();var t=active.get(p.getUUID());if(t==null||!valid(t)){if(t!=null)close(t);return Core.fail(s,"No valid nearby trade");}t.accepted.add(p.getUUID());if(t.accepted.size()<2)return Core.ok(s,"Waiting for other player");t.accepted.clear();if(t.stage==1){t.stage=2;for(UUID u:List.of(t.a,t.b))online(u).sendSystemMessage(Component.literal("FINAL: "+t.ga+" gold from "+online(t.a).getName().getString()+"; "+t.gb+" gold from "+online(t.b).getName().getString()+". /trade confirm"));return 1;}var a=online(t.a);var b=online(t.b);if(Core.profile(a).gold<t.ga||Core.profile(b).gold<t.gb){close(t);return Core.fail(s,"Insufficient funds; cancelled");}try{Core.transaction(()->{var pa=Core.profile(a);var pb=Core.profile(b);pa.gold=Math.addExact(pa.gold-t.ga,t.gb);pb.gold=Math.addExact(pb.gold-t.gb,t.ga);Core.state.ledger.add("trade "+UUID.randomUUID()+" "+t.a+" "+t.b+" "+t.ga+" "+t.gb);});}catch(RuntimeException ex){close(t);return Core.fail(s,"Trade failed; no exchange committed");}close(t);a.sendSystemMessage(Component.literal("Trade complete"));b.sendSystemMessage(Component.literal("Trade complete"));return 1;}
private void commands(RegisterCommandsEvent e){e.getDispatcher().register(Commands.literal("trade").then(Commands.literal("request").then(Commands.argument("player",EntityArgument.player()).executes(c->{var s=c.getSource();var p=s.getPlayerOrException();var b=EntityArgument.getPlayer(c,"player");if(!Core.near(p,b)||active.containsKey(p.getUUID())||active.containsKey(b.getUUID()))return Core.fail(s,"Trade requires available players within 3 blocks");requests.put(b.getUUID(),p.getUUID());expires.put(b.getUUID(),System.currentTimeMillis()+30000);b.sendSystemMessage(Component.literal("Trade from "+p.getName().getString()+"; /trade accept"));return 1;}))).then(Commands.literal("accept").executes(c->{var s=c.getSource();var p=s.getPlayerOrException();UUID id=requests.remove(p.getUUID());Long expiry=expires.remove(p.getUUID());var a=id==null?null:online(id);if(a==null||expiry==null||expiry<System.currentTimeMillis()||!Core.near(a,p)||active.containsKey(id)||active.containsKey(p.getUUID()))return Core.fail(s,"Request expired or invalid");var t=new Trade();t.a=id;t.b=p.getUUID();t.expiry=System.currentTimeMillis()+120000;active.put(t.a,t);active.put(t.b,t);return Core.ok(s,"Trade opened. /trade gold <amount>; /trade confirm twice.");})).then(Commands.literal("gold").then(Commands.argument("amount",LongArgumentType.longArg(0)).executes(c->{var s=c.getSource();var p=s.getPlayerOrException();var t=active.get(p.getUUID());long n=LongArgumentType.getLong(c,"amount");if(t==null||!valid(t)||t.stage!=1||Core.profile(p).gold<n)return Core.fail(s,"Invalid offer or locked trade");if(t.a.equals(p.getUUID()))t.ga=n;else t.gb=n;t.accepted.clear();for(UUID u:List.of(t.a,t.b))online(u).sendSystemMessage(Component.literal("Offer changed: "+t.ga+" / "+t.gb+" gold; acceptances reset."));return 1;}))).then(Commands.literal("confirm").executes(c->confirm(c.getSource()))).then(Commands.literal("cancel").executes(c->{var t=active.get(c.getSource().getPlayerOrException().getUUID());if(t!=null)close(t);return 1;})));}
}
