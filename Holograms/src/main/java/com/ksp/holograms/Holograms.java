package com.ksp.holograms;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.world.entity.Display;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
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
    private static final String FX="ksp_effects";
    private static final String BASE="ksp_effect_base";
    private static final String PAUSED="ksp_effect_paused";
    private static final String PERIOD="ksp_effect_period";
    private static final String MESSAGES="ksp_effect_messages";
    private static final Set<String> EFFECTS=Set.of("rainbow","gradient","pulse","fade","blink","typewriter","shimmer","scramble","vertical","messages","floating","rotation","swing","orbit","shake","rise","slide","wave","sparkles","arcane","fire","soul","spiral","healing","frost","dynamic");
    private static final Map<String,String> PRESETS=Map.of("legendary","shimmer,floating,sparkles","arcane","gradient,orbit,arcane","danger","pulse,shake,fire","divine","shimmer,floating,sparkles","frost","shimmer,frost","necromancer","pulse,soul,floating","royal","shimmer,pulse","portal","rotation,spiral");
    private static final String PREFIX="ksp_holo_";
    private static final String SCROLL="ksp_hologram_scroll";
    private static final String SOURCE="ksp_hologram_scroll_source";
    private static final String WIDTH="ksp_hologram_scroll_width";
    private static final String SPEED="ksp_hologram_scroll_speed";
    private final Map<UUID,Display> active=new LinkedHashMap<>();
    private final Map<UUID,Long> phases=new HashMap<>();
    private long ticks;
    public Holograms(){
        var bus=MinecraftForge.EVENT_BUS;
        bus.addListener(this::register);bus.addListener(this::join);bus.addListener(this::tick);bus.addListener(this::stop);
    }
    private void join(EntityJoinLevelEvent e){
        if(e.getLevel() instanceof ServerLevel && e.getEntity() instanceof Display d && d.getTags().contains("ksp_hologram") && animated(d)){
            active.put(d.getUUID(),d);phases.put(d.getUUID(),ticks);
        }
    }
    private static boolean animated(Display d){
        return d.getPersistentData().getBoolean(SCROLL)||!d.getPersistentData().getString(FX).isEmpty();
    }
    private static Set<String> effects(Display d){
        Set<String> result=new LinkedHashSet<>(Arrays.asList(d.getPersistentData().getString(FX).split(",")));
        result.retainAll(EFFECTS);return result;
    }
    private static boolean nearby(Display d){
        for(var p:((ServerLevel)d.level()).players())if(p.distanceToSqr(d)<=64*64)return true;
        return false;
    }
    private void activate(Display d){
        active.put(d.getUUID(),d);phases.put(d.getUUID(),ticks);
    }
    private static CompoundTag snapshot(Display d){
        CompoundTag tag=new CompoundTag();d.saveWithoutId(tag);return tag;
    }
    private static void mergeVisual(Display d,CompoundTag patch){
        CompoundTag tag=snapshot(d);tag.merge(patch);d.load(tag);
    }
    private void capture(Display d){
        var data=d.getPersistentData();
        if(!data.contains(BASE)){
            CompoundTag snap=snapshot(d),base=new CompoundTag();
            for(String key:List.of("transformation","text_opacity","billboard","interpolation_duration","start_interpolation"))
                if(snap.contains(key))base.put(key,snap.get(key).copy());
            data.put(BASE,base);
        }
        if(d instanceof Display.TextDisplay text&&!data.contains(SOURCE)){
            Component existing=text.getText();
            setSource(text,existing.getString().indexOf('\u00a7')>=0?decode(existing.getString().replace("&","&&")):existing.copy());
        }
    }
    private void restoreVisual(Display d){
        var data=d.getPersistentData();
        if(data.contains(BASE))mergeVisual(d,data.getCompound(BASE).copy());
    }
    private void resetEffects(Display d){
        restoreVisual(d);
        var data=d.getPersistentData();
        data.remove(FX);data.remove(BASE);data.remove(PAUSED);data.remove(PERIOD);data.remove(MESSAGES);
        if(!data.getBoolean(SCROLL)){
            if(d instanceof Display.TextDisplay text)text.setText(source(text));data.remove(SOURCE);active.remove(d.getUUID());phases.remove(d.getUUID());
        }else activate(d);
    }
    private int effect(CommandSourceStack s,String id,String action,String value,int number){
        if(!valid(s,id))return 0;var displays=findAll(s,id);if(displays.isEmpty())return 0;
        int changed=0;for(var d:displays)changed+=effectOne(s,d,action,value,number);return changed;
    }
    private static final Set<String> OBJECT_EFFECTS=Set.of("pulse","floating","rotation","swing","orbit","shake","rise","slide","sparkles","arcane","fire","soul","spiral","healing","frost");
    private int effectOne(CommandSourceStack s,Display d,String action,String value,int number){
        var data=d.getPersistentData();var set=effects(d);
        boolean text=d instanceof Display.TextDisplay;
        if(!text&&(action.equals("messages")||(action.equals("add")&&!OBJECT_EFFECTS.contains(value)))){s.sendFailure(Component.literal("Text-only effect skipped for item/block display."));return 0;}
        switch(action){
            case "list" -> {s.sendSuccess(()->Component.literal("Effects: "+set+" | available: "+String.join(", ",new TreeSet<>(EFFECTS))),false);return 1;}
            case "clear" -> {resetEffects(d);return 1;}
            case "pause" -> {data.putBoolean(PAUSED,true);return 1;}
            case "resume" -> {data.remove(PAUSED);activate(d);return 1;}
            case "speed" -> {data.putInt(PERIOD,number);return 1;}
            case "messages" -> {if(!length(s,value))return 0;capture(d);data.putString(MESSAGES,value);set.add("messages");}
            case "preset" -> {if(!PRESETS.containsKey(value)){s.sendFailure(Component.literal("Unknown preset."));return 0;}capture(d);set.addAll(Arrays.asList(PRESETS.get(value).split(",")));if(!text)set.retainAll(OBJECT_EFFECTS);}
            case "add" -> {if(!EFFECTS.contains(value)){s.sendFailure(Component.literal("Unknown effect."));return 0;}capture(d);set.add(value);}
            case "remove" -> {set.remove(value);restoreVisual(d);}
            default -> {return 0;}
        }
        if(set.isEmpty()){resetEffects(d);return 1;}
        d.getPersistentData().putString(FX,String.join(",",set));activate(d);
        s.sendSuccess(()->Component.literal("Effects: "+String.join(", ",set)),false);return 1;
    }
    private static int rgb(double hue){
        double h=(hue-Math.floor(hue))*6;int i=(int)h;double f=h-i;int v=255,p=71,q=(int)(255*(1-.72*f)),t=(int)(255*(1-.72*(1-f)));return switch(i){case 0->v<<16|t<<8|p;case 1->q<<16|v<<8|p;case 2->p<<16|v<<8|t;case 3->p<<16|q<<8|v;case 4->t<<16|p<<8|v;default->v<<16|p<<8|q;};
    }
    private Component textFrame(Display.TextDisplay d,Set<String> fx,long elapsed){
        var data=d.getPersistentData();Component c=source(d);
        int period=data.contains(PERIOD)?Math.max(10,data.getInt(PERIOD)):80;
        if(fx.contains("messages")){
            String[] messages=data.getString(MESSAGES).split("\\|",-1);
            if(messages.length>0&&!data.getString(MESSAGES).isEmpty())c=decode(messages[(int)((elapsed/period)%messages.length)].replace("{newline}","\n"));
        }
        if(fx.contains("dynamic")){
            MutableComponent out=Component.empty();
            for(Glyph g:glyphs(c))out.append(Component.literal(g.text()).setStyle(g.style()));
            // Replace complete placeholder strings using the plain source; keep a uniform first style.
            var gs=glyphs(c);Style st=gs.isEmpty()?Style.EMPTY:gs.getFirst().style();
            var server=((ServerLevel)d.level()).getServer();
            c=Component.literal(c.getString().replace("{online}",Integer.toString(server.getPlayerList().getPlayerCount())).replace("{max}",Integer.toString(server.getPlayerList().getMaxPlayers())).replace("{time}",Long.toString(d.level().getDayTime()%24000))).setStyle(st);
        }
        if(fx.contains("vertical")){
            List<List<Glyph>> lines=new ArrayList<>();lines.add(new ArrayList<>());
            for(Glyph g:glyphs(c)){if(g.text().equals("\n"))lines.add(new ArrayList<>());else lines.getLast().add(g);}
            MutableComponent out=Component.empty();
            for(int i=0;i<Math.min(3,lines.size());i++){
                if(i>0)out.append("\n");
                for(Glyph g:lines.get((int)((elapsed/period+i)%lines.size())))out.append(Component.literal(g.text()).setStyle(g.style()));
            }c=out;
        }
        if(data.getBoolean(SCROLL))c=frame(c,Math.max(4,Math.min(80,data.getInt(WIDTH))),elapsed/Math.max(2,data.getInt(SPEED)));
        List<Glyph> gs=glyphs(c);MutableComponent out=Component.empty();
        int reveal=(int)((elapsed/2)%(gs.size()+20L));
        double phase=(double)(elapsed%period)/period;
        for(int i=0;i<gs.size();i++){
            Glyph g=gs.get(i);String value=g.text();Style style=g.style();
            if(fx.contains("typewriter")&&i>=reveal)break;
            if(fx.contains("scramble")&&i>=reveal&&!value.isBlank())value=String.valueOf((char)('A'+Math.floorMod(i*17+elapsed/2,26)));
            if(fx.contains("rainbow")||fx.contains("gradient"))style=style.withColor(rgb(phase+(fx.contains("gradient")?i*.045:0)));
            if(fx.contains("shimmer"))style=style.withColor(Math.abs((i%Math.max(1,gs.size()))/(double)Math.max(1,gs.size())-phase)<.12?0xffffee:0xffbb44);
            if(fx.contains("wave"))style=style.withColor(rgb(.72+.08*Math.sin(i*.5+phase*Math.PI*2)));
            out.append(Component.literal(value).setStyle(style));
        }
        return out;
    }
    private void visualFrame(Display d,Set<String> fx,long elapsed){
        var data=d.getPersistentData();int period=data.contains(PERIOD)?Math.max(10,data.getInt(PERIOD)):80;
        double p=2*Math.PI*(elapsed%period)/period;
        boolean motion=!Collections.disjoint(fx,Set.of("pulse","floating","rotation","swing","orbit","shake","rise","slide"));
        boolean opacity=d instanceof Display.TextDisplay&&!Collections.disjoint(fx,Set.of("fade","blink","rise"));
        if(motion||opacity){
            CompoundTag base=data.getCompound(BASE),patch=new CompoundTag();
            if(motion){
                // Compose with the saved vanilla transformation, preserving its scale and translation.
                CompoundTag transform=base.getCompound("transformation").copy();
                var translation=transform.getList("translation",5);var scale=transform.getList("scale",5);
                double x=translation.size()==3?translation.getFloat(0):0,y=translation.size()==3?translation.getFloat(1):0,z=translation.size()==3?translation.getFloat(2):0;
                if(fx.contains("floating"))y+=.3*Math.sin(p);
                if(fx.contains("orbit")){x+=.5*Math.cos(p);z+=.5*Math.sin(p);}
                if(fx.contains("shake"))x+=.05*Math.sin(p*16);
                if(fx.contains("slide"))x+=.5*Math.sin(p);
                if(fx.contains("rise"))y+=(elapsed%period)/(double)period;
                transform.put("translation",floats(x,y,z));
                if(fx.contains("pulse")){
                    double f=1+.15*Math.sin(p);
                    transform.put("scale",floats((scale.size()==3?scale.getFloat(0):1)*f,(scale.size()==3?scale.getFloat(1):1)*f,(scale.size()==3?scale.getFloat(2):1)*f));
                }
                if(fx.contains("rotation")||fx.contains("swing")){
                    double angle=fx.contains("rotation")?p:.2*Math.sin(p);
                    transform.put("left_rotation",fx.contains("rotation")?floats(0,Math.sin(angle/2),0,Math.cos(angle/2)):floats(0,0,Math.sin(angle/2),Math.cos(angle/2)));
                    if(fx.contains("rotation"))patch.putString("billboard","fixed");
                }
                patch.put("transformation",transform);patch.putInt("interpolation_duration",4);patch.putInt("start_interpolation",0);
            }
            if(opacity){
                double alpha=fx.contains("blink")?(Math.sin(p)>=0?1:0):fx.contains("rise")?1-(elapsed%period)/(double)period:.5+.5*Math.sin(p);
                patch.putByte("text_opacity",(byte)Math.round(255*alpha));
            }
            mergeVisual(d,patch);
        }
        if(elapsed%20==0){
            ServerLevel level=(ServerLevel)d.level();
            var particle=fx.contains("fire")?net.minecraft.core.particles.ParticleTypes.FLAME:fx.contains("soul")?net.minecraft.core.particles.ParticleTypes.SOUL_FIRE_FLAME:fx.contains("frost")?net.minecraft.core.particles.ParticleTypes.SNOWFLAKE:fx.contains("healing")?net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER:fx.contains("arcane")||fx.contains("spiral")?net.minecraft.core.particles.ParticleTypes.PORTAL:fx.contains("sparkles")?net.minecraft.core.particles.ParticleTypes.END_ROD:null;
            if(particle!=null)for(int i=0;i<6;i++){double a=p+i*Math.PI/3;level.sendParticles(particle,d.getX()+.65*Math.cos(a),d.getY()+(fx.contains("spiral")?i*.13:.2),d.getZ()+.65*Math.sin(a),1,0,0,0,0);}
        }
    }
    private static net.minecraft.nbt.ListTag floats(double... values){
        var list=new net.minecraft.nbt.ListTag();for(double n:values)list.add(net.minecraft.nbt.FloatTag.valueOf((float)n));return list;
    }
    private void stop(ServerStoppingEvent e){active.clear();phases.clear();ticks=0;}
    private static List<Display> findAll(CommandSourceStack s,String id){
        List<Display> matches=new ArrayList<>();
        var bounds=new net.minecraft.world.phys.AABB(s.getPosition(),s.getPosition()).inflate(128);
        for(Entity e:s.getLevel().getEntities((Entity)null,bounds,e->e instanceof Display&&e.getTags().contains("ksp_hologram")&&e.getTags().contains(PREFIX+id))){
            if(e.position().distanceToSqr(s.getPosition())<=128*128)matches.add((Display)e);
        }
        matches.sort(Comparator.comparingDouble(d->d.position().distanceToSqr(s.getPosition())));
        if(matches.isEmpty())s.sendFailure(Component.literal("No loaded hologram with that ID within 128 blocks."));
        return matches;
    }
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
            if(!data.getBoolean(SCROLL)&&!data.contains(SOURCE)){Component existing=d.getText();setSource(d,existing.getString().indexOf('\u00a7')>=0?decode(existing.getString().replace("&","&&")):existing.copy());}
            data.putBoolean(SCROLL,true);data.putInt(WIDTH,width);data.putInt(SPEED,speed);
            active.put(d.getUUID(),d);phases.put(d.getUUID(),ticks);
            d.setText(frame(source(d),width,0));
        }else{
            if(data.getBoolean(SCROLL))d.setText(source(d));
            data.remove(SCROLL);data.remove(WIDTH);data.remove(SPEED);
            if(effects(d).isEmpty()){data.remove(SOURCE);active.remove(d.getUUID());phases.remove(d.getUUID());}else activate(d);
        }
        s.sendSuccess(()->Component.literal(enable?"Scrolling enabled: "+width+" characters, one step every "+speed+" ticks.":"Scrolling disabled; full text restored."),false);
        return 1;
    }
    private void tick(TickEvent.ServerTickEvent.Post e){
        ticks++;
        var it=active.entrySet().iterator();
        while(it.hasNext()){
            var entry=it.next();var d=entry.getValue();
            if(d.isRemoved()||!animated(d)){
                phases.remove(entry.getKey());it.remove();continue;
            }
            if(!nearby(d)||d.getPersistentData().getBoolean(PAUSED))continue;
            int speed=Math.max(2,Math.min(100,d.getPersistentData().getInt(SPEED)));
            long elapsed=ticks-phases.getOrDefault(entry.getKey(),ticks);
            var fx=effects(d);
            if(!fx.isEmpty()){
                if(elapsed%4==0)visualFrame(d,fx,elapsed);
                if(d instanceof Display.TextDisplay text&&elapsed%(d.getPersistentData().getBoolean(SCROLL)?speed:4)==0)text.setText(textFrame(text,fx,elapsed));
                continue;
            }
            if(elapsed%speed!=0)continue;
            int width=Math.max(4,Math.min(80,d.getPersistentData().getInt(WIDTH)));
            if(d instanceof Display.TextDisplay text)text.setText(frame(source(text),width,elapsed/speed));
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
    private static String tags(String id){return "Tags:[\"ksp_hologram\",\""+PREFIX+id+"\"]";}
    private static int object(CommandSourceStack s,String id,String raw,boolean item)throws com.mojang.brigadier.exceptions.CommandSyntaxException{
        if(!valid(s,id))return 0;s.getPlayerOrException();
        ResourceLocation key=ResourceLocation.tryParse(raw);
        if(key==null||(item?!BuiltInRegistries.ITEM.containsKey(key):!BuiltInRegistries.BLOCK.containsKey(key))||key.toString().equals("minecraft:air")){
            s.sendFailure(Component.literal("Unknown or empty "+(item?"item":"block")+": "+raw));return 0;
        }
        String type=item?"item_display":"block_display";
        String contents=item?"item:{id:\""+key+"\",count:1},item_display:\"fixed\"":"block_state:{Name:\""+key+"\"},transformation:{translation:[-0.5f,-0.5f,-0.5f],scale:[1f,1f,1f],left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f]}";
        String existing="@e[type=minecraft:"+type+",tag=ksp_hologram,tag="+PREFIX+id+",distance=..128,limit=1]";
        return run(s,"execute unless entity "+existing+" run summon minecraft:"+type+" ~ ~1.2 ~ {"+tags(id)+","+contents+",billboard:\"fixed\",view_range:1.0f}");
    }
    private int moveGroup(CommandSourceStack s,String id)throws com.mojang.brigadier.exceptions.CommandSyntaxException{
        if(!valid(s,id))return 0;s.getPlayerOrException();var group=findAll(s,id);if(group.isEmpty())return 0;
        Vec3 anchor=group.getFirst().position();Vec3 target=s.getPosition().add(0,2.2,0);
        for(var d:group){Vec3 p=target.add(d.position().subtract(anchor));d.teleportTo(p.x,p.y,p.z);}
        return group.size();
    }
    private int scaleGroup(CommandSourceStack s,String id,float f){
        if(!valid(s,id))return 0;var group=findAll(s,id);
        for(var d:group){
            var data=d.getPersistentData();
            CompoundTag base=data.contains(BASE)?data.getCompound(BASE).copy():snapshot(d);
            CompoundTag transform=base.getCompound("transformation").copy();
            transform.put("scale",floats(f,f,f));
            if(d instanceof Display.BlockDisplay)transform.put("translation",floats(-f/2,-f/2,-f/2));
            CompoundTag patch=new CompoundTag();patch.put("transformation",transform);
            mergeVisual(d,patch);
            if(data.contains(BASE)){var current=d.getPersistentData().getCompound(BASE);current.put("transformation",transform.copy());}
        }
        return group.size();
    }
    private static int deleteGroup(CommandSourceStack s,String id){
        if(!valid(s,id))return 0;var group=findAll(s,id);for(var d:group)d.discard();return group.size();
    }
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
            .then(Commands.literal("item").then(Commands.argument("id",StringArgumentType.word())
                .then(Commands.argument("item",net.minecraft.commands.arguments.ResourceLocationArgument.id())
                    .suggests((c,b)->net.minecraft.commands.SharedSuggestionProvider.suggestResource(BuiltInRegistries.ITEM.keySet(),b))
                    .executes(c->object(c.getSource(),id(c),net.minecraft.commands.arguments.ResourceLocationArgument.getId(c,"item").toString(),true)))))
            .then(Commands.literal("block").then(Commands.argument("id",StringArgumentType.word())
                .then(Commands.argument("block",net.minecraft.commands.arguments.ResourceLocationArgument.id())
                    .suggests((c,b)->net.minecraft.commands.SharedSuggestionProvider.suggestResource(BuiltInRegistries.BLOCK.keySet(),b))
                    .executes(c->object(c.getSource(),id(c),net.minecraft.commands.arguments.ResourceLocationArgument.getId(c,"block").toString(),false)))))
            .then(Commands.literal("text").then(Commands.argument("id",StringArgumentType.word())
                .then(Commands.argument("text",StringArgumentType.greedyString()).executes(c->{
                    String id=id(c),text=StringArgumentType.getString(c,"text");
                    if(!valid(c.getSource(),id)||!length(c.getSource(),text))return 0;
                    var d=find(c.getSource(),id);if(d==null)return 0;
                    Component component=decode(text.replace("{newline}","\n"));
                    if(animated(d)){
                        setSource(d,component);phases.put(d.getUUID(),ticks);
                        d.setText(textFrame(d,effects(d),0));
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
            .then(Commands.literal("effect").then(Commands.argument("id",StringArgumentType.word())
                .then(Commands.literal("add").then(Commands.argument("effect",StringArgumentType.word())
                    .suggests((c,b)->net.minecraft.commands.SharedSuggestionProvider.suggest(EFFECTS,b))
                    .executes(c->effect(c.getSource(),id(c),"add",StringArgumentType.getString(c,"effect"),0))))
                .then(Commands.literal("remove").then(Commands.argument("effect",StringArgumentType.word())
                    .suggests((c,b)->net.minecraft.commands.SharedSuggestionProvider.suggest(EFFECTS,b))
                    .executes(c->effect(c.getSource(),id(c),"remove",StringArgumentType.getString(c,"effect"),0))))
                .then(Commands.literal("list").executes(c->effect(c.getSource(),id(c),"list","",0)))
                .then(Commands.literal("clear").executes(c->effect(c.getSource(),id(c),"clear","",0)))
                .then(Commands.literal("pause").executes(c->effect(c.getSource(),id(c),"pause","",0)))
                .then(Commands.literal("resume").executes(c->effect(c.getSource(),id(c),"resume","",0)))
                .then(Commands.literal("speed").then(Commands.argument("ticks",IntegerArgumentType.integer(10,1200))
                    .executes(c->effect(c.getSource(),id(c),"speed","",IntegerArgumentType.getInteger(c,"ticks")))))
                .then(Commands.literal("messages").then(Commands.argument("messages",StringArgumentType.greedyString())
                    .executes(c->effect(c.getSource(),id(c),"messages",StringArgumentType.getString(c,"messages"),0))))))
            .then(Commands.literal("preset").then(Commands.argument("id",StringArgumentType.word())
                .then(Commands.argument("preset",StringArgumentType.word())
                    .suggests((c,b)->net.minecraft.commands.SharedSuggestionProvider.suggest(PRESETS.keySet(),b))
                    .executes(c->effect(c.getSource(),id(c),"preset",StringArgumentType.getString(c,"preset"),0)))))
            .then(Commands.literal("move").then(Commands.argument("id",StringArgumentType.word()).executes(c->{
                return moveGroup(c.getSource(),id(c));
            })))
            .then(Commands.literal("scale").then(Commands.argument("id",StringArgumentType.word())
                .then(Commands.argument("scale",FloatArgumentType.floatArg(.25f,5f)).executes(c->{
                    String id=id(c);float f=FloatArgumentType.getFloat(c,"scale");
                    return scaleGroup(c.getSource(),id,f);
                }))))
            .then(Commands.literal("delete").then(Commands.argument("id",StringArgumentType.word()).executes(c->{
                return deleteGroup(c.getSource(),id(c));
            })))
            .then(Commands.literal("list").executes(c->run(c.getSource(),"execute as @e[tag=ksp_hologram,distance=..128] run data get entity @s Tags"))));
    }
}
