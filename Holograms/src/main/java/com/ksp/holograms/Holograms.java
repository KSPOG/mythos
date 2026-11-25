package com.ksp.holograms;

import com.google.gson.Gson;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

@Mod("holograms")
public final class Holograms {
    private static final Gson JSON=new Gson();
    private static final String PREFIX="ksp_holo_";
    public Holograms(){MinecraftForge.EVENT_BUS.addListener(this::register);}
    private static String id(com.mojang.brigadier.context.CommandContext<CommandSourceStack> c){
        return StringArgumentType.getString(c,"id").toLowerCase(Locale.ROOT);
    }
    private static boolean valid(CommandSourceStack s,String id){
        if(id.matches("[a-z0-9_-]{1,32}"))return true;
        s.sendFailure(Component.literal("Hologram ID must contain 1–32 letters, digits, underscores or hyphens."));return false;
    }
    private static String selector(String id){return "@e[type=minecraft:text_display,tag=ksp_hologram,tag="+PREFIX+id+",distance=..128,sort=nearest,limit=1]";}
    private static String payload(String text){
        // JSON string component inside an SNBT string. No user-controlled command syntax.
        StringBuilder b=new StringBuilder();
        for(int i=0;i<text.length();i++){
            char ch=text.charAt(i);
            if(ch=='&'&&i+1<text.length()){
                char n=Character.toLowerCase(text.charAt(i+1));
                if(n=='&'){b.append('&');i++;continue;}
                if("0123456789abcdefklmnor".indexOf(n)>=0){b.append('\u00a7').append(n);i++;continue;}
            }
            b.append(ch);
        }
        return StringTag.valueOf(JSON.toJson(Map.of("text",b.toString().replace("{newline}","\n")))).toString();
    }
    private static int run(CommandSourceStack s,String command)throws com.mojang.brigadier.exceptions.CommandSyntaxException{
        return s.getServer().getCommands().getDispatcher().execute(command,s);
    }
    private static int create(CommandSourceStack s,String id,String text)throws com.mojang.brigadier.exceptions.CommandSyntaxException{
        if(!valid(s,id)||!length(s,text))return 0;
        s.getPlayerOrException();
        return run(s,"execute unless entity "+selector(id)+" run summon minecraft:text_display ~ ~2.2 ~ {Tags:[\"ksp_hologram\",\""+PREFIX+id+"\"],text:"+payload(text)+",billboard:\"center\",see_through:0b,shadow:1b,line_width:250,view_range:1.0f,background:0}");
    }
    private static boolean length(CommandSourceStack s,String text){
        if(!text.isBlank()&&text.length()<=1024)return true;
        s.sendFailure(Component.literal("Hologram text must contain 1–1024 characters."));return false;
    }
    private void register(RegisterCommandsEvent event){
        event.getDispatcher().register(Commands.literal("hologram").requires(s->s.hasPermission(2))
            .then(Commands.literal("create").then(Commands.argument("id",StringArgumentType.word())
                .then(Commands.argument("text",StringArgumentType.greedyString()).executes(c->create(c.getSource(),id(c),StringArgumentType.getString(c,"text"))))))
            .then(Commands.literal("text").then(Commands.argument("id",StringArgumentType.word())
                .then(Commands.argument("text",StringArgumentType.greedyString()).executes(c->{
                    String id=id(c),text=StringArgumentType.getString(c,"text");
                    return valid(c.getSource(),id)&&length(c.getSource(),text)?run(c.getSource(),"data merge entity "+selector(id)+" {text:"+payload(text)+"}"):0;
                }))))
            .then(Commands.literal("move").then(Commands.argument("id",StringArgumentType.word()).executes(c->{
                c.getSource().getPlayerOrException();String id=id(c);
                return valid(c.getSource(),id)?run(c.getSource(),"tp "+selector(id)+" ~ ~2.2 ~"):0;
            })))
            .then(Commands.literal("scale").then(Commands.argument("id",StringArgumentType.word())
                .then(Commands.argument("scale",FloatArgumentType.floatArg(.25f,5f)).executes(c->{
                    String id=id(c);float f=FloatArgumentType.getFloat(c,"scale");
                    return valid(c.getSource(),id)?run(c.getSource(),"data merge entity "+selector(id)+" {transformation:{translation:[0f,0f,0f],left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],scale:["+f+"f,"+f+"f,"+f+"f]}}"):0;
                }))))
            .then(Commands.literal("delete").then(Commands.argument("id",StringArgumentType.word()).executes(c->{
                String id=id(c);return valid(c.getSource(),id)?run(c.getSource(),"kill "+selector(id)):0;
            })))
            .then(Commands.literal("list").executes(c->run(c.getSource(),"execute as @e[type=minecraft:text_display,tag=ksp_hologram,distance=..128] run data get entity @s Tags"))));
    }
}
