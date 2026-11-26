package com.ksp.mythosclasses;

import com.ksp.mythosclasses.ClassRules.Role;
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

@Mod("mythosclasses")
public final class MythosClasses {
    private static final org.slf4j.Logger LOG=com.mojang.logging.LogUtils.getLogger();
    private final ClassStore store=new ClassStore();
    public MythosClasses(){
        var bus=MinecraftForge.EVENT_BUS;
        bus.addListener(this::start);bus.addListener(this::stop);bus.addListener(this::commands);
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
        Role r=role(p);return ok(s,r==null?"No class selected. /class choose <id>":r.label);
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
        e.getDispatcher().register(root);
    }
}
