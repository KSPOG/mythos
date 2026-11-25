package com.ksp.mythos.core;
import com.google.gson.*;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.*;
import net.minecraftforge.fml.common.Mod;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod("mythos_core")
public final class Core {
    public static final Logger LOG=LoggerFactory.getLogger("MYTHOS");
    public static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
    public static final String[] ATTR={"strength","dexterity","intelligence","vitality","wisdom","endurance","agility","luck"};
    public static final Set<String> CLASSES=Set.of("warrior","ranger","mage","rogue","cleric","necromancer");
    public static final List<Consumer<ServerPlayer>> PROFILE_LISTENERS=new ArrayList<>();
    public static State state;
    public static MinecraftServer server;
    private static Path file;
    public static final class Profile {
        public int level=1,points=0;
        public long xp=0,gold=0,bankGold=0;
        public String clazz="",clan="",name="";
        public Map<String,Integer> attrs=new HashMap<>();
        public List<String> vault=new ArrayList<>();
    }
    public static final class Clan {
        public String name="",tag="",owner="";
        public Set<String> members=new HashSet<>();
    }
    public static final class State {
        public Map<String,Profile> profiles=new HashMap<>();
        public Map<String,Clan> clans=new HashMap<>();
        public Map<String,JsonObject> items=new HashMap<>();
        public List<String> ledger=new ArrayList<>();
        public Map<String,String> homes=new HashMap<>();
    }
    public Core(){
        MinecraftForge.EVENT_BUS.addListener(this::start);
        MinecraftForge.EVENT_BUS.addListener(this::stop);
    }
    private void start(ServerAboutToStartEvent e){
        server=e.getServer();file=server.getWorldPath(LevelResource.ROOT).resolve("mythos-rpg.json");
        try{state=Files.exists(file)?GSON.fromJson(Files.readString(file),State.class):new State();
            if(state==null||state.profiles==null||state.clans==null||state.items==null||state.ledger==null||state.homes==null)throw new IllegalStateException("Invalid RPG state");
        }catch(Exception ex){throw new IllegalStateException("Cannot load MYTHOS data; refusing to overwrite",ex);}
    }
    private void stop(ServerStoppingEvent e){if(state!=null)save();state=null;server=null;}
    public static void save(){
        try{Path temp=file.resolveSibling(file.getFileName()+".tmp");Files.writeString(temp,GSON.toJson(state));
            Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        }catch(Exception ex){throw new IllegalStateException("MYTHOS persistence failed",ex);}
    }
    // Transactions mutate only durable RPG state; inventory side effects are not part of this API.
    public static void transaction(Runnable change){
        String before=GSON.toJson(state);
        try{change.run();save();}catch(RuntimeException ex){state=GSON.fromJson(before,State.class);throw ex;}
    }
    public static Profile profile(ServerPlayer p){
        Profile q=state.profiles.computeIfAbsent(p.getStringUUID(),k->new Profile());q.name=p.getGameProfile().getName();return q;
    }
    public static int ok(CommandSourceStack s,String msg){s.sendSuccess(()->Component.literal(msg),false);return 1;}
    public static int fail(CommandSourceStack s,String msg){s.sendFailure(Component.literal(msg));return 0;}
    public static void notifyProfile(ServerPlayer p){for(var l:PROFILE_LISTENERS)l.accept(p);}
    public static long required(int level){return Math.round(100*Math.pow(level,1.8));}
    public static void award(ServerPlayer p,long amount){
        if(amount<=0)return;int old=profile(p).level;
        transaction(()->{Profile q=profile(p);if(q.level>=100)return;q.xp=Math.addExact(q.xp,amount);
            while(q.level<100&&q.xp>=required(q.level)){q.xp-=required(q.level);q.level++;q.points+=5;}
            if(q.level==100)q.xp=0;
        });
        Profile q=profile(p);p.sendSystemMessage(Component.literal("+"+amount+" RPG EXP"));
        if(q.level>old)p.sendSystemMessage(Component.literal("LEVEL UP! "+old+" -> "+q.level+" | +"+((q.level-old)*5)+" attribute points. /stats"));
        notifyProfile(p);
    }
    public static String key(ServerPlayer p){return p.getStringUUID();}
    public static boolean near(ServerPlayer a,ServerPlayer b){return a!=b&&a.isAlive()&&b.isAlive()&&!a.isSpectator()&&!b.isSpectator()&&a.level()==b.level()&&a.distanceToSqr(b)<=9;}
}
