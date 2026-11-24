package com.npcarchitects;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.particles.ParticleTypes;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.fml.common.Mod;
import java.nio.file.*;
import java.util.*;

@Mod("npcarchitects")
public final class Architects {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static final Gson JSON = new Gson();
    private final Map<Integer, Job> jobs = new LinkedHashMap<>();
    private final Map<UUID, Selection> selections = new HashMap<>();
    private static final class Selection { BlockPos first, second; String dimension; int rotation; }
    private Path file;
    private int ticks;
    private boolean storageFailed;
    private static final class Job {
        int id, x, y, z, width, depth, cursor, stalled;
        int height=5, rotation;
        List<Integer> placed = new ArrayList<>();
        String dimension, worker, status = "RUNNING";
    }
    public Architects() {
        MinecraftForge.EVENT_BUS.addListener(this::commands);
        MinecraftForge.EVENT_BUS.addListener(this::started);
        MinecraftForge.EVENT_BUS.addListener(this::stopping);
        MinecraftForge.EVENT_BUS.addListener(this::tick);
        LOGGER.info("NPC Architects 0.1.2 initialized; Forge event listeners registered");
    }
    private void started(ServerStartedEvent e) {
        jobs.clear(); selections.clear(); ticks=0; storageFailed=false;
        LOGGER.info("NPC Architects server started");
        file=e.getServer().getWorldPath(LevelResource.ROOT).resolve("npcarchitects-jobs.json");
        try {
            if (Files.exists(file)) {
                List<Job> loaded=JSON.fromJson(Files.readString(file),new TypeToken<List<Job>>(){}.getType());
                if (loaded==null) throw new IllegalStateException("Empty job store");
                for (Job j:loaded) { cells(j); UUID.fromString(j.worker); if(j.cursor<0 || j.cursor>cells(j).size() || j.placed==null || jobs.putIfAbsent(j.id,j)!=null) throw new IllegalStateException("Invalid job"); }
            }
        } catch(Exception ex) { storageFailed=true; jobs.clear(); LOGGER.error("NPC Architects: cannot load jobs", ex); }
    }
    private void stopping(ServerStoppingEvent e) { save(); jobs.clear(); selections.clear(); file=null; }
    private boolean save() {
        if(file==null || storageFailed) return false;
        try {
            Path tmp=file.resolveSibling(file.getFileName()+".tmp");
            Files.writeString(tmp,JSON.toJson(jobs.values()));
            try { Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE); }
            catch(AtomicMoveNotSupportedException ex) { Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING); }
            return true;
        } catch(Exception ex) { storageFailed=true; LOGGER.error("NPC Architects: jobs halted", ex); return false; }
    }
    private void commands(RegisterCommandsEvent e) {
        var root=Commands.literal("architect").executes(c->{
            c.getSource().sendSuccess(()->Component.literal("NPC Architects 0.1.2 active. Commands: status, house <width> <depth> [height], pos1, pos2, rotate, preview house, build house, undo [id], pause, resume, cancel. Construction requires operator level 2."),false); return 1;
        });
        root.then(Commands.literal("ping").executes(c->{ c.getSource().sendSuccess(()->Component.literal("NPC Architects 0.1.2 is responding."),false); return 1; }));
        root.then(Commands.literal("house").requires(src->src.hasPermission(2))
            .then(Commands.argument("width",IntegerArgumentType.integer(5,15))
            .then(Commands.argument("depth",IntegerArgumentType.integer(5,15))
                .executes(c->directHouse(c.getSource(),IntegerArgumentType.getInteger(c,"width"),IntegerArgumentType.getInteger(c,"depth"),5))
                .then(Commands.argument("height",IntegerArgumentType.integer(4,12))
                    .executes(c->directHouse(c.getSource(),IntegerArgumentType.getInteger(c,"width"),IntegerArgumentType.getInteger(c,"depth"),IntegerArgumentType.getInteger(c,"height")))))));
        for (boolean first : new boolean[]{true,false}) {
            root.then(Commands.literal(first?"pos1":"pos2").requires(src->src.hasPermission(2))
                .executes(c->selectCorner(c.getSource(),first)));
        }
        root.then(Commands.literal("rotate").requires(src->src.hasPermission(2))
            .then(Commands.argument("degrees",IntegerArgumentType.integer(0,270)).executes(c->{
                int rotation=IntegerArgumentType.getInteger(c,"degrees");
                if(rotation%90!=0) return fail(c.getSource(),"Use 0, 90, 180 or 270 degrees.");
                selection(c.getSource()).rotation=rotation;
                c.getSource().sendSuccess(()->Component.literal("Rotation: "+rotation+" degrees"),false); return 1;
            })));
        root.then(Commands.literal("preview").requires(src->src.hasPermission(2))
            .then(Commands.literal("house").executes(c->selectedHouse(c.getSource(),true))));
        root.then(Commands.literal("build").requires(src->src.hasPermission(2))
            .then(Commands.literal("house").executes(c->selectedHouse(c.getSource(),false))));
        root.then(Commands.literal("undo").requires(src->src.hasPermission(2))
            .executes(c->undo(c.getSource(),jobs.keySet().stream().mapToInt(i->i).max().orElse(0)))
            .then(Commands.argument("id",IntegerArgumentType.integer(1)).executes(c->undo(c.getSource(),IntegerArgumentType.getInteger(c,"id")))));
        root.then(Commands.literal("status").requires(src->src.hasPermission(2)).executes(c->{ c.getSource().sendSuccess(()->Component.literal("NPC Architects: "+jobs.size()+" job(s)"+(storageFailed?" | storage error":" | storage ready")),false); for(Job j:jobs.values()) c.getSource().sendSuccess(()->Component.literal(j.id+": "+j.status+" "+j.cursor+"/"+cells(j).size()),false); return 1; }));
        for(String action:List.of("pause","resume","cancel")) root.then(Commands.literal(action).requires(src->src.hasPermission(2)).then(Commands.argument("id",IntegerArgumentType.integer(1)).executes(c->{ Job j=jobs.get(IntegerArgumentType.getInteger(c,"id")); if(j==null || storageFailed || j.status.equals("DONE") || j.status.equals("CANCELLED") || j.status.equals("UNDONE")) { c.getSource().sendFailure(Component.literal("Job unavailable, terminal or storage failed")); return 0; } j.status=switch(action){case "pause"->"PAUSED";case "cancel"->"CANCELLED";default->"RUNNING";}; j.stalled=0; if(!save()) return 0; c.getSource().sendSuccess(()->Component.literal("Job "+j.id+": "+j.status),false); return 1; })));
        e.getDispatcher().register(root);
        LOGGER.info("NPC Architects registered /architect commands");
    }
    private static List<Blueprint.Cell> cells(Job j) {
        return Blueprint.house(j.width,j.depth,j.height,j.rotation);
    }
    private static int fail(CommandSourceStack source,String message) {
        source.sendFailure(Component.literal(message)); return 0;
    }
    private Selection selection(CommandSourceStack source) throws CommandSyntaxException {
        var player=source.getPlayerOrException();
        String dimension=source.getLevel().dimension().location().toString();
        Selection sel=selections.computeIfAbsent(player.getUUID(),id->new Selection());
        if(!dimension.equals(sel.dimension)) { sel.first=null; sel.second=null; sel.dimension=dimension; }
        return sel;
    }
    private int selectCorner(CommandSourceStack source,boolean first) throws CommandSyntaxException {
        var sel=selection(source); var pos=source.getPlayerOrException().blockPosition();
        if(first) sel.first=pos; else sel.second=pos;
        source.sendSuccess(()->Component.literal((first?"Pos1: ":"Pos2: ")+pos.toShortString()),false); return 1;
    }
    private int directHouse(CommandSourceStack source,int w,int d,int h) throws CommandSyntaxException {
        var player=source.getPlayerOrException();
        return createHouse(source,player.blockPosition().offset(3,0,3),w,d,h,selection(source).rotation,false);
    }
    private int selectedHouse(CommandSourceStack source,boolean preview) throws CommandSyntaxException {
        var sel=selection(source);
        if(sel.first==null || sel.second==null) return fail(source,"Select both corners with /architect pos1 and pos2.");
        var a=sel.first; var b=sel.second;
        int w=Math.abs(a.getX()-b.getX())+1, d=Math.abs(a.getZ()-b.getZ())+1;
        int h=Math.abs(a.getY()-b.getY())+1;
        if(h==1) h=5;
        if(w<5||w>15||d<5||d>15||h<4||h>12) return fail(source,"Selection must be 5..15 blocks wide/deep and 4..12 high (or both corners at same Y for height 5).");
        var origin=new BlockPos(Math.min(a.getX(),b.getX()),Math.min(a.getY(),b.getY()),Math.min(a.getZ(),b.getZ()));
        // Rotation preserves the selected physical footprint.
        if(sel.rotation%180!=0) { int swap=w; w=d; d=swap; }
        return createHouse(source,origin,w,d,h,sel.rotation,preview);
    }
    private int createHouse(CommandSourceStack source,BlockPos origin,int w,int d,int h,int rotation,boolean preview) throws CommandSyntaxException {
        var player=source.getPlayerOrException(); var level=source.getLevel();
        Job j=new Job(); j.x=origin.getX(); j.y=origin.getY(); j.z=origin.getZ();
        j.width=w; j.depth=d; j.height=h; j.rotation=rotation; j.dimension=level.dimension().location().toString();
        var plan=cells(j);
        if(preview) {
            int blocked=0;
            for(var cell:plan) {
                var pos=origin.offset(cell.x(),cell.y(),cell.z());
                if(!level.hasChunkAt(pos)) { blocked++; continue; }
                boolean valid=level.getWorldBorder().isWithinBounds(pos) && !level.isOutsideBuildHeight(pos) && (level.getBlockState(pos).isAir()||level.getBlockState(pos).equals(material(cell.material())));
                if(!valid) blocked++;
                if(cell.x()%2==0 && cell.z()%2==0) level.sendParticles(valid?ParticleTypes.END_ROD:ParticleTypes.FLAME,pos.getX()+0.5,pos.getY()+0.5,pos.getZ()+0.5,1,0,0,0,0);
            }
            final int obstacles=blocked;
            source.sendSuccess(()->Component.literal("Preview: "+plan.size()+" blocks; "+obstacles+" blocked/unloaded. White particles=clear, flame=blocked. No blocks changed."),false); return 1;
        }
        if(file==null || storageFailed || jobs.size()>=100) return fail(source,"Storage unavailable or job limit reached");
        for(var cell:plan) {
            var pos=origin.offset(cell.x(),cell.y(),cell.z());
            if(!level.hasChunkAt(pos)||level.isOutsideBuildHeight(pos)||!level.getWorldBorder().isWithinBounds(pos)||(!level.getBlockState(pos).isAir()&&!level.getBlockState(pos).equals(material(cell.material())))) return fail(source,"Site obstructed or unloaded: "+pos.toShortString());
            for(Job other:jobs.values()) {
                if(!j.dimension.equals(other.dimension)||other.status.equals("UNDONE")) continue;
                int ow=other.rotation%180==0?other.width:other.depth, od=other.rotation%180==0?other.depth:other.width;
                if(pos.getX()>=other.x&&pos.getX()<other.x+ow&&pos.getY()>=other.y&&pos.getY()<other.y+other.height&&pos.getZ()>=other.z&&pos.getZ()<other.z+od) return fail(source,"Site overlaps existing job "+other.id);
            }
        }
        var npc=EntityType.VILLAGER.spawn(level,player.blockPosition(),MobSpawnType.COMMAND);
        if(npc==null) return fail(source,"Could not spawn builder");
        npc.setPersistenceRequired(); npc.setCustomName(Component.literal("Architect")); npc.setCustomNameVisible(true);
        j.id=jobs.keySet().stream().mapToInt(i->i).max().orElse(0)+1; j.worker=npc.getUUID().toString(); jobs.put(j.id,j);
        if(!save()) { jobs.remove(j.id); npc.discard(); return fail(source,"Failed to save job"); }
        LOGGER.info("NPC Architects created job {} at {}",j.id,origin);
        source.sendSuccess(()->Component.literal("Created job "+j.id+" at "+origin.toShortString()+" ("+plan.size()+" blocks)"),false); return 1;
    }
    private int undo(CommandSourceStack source,int id) {
        Job j=jobs.get(id);
        if(j==null||storageFailed||j.status.equals("UNDONE")) return fail(source,"No undoable job "+id);
        if(j.placed==null||j.placed.isEmpty()) return fail(source,"No tracked placements to undo (older jobs cannot be undone safely).");
        var level=source.getLevel();
        if(!level.dimension().location().toString().equals(j.dimension)) return fail(source,"Go to the job dimension first.");
        var plan=cells(j);
        for(int index:j.placed) {
            if(index<0||index>=plan.size()) return fail(source,"Invalid undo history.");
            var c=plan.get(index); var pos=new BlockPos(j.x+c.x(),j.y+c.y(),j.z+c.z());
            if(!level.hasChunkAt(pos)||!level.getWorldBorder().isWithinBounds(pos)) return fail(source,"Load the entire site before undoing.");
        }
        j.status="PAUSED"; if(!save()) return fail(source,"Could not save paused job.");
        if(level.getEntity(UUID.fromString(j.worker)) instanceof Villager npc) npc.getNavigation().stop();
        int removed=0, skipped=0;
        for(int i=j.placed.size()-1;i>=0;i--) {
            var c=plan.get(j.placed.get(i)); var pos=new BlockPos(j.x+c.x(),j.y+c.y(),j.z+c.z());
            if(level.getBlockState(pos).equals(material(c.material())) && level.setBlock(pos,Blocks.AIR.defaultBlockState(),3)) removed++; else skipped++;
        }
        j.placed.clear(); j.status="UNDONE"; if(!save()) return fail(source,"Undo applied but storage failed; inspect server log.");
        final int count=removed, ignored=skipped;
        source.sendSuccess(()->Component.literal("Undo job "+id+": "+count+" removed, "+ignored+" changed/missing blocks skipped."),false); return 1;
    }
    private static BlockState material(String name) { return switch(name) { case "floor"->Blocks.COBBLESTONE.defaultBlockState(); case "roof"->Blocks.SPRUCE_PLANKS.defaultBlockState(); case "glass"->Blocks.GLASS.defaultBlockState(); default->Blocks.OAK_PLANKS.defaultBlockState(); }; }
    private void tick(TickEvent.ServerTickEvent.Post e) {
        if(storageFailed || ++ticks%10!=0) return;
        for(Job j:jobs.values()) { if(storageFailed) break; if(j.status.equals("RUNNING")) step(e.getServer(),j); }
    }
    private void step(MinecraftServer server,Job j) {
        ServerLevel level=null; for(var l:server.getAllLevels()) if(l.dimension().location().toString().equals(j.dimension)) {level=l;break;}
        if(level==null) return;
        var cells=cells(j);
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
            if(level.setBlock(pos,wanted,3)) { j.placed.add(j.cursor); j.cursor++; j.stalled=0; save(); return; }
        } else {
            for(var direction:net.minecraft.core.Direction.Plane.HORIZONTAL) { var stand=pos.relative(direction,2); if(!level.hasChunkAt(stand) || !level.isEmptyBlock(stand) || !level.isEmptyBlock(stand.above())) continue; if(npc.getNavigation().moveTo(stand.getX()+0.5,stand.getY(),stand.getZ()+0.5,1.0)) break; }
        }
        if(++j.stalled>=60) { j.status="BLOCKED"; npc.getNavigation().stop(); save(); }
    }
}
