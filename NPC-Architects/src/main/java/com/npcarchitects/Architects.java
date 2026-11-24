package com.npcarchitects;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.fml.common.Mod;
import java.nio.file.*;
import java.util.*;

@Mod("npcarchitects")
public final class Architects {
    private static final Gson JSON = new Gson();
    private final Map<Integer, Job> jobs = new LinkedHashMap<>();
    private Path file;
    private int ticks;
    private boolean storageFailed;
    private static final class Job {
        int id, x, y, z, width, depth, cursor, stalled;
        String dimension, worker, status = "RUNNING";
    }
    public Architects() {
        MinecraftForge.EVENT_BUS.addListener(this::commands);
        MinecraftForge.EVENT_BUS.addListener(this::started);
        MinecraftForge.EVENT_BUS.addListener(this::stopping);
        MinecraftForge.EVENT_BUS.addListener(this::tick);
    }
    private void started(ServerStartedEvent e) {
        jobs.clear(); ticks=0; storageFailed=false;
        file=e.getServer().getWorldPath(LevelResource.ROOT).resolve("npcarchitects-jobs.json");
        try {
            if (Files.exists(file)) {
                List<Job> loaded=JSON.fromJson(Files.readString(file),new TypeToken<List<Job>>(){}.getType());
                if (loaded==null) throw new IllegalStateException("Empty job store");
                for (Job j:loaded) { Blueprint.house(j.width,j.depth); UUID.fromString(j.worker); if(j.cursor<0 || j.cursor>Blueprint.house(j.width,j.depth).size() || jobs.putIfAbsent(j.id,j)!=null) throw new IllegalStateException("Invalid job"); }
            }
        } catch(Exception ex) { storageFailed=true; jobs.clear(); System.err.println("NPC Architects: cannot load jobs: "+ex); }
    }
    private void stopping(ServerStoppingEvent e) { save(); jobs.clear(); file=null; }
    private boolean save() {
        if(file==null || storageFailed) return false;
        try {
            Path tmp=file.resolveSibling(file.getFileName()+".tmp");
            Files.writeString(tmp,JSON.toJson(jobs.values()));
            try { Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE); }
            catch(AtomicMoveNotSupportedException ex) { Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING); }
            return true;
        } catch(Exception ex) { storageFailed=true; System.err.println("NPC Architects: jobs halted: "+ex); return false; }
    }
    private void commands(RegisterCommandsEvent e) {
        var root=Commands.literal("architect").requires(s->s.hasPermission(2));
        root.then(Commands.literal("house").then(Commands.argument("width",IntegerArgumentType.integer(5,15)).then(Commands.argument("depth",IntegerArgumentType.integer(5,15)).executes(c->{
            var s=c.getSource(); var p=s.getPlayerOrException(); var level=s.getLevel();
            if(storageFailed || jobs.size()>=100) { s.sendFailure(Component.literal("Storage unavailable or job limit reached")); return 0; }
            var origin=p.blockPosition().offset(3,0,3); int w=IntegerArgumentType.getInteger(c,"width"), d=IntegerArgumentType.getInteger(c,"depth");
            for(var cell:Blueprint.house(w,d)) { var pos=origin.offset(cell.x(),cell.y(),cell.z()); if(!level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos) || (!level.getBlockState(pos).isAir() && !level.getBlockState(pos).equals(material(cell.material())))) { s.sendFailure(Component.literal("Site obstructed or unloaded: "+pos)); return 0; } }
            var npc=EntityType.VILLAGER.spawn(level,p.blockPosition(),EntitySpawnReason.COMMAND);
            if(npc==null) { s.sendFailure(Component.literal("Could not spawn builder")); return 0; }
            npc.setPersistenceRequired(); npc.setCustomName(Component.literal("Architect")); npc.setCustomNameVisible(true);
            Job j=new Job(); j.id=jobs.keySet().stream().mapToInt(i->i).max().orElse(0)+1; j.x=origin.getX(); j.y=origin.getY(); j.z=origin.getZ(); j.width=w; j.depth=d; j.dimension=level.dimension().location().toString(); j.worker=npc.getUUID().toString(); jobs.put(j.id,j);
            if(!save()) { jobs.remove(j.id); npc.discard(); s.sendFailure(Component.literal("Failed to save job")); return 0; }
            s.sendSuccess(()->Component.literal("Created job "+j.id),false); return 1;
        }))));
        root.then(Commands.literal("status").executes(c->{ for(Job j:jobs.values()) c.getSource().sendSuccess(()->Component.literal(j.id+": "+j.status+" "+j.cursor+"/"+Blueprint.house(j.width,j.depth).size()),false); return 1; }));
        for(String action:List.of("pause","resume","cancel")) root.then(Commands.literal(action).then(Commands.argument("id",IntegerArgumentType.integer(1)).executes(c->{ Job j=jobs.get(IntegerArgumentType.getInteger(c,"id")); if(j==null || storageFailed || j.status.equals("DONE") || j.status.equals("CANCELLED")) return 0; j.status=switch(action){case "pause"->"PAUSED";case "cancel"->"CANCELLED";default->"RUNNING";}; j.stalled=0; if(!save()) return 0; c.getSource().sendSuccess(()->Component.literal("Job "+j.id+": "+j.status),false); return 1; })));
        e.getDispatcher().register(root);
    }
    private static BlockState material(String name) { return switch(name) { case "floor"->Blocks.COBBLESTONE.defaultBlockState(); case "roof"->Blocks.SPRUCE_PLANKS.defaultBlockState(); case "glass"->Blocks.GLASS.defaultBlockState(); default->Blocks.OAK_PLANKS.defaultBlockState(); }; }
    private void tick(TickEvent.ServerTickEvent.Post e) {
        if(storageFailed || ++ticks%10!=0) return;
        for(Job j:jobs.values()) { if(storageFailed) break; if(j.status.equals("RUNNING")) step(e.getServer(),j); }
    }
    private void step(MinecraftServer server,Job j) {
        ServerLevel level=null; for(var l:server.getAllLevels()) if(l.dimension().location().toString().equals(j.dimension)) {level=l;break;}
        if(level==null) return;
        var cells=Blueprint.house(j.width,j.depth);
        if(j.cursor>=cells.size()) { j.status="DONE"; save(); return; }
        if(!(level.getEntity(UUID.fromString(j.worker)) instanceof Villager npc) || !npc.isAlive()) return;
        var c=cells.get(j.cursor); var pos=new BlockPos(j.x+c.x(),j.y+c.y(),j.z+c.z());
        if(!level.hasChunkAt(pos)) return;
        var wanted=material(c.material()); var current=level.getBlockState(pos);
        if(current.equals(wanted)) { j.cursor++; j.stalled=0; save(); return; }
        if(!current.isAir() || !level.getWorldBorder().isWithinBounds(pos)) { j.status="BLOCKED"; save(); return; }
        double dx=npc.getX()-pos.getX()-0.5, dy=npc.getY()-pos.getY()-0.5, dz=npc.getZ()-pos.getZ()-0.5;
        if(dx*dx+dy*dy+dz*dz<=12.25 && level.getEntities(npc,new AABB(pos)).isEmpty() && !npc.getBoundingBox().intersects(new AABB(pos))) {
            npc.getNavigation().stop(); npc.getLookControl().setLookAt(pos.getX()+0.5,pos.getY()+0.5,pos.getZ()+0.5); npc.swing(InteractionHand.MAIN_HAND);
            if(level.setBlock(pos,wanted,3)) { j.cursor++; j.stalled=0; save(); return; }
        } else {
            for(var direction:net.minecraft.core.Direction.Plane.HORIZONTAL) { var stand=pos.relative(direction,2); if(!level.hasChunkAt(stand) || !level.isEmptyBlock(stand) || !level.isEmptyBlock(stand.above())) continue; if(npc.getNavigation().moveTo(stand.getX()+0.5,stand.getY(),stand.getZ()+0.5,1.0)) break; }
        }
        if(++j.stalled>=60) { j.status="BLOCKED"; npc.getNavigation().stop(); save(); }
    }
}
