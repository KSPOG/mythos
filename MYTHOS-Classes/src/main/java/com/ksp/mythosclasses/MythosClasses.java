package com.ksp.mythosclasses;

import com.ksp.mythosclasses.ClassRules.Role;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

@Mod("mythosclasses")
public final class MythosClasses {
    private static final org.slf4j.Logger LOG=com.mojang.logging.LogUtils.getLogger();
    private final ClassStore store=new ClassStore();
    private final Map<UUID,Long> notices=new HashMap<>();
    private long tick;
    public MythosClasses(){
        var bus=MinecraftForge.EVENT_BUS;
        bus.addListener(this::start);bus.addListener(this::stop);bus.addListener(this::commands);
        bus.addListener(this::tick);bus.addListener(this::attack);bus.addListener(this::damage);
        bus.addListener(this::use);bus.addListener(this::blockUse);bus.addListener(this::projectile);bus.addListener(this::logout);
    }
    private void start(ServerStartedEvent e){
        tick=0;notices.clear();
        try{store.load(e.getServer().getWorldPath(LevelResource.ROOT).resolve("mythos-classes.json"));}
        catch(Exception ex){LOG.error("Class storage unavailable; restricted weapons locked",ex);}
    }
    private void stop(ServerStoppingEvent e){notices.clear();store.ready=false;}
    private void logout(PlayerEvent.PlayerLoggedOutEvent e){notices.remove(e.getEntity().getUUID());}
    private Role role(ServerPlayer p){return store.get(p.getUUID());}
    private boolean allowed(ServerPlayer p,ItemStack stack){return ClassRules.allows(role(p),ClassWeapons.family(stack));}
    private void deny(ServerPlayer p){
        if(tick>=notices.getOrDefault(p.getUUID(),0L)){
            notices.put(p.getUUID(),tick+30);
            p.displayClientMessage(Component.literal("Your class cannot use this weapon. /class info"),true);
        }
    }
    private void attack(AttackEntityEvent e){
        if(e.getEntity() instanceof ServerPlayer p&&!allowed(p,p.getMainHandItem())){e.setCanceled(true);deny(p);}
    }
    private void use(PlayerInteractEvent.RightClickItem e){
        if(e.getEntity() instanceof ServerPlayer p&&!allowed(p,e.getItemStack())){e.setCanceled(true);deny(p);}
    }
    private void blockUse(PlayerInteractEvent.RightClickBlock e){
        if(e.getEntity() instanceof ServerPlayer p&&!allowed(p,e.getItemStack())){e.setCanceled(true);deny(p);}
    }
    private void projectile(EntityJoinLevelEvent e){
        if(e.getLevel().isClientSide()||e.loadedFromDisk()||!(e.getEntity() instanceof Projectile projectile)||!(projectile.getOwner() instanceof ServerPlayer p))return;
        if(!allowed(p,p.getMainHandItem())||!allowed(p,p.getOffhandItem())){e.setCanceled(true);deny(p);}
    }
    private void damage(LivingAttackEvent e){
        if(e.getEntity().level().isClientSide()||!(e.getSource().getEntity() instanceof ServerPlayer p))return;
        if(!(e.getSource().getDirectEntity() instanceof Projectile)&&!allowed(p,p.getMainHandItem())){e.setCanceled(true);deny(p);}
    }
    private void tick(TickEvent.ServerTickEvent.Post e){
        tick++;
        for(ServerPlayer p:e.getServer().getPlayerList().getPlayers()){
            for(InteractionHand hand:InteractionHand.values()){
                ItemStack stack=p.getItemInHand(hand);
                if(allowed(p,stack))continue;
                if(p.isUsingItem()&&p.getUsedItemHand()==hand)p.stopUsingItem();
                p.setItemInHand(hand,ItemStack.EMPTY);
                // Never destroy or automatically drop a restricted weapon.
                // Move only to non-selected storage slots; if full, keep it
                // in hand but the interaction and combat gates still deny use.
                boolean moved=false;
                var inv=p.getInventory();
                for(int i=0;i<36;i++){
                    if(i==inv.selected||!inv.getItem(i).isEmpty())continue;
                    inv.setItem(i,stack);moved=true;break;
                }
                if(!moved)p.setItemInHand(hand,stack);
                inv.setChanged();
                deny(p);
            }
        }
    }
    private int ok(CommandSourceStack s,String msg){s.sendSuccess(()->Component.literal(msg),false);return 1;}
    private int fail(CommandSourceStack s,String msg){s.sendFailure(Component.literal(msg));return 0;}
    private int assign(CommandSourceStack s,ServerPlayer p,Role role,boolean admin){
        if(role==null||!store.ready)return fail(s,"Unknown class or storage unavailable.");
        if(!admin&&role(p)!=null)return fail(s,"Class already selected; ask an administrator to change it.");
        try{store.assign(p.getUUID(),role);}catch(Exception ex){LOG.error("Class assignment failed",ex);return fail(s,"Class save failed.");}
        return ok(s,p.getGameProfile().getName()+" selected "+role.label+".");
    }
    private int info(CommandSourceStack s,ServerPlayer p){
        Role r=role(p);return ok(s,r==null?"No class selected. /class choose <id>":r.label+" | Allowed weapons: "+r.weapons);
    }
    private void commands(RegisterCommandsEvent e){
        var root=Commands.literal("class").executes(c->info(c.getSource(),c.getSource().getPlayerOrException()));
        root.then(Commands.literal("info").executes(c->info(c.getSource(),c.getSource().getPlayerOrException())));
        root.then(Commands.literal("list").executes(c->{for(Role r:Role.values())ok(c.getSource(),r.id()+" | "+r.label+" | "+r.weapons);return 1;}));
        root.then(Commands.literal("choose").then(Commands.argument("id",StringArgumentType.word()).suggests((c,b)->SharedSuggestionProvider.suggest(Arrays.stream(Role.values()).map(Role::id),b)).executes(c->assign(c.getSource(),c.getSource().getPlayerOrException(),Role.parse(StringArgumentType.getString(c,"id")),false))));
        root.then(Commands.literal("set").requires(s->s.hasPermission(2)).then(Commands.argument("player",EntityArgument.player()).then(Commands.argument("id",StringArgumentType.word()).suggests((c,b)->SharedSuggestionProvider.suggest(Arrays.stream(Role.values()).map(Role::id),b)).executes(c->assign(c.getSource(),EntityArgument.getPlayer(c,"player"),Role.parse(StringArgumentType.getString(c,"id")),true)))));
        root.then(Commands.literal("clear").requires(s->s.hasPermission(2)).then(Commands.argument("player",EntityArgument.player()).executes(c->{
            try{store.assign(EntityArgument.getPlayer(c,"player").getUUID(),null);return ok(c.getSource(),"Class cleared.");}
            catch(Exception ex){LOG.error("Class reset failed",ex);return fail(c.getSource(),"Class save failed.");}
        })));
        root.then(Commands.literal("giveweapon").requires(s->s.hasPermission(2)).then(Commands.argument("player",EntityArgument.player()).then(Commands.argument("id",StringArgumentType.word()).executes(c->{
            Role r=Role.parse(StringArgumentType.getString(c,"id"));if(r==null)return fail(c.getSource(),"Unknown class.");
            ServerPlayer p=EntityArgument.getPlayer(c,"player");ItemStack item=ClassWeapons.starter(r);
            if(!p.getInventory().add(item))return fail(c.getSource(),"Inventory full.");
            return ok(c.getSource(),"Issued class weapon.");
        }))));
        e.getDispatcher().register(root);
    }
}
