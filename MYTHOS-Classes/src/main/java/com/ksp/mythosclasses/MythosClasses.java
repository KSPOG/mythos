package com.ksp.mythosclasses;

import com.ksp.mythosclasses.ClassRules.Role;
import com.ksp.mythosclasses.ClassRules.Stat;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

@Mod("mythosclasses")
public final class MythosClasses {
    private static final org.slf4j.Logger LOG=com.mojang.logging.LogUtils.getLogger();
    private final ClassStore store=new ClassStore();
    public MythosClasses(){
        var bus=MinecraftForge.EVENT_BUS;
        bus.addListener(this::start);bus.addListener(this::stop);bus.addListener(this::commands);
        bus.addListener(EventPriority.LOWEST,this::death);
    }
    private void start(ServerStartedEvent e){
        ClassApi.attach(store);
        try{store.load(e.getServer().getWorldPath(LevelResource.ROOT).resolve("mythos-classes.json"));}
        catch(Exception ex){LOG.error("Class storage unavailable",ex);}
    }
    private void stop(ServerStoppingEvent e){store.ready=false;ClassApi.detach(store);}
    private Role role(ServerPlayer p){return store.get(p.getUUID());}
    private int ok(CommandSourceStack s,String msg){s.sendSuccess(()->Component.literal(msg),false);return 1;}
    private int fail(CommandSourceStack s,String msg){s.sendFailure(Component.literal(msg));return 0;}
    private int assign(CommandSourceStack s,ServerPlayer p,Role role,boolean admin){
        if(role==null||!store.ready)return fail(s,"Unknown class or storage unavailable.");
        if(!admin&&role(p)!=null)return fail(s,"Class already selected; ask an administrator to change it.");
        try{store.assign(p.getUUID(),role);}catch(Exception ex){LOG.error("Class assignment failed",ex);return fail(s,"Class save failed.");}
        return ok(s,p.getGameProfile().getName()+" selected "+role.label+".");
    }
    private int info(CommandSourceStack s,ServerPlayer p){
        Role r=role(p);if(r==null)return fail(s,"No class selected. /class choose <id>");
        return ok(s,r.label+" | Level "+ClassRules.level(store.points(p.getUUID()).xp)+"/"+ClassRules.MAX_LEVEL+" | Unspent stat points: "+store.points(p.getUUID()).available());
    }
    private int stats(CommandSourceStack s,ServerPlayer p){
        var snapshot=ClassApi.stats(p.getUUID());
        if(snapshot.isEmpty())return fail(s,"No class selected or storage unavailable.");
        var data=snapshot.get();
        ok(s,role(p).label+" | Level "+ClassRules.level(store.points(p.getUUID()).xp)+" | Stat points: "+data.availablePoints()+" available / "+data.earnedPoints()+" earned");
        data.values().forEach((name,value)->ok(s,name+" | "+value.total()+" ("+value.base()+" base + "+value.allocated()+" allocated)"));
        return 1;
    }
    private int level(CommandSourceStack s,ServerPlayer p){
        var progress=ClassApi.progression(p.getUUID());
        if(progress.isEmpty())return fail(s,"Select a class first.");
        var v=progress.get();
        return ok(s,role(p).label+" | Level "+v.level()+"/"+v.maxLevel()+" | XP "+v.xp()+(v.level()==v.maxLevel()?" (MAX)":" / "+v.nextLevelXp()+" | Next level: "+v.xpIntoLevel()+"/"+v.xpRequired())+" | Unspent points: "+store.points(p.getUUID()).available());
    }
    private Optional<ClassApi.XpReward> reward(ServerPlayer p,int amount){
        var result=ClassApi.grantXp(p.getUUID(),amount);
        result.ifPresent(v->{
            if(v.awarded()==0)return;
            if(v.newLevel()>v.oldLevel()){
                p.sendSystemMessage(Component.literal("MYTHOS | Level "+v.newLevel()+" reached! +"+v.statPoints()+" stat points."));
                p.serverLevel().playSound(null,p.getX(),p.getY(),p.getZ(),SoundEvents.PLAYER_LEVELUP,SoundSource.PLAYERS,0.7F,1.1F);
            }else p.displayClientMessage(Component.literal("MYTHOS +"+v.awarded()+" XP"),true);
        });
        return result;
    }
    private void death(LivingDeathEvent e){
        if(e.isCanceled()||e.getEntity().level().isClientSide()||!(e.getEntity() instanceof Monster))return;
        if(e.getEntity().getTags().contains("mythos_pet")||e.getEntity().getTags().contains("mythos_mount"))return;
        if(e.getSource().getEntity() instanceof ServerPlayer p&&p.isAlive()&&!p.isSpectator()&&!p.isCreative()&&p.level()==e.getEntity().level()&&role(p)!=null&&!p.isAlliedTo(e.getEntity())&&!e.getEntity().isAlliedTo(p))
            reward(p,50);
    }
    private int preview(CommandSourceStack s,Role role){
        if(role==null)return fail(s,"Unknown class.");
        ok(s,role.label+" | Starting stats | "+ClassRules.START_POINTS+" free points");
        for(Stat stat:Stat.values())ok(s,stat.id()+": "+role.base(stat));
        return 1;
    }
    @FunctionalInterface private interface Change {void run() throws Exception;}
    private int mutate(CommandSourceStack s,String message,Change action){
        if(!store.ready)return fail(s,"Class storage unavailable.");
        try{action.run();return ok(s,message);}
        catch(IllegalArgumentException ex){return fail(s,ex.getMessage());}
        catch(Exception ex){LOG.error("Stat change failed",ex);return fail(s,"Stat save failed.");}
    }
    private void commands(RegisterCommandsEvent e){
        var root=Commands.literal("class").executes(c->info(c.getSource(),c.getSource().getPlayerOrException()));
        root.then(Commands.literal("info").executes(c->info(c.getSource(),c.getSource().getPlayerOrException())));
        root.then(Commands.literal("list").executes(c->{for(Role r:Role.values())ok(c.getSource(),r.id()+" | "+r.label);return 1;}));
        root.then(Commands.literal("choose").then(Commands.argument("id",StringArgumentType.word()).suggests((c,b)->SharedSuggestionProvider.suggest(Arrays.stream(Role.values()).map(Role::id),b)).executes(c->assign(c.getSource(),c.getSource().getPlayerOrException(),Role.parse(StringArgumentType.getString(c,"id")),false))));
        root.then(Commands.literal("set").requires(s->s.hasPermission(2)).then(Commands.argument("player",EntityArgument.player()).then(Commands.argument("id",StringArgumentType.word()).suggests((c,b)->SharedSuggestionProvider.suggest(Arrays.stream(Role.values()).map(Role::id),b)).executes(c->assign(c.getSource(),EntityArgument.getPlayer(c,"player"),Role.parse(StringArgumentType.getString(c,"id")),true)))));
        root.then(Commands.literal("clear").requires(s->s.hasPermission(2)).then(Commands.argument("player",EntityArgument.player()).executes(c->{
            try{store.assign(EntityArgument.getPlayer(c,"player").getUUID(),null);return ok(c.getSource(),"Class cleared.");}
            catch(Exception ex){LOG.error("Class reset failed",ex);return fail(c.getSource(),"Class save failed.");}
        })));
        root.then(Commands.literal("stats").executes(c->stats(c.getSource(),c.getSource().getPlayerOrException())).then(Commands.argument("player",EntityArgument.player()).requires(s->s.hasPermission(2)).executes(c->stats(c.getSource(),EntityArgument.getPlayer(c,"player")))));
        root.then(Commands.literal("preview").then(Commands.argument("id",StringArgumentType.word()).suggests((c,b)->SharedSuggestionProvider.suggest(Arrays.stream(Role.values()).map(Role::id),b)).executes(c->preview(c.getSource(),Role.parse(StringArgumentType.getString(c,"id"))))));
        root.then(Commands.literal("allocate").then(Commands.argument("stat",StringArgumentType.word()).suggests((c,b)->SharedSuggestionProvider.suggest(Arrays.stream(Stat.values()).map(Stat::id),b)).then(Commands.argument("amount",IntegerArgumentType.integer(1,ClassRules.MAX_STAT)).executes(c->{
            var p=c.getSource().getPlayerOrException();Stat stat=Stat.parse(StringArgumentType.getString(c,"stat"));int amount=IntegerArgumentType.getInteger(c,"amount");
            return mutate(c.getSource(),"Allocated "+amount+" stat points.",()->store.allocate(p.getUUID(),stat,amount));
        }))));
        var points=Commands.literal("points").requires(s->s.hasPermission(2));
        points.then(Commands.literal("grant").then(Commands.argument("player",EntityArgument.player()).then(Commands.argument("amount",IntegerArgumentType.integer(1,ClassRules.MAX_POINTS)).executes(c->{
            var p=EntityArgument.getPlayer(c,"player");int amount=IntegerArgumentType.getInteger(c,"amount");
            return mutate(c.getSource(),"Granted "+amount+" stat points.",()->store.grant(p.getUUID(),amount));
        }))));
        points.then(Commands.literal("reset").then(Commands.argument("player",EntityArgument.player()).executes(c->{
            var p=EntityArgument.getPlayer(c,"player");
            return mutate(c.getSource(),"Refunded allocated stat points.",()->store.reset(p.getUUID()));
        })));
        root.then(points);
        root.then(Commands.literal("level").executes(c->level(c.getSource(),c.getSource().getPlayerOrException())).then(Commands.argument("player",EntityArgument.player()).requires(s->s.hasPermission(2)).executes(c->level(c.getSource(),EntityArgument.getPlayer(c,"player")))));
        var xp=Commands.literal("xp").executes(c->level(c.getSource(),c.getSource().getPlayerOrException()));
        xp.then(Commands.literal("grant").requires(s->s.hasPermission(2)).then(Commands.argument("player",EntityArgument.player()).then(Commands.argument("amount",IntegerArgumentType.integer(1,ClassRules.MAX_XP)).executes(c->{
            var p=EntityArgument.getPlayer(c,"player");
            var result=reward(p,IntegerArgumentType.getInteger(c,"amount"));
            if(result.isEmpty())return fail(c.getSource(),"XP grant failed or no class selected.");
            var v=result.get();return ok(c.getSource(),"Granted "+v.awarded()+" XP | Level "+v.newLevel()+" | +"+v.statPoints()+" stat points.");
        }))));
        root.then(xp);
        e.getDispatcher().register(root);
    }
}
