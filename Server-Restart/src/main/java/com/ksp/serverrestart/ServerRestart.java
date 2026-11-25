package com.ksp.serverrestart;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.world.BossEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.MutableComponent;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.server.level.ServerPlayer;
import java.nio.file.*;
import java.util.UUID;

@Mod("serverrestart")
public final class ServerRestart {
    private static final org.slf4j.Logger LOG=com.mojang.logging.LogUtils.getLogger();
    private final ServerBossEvent bar=new ServerBossEvent(Component.literal("Server restart"),BossEvent.BossBarColor.GREEN,BossEvent.BossBarOverlay.PROGRESS);
    private long deadlineNanos,totalSeconds,lastSeconds=-1;
    private boolean active,stopping;
    private BroadcastConfig config=BroadcastConfig.defaults();
    private static final String MARKER=".server-restart-request";
    private static final String TOKEN_ENV="KSP_RESTART_TOKEN";

    public ServerRestart() {
        MinecraftForge.EVENT_BUS.addListener(this::commands);
        MinecraftForge.EVENT_BUS.addListener(this::tick);
        MinecraftForge.EVENT_BUS.addListener(this::join);
        MinecraftForge.EVENT_BUS.addListener(this::leave);
        MinecraftForge.EVENT_BUS.addListener(this::stop);
        MinecraftForge.EVENT_BUS.addListener(this::started);
        bar.setVisible(false);
    }

    private void commands(RegisterCommandsEvent event) {
        var root=Commands.literal("restart").requires(s->s.hasPermission(2))
            .executes(c->status(c.getSource()))
            .then(Commands.literal("status").executes(c->status(c.getSource())))
            .then(Commands.literal("cancel").executes(c->cancel(c.getSource())))
            .then(Commands.literal("reload").executes(c->reload(c.getSource())))
            .then(Commands.literal("now").executes(c->schedule(c.getSource(),1)))
            .then(Commands.argument("duration",StringArgumentType.word())
                .suggests((c,b)->{for(String v:new String[]{"30s","5m","15m","50m","1h","1h30m"}) if(v.startsWith(b.getRemaining())) b.suggest(v); return b.buildFuture();})
                .executes(c->{
                    try { return schedule(c.getSource(),DurationParser.seconds(StringArgumentType.getString(c,"duration"))); }
                    catch(IllegalArgumentException ex) { c.getSource().sendFailure(Component.literal(ex.getMessage())); return 0; }
                }));
        event.getDispatcher().register(root);
    }
    private static String token() {
        String value=System.getenv(TOKEN_ENV);
        if(value==null) return null;
        try { return UUID.fromString(value).toString().equals(value)?value:null; }
        catch(IllegalArgumentException ex) { return null; }
    }
    private int schedule(CommandSourceStack source,long seconds) {
        if(stopping) { source.sendFailure(Component.literal("Server restart is already in progress.")); return 0; }
        if(token()==null) { source.sendFailure(Component.literal("Restart launcher unavailable. Start the server using start-restart.ps1 or start-restart.sh.")); return 0; }
        deadlineNanos=System.nanoTime()+seconds*1_000_000_000L;
        totalSeconds=seconds; lastSeconds=seconds; active=true;
        bar.setVisible(true);
        for(var player:source.getServer().getPlayerList().getPlayers()) bar.addPlayer(player);
        update(seconds);
        announce(source.getServer(),"scheduled",seconds);
        if(source.getEntity()==null) source.sendSuccess(()->Component.literal("Restart scheduled in "+DurationParser.format(seconds)),false);
        return 1;
    }
    private long remaining() {
        long nanos=deadlineNanos-System.nanoTime();
        return nanos<=0?0:1+(nanos-1)/1_000_000_000L;
    }
    private int status(CommandSourceStack source) {
        source.sendSuccess(()->Component.literal(active?"Restart in "+DurationParser.format(remaining()):stopping?"Restart in progress":"No restart scheduled"),false);
        return 1;
    }
    private int cancel(CommandSourceStack source) {
        if(!active||stopping) { source.sendFailure(Component.literal("No cancellable restart.")); return 0; }
        clear();
        announce(source.getServer(),"cancelled",0);
        if(source.getEntity()==null) source.sendSuccess(()->Component.literal("Restart cancelled."),false);
        return 1;
    }
    private void clear() { active=false; bar.setVisible(false); bar.removeAllPlayers(); }
    private void update(long seconds) {
        bar.setName(colored(config.render(config.bossbar(),seconds)));
        bar.setProgress(Math.max(0f,Math.min(1f,(float)((double)seconds/totalSeconds))));
        bar.setColor(seconds<=60?BossEvent.BossBarColor.RED:seconds<=300?BossEvent.BossBarColor.YELLOW:BossEvent.BossBarColor.GREEN);
    }
    private static Path configPath() { return FMLPaths.CONFIGDIR.get().resolve("serverrestart.properties"); }
    private void started(ServerStartedEvent event) {
        try { config=BroadcastConfig.load(configPath()); LOG.info("Server Restart broadcast config loaded: {}",configPath()); }
        catch(Exception ex) { LOG.error("Invalid restart broadcast config; using defaults",ex); }
    }
    private int reload(CommandSourceStack source) {
        try {
            var updated=BroadcastConfig.load(configPath());
            config=updated;
            if(active) update(remaining());
            source.sendSuccess(()->Component.literal("Server Restart configuration reloaded."),false); return 1;
        } catch(Exception ex) {
            LOG.warn("Restart config reload rejected",ex);
            source.sendFailure(Component.literal("Configuration not reloaded: "+ex.getMessage())); return 0;
        }
    }
    private static MutableComponent colored(String value) {
        var result=Component.empty();
        var part=new StringBuilder();
        var style=net.minecraft.network.chat.Style.EMPTY;
        for(int i=0;i<value.length();i++) {
            char ch=value.charAt(i);
            if(ch=='&'&&i+1<value.length()) {
                char next=value.charAt(i+1);
                if(next=='&') { part.append('&'); i++; continue; }
                ChatFormatting format=ChatFormatting.getByCode(Character.toLowerCase(next));
                if(format!=null) {
                    if(!part.isEmpty()) { result.append(Component.literal(part.toString()).setStyle(style)); part.setLength(0); }
                    style=format==ChatFormatting.RESET?net.minecraft.network.chat.Style.EMPTY:format.isColor()?net.minecraft.network.chat.Style.EMPTY.applyLegacyFormat(format):style.applyLegacyFormat(format);
                    i++; continue;
                }
            }
            part.append(ch);
        }
        if(!part.isEmpty()) result.append(Component.literal(part.toString()).setStyle(style));
        return result;
    }
    private void announce(MinecraftServer server,String event,long seconds) {
        String template=config.message(event);
        if(template.isBlank()) return;
        var message=colored(config.render(template,seconds));
        // Native server system broadcast (not signed player chat or operator command feedback).
        server.getPlayerList().broadcastSystemMessage(message,false);
    }
    private void tick(TickEvent.ServerTickEvent.Post event) {
        if(!active||stopping) return;
        long seconds=remaining();
        if(seconds!=lastSeconds) {
            update(seconds);
            if(lastSeconds>0) for(long threshold:config.warningSeconds()) {
                if(lastSeconds>threshold&&seconds<=threshold&&seconds>0) { announce(event.getServer(),"warning",seconds); break; }
            }
            lastSeconds=seconds;
        }
        if(seconds==0) restart(event.getServer());
    }
    private void restart(MinecraftServer server) {
        // The supervisor starts the next JVM only after this JVM exits normally.
        String value=token();
        try {
            if(value==null) throw new java.io.IOException("Supervisor token missing");
            Path marker=Path.of(MARKER).toAbsolutePath();
            Files.writeString(marker,value,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE);
        } catch(Exception ex) {
            clear(); announce(server,"aborted",0);
            LOG.error("Restart marker could not be written",ex); return;
        }
        stopping=true; clear();
        announce(server,"restarting",0);
        server.halt(false);
    }
    private void join(PlayerEvent.PlayerLoggedInEvent event) {
        if(active&&event.getEntity() instanceof ServerPlayer player) bar.addPlayer(player);
    }
    private void leave(PlayerEvent.PlayerLoggedOutEvent event) {
        if(event.getEntity() instanceof ServerPlayer player) bar.removePlayer(player);
    }
    private void stop(ServerStoppingEvent event) { clear(); }
}
