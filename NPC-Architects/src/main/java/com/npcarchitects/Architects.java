package com.npcarchitects;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
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
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.server.level.TicketType;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
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
    private Path file, schematicDirectory, planDirectory;
    private static final TicketType<Integer> WORK_TICKET=TicketType.create("npcarchitects_work",Integer::compare,40);
    private static final int SURVEY_BATCH=1024;
    private int ticks;
    private boolean storageFailed;
    private static final class Job {
        int id, x, y, z, width, depth, cursor, stalled;
        int height=5, rotation, clearCursor;
        transient BlockPos workTarget;
        transient Vec3 workStand;
        transient int movementFailures;
        boolean clearing, workerDied;
        String schematic;
        transient List<Blueprint.Cell> imported;
        String planFile;
        boolean surveying;
        int surveyCursor;
        transient ChunkPos workChunk, workerChunk;
        
        transient Map<String,BlockState> importedStates;
        transient Map<BlockPos,BlockState> desiredCache;
        Integer workerX, workerY, workerZ;
        List<Cleared> cleared = new ArrayList<>();
        String reason = "";
        List<Integer> placed = new ArrayList<>();
        String dimension, worker, status = "RUNNING";
    }
    private static final class Cleared {
        int x,y,z,state;
        Cleared(int x,int y,int z,int state) { this.x=x; this.y=y; this.z=z; this.state=state; }
    }
    public Architects() {
        MinecraftForge.EVENT_BUS.addListener(this::commands);
        MinecraftForge.EVENT_BUS.addListener(this::started);
        MinecraftForge.EVENT_BUS.addListener(this::stopping);
        MinecraftForge.EVENT_BUS.addListener(this::tick);
        MinecraftForge.EVENT_BUS.addListener(this::workerDeath);
        MinecraftForge.EVENT_BUS.addListener(this::targetChanged);
        LOGGER.info("NPC Architects 0.1.6 initialized; Forge event listeners registered");
    }
    private void started(ServerStartedEvent e) {
        jobs.clear(); selections.clear(); ticks=0; storageFailed=false;
        LOGGER.info("NPC Architects server started");
        file=e.getServer().getWorldPath(LevelResource.ROOT).resolve("npcarchitects-jobs.json");
        try {
            schematicDirectory=file.resolveSibling("npcarchitects-schematics");
            Files.createDirectories(schematicDirectory);
            planDirectory=file.resolveSibling("npcarchitects-plans");
            Files.createDirectories(planDirectory);
            if (Files.exists(file)) {
                List<Job> loaded=JSON.fromJson(Files.readString(file),new TypeToken<List<Job>>(){}.getType());
                if (loaded==null) throw new IllegalStateException("Empty job store");
                for (Job j:loaded) { loadPlan(j); validatePlan(j); UUID.fromString(j.worker); if(j.cursor<0 || j.cursor>cells(j).size() || j.clearCursor<0 || j.clearCursor>j.width*j.depth*j.height || j.surveyCursor<0 || j.surveyCursor>j.width*j.depth*j.height || j.cleared==null || j.placed==null || jobs.putIfAbsent(j.id,j)!=null) throw new IllegalStateException("Invalid job"); }
            }
        } catch(Exception ex) { storageFailed=true; jobs.clear(); LOGGER.error("NPC Architects: cannot load jobs", ex); }
        if(!storageFailed) for(Job j:jobs.values()) if(j.status.equals("RUNNING")) { var level=jobLevel(e.getServer(),j); if(level!=null) ticket(level,j,workerAnchor(j),true); }
    }
    private void stopping(ServerStoppingEvent e) { for(Job j:jobs.values()) { var level=jobLevel(e.getServer(),j); if(level!=null) releaseTickets(level,j); } save(); jobs.clear(); selections.clear(); file=null; schematicDirectory=null; planDirectory=null; }
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
            c.getSource().sendSuccess(()->Component.literal("NPC Architects 0.1.6 active. Commands: status, house <width> <depth> [height], pos1, pos2, rotate, preview house, build house, schematic list/info/preview/build, undo [id], recover <id>, pause, resume, cancel. Construction requires operator level 2."),false); return 1;
        });
        root.then(Commands.literal("ping").executes(c->{ c.getSource().sendSuccess(()->Component.literal("NPC Architects 0.1.6 is responding."),false); return 1; }));
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
        var schematics=Commands.literal("schematic").requires(src->src.hasPermission(2));
        schematics.then(Commands.literal("list").executes(c->listSchematics(c.getSource())));
        for(String action:List.of("info","preview","build")) {
            schematics.then(Commands.literal(action).then(Commands.argument("name",StringArgumentType.word())
                .suggests((c,b)->{ for(String name:schematicNames()) if(name.startsWith(b.getRemaining())) b.suggest(name); return b.buildFuture(); })
                .executes(c->schematicAction(c.getSource(),StringArgumentType.getString(c,"name"),action))));
        }
        root.then(schematics);
        root.then(Commands.literal("undo").requires(src->src.hasPermission(2))
            .executes(c->undo(c.getSource(),jobs.keySet().stream().mapToInt(i->i).max().orElse(0)))
            .then(Commands.argument("id",IntegerArgumentType.integer(1)).executes(c->undo(c.getSource(),IntegerArgumentType.getInteger(c,"id")))));
        root.then(Commands.literal("recover").requires(src->src.hasPermission(2))
            .then(Commands.argument("id",IntegerArgumentType.integer(1)).executes(c->recover(c.getSource(),IntegerArgumentType.getInteger(c,"id")))));
        root.then(Commands.literal("status").requires(src->src.hasPermission(2)).executes(c->{ c.getSource().sendSuccess(()->Component.literal("NPC Architects: "+jobs.size()+" job(s)"+(storageFailed?" | storage error":" | storage ready")),false); for(Job j:jobs.values()) c.getSource().sendSuccess(()->Component.literal(j.id+": "+(j.schematic==null?"house":j.schematic)+" "+j.status+" "+(j.surveying?"SURVEYING "+j.surveyCursor+"/"+(j.width*j.depth*j.height):j.clearing?"CLEARING "+j.clearCursor+"/"+(j.width*j.depth*j.height):"BUILDING "+j.cursor+"/"+cells(j).size())+(j.reason.isEmpty()?"":" | "+j.reason)),false); return 1; }));
        for(String action:List.of("pause","resume","cancel")) root.then(Commands.literal(action).requires(src->src.hasPermission(2)).then(Commands.argument("id",IntegerArgumentType.integer(1)).executes(c->{ Job j=jobs.get(IntegerArgumentType.getInteger(c,"id")); if(j==null || storageFailed || j.status.equals("DONE") || j.status.equals("CANCELLED") || j.status.equals("UNDONE")) { c.getSource().sendFailure(Component.literal("Job unavailable, terminal or storage failed")); return 0; } j.status=switch(action){case "pause"->"PAUSED";case "cancel"->"CANCELLED";default->"RUNNING";}; j.stalled=0; j.reason=""; resetMovement(j); var world=jobLevel(c.getSource().getServer(),j); if(world!=null) { if(world.getEntity(UUID.fromString(j.worker)) instanceof Villager worker) setWorking(worker,j.status.equals("RUNNING")); if(j.status.equals("RUNNING")) ticket(world,j,workerAnchor(j),true); else releaseTickets(world,j); } if(!save()) return 0; c.getSource().sendSuccess(()->Component.literal("Job "+j.id+": "+j.status),false); return 1; })));
        e.getDispatcher().register(root);
        LOGGER.info("NPC Architects registered /architect commands");
    }
    private static List<Blueprint.Cell> cells(Job j) {
        return j.imported!=null?j.imported:Blueprint.house(j.width,j.depth,j.height,j.rotation);
    }
    private List<String> schematicNames() {
        if(schematicDirectory==null) return List.of();
        try(var files=Files.list(schematicDirectory)) {
            return files.filter(p->Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS))
                .map(p->p.getFileName().toString()).filter(n->n.matches("[A-Za-z0-9_-]{1,64}\\.schem"))
                .map(n->n.substring(0,n.length()-6)).sorted().limit(200).toList();
        } catch(Exception ex) { LOGGER.warn("Cannot list schematics",ex); return List.of(); }
    }
    private int listSchematics(CommandSourceStack source) {
        var names=schematicNames();
        source.sendSuccess(()->Component.literal("Schematics ("+names.size()+"): "+(names.isEmpty()?"none — place .schem files in the world/npcarchitects-schematics folder":String.join(", ",names))),false);
        return 1;
    }
    private int schematicAction(CommandSourceStack source,String name,String action) throws CommandSyntaxException {
        if(schematicDirectory==null||!name.matches("[A-Za-z0-9_-]{1,64}")) return fail(source,"Invalid schematic name. Use letters, numbers, underscore or hyphen; omit .schem.");
        var path=schematicDirectory.resolve(name+".schem");
        if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)) return fail(source,"Schematic not found: "+name);
        var sel=selection(source);
        SchematicImport.Plan imported;
        try { imported=SchematicImport.read(path,sel.rotation); }
        catch(Exception ex) { LOGGER.warn("Schematic import rejected: {}",name,ex); return fail(source,"Cannot import "+name+": "+ex.getMessage()); }
        if(action.equals("info")) {
            source.sendSuccess(()->Component.literal(name+": "+imported.width()+" x "+imported.height()+" x "+imported.depth()+", "+imported.cells().size()+" blocks, rotation "+sel.rotation+" degrees. Air inside volume will be cleared."),false); return 1;
        }
        Job j=new Job(); j.width=imported.width(); j.depth=imported.depth(); j.height=imported.height();
        j.rotation=0; j.schematic=name; j.imported=imported.cells();
        var origin=sel.first!=null?sel.first:source.getPlayerOrException().blockPosition().offset(3,0,3);
        return createJob(source,origin,j,action.equals("preview"));
    }
    private static void validatePlan(Job j) {
        var plan=cells(j);
        if(j.imported==null) return;
        if(j.rotation!=0||j.width<1||j.depth<1||j.height<1||(long)j.width*j.depth*j.height>Integer.MAX_VALUE-8L||plan.isEmpty()||plan.size()>(long)j.width*j.depth*j.height) throw new IllegalArgumentException("Invalid saved schematic dimensions");
        var seen=new HashSet<BlockPos>();
        int lastY=-1;
        for(var c:plan) {
            if(c.x()<0||c.x()>=j.width||c.z()<0||c.z()>=j.depth||c.y()<lastY||c.y()<0||c.y()>=j.height||!seen.add(new BlockPos(c.x(),c.y(),c.z()))||material(j,c.material()).isAir()) throw new IllegalArgumentException("Invalid saved schematic cell");
            lastY=c.y();
        }
    }
    private void persistPlan(Job j) throws java.io.IOException {
        j.planFile=UUID.randomUUID()+".json";
        var path=planDirectory.resolve(j.planFile);
        var temp=path.resolveSibling(path.getFileName()+".tmp");
        try(var out=Files.newBufferedWriter(temp)) { JSON.toJson(j.imported,out); }
        try { Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE); }
        catch(AtomicMoveNotSupportedException ex) { Files.move(temp,path); }
    }
    private void loadPlan(Job j) throws java.io.IOException {
        if(j.planFile==null) { if(j.schematic!=null) throw new java.io.IOException("Missing saved schematic plan"); return; }
        if(!j.planFile.matches("[0-9a-f-]{36}\\.json")) throw new java.io.IOException("Invalid saved plan filename");
        var path=planDirectory.resolve(j.planFile);
        if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)) throw new java.io.IOException("Missing saved plan "+j.planFile);
        try(var reader=Files.newBufferedReader(path)) {
            j.imported=JSON.fromJson(reader,new TypeToken<List<Blueprint.Cell>>(){}.getType());
        }
        if(j.imported==null) throw new java.io.IOException("Empty saved plan");
    }
    private static int physicalWidth(Job j) { return j.rotation%180==0?j.width:j.depth; }
    private static int physicalDepth(Job j) { return j.rotation%180==0?j.depth:j.width; }
    private static boolean overlaps(Job a,Job b) {
        return a.dimension.equals(b.dimension)&&!b.status.equals("UNDONE")
            &&(long)a.x<b.x+(long)physicalWidth(b)&&(long)b.x<a.x+(long)physicalWidth(a)
            &&(long)a.y<b.y+(long)b.height&&(long)b.y<a.y+(long)a.height
            &&(long)a.z<b.z+(long)physicalDepth(b)&&(long)b.z<a.z+(long)physicalDepth(a);
    }
    private int createSchematicJob(CommandSourceStack source,BlockPos origin,Job j,boolean preview) throws CommandSyntaxException {
        var level=source.getLevel();
        long endX=(long)j.x+j.width-1,endY=(long)j.y+j.height-1,endZ=(long)j.z+j.depth-1;
        if(endX>Integer.MAX_VALUE||endY>Integer.MAX_VALUE||endZ>Integer.MAX_VALUE) return fail(source,"Schematic exceeds coordinate range");
        var end=new BlockPos((int)endX,(int)endY,(int)endZ);
        if(level.isOutsideBuildHeight(origin)||level.isOutsideBuildHeight(end)||!level.getWorldBorder().isWithinBounds(origin)||!level.getWorldBorder().isWithinBounds(end)) return fail(source,"Schematic exceeds world border or dimension build height");
        for(Job other:jobs.values()) if(overlaps(j,other)) return fail(source,"Site overlaps job "+other.id);
        if(preview) {
            var plan=cells(j);
            int samples=0;
            for(int i=0;i<plan.size();i+=Math.max(1,(plan.size()+511L)/512>Integer.MAX_VALUE?Integer.MAX_VALUE:(int)((plan.size()+511L)/512))) {
                var c=plan.get(i); var pos=origin.offset(c.x(),c.y(),c.z());
                if(level.hasChunkAt(pos)) { level.sendParticles(ParticleTypes.END_ROD,pos.getX()+0.5,pos.getY()+0.5,pos.getZ()+0.5,1,0,0,0,0); samples++; }
            }
            final int count=samples;
            source.sendSuccess(()->Component.literal("Schematic preview: "+j.width+" x "+j.height+" x "+j.depth+", "+plan.size()+" blocks; "+count+" particles in loaded chunks. Full protection survey runs before clearing. No blocks changed."),false); return 1;
        }
        if(file==null||storageFailed) return fail(source,"Storage unavailable");
        j.surveying=true; j.clearing=true;
        return spawnJob(source,j,origin,cells(j).size());
    }
    private static BlockPos workerAnchor(Job j) {
        if(j.workerX!=null && j.workerY!=null && j.workerZ!=null) return new BlockPos(j.workerX,j.workerY,j.workerZ);
        // Legacy jobs without a saved worker location can at least reload their construction origin.
        return new BlockPos(j.x,j.y,j.z);
    }
    private void checkpointWorker(ServerLevel level,Job j,Villager npc) {
        var previous=new ChunkPos(workerAnchor(j));
        remember(j,npc);
        var next=new ChunkPos(npc.blockPosition());
        ticket(level,j,npc.blockPosition(),true);
        if(!previous.equals(next)) save();
    }
    private static void ticket(ServerLevel level,Job j,BlockPos pos,boolean worker) {
        var next=new ChunkPos(pos); var previous=worker?j.workerChunk:j.workChunk;
        if(previous!=null&&!previous.equals(next)) {
            var other=worker?j.workChunk:j.workerChunk;
            if(!previous.equals(other)) level.getChunkSource().removeRegionTicket(WORK_TICKET,previous,2,j.id);
        }
        level.getChunkSource().addRegionTicket(WORK_TICKET,next,2,j.id);
        if(worker) j.workerChunk=next; else j.workChunk=next;
    }
    private static void releaseTickets(ServerLevel level,Job j) {
        if(j.workChunk!=null) level.getChunkSource().removeRegionTicket(WORK_TICKET,j.workChunk,2,j.id);
        if(j.workerChunk!=null&&!j.workerChunk.equals(j.workChunk)) level.getChunkSource().removeRegionTicket(WORK_TICKET,j.workerChunk,2,j.id);
        j.workChunk=null; j.workerChunk=null;
    }
    private static boolean loadTarget(ServerLevel level,Job j,BlockPos pos) {
        ticket(level,j,pos,false);
        // Let the ticket system load/generate asynchronously; never synchronously generate a map in a tick.
        return level.hasChunkAt(pos);
    }
    private void surveyStep(ServerLevel level,Villager npc,Job j) {
        int total=j.width*j.depth*j.height;
        var desired=desiredStates(j);
        ChunkPos chunk=null;
        int start=j.surveyCursor;
        while(j.surveyCursor<total&&j.surveyCursor-start<SURVEY_BATCH) {
            var pos=clearPosition(j,j.surveyCursor);
            var next=new ChunkPos(pos);
            if(chunk!=null&&!chunk.equals(next)) break;
            chunk=next;
            if(!loadTarget(level,j,pos)) { waiting(j,"Loading survey chunk "+next); return; }
            String problem=siteProblem(level,pos);
            if(problem!=null) { blocked(j,npc,problem+" at "+pos.toShortString()); return; }
            var current=level.getBlockState(pos);
            var wanted=desired.getOrDefault(pos,Blocks.AIR.defaultBlockState());
            if(!current.isAir()&&!current.equals(wanted)&&!canClear(level,pos,current)) { blocked(j,npc,"Protected or unbreakable block at "+pos.toShortString()); return; }
            j.surveyCursor++;
        }
        if(j.surveyCursor==total) { j.surveying=false; LOGGER.info("NPC Architects job {} completed map survey",j.id); }
        j.reason=""; save();
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
        Job j=new Job(); j.width=w; j.depth=d; j.height=h; j.rotation=rotation;
        return createJob(source,origin,j,preview);
    }
    private int createJob(CommandSourceStack source,BlockPos origin,Job j,boolean preview) throws CommandSyntaxException {
        var player=source.getPlayerOrException(); var level=source.getLevel();
        j.x=origin.getX(); j.y=origin.getY(); j.z=origin.getZ(); j.dimension=level.dimension().location().toString();
        int w=j.width,d=j.depth,h=j.height;
        var plan=cells(j);
        if(j.imported!=null) return createSchematicJob(source,origin,j,preview);
        var desired=desiredStates(j);
        int removable=0, blocked=0;
        String firstProblem=null;
        for(int i=0;i<w*d*h;i++) {
            var pos=clearPosition(j,i);
            String problem=siteProblem(level,pos);
            var wanted=desired.getOrDefault(pos,Blocks.AIR.defaultBlockState());
            boolean needsClear=false;
            if(problem==null) {
                var current=level.getBlockState(pos);
                needsClear=!current.isAir()&&!current.equals(wanted);
                if(needsClear&&!canClear(level,pos,current)) problem="Protected or unbreakable block";
            }
            for(Job other:jobs.values()) {
                if(!j.dimension.equals(other.dimension)||other.status.equals("UNDONE")) continue;
                int ow=other.rotation%180==0?other.width:other.depth, od=other.rotation%180==0?other.depth:other.width;
                if(pos.getX()>=other.x&&pos.getX()<other.x+ow&&pos.getY()>=other.y&&pos.getY()<other.y+other.height&&pos.getZ()>=other.z&&pos.getZ()<other.z+od) { problem="Site overlaps job "+other.id; break; }
            }
            if(problem!=null) { blocked++; if(firstProblem==null) firstProblem=problem+" at "+pos.toShortString(); }
            else if(needsClear) removable++;
            if(preview && level.hasChunkAt(pos) && (problem!=null||needsClear||desired.containsKey(pos)) && (i%Math.max(3,(w*d*h+511)/512)==0))
                level.sendParticles(problem!=null?ParticleTypes.FLAME:needsClear?ParticleTypes.SMOKE:ParticleTypes.END_ROD,pos.getX()+0.5,pos.getY()+0.5,pos.getZ()+0.5,1,0,0,0,0);
        }
        if(preview) {
            final String message="Preview: "+plan.size()+" blueprint blocks, "+removable+" blocks to clear, "+blocked+" blocked positions. "+(firstProblem==null?"":"First issue: "+firstProblem+". ")+"No blocks changed.";
            source.sendSuccess(()->Component.literal(message),false); return 1;
        }
        if(file==null || storageFailed) return fail(source,"Storage unavailable or job limit reached");
        if(blocked>0) return fail(source,firstProblem);
        j.clearing=true;
        return spawnJob(source,j,origin,plan.size());
    }
    private int spawnJob(CommandSourceStack source,Job j,BlockPos origin,int blockCount) throws CommandSyntaxException {
        var player=source.getPlayerOrException(); var level=source.getLevel();
        var npc=EntityType.VILLAGER.spawn(level,player.blockPosition(),MobSpawnType.COMMAND);
        if(npc==null) return fail(source,"Could not spawn builder");
        protect(npc);
        remember(j,npc);
        j.id=jobs.keySet().stream().mapToInt(i->i).max().orElse(0)+1; j.worker=npc.getUUID().toString();
        try { if(j.imported!=null) persistPlan(j); } catch(Exception ex) { npc.discard(); LOGGER.error("Cannot persist schematic plan",ex); return fail(source,"Cannot persist schematic plan: "+ex.getMessage()); }
        jobs.put(j.id,j);
        if(!save()) { jobs.remove(j.id); npc.discard(); return fail(source,"Failed to save job"); }
        ticket(level,j,npc.blockPosition(),true);
        LOGGER.info("NPC Architects created job {} at {}",j.id,origin);
        source.sendSuccess(()->Component.literal("Created job "+j.id+" at "+origin.toShortString()+" ("+blockCount+" blocks)"),false); return 1;
    }
    private int undo(CommandSourceStack source,int id) {
        Job j=jobs.get(id);
        if(j==null||storageFailed||j.status.equals("UNDONE")) return fail(source,"No undoable job "+id);
        if(j.placed.isEmpty()&&j.cleared.isEmpty()) return fail(source,"No tracked placements to undo (older jobs cannot be undone safely).");
        var level=source.getLevel();
        if(!level.dimension().location().toString().equals(j.dimension)) return fail(source,"Go to the job dimension first.");
        var plan=cells(j);
        for(int index:j.placed) {
            if(index<0||index>=plan.size()) return fail(source,"Invalid undo history.");
            var c=plan.get(index); var pos=new BlockPos(j.x+c.x(),j.y+c.y(),j.z+c.z());
            if(!level.hasChunkAt(pos)||!level.getWorldBorder().isWithinBounds(pos)) return fail(source,"Load the entire site before undoing.");
        }
        for(Cleared old:j.cleared) {
            var pos=new BlockPos(old.x,old.y,old.z);
            if(siteProblem(level,pos)!=null) return fail(source,"Load the entire clearing site before undoing.");
        }
        j.status="PAUSED"; if(!save()) return fail(source,"Could not save paused job."); releaseTickets(level,j);
        if(level.getEntity(UUID.fromString(j.worker)) instanceof Villager npc) setWorking(npc,false);
        int removed=0, skipped=0;
        for(int i=j.placed.size()-1;i>=0;i--) {
            var c=plan.get(j.placed.get(i)); var pos=new BlockPos(j.x+c.x(),j.y+c.y(),j.z+c.z());
            if(level.getBlockState(pos).equals(material(j,c.material())) && level.setBlock(pos,Blocks.AIR.defaultBlockState(),3)) removed++; else skipped++;
        }
        int restored=0;
        for(int i=j.cleared.size()-1;i>=0;i--) {
            var old=j.cleared.get(i); var pos=new BlockPos(old.x,old.y,old.z);
            if(level.getBlockState(pos).isAir() && level.getEntities(null,new AABB(pos)).isEmpty() && level.setBlock(pos,net.minecraft.world.level.block.Block.stateById(old.state),3)) restored++; else skipped++;
        }
        j.cleared.clear(); j.placed.clear(); j.status="UNDONE"; if(!save()) return fail(source,"Undo applied but storage failed; inspect server log.");
        final int count=removed, ignored=skipped, recovered=restored;
        source.sendSuccess(()->Component.literal("Undo job "+id+": "+count+" removed, "+recovered+" terrain blocks restored, "+ignored+" changed/missing blocks skipped."),false); return 1;
    }
    private static Map<BlockPos,BlockState> desiredStates(Job j) {
        if(j.desiredCache==null) {
            Map<BlockPos,BlockState> result=new HashMap<>();
            for(var c:cells(j)) result.put(new BlockPos(j.x+c.x(),j.y+c.y(),j.z+c.z()),material(j,c.material()));
            j.desiredCache=result;
        }
        return j.desiredCache;
    }
    private static BlockPos clearPosition(Job j,int i) {
        int w=physicalWidth(j), d=physicalDepth(j);
        if(j.imported==null) return new BlockPos(j.x+(i/d)%w,j.y+j.height-1-i/(w*d),j.z+i%d);
        // Process a full vertical column of chunk-sized strips before moving across the map.
        // The index is stable across restart and does not depend on chunk load order.
        long stripSpan=16L*d*j.height;
        int strip=(int)(i/stripSpan);
        int sx=strip*16, sw=Math.min(16,w-sx);
        int local=(int)(i-strip*stripSpan);
        long rowSpan=16L*sw*j.height;
        int row=(int)(local/rowSpan);
        int sz=row*16, sd=Math.min(16,d-sz);
        int within=(int)(local-row*rowSpan);
        int layer=within/(sw*sd), cell=within%(sw*sd);
        return new BlockPos(j.x+sx+cell/sd,j.y+j.height-1-layer,j.z+sz+cell%sd);
    }
    private static String siteProblem(ServerLevel level,BlockPos pos) {
        if(!level.hasChunkAt(pos)) return "Chunk unloaded";
        if(level.isOutsideBuildHeight(pos)||!level.getWorldBorder().isWithinBounds(pos)) return "Outside world bounds";
        return null;
    }
    private static boolean canClear(ServerLevel level,BlockPos pos,BlockState state) {
        return !state.hasBlockEntity() && level.getBlockEntity(pos)==null && state.getDestroySpeed(level,pos)>=0 && state.getFluidState().isEmpty();
    }
    private void blocked(Job j,Villager npc,String reason) {
        j.status="BLOCKED"; j.reason=reason; setWorking(npc,false); resetMovement(j); if(npc.level() instanceof ServerLevel world) releaseTickets(world,j);
        LOGGER.warn("NPC Architects job {} blocked: {}",j.id,reason); save();
    }
    // The construction controller owns movement; ordinary villager brain goals must not compete.
    private static void setWorking(Villager npc,boolean working) {
        if(!npc.isNoAi()) { npc.getNavigation().stop(); npc.setNoAi(true); }
        if(npc.isNoGravity()!=working) npc.setNoGravity(working);
        if(!working) npc.setDeltaMovement(Vec3.ZERO);
    }
    private static void resetMovement(Job j) {
        j.workTarget=null; j.workStand=null; j.movementFailures=0;
    }
    private static boolean inReach(Villager npc,BlockPos pos) {
        return npc.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos))<=12.25;
    }
    private static boolean safeStand(ServerLevel level,Villager npc,Vec3 feet,BlockPos target) {
        var box=npc.getBoundingBox().move(feet.subtract(npc.position()));
        if(box.intersects(new AABB(target))) return false;
        if(!level.getWorldBorder().isWithinBounds(box)) return false;
        var low=BlockPos.containing(box.minX,box.minY,box.minZ);
        var high=BlockPos.containing(box.maxX,box.maxY,box.maxZ);
        if(level.isOutsideBuildHeight(low)||level.isOutsideBuildHeight(high)) return false;
        for(int x=low.getX();x<=high.getX();x++) for(int z=low.getZ();z<=high.getZ();z++)
            if(!level.hasChunkAt(new BlockPos(x,low.getY(),z))) return false;
        return level.noCollision(npc,box) && level.getEntities(npc,box).isEmpty();
    }
    private static Vec3 chooseStand(ServerLevel level,Villager npc,BlockPos target) {
        Vec3 best=null; double bestScore=Double.MAX_VALUE;
        for(int dy:new int[]{-1,0,-2,1,-3,2}) for(int dx=-3;dx<=3;dx++) for(int dz=-3;dz<=3;dz++) {
            if(dx==0&&dz==0&&dy<1) continue;
            var feet=new Vec3(target.getX()+0.5+dx,target.getY()+dy,target.getZ()+0.5+dz);
            if(feet.add(0,npc.getEyeHeight(),0).distanceToSqr(Vec3.atCenterOf(target))>10.89) continue;
            if(!safeStand(level,npc,feet,target)) continue;
            double score=feet.distanceToSqr(npc.position());
            if(score<bestScore) { bestScore=score; best=feet; }
        }
        return best;
    }
    private boolean approach(Job j,Villager npc,ServerLevel level,BlockPos pos) {
        setWorking(npc,true);
        npc.setDeltaMovement(Vec3.ZERO);
        if(inReach(npc,pos)&&!npc.getBoundingBox().intersects(new AABB(pos))) {
            resetMovement(j);
            npc.getLookControl().setLookAt(pos.getX()+0.5,pos.getY()+0.5,pos.getZ()+0.5);
            return true;
        }
        if(!pos.equals(j.workTarget)||j.workStand==null||!safeStand(level,npc,j.workStand,pos)) {
            j.workTarget=pos; j.workStand=chooseStand(level,npc,pos); j.movementFailures=0;
        }
        if(j.workStand==null) return false;
        var delta=j.workStand.subtract(npc.position());
        if(delta.lengthSqr()>32*32 && safeStand(level,npc,j.workStand,pos)) {
            npc.teleportTo(j.workStand.x,j.workStand.y,j.workStand.z); checkpointWorker(level,j,npc); return false;
        }
        if(delta.lengthSqr()<0.01) { j.workStand=null; return false; }
        var movement=delta.normalize().scale(Math.min(0.65,delta.length()));
        var before=npc.position();
        // Move through collision-checked space; elevated positions use temporary hover.
        npc.move(MoverType.SELF,movement);
        npc.setDeltaMovement(Vec3.ZERO);
        if(npc.position().distanceToSqr(before)<0.01 || npc.position().distanceToSqr(j.workStand)>=before.distanceToSqr(j.workStand)-0.01) j.movementFailures++;
        else j.movementFailures=0;
        // A blocked direct route may reposition only to a verified empty loaded stance.
        if(j.movementFailures>=4 && safeStand(level,npc,j.workStand,pos)) {
            npc.teleportTo(j.workStand.x,j.workStand.y,j.workStand.z);
            LOGGER.debug("NPC Architects job {} repositioned to safe work stance for {}",j.id,pos);
            j.movementFailures=0;
        }
        checkpointWorker(level,j,npc);
        npc.getLookControl().setLookAt(pos.getX()+0.5,pos.getY()+0.5,pos.getZ()+0.5);
        return false;
    }
    private void clearStep(ServerLevel level,Villager npc,Job j) {
        int total=j.width*j.depth*j.height;
        var desired=desiredStates(j);
        int start=j.clearCursor;
        while(j.clearCursor<total && j.clearCursor-start<SURVEY_BATCH) {
            var pos=clearPosition(j,j.clearCursor);
            if(!loadTarget(level,j,pos)) { waiting(j,"Loading clearing chunk "+new ChunkPos(pos)); return; }
            String problem=siteProblem(level,pos);
            if(problem!=null) { blocked(j,npc,problem+" at "+pos.toShortString()); return; }
            var current=level.getBlockState(pos);
            if(current.isAir()||current.equals(desired.getOrDefault(pos,Blocks.AIR.defaultBlockState()))) { j.clearCursor++; continue; }
            if(!canClear(level,pos,current)) { blocked(j,npc,"Protected or unbreakable block at "+pos.toShortString()); return; }
            if(approach(j,npc,level,pos)) {
                npc.swing(InteractionHand.MAIN_HAND);
                // No drops: undo can restore cleared terrain without duplicating resources.
                if(level.destroyBlock(pos,false,npc)) {
                    j.cleared.add(new Cleared(pos.getX(),pos.getY(),pos.getZ(),net.minecraft.world.level.block.Block.getId(current)));
                    j.clearCursor++; j.stalled=0; save(); return;
                }
            }
            if(++j.stalled>=60) blocked(j,npc,"Cannot reach or clear "+pos.toShortString());
            return;
        }
        if(j.clearCursor<total) { save(); return; }
        j.clearing=false; j.stalled=0; save();
        LOGGER.info("NPC Architects job {} finished site clearing",j.id);
    }
    private static BlockState material(Job j,String name) { if(j.imported!=null) { if(j.importedStates==null) j.importedStates=new HashMap<>(); return j.importedStates.computeIfAbsent(name,SchematicImport::parseState); } return switch(name) { case "floor"->Blocks.COBBLESTONE.defaultBlockState(); case "roof"->Blocks.SPRUCE_PLANKS.defaultBlockState(); case "glass"->Blocks.GLASS.defaultBlockState(); default->Blocks.OAK_PLANKS.defaultBlockState(); }; }
    private static boolean terminal(Job j) {
        return j.status.equals("DONE")||j.status.equals("CANCELLED")||j.status.equals("UNDONE");
    }
    private static ServerLevel jobLevel(MinecraftServer server,Job j) {
        for(var level:server.getAllLevels()) if(level.dimension().location().toString().equals(j.dimension)) return level;
        return null;
    }
    private static void protect(Villager npc) {
        npc.setInvulnerable(true);
        npc.setPersistenceRequired();
        if(!npc.getTags().contains("npcarchitects_worker")) {
            npc.addTag("npcarchitects_worker");
            npc.setCustomName(Component.literal("Architect"));
            npc.setCustomNameVisible(true);
        }
    }
    private static void remember(Job j,Villager npc) {
        var pos=npc.blockPosition(); j.workerX=pos.getX(); j.workerY=pos.getY(); j.workerZ=pos.getZ();
    }
    private void targetChanged(LivingChangeTargetEvent event) {
        if(event.getNewTarget() instanceof Villager villager && villager.getTags().contains("npcarchitects_worker")) event.setCanceled(true);
    }
    private void workerDeath(LivingDeathEvent event) {
        if(storageFailed || !(event.getEntity() instanceof Villager npc)) return;
        for(Job j:jobs.values()) if(!terminal(j)&&npc.getUUID().toString().equals(j.worker)) {
            remember(j,npc); j.workerDied=true; j.reason="Builder died; replacement pending";
            LOGGER.warn("NPC Architects job {} lost builder {}",j.id,j.worker); save(); break;
        }
    }
    private void waiting(Job j,String reason) {
        if(!reason.equals(j.reason)) { j.reason=reason; LOGGER.warn("NPC Architects job {}: {}",j.id,reason); save(); }
    }
    private Villager replaceWorker(ServerLevel level,Job j,BlockPos anchor) {
        if(storageFailed) return null;
        for(int radius=0;radius<=3;radius++) for(int dx=-radius;dx<=radius;dx++) for(int dz=-radius;dz<=radius;dz++) for(int dy:new int[]{0,1,-1,2,-2}) {
            if(Math.max(Math.abs(dx),Math.abs(dz))!=radius) continue;
            var pos=anchor.offset(dx,dy,dz);
            if(siteProblem(level,pos)!=null||level.isOutsideBuildHeight(pos.above())||!level.isEmptyBlock(pos)||!level.isEmptyBlock(pos.above())||!level.getBlockState(pos.below()).isFaceSturdy(level,pos.below(),net.minecraft.core.Direction.UP)||!level.getEntities(null,new AABB(pos).expandTowards(0,1,0)).isEmpty()) continue;
            var replacement=EntityType.VILLAGER.spawn(level,pos,MobSpawnType.COMMAND);
            if(replacement==null) continue;
            protect(replacement);
            String previous=j.worker; boolean died=j.workerDied; Integer oldX=j.workerX,oldY=j.workerY,oldZ=j.workerZ;
            j.worker=replacement.getUUID().toString(); remember(j,replacement); j.workerDied=false; j.stalled=0; j.reason="";
            if(!save()) { replacement.discard(); j.worker=previous; j.workerDied=died; j.workerX=oldX;j.workerY=oldY;j.workerZ=oldZ; return null; }
            LOGGER.info("NPC Architects job {} replaced builder {} with {}",j.id,previous,j.worker);
            return replacement;
        }
        return null;
    }
    private int recover(CommandSourceStack source,int id) throws CommandSyntaxException {
        Job j=jobs.get(id);
        if(j==null||terminal(j)||storageFailed) return fail(source,"No recoverable job "+id);
        var player=source.getPlayerOrException(); var level=source.getLevel();
        if(!level.dimension().location().toString().equals(j.dimension)) return fail(source,"Go to the job dimension first.");
        for(var world:source.getServer().getAllLevels()) if(world.getEntity(UUID.fromString(j.worker)) instanceof Villager npc && npc.isAlive()) {
            protect(npc); return fail(source,"Builder is still alive; no replacement spawned. Use /architect resume "+id);
        }
        if(replaceWorker(level,j,player.blockPosition())==null) return fail(source,"No safe loaded spawn position nearby or storage failed.");
        j.status="RUNNING"; if(!save()) return fail(source,"Replacement spawned but job resume could not be saved."); ticket(level,j,workerAnchor(j),true);
        source.sendSuccess(()->Component.literal("Builder replaced for job "+id+"; construction resumed from saved progress."),false); return 1;
    }
    private void tick(TickEvent.ServerTickEvent.Post e) {
        if(++ticks%10!=0) return;
        if(storageFailed) {
            for(Job j:jobs.values()) { var world=jobLevel(e.getServer(),j); if(world!=null) { if(world.getEntity(UUID.fromString(j.worker)) instanceof Villager worker) setWorking(worker,false); releaseTickets(world,j); } }
            return;
        }
        for(Job j:jobs.values()) {
            if(storageFailed) break;
            var level=jobLevel(e.getServer(),j);
            if(level==null) continue;
            if(level.getEntity(UUID.fromString(j.worker)) instanceof Villager worker && worker.isAlive()) { protect(worker); setWorking(worker,j.status.equals("RUNNING")&&!storageFailed); if(j.status.equals("RUNNING")) checkpointWorker(level,j,worker); }
            if(j.status.equals("RUNNING")) {
                ticket(level,j,workerAnchor(j),true);
                step(e.getServer(),j);
            } else releaseTickets(level,j);
        }
    }
    private void step(MinecraftServer server,Job j) {
        ServerLevel level=null; for(var l:server.getAllLevels()) if(l.dimension().location().toString().equals(j.dimension)) {level=l;break;}
        if(level==null) return;
        var cells=cells(j);
        Villager npc;
        if(level.getEntity(UUID.fromString(j.worker)) instanceof Villager living && living.isAlive()) {
            npc=living; protect(npc); checkpointWorker(level,j,npc);
            if(j.workerDied || j.reason.startsWith("Builder")) { j.workerDied=false; j.reason=""; if(!save()) return; }
        } else {
            if(j.workerDied) {
                var anchor=workerAnchor(j);
                npc=replaceWorker(level,j,anchor);
                if(npc==null) { waiting(j,"Builder died; waiting for a safe loaded respawn position. Use /architect recover "+j.id); return; }
            } else {
                waiting(j,"Builder unavailable; loading saved worker chunk "+new ChunkPos(workerAnchor(j))+". Use /architect recover "+j.id+" only if the builder is gone.");
                return;
            }
        }
        if(j.surveying) { surveyStep(level,npc,j); return; }
        if(j.clearing) { clearStep(level,npc,j); return; }
        if(j.cursor>=cells.size()) { j.status="DONE"; j.reason=""; setWorking(npc,false); resetMovement(j); releaseTickets(level,j); LOGGER.info("NPC Architects job {} completed",j.id); save(); return; }
        var c=cells.get(j.cursor); var pos=new BlockPos(j.x+c.x(),j.y+c.y(),j.z+c.z());
        if(!loadTarget(level,j,pos)) { waiting(j,"Loading construction chunk "+new ChunkPos(pos)); return; }
        String problem=siteProblem(level,pos);
        if(problem!=null) { blocked(j,npc,problem+" at "+pos.toShortString()); return; }
        var wanted=material(j,c.material()); var current=level.getBlockState(pos);
        if(current.equals(wanted)) { j.cursor++; j.stalled=0; j.reason=""; save(); return; }
        if(!current.isAir() || !level.getWorldBorder().isWithinBounds(pos)) { blocked(j,npc,"New obstruction at "+pos.toShortString()); return; }
        if(approach(j,npc,level,pos) && level.getEntities(npc,new AABB(pos)).isEmpty()) {
            npc.swing(InteractionHand.MAIN_HAND);
            if(level.setBlock(pos,wanted,3)) {
                j.placed.add(j.cursor); j.cursor++; j.stalled=0; j.reason="";
                if(j.cursor==cells.size() || cells.get(j.cursor).y()!=c.y()) LOGGER.info("NPC Architects job {} finished layer {} of {}",j.id,c.y()+1,j.height);
                save(); return;
            }
        }
        if(++j.stalled>=60) blocked(j,npc,"Cannot reach or place "+wanted.getBlock().getDescriptionId()+" at "+pos.toShortString()+" (layer "+(c.y()+1)+"/"+j.height+")");
    }
}
