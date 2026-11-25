package com.ksp.randomteleport;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import java.nio.file.*;
import java.util.*;

@Mod("randomteleport")
public final class RandomTeleport {
    private static final int MAX_ATTEMPTS=200, COLUMN_BUDGET=128;
    private static final Set<String> DEFAULT_BIOMES=Set.of("minecraft:plains","minecraft:sunflower_plains","minecraft:savanna","minecraft:meadow");
    private final Map<UUID,Search> searches=new LinkedHashMap<>();
    private final Random random=new Random();
    private record Config(int radius,int searchRadius,int size,int relief,Set<String> biomes){}
    private static final class Search {
        final UUID player; final ServerLevel level; final int startX,startZ,size,relief,radius;
        final Set<String> biomes;
        int attempts,x,z,min,max,probe,chunkIndex,column;
        boolean candidate,full;
        Search(ServerPlayer p,int size,int relief,Config c){
            player=p.getUUID();level=p.serverLevel();startX=p.blockPosition().getX();startZ=p.blockPosition().getZ();
            this.size=size;this.relief=relief;radius=Math.min(c.searchRadius(),Math.max(16,(p.getServer().getPlayerList().getViewDistance()-2)*16));biomes=c.biomes();
        }
        int chunks(){return (size+15)/16;}
    }
    public RandomTeleport(){
        var bus=MinecraftForge.EVENT_BUS;
        bus.addListener(this::register);bus.addListener(this::tick);bus.addListener(this::stop);
    }
    private void register(RegisterCommandsEvent event){
        event.getDispatcher().register(Commands.literal("rtp").requires(s->s.hasPermission(2))
            .executes(c->{
                ServerPlayer p=c.getSource().getPlayerOrException();
                if(!p.level().dimension().equals(Level.OVERWORLD)){
                    c.getSource().sendFailure(Component.literal("Random teleport is limited to the Overworld."));return 0;
                }
                cancel(p.getUUID());
                c.getSource().getServer().getCommands().performPrefixedCommand(c.getSource(),"spreadplayers ~ ~ 0 "+config().radius()+" false @s");
                return Command.SINGLE_SUCCESS;
            })
            .then(Commands.literal("flat")
                .executes(c->start(c.getSource(),-1,-1))
                .then(Commands.argument("size",IntegerArgumentType.integer(16,512))
                    .executes(c->start(c.getSource(),IntegerArgumentType.getInteger(c,"size"),-1))
                    .then(Commands.argument("relief",IntegerArgumentType.integer(0,64))
                        .executes(c->start(c.getSource(),IntegerArgumentType.getInteger(c,"size"),IntegerArgumentType.getInteger(c,"relief"))))))
            .then(Commands.literal("status").executes(c->{
                var p=c.getSource().getPlayerOrException();var s=searches.get(p.getUUID());
                say(p,s==null?"No active terrain search.": "Candidate "+s.attempts+"/"+MAX_ATTEMPTS+" | "+(s.full?"Full survey "+s.chunkIndex+"/"+(s.chunks()*s.chunks())+" chunks":"Coarse survey")+" | footprint "+s.size+" x "+s.size);
                return 1;
            }))
            .then(Commands.literal("cancel").executes(c->{var p=c.getSource().getPlayerOrException();cancel(p.getUUID());say(p,"Terrain search cancelled.");return 1;})));
    }
    private int start(CommandSourceStack source,int size,int relief)throws com.mojang.brigadier.exceptions.CommandSyntaxException{
        ServerPlayer p=source.getPlayerOrException();
        if(!p.level().dimension().equals(Level.OVERWORLD)){source.sendFailure(Component.literal("Flat search is limited to the Overworld."));return 0;}
        Config c=config();if(size<0)size=c.size();if(relief<0)relief=c.relief();
        cancel(p.getUUID());
        // Single global search limits chunk generation and survey load.
        if(!searches.isEmpty()){source.sendFailure(Component.literal("Another terrain search is active."));return 0;}
        var search=new Search(p,size,relief,c);
        if(size>2*search.radius){source.sendFailure(Component.literal("Footprint exceeds conservative loaded search range. Try a smaller footprint or explore a candidate area with sufficient view distance."));return 0;}
        searches.put(p.getUUID(),search);
        say(p,"Searching already-loaded terrain within "+searches.get(p.getUUID()).radius+" blocks. No new chunks will be generated by the survey. Use /rtp status or /rtp cancel.");
        return 1;
    }
    private static void say(ServerPlayer p,String message){p.sendSystemMessage(Component.literal("[Random Teleport] "+message));}
    private void cancel(UUID id){searches.remove(id);}
    private void stop(ServerStoppingEvent event){searches.clear();}
    private boolean nextCandidate(Search s){
        if(++s.attempts>MAX_ATTEMPTS)return false;
        int span=Math.max(0,s.radius-s.size/2);
        int cx=s.startX+(span==0?0:random.nextInt(2*span+1)-span);
        int cz=s.startZ+(span==0?0:random.nextInt(2*span+1)-span);
        s.x=Math.floorDiv(cx-s.size/2,16)*16;s.z=Math.floorDiv(cz-s.size/2,16)*16;
        s.min=Integer.MAX_VALUE;s.max=Integer.MIN_VALUE;s.probe=0;s.chunkIndex=0;s.column=0;s.full=false;
        var border=s.level.getWorldBorder();
        s.candidate=border.isWithinBounds(new BlockPos(s.x,64,s.z))&&border.isWithinBounds(new BlockPos(s.x+s.size-1,64,s.z+s.size-1));
        return true;
    }
    private static LevelChunk loaded(Search s,int x,int z){
        return s.level.getChunkSource().getChunkNow(x>>4,z>>4);
    }
    private static boolean vegetation(BlockState state){
        return state.is(Blocks.SHORT_GRASS)||state.is(Blocks.TALL_GRASS)||state.is(Blocks.FERN)||state.is(Blocks.LARGE_FERN)||state.is(net.minecraft.tags.BlockTags.FLOWERS);
    }
    private boolean check(Search s,LevelChunk chunk,int x,int z){
        // Chunk-local reads only. Never call a synchronous chunk loading API.
        int y=chunk.getHeight(Heightmap.Types.WORLD_SURFACE,x&15,z&15);
        BlockPos pos=new BlockPos(x,y,z);
        for(int i=0;i<3&&vegetation(chunk.getBlockState(pos));i++)pos=pos.below();
        y=pos.getY();
        if(y<s.level.getMinBuildHeight()+5||y>s.level.getMaxBuildHeight()-60)return false;
        BlockState floor=chunk.getBlockState(pos);
        if(!floor.is(Blocks.GRASS_BLOCK)&&!floor.is(Blocks.DIRT)&&!floor.is(Blocks.COARSE_DIRT)&&!floor.is(Blocks.PODZOL))return false;
        if(!floor.getFluidState().isEmpty())return false;
        for(int dy=1;dy<=2;dy++){var above=chunk.getBlockState(pos.above(dy));if(!above.isAir()&&!vegetation(above))return false;}
        var biome=chunk.getNoiseBiome(x>>2,y>>2,z>>2).unwrapKey();
        if(biome.isEmpty()||!s.biomes.contains(biome.get().location().toString()))return false;
        s.min=Math.min(s.min,y);s.max=Math.max(s.max,y);
        return s.max-s.min<=s.relief;
    }
    private void tick(TickEvent.ServerTickEvent.Post event){
        if(searches.isEmpty())return;
        var s=searches.values().iterator().next();
        var p=event.getServer().getPlayerList().getPlayer(s.player);
        if(p==null||p.serverLevel()!=s.level||!p.hasPermissions(2)){cancel(s.player);return;}
        if(!s.candidate){
            if(!nextCandidate(s)){say(p,"No qualifying loaded site found. Move to another plains area, increase server view distance if safe, or try greater relief/smaller footprint.");cancel(s.player);return;}
            if(!s.candidate)return;
        }
        int budget=COLUMN_BUDGET;
        long deadline=System.nanoTime()+1_000_000L;
        while(budget-->0&&System.nanoTime()<deadline){
            int x,z;
            if(!s.full){
                // 5 x 5 coarse grid including the corners and centre.
                x=s.x+(s.size-1)*(s.probe%5)/4;z=s.z+(s.size-1)*(s.probe/5)/4;
            }else{
                int n=s.chunks();
                if(s.chunkIndex>=n*n){finish(s,p);return;}
                x=s.x+(s.chunkIndex%n)*16+(s.column%16);
                z=s.z+(s.chunkIndex/n)*16+(s.column/16);
                if(x>=s.x+s.size||z>=s.z+s.size){advance(s);continue;}
            }
            var chunk=loaded(s,x,z);
            if(chunk==null||!check(s,chunk,x,z)){s.candidate=false;return;}
            if(!s.full){if(++s.probe==25){s.full=true;s.min=Integer.MAX_VALUE;s.max=Integer.MIN_VALUE;}}
            else advance(s);
        }
    }
    private static void advance(Search s){if(++s.column==256){s.column=0;s.chunkIndex++;}}
    private void finish(Search s,ServerPlayer p){
        var chunk=loaded(s,s.x,s.z);
        if(chunk==null||!check(s,chunk,s.x,s.z)){s.candidate=false;return;}
        int y=chunk.getHeight(Heightmap.Types.WORLD_SURFACE,s.x&15,s.z&15);
        for(int i=0;i<3&&vegetation(chunk.getBlockState(new BlockPos(s.x,y,s.z)));i++)y--;
        p.teleportTo(s.level,s.x+.5,y+1,s.z+.5,p.getYRot(),p.getXRot());
        say(p,"Site found! Corners X/Z: "+s.x+", "+s.z+" to "+(s.x+s.size-1)+", "+(s.z+s.size-1)+". Surface Y: "+s.min+" to "+s.max+". You are at the minimum X/Z corner.");
        say(p,"Emberfall schematic ground layer is offset 5: for a level surface at Y="+s.min+", select schematic origin Y="+(s.min-5)+". Terrain may still require preparation.");
        cancel(s.player);
    }
    private Config config(){
        Path path=FMLPaths.CONFIGDIR.get().resolve("randomteleport.properties");
        var props=new Properties();
        try{
            Files.createDirectories(path.getParent());
            if(!Files.exists(path))Files.writeString(path,"radius=5000\nsearchRadius=50000\nflatAreaSize=320\nmaxHeightDifference=3\npreferredBiomes=minecraft:plains,minecraft:sunflower_plains,minecraft:savanna,minecraft:meadow\n",StandardOpenOption.CREATE_NEW);
            try(var reader=Files.newBufferedReader(path)){props.load(reader);}
        }catch(Exception ex){com.mojang.logging.LogUtils.getLogger().warn("Cannot read RTP configuration; using defaults",ex);}
        var biomes=new HashSet<String>();
        for(String biome:props.getProperty("preferredBiomes",String.join(",",DEFAULT_BIOMES)).split(",")){if(!biome.isBlank())biomes.add(biome.trim());}
        return new Config(number(props,"radius",5000,1,10000),number(props,"searchRadius",50000,100,100000),number(props,"flatAreaSize",320,16,512),number(props,"maxHeightDifference",3,0,64),biomes.isEmpty()?DEFAULT_BIOMES:Set.copyOf(biomes));
    }
    private static int number(Properties p,String key,int fallback,int min,int max){
        try{return Math.max(min,Math.min(max,Integer.parseInt(p.getProperty(key,Integer.toString(fallback)).trim())));}
        catch(NumberFormatException ex){return fallback;}
    }
}
