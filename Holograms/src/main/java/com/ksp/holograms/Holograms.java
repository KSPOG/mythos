package com.ksp.holograms;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.ChatFormatting;
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
    private static final String PREFIX="ksp_holo_";
    private static final String SCROLL="ksp_hologram_scroll";
    private static final String SOURCE="ksp_hologram_scroll_source";
    private static final String WIDTH="ksp_hologram_scroll_width";
    private static final String SPEED="ksp_hologram_scroll_speed";
    private final Map<UUID,Display.TextDisplay> active=new LinkedHashMap<>();
    private final Map<UUID,Long> phases=new HashMap<>();
    private long ticks;
    public Holograms(){
        var bus=MinecraftForge.EVENT_BUS;
        bus.addListener(this::register);bus.addListener(this::join);bus.addListener(this::tick);bus.addListener(this::stop);
    }
    private void join(EntityJoinLevelEvent e){
        if(e.getLevel() instanceof ServerLevel && e.getEntity() instanceof Display.TextDisplay d && d.getTags().contains("ksp_hologram") && d.getPersistentData().getBoolean(SCROLL)){
            active.put(d.getUUID(),d);phases.put(d.getUUID(),ticks);
        }
    }
    private void stop(ServerStoppingEvent e){active.clear();phases.clear();ticks=0;}
    private static Display.TextDisplay find(CommandSourceStack s,String id){
        Display.TextDisplay best=null;double dist=128*128;
        for(Entity entity:s.getLevel().getEntities(net.minecraft.world.entity.EntityType.TEXT_DISPLAY,new net.minecraft.world.phys.AABB(s.getPosition(),s.getPosition()).inflate(128),d->true)){
            if(!(entity instanceof Display.TextDisplay d)||!d.getTags().contains("ksp_hologram")||!d.getTags().contains(PREFIX+id))continue;
            double n=d.position().distanceToSqr(s.getPosition());
            if(n<=dist){dist=n;best=d;}
        }
        if(best==null)s.sendFailure(Component.literal("No loaded hologram with that ID within 128 blocks."));
        return best;
    }
    private static Component decode(String text){
        MutableComponent result=Component.empty();Style style=Style.EMPTY;
        StringBuilder run=new StringBuilder();
        for(int i=0;i<text.length();i++){
            char c=text.charAt(i);
            if((c=='&'||c=='\u00a7')&&i+1<text.length()){
                char n=Character.toLowerCase(text.charAt(i+1));
                if(c=='&'&&n=='&'){run.append('&');i++;continue;}
                ChatFormatting fmt=ChatFormatting.getByCode(n);
                if(fmt!=null){
                    if(!run.isEmpty()){result.append(Component.literal(run.toString()).setStyle(style));run.setLength(0);}
                    style=fmt==ChatFormatting.RESET?Style.EMPTY:style.applyLegacyFormat(fmt);i++;continue;
                }
            }
            run.append(c);
        }
        if(!run.isEmpty())result.append(Component.literal(run.toString()).setStyle(style));
        return result;
    }
    private record Glyph(String text,Style style){}
    private static List<Glyph> glyphs(Component text){
        List<Glyph> result=new ArrayList<>();
        text.visit((style,value)->{
            value.codePoints().forEach(cp->result.add(new Glyph(new String(Character.toChars(cp)),style)));
            return Optional.empty();
        },Style.EMPTY);
        return result;
    }
    private static Component frame(Component source,int width,long offset){
        // Scroll each long line independently, preserving formatting and Unicode code points.
        List<List<Glyph>> lines=new ArrayList<>();lines.add(new ArrayList<>());
        for(Glyph g:glyphs(source)){
            if(g.text().equals("\n"))lines.add(new ArrayList<>());
            else lines.getLast().add(g);
        }
        MutableComponent result=Component.empty();
        for(int l=0;l<lines.size();l++){
            if(l>0)result.append("\n");
            var line=lines.get(l);
            if(line.size()<=width){
                for(Glyph g:line)result.append(Component.literal(g.text()).setStyle(g.style()));
                continue;
            }
            int cycle=line.size()+4;
            for(int i=0;i<width;i++){
                int j=(int)Math.floorMod(offset+i,(long)cycle);
                Glyph g=j<line.size()?line.get(j):new Glyph(" ",Style.EMPTY);
                result.append(Component.literal(g.text()).setStyle(g.style()));
            }
        }
        return result;
    }
    private static Component source(Display.TextDisplay d){
        String json=d.getPersistentData().getString(SOURCE);
        if(json.isEmpty())return d.getText().copy();
        try{
            Component c=Component.Serializer.fromJson(json,d.level().registryAccess());
            if(c!=null)return c;
        }catch(Exception ignored){}
        return d.getText().copy();
    }
    private void setSource(Display.TextDisplay d,Component component){
        d.getPersistentData().putString(SOURCE,Component.Serializer.toJson(component,d.level().registryAccess()));
    }
    private int scroll(CommandSourceStack s,String id,boolean enable,int width,int speed){
        if(!valid(s,id))return 0;
        var d=find(s,id);if(d==null)return 0;
        var data=d.getPersistentData();
        if(enable){
            if(!data.getBoolean(SCROLL)){Component existing=d.getText();setSource(d,existing.getString().indexOf('\u00a7')>=0?decode(existing.getString().replace("&","&&")):existing.copy());}
            data.putBoolean(SCROLL,true);data.putInt(WIDTH,width);data.putInt(SPEED,speed);
            active.put(d.getUUID(),d);phases.put(d.getUUID(),ticks);
            d.setText(frame(source(d),width,0));
        }else{
            if(data.getBoolean(SCROLL))d.setText(source(d));
            data.remove(SCROLL);data.remove(SOURCE);data.remove(WIDTH);data.remove(SPEED);
            active.remove(d.getUUID());phases.remove(d.getUUID());
        }
        s.sendSuccess(()->Component.literal(enable?"Scrolling enabled: "+width+" characters, one step every "+speed+" ticks.":"Scrolling disabled; full text restored."),false);
        return 1;
    }
    private void tick(TickEvent.ServerTickEvent.Post e){
        ticks++;
        var it=active.entrySet().iterator();
        while(it.hasNext()){
            var entry=it.next();var d=entry.getValue();
            if(d.isRemoved()||!d.getPersistentData().getBoolean(SCROLL)){
                phases.remove(entry.getKey());it.remove();continue;
            }
            int speed=Math.max(2,Math.min(100,d.getPersistentData().getInt(SPEED)));
            long elapsed=ticks-phases.getOrDefault(entry.getKey(),ticks);
            if(elapsed%speed!=0)continue;
            int width=Math.max(4,Math.min(80,d.getPersistentData().getInt(WIDTH)));
            d.setText(frame(source(d),width,elapsed/speed));
        }
    }
    private static String id(com.mojang.brigadier.context.CommandContext<CommandSourceStack> c){
        return StringArgumentType.getString(c,"id").toLowerCase(Locale.ROOT);
    }
    private static boolean valid(CommandSourceStack s,String id){
        if(id.matches("[a-z0-9_-]{1,32}"))return true;
        s.sendFailure(Component.literal("Hologram ID must contain 1–32 letters, digits, underscores or hyphens."));return false;
    }
    private static String selector(String id){return "@e[type=minecraft:text_display,tag=ksp_hologram,tag="+PREFIX+id+",distance=..128,sort=nearest,limit=1]";}
    private static String payload(CommandSourceStack s,String text){
        // JSON component encoded as an SNBT string, never raw command syntax.
        return StringTag.valueOf(Component.Serializer.toJson(decode(text.replace("{newline}","\n")),s.getServer().registryAccess())).toString();
    }
    private static int run(CommandSourceStack s,String command)throws com.mojang.brigadier.exceptions.CommandSyntaxException{
        return s.getServer().getCommands().getDispatcher().execute(command,s);
    }
    private static int create(CommandSourceStack s,String id,String text)throws com.mojang.brigadier.exceptions.CommandSyntaxException{
        if(!valid(s,id)||!length(s,text))return 0;
        s.getPlayerOrException();
        return run(s,"execute unless entity "+selector(id)+" run summon minecraft:text_display ~ ~2.2 ~ {Tags:[\"ksp_hologram\",\""+PREFIX+id+"\"],text:"+payload(s,text)+",billboard:\"center\",see_through:0b,shadow:1b,line_width:250,view_range:1.0f,background:0}");
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
                    if(!valid(c.getSource(),id)||!length(c.getSource(),text))return 0;
                    var d=find(c.getSource(),id);if(d==null)return 0;
                    Component component=decode(text.replace("{newline}","\n"));
                    if(d.getPersistentData().getBoolean(SCROLL)){
                        setSource(d,component);phases.put(d.getUUID(),ticks);
                        d.setText(frame(component,Math.max(4,Math.min(80,d.getPersistentData().getInt(WIDTH))),0));
                    }else d.setText(component);
                    return 1;
                }))))
            .then(Commands.literal("scroll").then(Commands.argument("id",StringArgumentType.word())
                .then(Commands.literal("on").executes(c->scroll(c.getSource(),id(c),true,24,4))
                    .then(Commands.argument("width",IntegerArgumentType.integer(4,80))
                        .executes(c->scroll(c.getSource(),id(c),true,IntegerArgumentType.getInteger(c,"width"),4))
                        .then(Commands.argument("ticks",IntegerArgumentType.integer(2,100))
                            .executes(c->scroll(c.getSource(),id(c),true,IntegerArgumentType.getInteger(c,"width"),IntegerArgumentType.getInteger(c,"ticks"))))))
                .then(Commands.literal("off").executes(c->scroll(c.getSource(),id(c),false,24,4)))))
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
