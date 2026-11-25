package com.ksp.serverlist;

import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import java.nio.file.*;
import java.util.*;

@Mod("serverlist")
public final class ServerList {
    private static final org.slf4j.Logger LOG=com.mojang.logging.LogUtils.getLogger();
    private record Config(List<String> messages,int seconds){}
    private Config config;
    private MinecraftServer server;
    private String original;
    private long started;
    private int ticks;
    public ServerList(){
        var bus=MinecraftForge.EVENT_BUS;
        bus.addListener(this::start);bus.addListener(this::stop);bus.addListener(this::tick);bus.addListener(this::register);
    }
    private Config load()throws Exception{
        Path path=FMLPaths.CONFIGDIR.get().resolve("serverlist.properties");
        Files.createDirectories(path.getParent());
        if(!Files.exists(path))Files.writeString(path,
            "# Use & color codes, {newline}, {online}, {max} and {version}.\nrotationSeconds=15\nmessage.1=&5&lMYTHOS &8| &dShattered Worlds{newline}&7A new MMORPG adventure awaits\nmessage.2=&5&lMYTHOS &8| &dShattered Worlds{newline}&a{online}&7/&a{max} &7Adventurers online\n",
            StandardOpenOption.CREATE_NEW);
        Properties p=new Properties();
        try(var reader=Files.newBufferedReader(path)){p.load(reader);}
        int seconds=Integer.parseInt(p.getProperty("rotationSeconds","15").trim());
        if(seconds<5||seconds>3600)throw new IllegalArgumentException("rotationSeconds must be 5–3600");
        List<String> messages=new ArrayList<>();
        for(int i=1;i<=32;i++){String m=p.getProperty("message."+i);if(m!=null&&!m.isBlank()){if(m.length()>512)throw new IllegalArgumentException("MOTD too long");messages.add(m);}}
        if(messages.isEmpty())throw new IllegalArgumentException("At least one message.N is required");
        return new Config(List.copyOf(messages),seconds);
    }
    private void start(ServerStartedEvent e){server=e.getServer();original=server.getMotd();ticks=0;reload();}
    private boolean reload(){
        try{Config next=load();config=next;started=System.nanoTime();apply();return true;}
        catch(Exception ex){LOG.error("Server List configuration rejected; retaining previous MOTD",ex);return false;}
    }
    private void stop(ServerStoppingEvent e){if(original!=null)e.getServer().setMotd(original);server=null;config=null;}
    private void tick(TickEvent.ServerTickEvent.Post e){if(server==e.getServer()&&++ticks%20==0)apply();}
    private void apply(){
        if(server==null||config==null)return;
        long elapsed=Math.max(0,(System.nanoTime()-started)/1_000_000_000L);
        int index=(int)((elapsed/config.seconds())%config.messages().size());
        String motd=config.messages().get(index).replace("{newline}","\n")
            .replace("{online}",Integer.toString(server.getPlayerList().getPlayerCount()))
            .replace("{max}",Integer.toString(server.getPlayerList().getMaxPlayers()))
            .replace("{version}",server.getServerVersion());
        server.setMotd(colors(motd));
    }
    static String colors(String value){
        StringBuilder b=new StringBuilder();
        for(int i=0;i<value.length();i++){
            char c=value.charAt(i);
            if(c=='&'&&i+1<value.length()){
                char n=Character.toLowerCase(value.charAt(i+1));
                if(n=='&'){b.append('&');i++;continue;}
                if("0123456789abcdefklmnor".indexOf(n)>=0){b.append('\u00a7').append(n);i++;continue;}
            }
            b.append(c);
        }
        return b.toString();
    }
    private void register(RegisterCommandsEvent e){
        e.getDispatcher().register(Commands.literal("motd").requires(s->s.hasPermission(2))
            .then(Commands.literal("reload").executes(c->{
                if(!reload()){c.getSource().sendFailure(Component.literal("Invalid serverlist.properties; previous configuration retained."));return 0;}
                c.getSource().sendSuccess(()->Component.literal("Server List configuration reloaded."),false);return 1;
            }))
            .then(Commands.literal("preview").executes(c->{c.getSource().sendSuccess(()->Component.literal(c.getSource().getServer().getMotd()),false);return 1;})));
    }
}
