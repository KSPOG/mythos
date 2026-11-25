package com.ksp.randomteleport;

import com.mojang.brigadier.Command;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import java.nio.file.*;
import java.util.Properties;

@Mod("randomteleport")
public final class RandomTeleport {
    public RandomTeleport(){MinecraftForge.EVENT_BUS.addListener(this::register);}
    private void register(RegisterCommandsEvent event){
        event.getDispatcher().register(Commands.literal("rtp")
            .requires(s->s.hasPermission(2))
            .executes(c->{
                ServerPlayer p=c.getSource().getPlayerOrException();
                if(!p.level().dimension().equals(Level.OVERWORLD)){
                    c.getSource().sendFailure(Component.literal("Random teleport is currently limited to the Overworld."));
                    return 0;
                }
                int radius=radius();
                // Use the vanilla spreadplayers implementation, including its terrain
                // search and failure handling. Never elevate the player's permissions.
                String command="spreadplayers ~ ~ 0 "+radius+" false @s";
                c.getSource().getServer().getCommands().performPrefixedCommand(c.getSource(),command);
                return Command.SINGLE_SUCCESS;
            }));
    }
    private int radius(){
        Path path=FMLPaths.CONFIGDIR.get().resolve("randomteleport.properties");
        try{
            Files.createDirectories(path.getParent());
            if(!Files.exists(path))Files.writeString(path,
                "# Maximum horizontal offset from the current position, in blocks.\nradius=5000\n",
                StandardOpenOption.CREATE_NEW);
            var props=new Properties();
            try(var reader=Files.newBufferedReader(path)){props.load(reader);}
            return Math.max(1,Math.min(10000,Integer.parseInt(props.getProperty("radius","5000").trim())));
        }catch(Exception ex){
            com.mojang.logging.LogUtils.getLogger().warn("Invalid RTP configuration; using default radius",ex);
            return 5000;
        }
    }
}
