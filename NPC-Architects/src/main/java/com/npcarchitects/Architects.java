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
import net.minecraft.ChatFormatting;
import net.minecraftforge.event.entity.player.PlayerEvent;
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
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
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
    private static final TicketType<String> WORK_TICKET=TicketType.create("npcarchitects_work",String::compareTo,40);
    private static final int MAX_TEAM=Math.max(1,Math.min(64,Integer.getInteger("npcarchitects.maxTeam",16)));
    private static final int CLEAR_BATCH=1024;
    // One block mutation per worker per work cycle. Keep the durable save after each mutation.
    private static final int WORK_INTERVAL=WorkCadence.interval(Integer.getInteger("npcarchitects.workIntervalTicks",4));
    private static final int HOME_ATTEMPTS=WorkCadence.attempts(60,WORK_INTERVAL);
    // Distance per movement tick, independent of the block mutation cadence.
    private static final double MOVE_SPEED=movementSpeed();
    private static double movementSpeed() {
        try {
            double speed=Double.parseDouble(System.getProperty("npcarchitects.moveSpeed","0.65"));
            return Double.isFinite(speed)?Math.max(0.1,Math.min(1.0,speed)):0.65;
        } catch(NumberFormatException ex) { return 0.65; }
    }
    private static final int SURVEY_BATCH=Math.max(1,Integer.getInteger("npcarchitects.surveyBatch",16384));
    private static final long SURVEY_BUDGET_NS=Math.max(1,Math.min(20,Integer.getInteger("npcarchitects.surveyBudgetMs",4)))*1_000_000L;
    private static final int SURVEY_CHECKPOINT_TICKS=100;
    private int surveyRoundRobin;
    private int ticks;
    private boolean storageFailed;
    private ProgressJournal journal;
    private boolean progressDirty;
    private int lastProgressFlush;
    private static final int FLUSH_TICKS=20;
    private static final long WORK_BUDGET_NS=Math.max(2,Math.min(40,Integer.getInteger("npcarchitects.workBudgetMs",12)))*1_000_000L;
    private long workDeadline;
    private int workJobOffset;
    private PreviewScan previewScan;
    private static final int PREVIEW_BATCH=8192;
    private static final long PREVIEW_BUDGET_NS=2_000_000L;
    private static final int PREVIEW_LIFETIME=1200;
    private static final class PreviewScan {
        UUID viewer;
        Job original,replacement;
        String originalPlan;
        int cursor,indexCursor,startTick,completeTick=-1;
        Map<BlockPos,BlockState> desired=new HashMap<>();
        long added,removed,replaced,retained,empty,unknown,protectedCount;
        // Bounded local overlay; never store or mutate live world blocks.
        Set<BlockPos> shown=new HashSet<>();
        Iterator<BlockPos> restore;
        boolean closing;
        int centerX=Integer.MIN_VALUE,centerZ=Integer.MIN_VALUE,renderCursor;
        int minX,maxX,minZ,maxZ;
        int nextRefresh;
    
    }
    private static final int STUCK_ATTEMPTS=WorkCadence.attempts(Math.max(1,Math.min(3600,Integer.getInteger("npcarchitects.stuckSeconds",15))),WORK_INTERVAL);
    private static final int RECOVERY_LIMIT=Math.max(1,Math.min(20,Integer.getInteger("npcarchitects.stuckRetries",3)));
    private static class Worker {
        String worker;
        Integer workerX,workerY,workerZ;
        Integer homeX,homeY,homeZ;
        int recoveryAttempts;
        boolean returningHome, respawnRequested;
        transient int homeWait;
        boolean workerDied;
        transient BlockPos workTarget;
        transient Vec3 workStand;
        transient int movementFailures;
        transient ChunkPos workChunk,workerChunk;
        transient int assignment=-1, teamStalled;
        transient boolean assignmentClearing;
        transient String chunkStage="IDLE";
    }
    private static final class Job extends Worker {
        List<Worker> assistants=new ArrayList<>();
        String distribution="adaptive";
        int spacing=8;
        transient int zoneLayer=Integer.MIN_VALUE, zoneScan, zoneTeam;
        transient String zoneMode;
        transient List<NavigableSet<Integer>> zoneQueues;
        transient Map<Long,NavigableSet<Integer>> buildChunks;
        transient int buildChunkLayer=Integer.MIN_VALUE,buildChunkScan;
        transient List<ClearChunk> clearChunks;
        transient Map<Worker,Long> chunkOwners;
        transient boolean chunkPhaseClearing;
        transient int chunkPhaseLayer=Integer.MIN_VALUE;
        transient Map<Long,ClearChunk> lifecycleChunks;
        transient Map<Long,Long> deferredChunks;
        transient int lifecycleScan;
        transient boolean lifecycleIndexed;
        boolean independentChunks;
        transient int workerOffset;
        long storageGeneration;
        transient int journalCleared,journalPlaced;
        transient Set<Integer> pendingClear=new HashSet<>(),pendingBuild=new HashSet<>();

        Set<Integer> teamDone=new HashSet<>();
        Set<Integer> clearDone=new HashSet<>();
        List<String> retiredWorkers=new ArrayList<>();
        int id, x, y, z, width, depth, cursor, stalled;
        int height=5, rotation, clearCursor;
        boolean clearing;
        String schematic;
        transient List<Blueprint.Cell> imported;
        String planFile;
        boolean surveying;
        int surveyCursor;
        transient int surveyCheckpointTick;
        transient int surveyCheckpointCursor;

        
        transient Map<String,BlockState> importedStates;
        transient Map<BlockPos,BlockState> desiredCache;

        List<Cleared> cleared = new ArrayList<>();
        String reason = "";
        List<Integer> placed = new ArrayList<>();
        String dimension, status = "RUNNING";
        String owner;
        long completedAt;
        boolean completionNotified;
    }
    private static final class ClearChunk {
        final long key; final int minX,maxX,minZ,maxZ;
        int x,z,y;
        int verifyOffset, drainPasses;
        ClearChunk(long key,int minX,int maxX,int minZ,int maxZ,int top){
            this.key=key;this.minX=minX;this.maxX=maxX;this.minZ=minZ;this.maxZ=maxZ;
            x=minX;z=minZ;y=top;
        }
        boolean done(){return x>maxX;}
        void advance(int top,int bottom){
            if(--y>=bottom)return;
            y=top;if(++z>maxZ){z=minZ;x++;}
        }
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
        MinecraftForge.EVENT_BUS.addListener(this::workerJoined);
        MinecraftForge.EVENT_BUS.addListener(this::playerJoined);
        LOGGER.info("NPC Architects 0.3.3 initialized; work interval {} ticks, maximum {} block actions/second per worker at 20 TPS, stuck threshold {} cycles",WORK_INTERVAL,20.0/WORK_INTERVAL,STUCK_ATTEMPTS);
    }
    private void started(ServerStartedEvent e) {
        previewScan=null; jobs.clear(); selections.clear(); ticks=0; surveyRoundRobin=0; storageFailed=false; progressDirty=false; lastProgressFlush=0; workJobOffset=0;
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
                for (Job j:loaded) { loadPlan(j); validatePlan(j); UUID.fromString(j.worker); validateTeam(j); if(j.cursor<0 || j.cursor>cells(j).size() || j.clearCursor<0 || j.clearCursor>j.width*j.depth*j.height || j.surveyCursor<0 || j.surveyCursor>j.width*j.depth*j.height || j.cleared==null || j.placed==null || jobs.putIfAbsent(j.id,j)!=null) throw new IllegalStateException("Invalid job"); }
            }
            journal=new ProgressJournal(file.resolveSibling("npcarchitects-progress.log"));
            journal.replay(payload->{
                ProgressFrame frame=JSON.fromJson(payload,ProgressFrame.class);
                Job j=jobs.get(frame.id);
                if(j==null)throw new IllegalStateException("Unknown journal job "+frame.id);
                if(frame.generation!=j.storageGeneration)return;
                if(frame.anchors!=null)for(Worker w:team(j))for(Worker anchor:frame.anchors)if(w.worker.equals(anchor.worker)){w.workerX=anchor.workerX;w.workerY=anchor.workerY;w.workerZ=anchor.workerZ;break;}
                if(frame.clearedFrom>j.cleared.size()||frame.placedFrom>j.placed.size())throw new IllegalStateException("Missing journal history");
                for(int i=Math.max(0,j.cleared.size()-frame.clearedFrom);i<frame.cleared.size();i++)j.cleared.add(frame.cleared.get(i));
                for(int i=Math.max(0,j.placed.size()-frame.placedFrom);i<frame.placed.size();i++)j.placed.add(frame.placed.get(i));
                if(frame.clearCursor>=j.clearCursor){
                    j.clearDone.addAll(frame.clearAdded);j.clearCursor=frame.clearCursor;
                    j.clearDone.removeIf(i->i<j.clearCursor);
                }
                if(frame.cursor>=j.cursor){
                    j.teamDone.addAll(frame.buildAdded);j.cursor=frame.cursor;
                    j.teamDone.removeIf(i->i<j.cursor);
                }
                if(frame.clearCursor==j.width*j.depth*j.height){j.clearing=false;j.clearDone.clear();}
            });
            for(Job j:jobs.values()){
                validateTeam(j);j.journalCleared=j.cleared.size();j.journalPlaced=j.placed.size();
                j.pendingClear=new HashSet<>();j.pendingBuild=new HashSet<>();
            }
        } catch(Exception ex) { storageFailed=true; jobs.clear(); LOGGER.error("NPC Architects: cannot load jobs", ex); }
        if(!storageFailed) for(Job j:jobs.values()) if(j.status.equals("RUNNING")) { var level=jobLevel(e.getServer(),j); if(level!=null) for(Worker w:team(j)) ticket(level,w,workerAnchor(w),true); }
    }
    private static final class ProgressFrame {
        int id,cursor,clearCursor,clearedFrom,placedFrom;
        List<Worker> anchors;
        long generation;
        List<Cleared> cleared;
        List<Integer> placed;
        Set<Integer> clearAdded,buildAdded;
        ProgressFrame(Job j){
            id=j.id;generation=j.storageGeneration;cursor=j.cursor;clearCursor=j.clearCursor;
            anchors=new ArrayList<>();for(Worker w:team(j)){Worker anchor=new Worker();anchor.worker=w.worker;anchor.workerX=w.workerX;anchor.workerY=w.workerY;anchor.workerZ=w.workerZ;anchors.add(anchor);}
            clearedFrom=j.journalCleared;placedFrom=j.journalPlaced;
            cleared=new ArrayList<>(j.cleared.subList(clearedFrom,j.cleared.size()));
            placed=new ArrayList<>(j.placed.subList(placedFrom,j.placed.size()));
            clearAdded=new HashSet<>(j.pendingClear);buildAdded=new HashSet<>(j.pendingBuild);
        }
    }
    private void markProgress(Job j,int index,boolean clearing){
        (clearing?j.pendingClear:j.pendingBuild).add(index);progressDirty=true;
    }
    private boolean flushProgress(){
        if(storageFailed||journal==null)return false;
        if(!progressDirty)return true;
        try{
            for(Job j:jobs.values()){
                if(terminal(j))continue;
                journal.append(JSON.toJson(new ProgressFrame(j)));
                j.journalCleared=j.cleared.size();j.journalPlaced=j.placed.size();
                j.pendingClear.clear();j.pendingBuild.clear();
            }
            journal.force();progressDirty=false;lastProgressFlush=ticks;return true;
        }catch(Exception ex){storageFailed=true;LOGGER.error("NPC Architects: progress journal failed; jobs halted",ex);return false;}
    }
    private void stopping(ServerStoppingEvent e) {
        for(Job j:jobs.values()){var level=jobLevel(e.getServer(),j);if(level!=null)for(Worker w:team(j))releaseTickets(level,w);}
        save();if(journal!=null)try{journal.close();}catch(Exception ex){LOGGER.error("Cannot close architect journal",ex);}
        journal=null;previewScan=null;jobs.clear();selections.clear();file=null;schematicDirectory=null;planDirectory=null;
    }
    // Full snapshots are reserved for control/lifecycle transitions, not block actions.
    private boolean save() {
        if(file==null||storageFailed||journal==null)return false;
        try{
            // New generations make replay safe if a crash happens after snapshot
            // publication but before journal truncation (including undo).
            for(Job j:jobs.values())j.storageGeneration++;
            Path tmp=file.resolveSibling(file.getFileName()+".tmp");
            try(var out=Files.newBufferedWriter(tmp)){JSON.toJson(jobs.values(),out);}
            try{Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException ex){Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING);}
            journal.reset();
            for(Job j:jobs.values()){
                j.journalCleared=j.cleared.size();j.journalPlaced=j.placed.size();j.pendingClear.clear();j.pendingBuild.clear();
            }
            progressDirty=false;lastProgressFlush=ticks;return true;
        }catch(Exception ex){storageFailed=true;LOGGER.error("NPC Architects: jobs halted",ex);return false;}
    }
    private void commands(RegisterCommandsEvent e) {
        var root=Commands.literal("architect").executes(c->{
            c.getSource().sendSuccess(()->Component.literal("NPC Architects 0.3.3 active. Commands: status [all/id], house <width> <depth> [height], pos1, pos2, rotate, preview house, build house, schematic list/info/preview/build, redesign <id> <name> [rotation], redesign preview <id> <name> [rotation], redesign preview clear, undo [id], recover <id>, respawn <id> [worker]/all, add <id> <count>, distribution, spacing, rebalance, zones, pause, resume, cancel. Construction requires operator level 2."),false); return 1;
        });
        root.then(Commands.literal("ping").executes(c->{ c.getSource().sendSuccess(()->Component.literal("NPC Architects 0.3.3 is responding."),false); return 1; }));
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
        root.then(Commands.literal("redesign").requires(src->src.hasPermission(2))
            .then(Commands.literal("preview")
                .then(Commands.literal("clear").executes(c->clearRedesignPreview(c.getSource())))
                .then(Commands.argument("id",IntegerArgumentType.integer(1))
                    .then(Commands.argument("name",StringArgumentType.word())
                        .suggests((c,b)->{for(String name:schematicNames())if(name.startsWith(b.getRemaining()))b.suggest(name);return b.buildFuture();})
                        .executes(c->startRedesignPreview(c.getSource(),IntegerArgumentType.getInteger(c,"id"),StringArgumentType.getString(c,"name"),0))
                        .then(Commands.argument("rotation",IntegerArgumentType.integer(0,270))
                            .executes(c->startRedesignPreview(c.getSource(),IntegerArgumentType.getInteger(c,"id"),StringArgumentType.getString(c,"name"),IntegerArgumentType.getInteger(c,"rotation")))))))
            .then(Commands.argument("id",IntegerArgumentType.integer(1))
                .then(Commands.argument("name",StringArgumentType.word())
                    .suggests((c,b)->{for(String name:schematicNames())if(name.startsWith(b.getRemaining()))b.suggest(name);return b.buildFuture();})
                    .executes(c->redesign(c.getSource(),IntegerArgumentType.getInteger(c,"id"),StringArgumentType.getString(c,"name"),0))
                    .then(Commands.argument("rotation",IntegerArgumentType.integer(0,270))
                        .executes(c->redesign(c.getSource(),IntegerArgumentType.getInteger(c,"id"),StringArgumentType.getString(c,"name"),IntegerArgumentType.getInteger(c,"rotation")))))));
        root.then(Commands.literal("undo").requires(src->src.hasPermission(2))
            .executes(c->undo(c.getSource(),jobs.keySet().stream().mapToInt(i->i).max().orElse(0)))
            .then(Commands.argument("id",IntegerArgumentType.integer(1)).executes(c->undo(c.getSource(),IntegerArgumentType.getInteger(c,"id")))));
        root.then(Commands.literal("recover").requires(src->src.hasPermission(2))
            .then(Commands.argument("id",IntegerArgumentType.integer(1)).executes(c->recover(c.getSource(),IntegerArgumentType.getInteger(c,"id")))));
        root.then(Commands.literal("respawn").requires(src->src.hasPermission(2))
            .then(Commands.literal("all").executes(c->requestRespawn(c.getSource(),0,0)))
            .then(Commands.argument("id",IntegerArgumentType.integer(1))
                .executes(c->requestRespawn(c.getSource(),IntegerArgumentType.getInteger(c,"id"),0))
                .then(Commands.argument("worker",IntegerArgumentType.integer(1))
                    .executes(c->requestRespawn(c.getSource(),IntegerArgumentType.getInteger(c,"id"),IntegerArgumentType.getInteger(c,"worker"))))));
        root.then(Commands.literal("add").requires(src->src.hasPermission(2)).then(Commands.argument("id",IntegerArgumentType.integer(1)).then(Commands.argument("count",IntegerArgumentType.integer(1,64)).executes(c->addWorkers(c.getSource(),IntegerArgumentType.getInteger(c,"id"),IntegerArgumentType.getInteger(c,"count"))))));
        root.then(Commands.literal("status").requires(src->src.hasPermission(2))
            .executes(c->showStatus(c.getSource(),false))
            .then(Commands.literal("all").executes(c->showStatus(c.getSource(),true)))
            .then(Commands.argument("id",IntegerArgumentType.integer(1)).executes(c->showRoster(c.getSource(),IntegerArgumentType.getInteger(c,"id")))));
        for(String action:List.of("pause","resume","cancel")) root.then(Commands.literal(action).requires(src->src.hasPermission(2)).then(Commands.argument("id",IntegerArgumentType.integer(1)).executes(c->{ Job j=jobs.get(IntegerArgumentType.getInteger(c,"id")); if(j==null || storageFailed || j.status.equals("DONE") || j.status.equals("CANCELLED") || j.status.equals("UNDONE")) { c.getSource().sendFailure(Component.literal("Job unavailable, terminal or storage failed")); return 0; } j.status=switch(action){case "pause"->"PAUSED";case "cancel"->"CANCELLED";default->"RUNNING";}; j.stalled=0; j.reason=""; var world=jobLevel(c.getSource().getServer(),j); for(Worker w:team(j)) { resetMovement(w); w.assignment=-1; w.teamStalled=0; w.returningHome=false; w.recoveryAttempts=0; w.homeWait=0; if(world!=null) { if(world.getEntity(UUID.fromString(w.worker)) instanceof Villager worker) setWorking(worker,j.status.equals("RUNNING")); if(j.status.equals("RUNNING")) ticket(world,w,workerAnchor(w),true); else releaseTickets(world,w); } } if(!save()) return 0; if(terminal(j)) cleanupWorker(c.getSource().getServer(),j); c.getSource().sendSuccess(()->Component.literal("Job "+j.id+": "+j.status),false); return 1; })));
        root.then(Commands.literal("distribution").requires(src->src.hasPermission(2))
            .then(Commands.argument("id",IntegerArgumentType.integer(1))
                .then(Commands.argument("mode",StringArgumentType.word())
                    .suggests((c,b)->net.minecraft.commands.SharedSuggestionProvider.suggest(List.of("adaptive","quadrants","strips","chunks"),b))
                    .executes(c->configureZones(c.getSource(),IntegerArgumentType.getInteger(c,"id"),StringArgumentType.getString(c,"mode"),-1)))));
        root.then(Commands.literal("spacing").requires(src->src.hasPermission(2))
            .then(Commands.argument("id",IntegerArgumentType.integer(1))
                .then(Commands.argument("blocks",IntegerArgumentType.integer(0,64))
                    .executes(c->configureZones(c.getSource(),IntegerArgumentType.getInteger(c,"id"),null,IntegerArgumentType.getInteger(c,"blocks"))))));
        root.then(Commands.literal("rebalance").requires(src->src.hasPermission(2))
            .then(Commands.argument("id",IntegerArgumentType.integer(1)).executes(c->configureZones(c.getSource(),IntegerArgumentType.getInteger(c,"id"),null,-1))));
        root.then(Commands.literal("zones").requires(src->src.hasPermission(2))
            .then(Commands.argument("id",IntegerArgumentType.integer(1)).executes(c->showZones(c.getSource(),IntegerArgumentType.getInteger(c,"id")))));
        e.getDispatcher().register(root);
        LOGGER.info("NPC Architects registered /architect commands");
    }
    private int showRoster(CommandSourceStack source,int id) {
        Job j=jobs.get(id);
        if(j==null||terminal(j)) return fail(source,"No active project "+id);
        var members=team(j);
        source.sendSuccess(()->Component.literal("Project #"+id+" | "+j.status+" | "+members.size()+" Architects"),false);
        for(int i=0;i<members.size();i++) {
            Worker w=members.get(i);
            String line="Architect #"+(i+1)+(i==0?" (primary)":"")+" | Home: "+home(w).toShortString()
                +(w.respawnRequested?" | RESPAWN QUEUED":w.returningHome?" | RECOVERING":"")+" | "+w.worker;
            source.sendSuccess(()->Component.literal(line),false);
        }
        return 1;
    }
    private int showStatus(CommandSourceStack source,boolean all) {
        var visible=jobs.values().stream()
            .filter(j->all||!j.status.equals("CANCELLED")&&!j.status.equals("UNDONE")).toList();
        source.sendSuccess(()->Component.literal("NPC Architects: "+visible.size()+" job(s)"
            +(storageFailed?" | storage error":" | storage ready")),false);
        for(Job j:visible) {
            String progress=terminal(j)?"":j.surveying?" SURVEYING "+j.surveyCursor+"/"+(j.width*j.depth*j.height)
                :j.independentChunks?" CHUNK WORK | cleared "+(j.clearCursor+j.clearDone.size())+"/"+(j.width*j.depth*j.height)+" | built "+(j.cursor+j.teamDone.size())+"/"+cells(j).size()
                :j.clearing?" CLEARING "+j.clearCursor+"/"+(j.width*j.depth*j.height)
                :" BUILDING "+j.cursor+"/"+cells(j).size();
            source.sendSuccess(()->Component.literal(j.id+": "+(j.schematic==null?"house":j.schematic)
                +" "+j.status+progress+" | Architects: "+(terminal(j)?0:team(j).size())
                +(j.clearing&&!terminal(j)&&!j.clearDone.isEmpty()?" | parallel cleared positions: "+j.clearDone.size():"")
                +(terminal(j)||j.teamDone.isEmpty()?"":" | parallel completed: "+j.teamDone.size())
                +(j.reason.isEmpty()?"":" | "+j.reason)),false);
        }
        if(!all&&visible.size()!=jobs.size()) source.sendSuccess(()->Component.literal("Hidden cancelled/undone jobs: "+(jobs.size()-visible.size())+" | /architect status all"),false);
        return 1;
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
        // Pos1 records the player's feet; align the lowest occupied schematic layer
        // with the supporting ground block instead of retaining empty bottom padding.
        var anchor=sel.first!=null?sel.first:source.getPlayerOrException().blockPosition().offset(3,0,3);
        int lowestY=imported.cells().get(0).y();
        if(lowestY>0){
            j.height-=lowestY;
            j.imported=imported.cells().stream().map(c->new Blueprint.Cell(c.x(),c.y()-lowestY,c.z(),c.material())).toList();
        }
        var origin=anchor.below();
        source.sendSuccess(()->Component.literal("Schematic ground origin: "+origin.toShortString()+" | empty bottom layers removed: "+lowestY),false);
        return createJob(source,origin,j,action.equals("preview"));
    }
    // A redesign changes the blueprint, never the project's world origin or roster.
    // Old plan indices have no meaning in the new plan. Archive the old record
    // before resetting progress and use the existing world as the new undo baseline.
    private int redesign(CommandSourceStack source,int id,String name,int rotation) {
        Job j=jobs.get(id);
        if(j==null||terminal(j)||storageFailed||j.imported==null)return fail(source,"Redesign requires a nonterminal schematic project.");
        if(rotation%90!=0)return fail(source,"Use 0, 90, 180 or 270 degrees.");
        if(!j.dimension.equals(source.getLevel().dimension().location().toString()))return fail(source,"Go to the project dimension first.");
        if(schematicDirectory==null||!name.matches("[A-Za-z0-9_-]{1,64}"))return fail(source,"Invalid schematic name.");
        var path=schematicDirectory.resolve(name+".schem");
        if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))return fail(source,"Schematic not found: "+name);
        try{
            Job replacement=loadRedesignReplacement(source,j,name,rotation,path);
            var level=source.getLevel();
            persistPlan(replacement);
            var archiveDirectory=file.resolveSibling("npcarchitects-redesign-history");
            Files.createDirectories(archiveDirectory);
            var archive=archiveDirectory.resolve("job-"+id+"-"+UUID.randomUUID()+".json");
            var temp=archive.resolveSibling(archive.getFileName()+".tmp");
            try(var out=Files.newBufferedWriter(temp)){JSON.toJson(j,out);}
            try{Files.move(temp,archive,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException ex){Files.move(temp,archive);}
            // No world mutations occur until the new blueprint and job snapshot exist.
            stopTeam(level,j);
            j.imported=replacement.imported;j.planFile=replacement.planFile;j.schematic=name;
            j.importedStates=null;j.desiredCache=null;
            j.cursor=0;j.clearCursor=0;j.surveyCursor=0;j.surveyCheckpointCursor=0;j.surveyCheckpointTick=ticks;
            j.teamDone.clear();j.clearDone.clear();j.placed.clear();j.cleared.clear();
            j.pendingBuild.clear();j.pendingClear.clear();j.journalCleared=0;j.journalPlaced=0;
            j.zoneQueues=null;j.chunkOwners=null;j.buildChunks=null;j.clearChunks=null;
            j.lifecycleChunks=null;j.lifecycleIndexed=false;j.lifecycleScan=0;j.deferredChunks=null;
            j.workerOffset=0;j.independentChunks=true;j.clearing=true;j.surveying=true;
            j.status="RUNNING";j.reason="";j.stalled=0;j.completedAt=0;j.completionNotified=false;
            for(Worker w:team(j)){
                resetMovement(w);w.assignment=-1;w.teamStalled=0;w.chunkStage="SURVEYING";
                w.returningHome=false;w.recoveryAttempts=0;w.homeWait=0;
            }
            if(!save())return fail(source,"Redesign snapshot failed; construction halted. Restore from backup before continuing.");
            for(Worker w:team(j))ticket(level,w,workerAnchor(w),true);
            LOGGER.info("NPC Architects job {} redesigned to {}; old record archived at {}",id,name,archive);
            source.sendSuccess(()->Component.literal("Project #"+id+" redesigned to "+name+"; "+team(j).size()+" Architects retained. Resurveying same origin. Matching blocks retained. Undo now applies only to redesign changes. Previous record: "+archive.getFileName()),false);
            return 1;
        }catch(Exception ex){LOGGER.error("Cannot redesign project {}",id,ex);return fail(source,"Cannot redesign project: "+ex.getMessage());}
    }
    private Job loadRedesignReplacement(CommandSourceStack source,Job j,String name,int rotation,Path path)throws Exception{
        Job replacement=new Job();
            var imported=SchematicImport.read(path,rotation);
            replacement.width=imported.width();replacement.depth=imported.depth();
            int lowest=imported.cells().get(0).y();
            replacement.height=imported.height()-lowest;
            replacement.imported=lowest==0?imported.cells():imported.cells().stream().map(c->new Blueprint.Cell(c.x(),c.y()-lowest,c.z(),c.material())).toList();
            replacement.schematic=name;replacement.rotation=0;
            replacement.x=j.x;replacement.y=j.y;replacement.z=j.z;replacement.dimension=j.dimension;
            validatePlan(replacement);
            // Restrict redesign to the exact normalized volume: shrinking would leave
            // old blocks behind, growing could destroy terrain outside the approved site.
            if(replacement.width!=j.width||replacement.depth!=j.depth||replacement.height!=j.height)
                throw new IllegalArgumentException("Replacement must match existing normalized dimensions "+j.width+" x "+j.height+" x "+j.depth+"; got "+replacement.width+" x "+replacement.height+" x "+replacement.depth+".");
            for(Job other:jobs.values())if(other!=j&&overlaps(replacement,other))throw new IllegalArgumentException("Replacement overlaps job "+other.id);
            var origin=new BlockPos(j.x,j.y,j.z);
            var end=origin.offset(j.width-1,j.height-1,j.depth-1);
            var level=source.getLevel();
            if(level.isOutsideBuildHeight(origin)||level.isOutsideBuildHeight(end)||!level.getWorldBorder().isWithinBounds(origin)||!level.getWorldBorder().isWithinBounds(end))throw new IllegalArgumentException("Replacement outside world bounds.");

        return replacement;
    }
    private int clearRedesignPreview(CommandSourceStack source)throws CommandSyntaxException{
        var player=source.getPlayerOrException();
        if(previewScan!=null&&previewScan.viewer.equals(player.getUUID()))closePreview(previewScan);
        source.sendSuccess(()->Component.literal("Redesign preview clearing; real blocks restored in bounded batches."),false);return 1;
    }
    private int startRedesignPreview(CommandSourceStack source,int id,String name,int rotation)throws CommandSyntaxException{
        var player=source.getPlayerOrException();
        Job j=jobs.get(id);
        if(j==null||terminal(j)||storageFailed||j.imported==null)return fail(source,"Preview requires a nonterminal schematic project.");
        if(rotation%90!=0)return fail(source,"Use 0, 90, 180 or 270 degrees.");
        if(!j.dimension.equals(source.getLevel().dimension().location().toString()))return fail(source,"Go to the project dimension first.");
        if(schematicDirectory==null||!name.matches("[A-Za-z0-9_-]{1,64}"))return fail(source,"Invalid schematic name.");
        var path=schematicDirectory.resolve(name+".schem");
        if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))return fail(source,"Schematic not found: "+name);
        if(!player.isSpectator())return fail(source,"Switch to spectator mode before textured preview (/gamemode spectator). Preview blocks are visual only.");
        if(previewScan!=null)return fail(source,"A preview is active or restoring. Use /architect redesign preview clear and wait for restoration.");
        try{
            var replacement=loadRedesignReplacement(source,j,name,rotation,path);
            var p=new PreviewScan();p.viewer=player.getUUID();p.original=j;p.originalPlan=j.planFile;p.replacement=replacement;p.startTick=ticks;
            previewScan=p;
            source.sendSuccess(()->Component.literal("Read-only redesign preview #"+id+": "+name+". Actual schematic textures in a local area around you; builders continue. Spectator only. Counts are a rolling estimate."),false);return 1;
        }catch(Exception ex){LOGGER.warn("Cannot preview redesign {}",id,ex);return fail(source,"Cannot preview redesign: "+ex.getMessage());}
    }
    private static final int TEXTURE_RADIUS=24;
    private static final int TEXTURE_PACKET_BUDGET=256;
    private static final int TEXTURE_SCAN_BUDGET=8192;
    private static final int TEXTURE_MAX_SHOWN=32768;
    private static void closePreview(PreviewScan p){
        p.closing=true;
        if(p.restore==null)p.restore=p.shown.iterator();
    }
    private static void sendPreviewBlock(net.minecraft.server.level.ServerPlayer player,BlockPos pos,BlockState state){
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket(pos,state));
    }
    private boolean restorePreview(PreviewScan p,net.minecraft.server.level.ServerPlayer player){
        var level=player.serverLevel();int sent=0,scanned=0;
        long deadline=System.nanoTime()+PREVIEW_BUDGET_NS;
        while(p.restore.hasNext()&&sent<TEXTURE_PACKET_BUDGET&&scanned++<TEXTURE_SCAN_BUDGET&&System.nanoTime()<deadline){
            var pos=p.restore.next();
            // A client chunk unload discards its fake block state. Never load chunks for restoration.
            if(level.hasChunkAt(pos)){
                sendPreviewBlock(player,pos,level.getBlockState(pos));sent++;
                var be=level.getBlockEntity(pos);
                if(be!=null&&be.getUpdatePacket()!=null)player.connection.send(be.getUpdatePacket());
            }
            p.restore.remove();
        }
        if(p.restore.hasNext())return false;
        p.restore=null;return true;
    }
    private void renderTexturedPreview(PreviewScan p,net.minecraft.server.level.ServerPlayer player){
        var level=player.serverLevel();var j=p.replacement;
        int cx=player.blockPosition().getX()>>4,cz=player.blockPosition().getZ()>>4;
        if(p.centerX!=cx||p.centerZ!=cz){
            if(p.restore==null&&!p.shown.isEmpty())p.restore=p.shown.iterator();
            if(p.restore!=null&&!restorePreview(p,player))return;
            p.centerX=cx;p.centerZ=cz;p.renderCursor=0;
            int x=cx*16+8,z=cz*16+8;
            p.minX=Math.max(j.x,x-TEXTURE_RADIUS);p.maxX=Math.min(j.x+j.width-1,x+TEXTURE_RADIUS);
            p.minZ=Math.max(j.z,z-TEXTURE_RADIUS);p.maxZ=Math.min(j.z+j.depth-1,z+TEXTURE_RADIUS);
            p.nextRefresh=0;
        }
        if(p.minX>p.maxX||p.minZ>p.maxZ)return;
        int width=p.maxX-p.minX+1,depth=p.maxZ-p.minZ+1,area=width*depth,volume=area*j.height;
        if(p.renderCursor>=volume){
            if(ticks<p.nextRefresh)return;
            p.renderCursor=0;
        }
        int sent=0,scanned=0;
        long deadline=System.nanoTime()+PREVIEW_BUDGET_NS;
        while(p.renderCursor<volume&&sent<TEXTURE_PACKET_BUDGET&&scanned++<TEXTURE_SCAN_BUDGET&&System.nanoTime()<deadline){
            int i=p.renderCursor++,cell=i%area;
            var pos=new BlockPos(p.minX+cell/depth,j.y+i/area,p.minZ+cell%depth);
            if(!level.hasChunkAt(pos))continue;
            var current=level.getBlockState(pos);var wanted=p.desired.getOrDefault(pos,Blocks.AIR.defaultBlockState());
            // Do not hide protected blocks or block entity data.
            if(!current.equals(wanted)&&!current.isAir()&&!canClear(level,pos,current)){
                if(p.shown.remove(pos)){sendPreviewBlock(player,pos,current);sent++;}
                continue;
            }
            if(current.equals(wanted)||(current.isAir()&&wanted.isAir())){
                if(p.shown.remove(pos)){sendPreviewBlock(player,pos,current);sent++;}
                continue;
            }
            if(p.shown.size()>=TEXTURE_MAX_SHOWN&&!p.shown.contains(pos))continue;
            sendPreviewBlock(player,pos,wanted);p.shown.add(pos);sent++;
        }
        if(p.renderCursor>=volume)p.nextRefresh=ticks+40;
    }
    private void tickRedesignPreview(MinecraftServer server){
        var p=previewScan;if(p==null)return;
        var player=server.getPlayerList().getPlayer(p.viewer);
        // Disconnect and dimension transition discard the client's old chunk cache.
        if(player==null||!p.original.dimension.equals(player.serverLevel().dimension().location().toString())){previewScan=null;return;}
        if(!player.isSpectator()||terminal(p.original)||!Objects.equals(p.originalPlan,p.original.planFile)||storageFailed)closePreview(p);
        if(p.closing){if(restorePreview(p,player)){previewScan=null;player.sendSystemMessage(Component.literal("Textured preview cleared; real block states restored."));}return;}
        if(p.completeTick<0&&ticks-p.startTick>12000){player.sendSystemMessage(Component.literal("Redesign preview timed out."));closePreview(p);return;}
        var level=player.serverLevel();var j=p.replacement;
        int volume=j.width*j.depth*j.height;
        if(p.completeTick<0){
            long deadline=System.nanoTime()+PREVIEW_BUDGET_NS;
            var plan=cells(j);
            int indexed=0;
            while(p.indexCursor<plan.size()&&indexed++<PREVIEW_BATCH&&System.nanoTime()<deadline){
                var cell=plan.get(p.indexCursor++);p.desired.put(buildPosition(j,cell),material(j,cell.material()));
            }
            if(p.indexCursor<plan.size())return;
            var desired=p.desired;
            int processed=0;
            while(p.cursor<volume&&processed++<PREVIEW_BATCH&&System.nanoTime()<deadline){
                var pos=clearPosition(j,p.cursor++);
                if(!level.hasChunkAt(pos)){p.unknown++;continue;}
                var current=level.getBlockState(pos);var wanted=desired.getOrDefault(pos,Blocks.AIR.defaultBlockState());
                if(current.equals(wanted)||(current.isAir()&&wanted.isAir())){if(wanted.isAir())p.empty++;else p.retained++;continue;}
                if(!current.isAir()&&!canClear(level,pos,current))p.protectedCount++;
                if(current.isAir())p.added++;
                else if(wanted.isAir())p.removed++;
                else p.replaced++;
            }
            if(p.cursor>=volume){
                p.completeTick=ticks;
                player.sendSystemMessage(Component.literal("Redesign preview #"+p.original.id+" | add "+p.added+" | remove "+p.removed+" | replace "+p.replaced+" | retain "+p.retained+" | matching air "+p.empty+" | unloaded/unknown "+p.unknown+" | protected conflicts "+p.protectedCount+" (rolling scan, not an atomic snapshot; protected conflicts included in change counts)."));
            }else if((ticks-p.startTick)%100==0){
                player.sendSystemMessage(Component.literal("Redesign preview scanned "+p.cursor+"/"+volume+" positions; unloaded "+p.unknown+"."));
            }
        }
        if(p.completeTick>=0&&ticks-p.completeTick>=PREVIEW_LIFETIME){closePreview(p);return;}
        if(p.indexCursor>=cells(j).size())renderTexturedPreview(p,player);
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
    private static BlockPos workerAnchor(Worker j) {
        if(j.workerX!=null && j.workerY!=null && j.workerZ!=null) return new BlockPos(j.workerX,j.workerY,j.workerZ);
        // Legacy jobs without a saved worker location can at least reload their construction origin.
        if(j instanceof Job job) return new BlockPos(job.x,job.y,job.z);
        throw new IllegalStateException("Assistant anchor missing");
    }
    private void checkpointWorker(ServerLevel level,Worker j,Villager npc) {
        var previous=new ChunkPos(workerAnchor(j));
        remember(j,npc);
        var next=new ChunkPos(npc.blockPosition());
        ticket(level,j,npc.blockPosition(),true);
        if(!previous.equals(next)) progressDirty=true;
    }
    private static void ticket(ServerLevel level,Worker j,BlockPos pos,boolean worker) {
        var next=new ChunkPos(pos); var previous=worker?j.workerChunk:j.workChunk;
        if(previous!=null&&!previous.equals(next)) {
            var other=worker?j.workChunk:j.workerChunk;
            if(!previous.equals(other)) level.getChunkSource().removeRegionTicket(WORK_TICKET,previous,2,j.worker);
        }
        level.getChunkSource().addRegionTicket(WORK_TICKET,next,2,j.worker);
        if(worker) j.workerChunk=next; else j.workChunk=next;
    }
    private static void releaseTickets(ServerLevel level,Worker j) {
        if(j.workChunk!=null) level.getChunkSource().removeRegionTicket(WORK_TICKET,j.workChunk,2,j.worker);
        if(j.workerChunk!=null&&!j.workerChunk.equals(j.workChunk)) level.getChunkSource().removeRegionTicket(WORK_TICKET,j.workerChunk,2,j.worker);
        j.workChunk=null; j.workerChunk=null;
    }
    private static boolean loadTarget(ServerLevel level,Worker j,BlockPos pos) {
        ticket(level,j,pos,false);
        // Let the ticket system load/generate asynchronously; never synchronously generate a map in a tick.
        return level.hasChunkAt(pos);
    }
    // Surveying is read-only. Keep all world access on the server thread and share
    // one time/position budget across active surveys rather than per NPC.
    private int surveyStep(ServerLevel level,Villager npc,Job j,long deadline,int allowance) {
        int total=j.width*j.depth*j.height;
        int start=j.surveyCursor;
        var desired=desiredStates(j);
        ChunkPos chunk=null;
        var air=Blocks.AIR.defaultBlockState();
        while(j.surveyCursor<total && j.surveyCursor-start<allowance) {
            if((j.surveyCursor-start)%32==0 && System.nanoTime()-deadline>=0) break;
            var pos=clearPosition(j,j.surveyCursor);
            // Refresh the ticket and check chunk readiness only when the chunk changes.
            int cx=pos.getX()>>4, cz=pos.getZ()>>4;
            if(chunk==null||chunk.x!=cx||chunk.z!=cz) {
                chunk=new ChunkPos(cx,cz);
                if(!loadTarget(level,j,pos)) {
                    j.reason="Loading survey chunk "+chunk;
                    break;
                }
            }
            if(level.isOutsideBuildHeight(pos)||!level.getWorldBorder().isWithinBounds(pos)) {
                blocked(j,npc,"Outside world bounds at "+pos.toShortString()); return j.surveyCursor-start;
            }
            var current=level.getBlockState(pos);
            // Empty cells cannot contain an obstruction; skip the blueprint lookup.
            if(!current.isAir()) {
                var wanted=desired.getOrDefault(pos,air);
                if(!current.equals(wanted)&&!canClear(level,pos,current)) {
                    blocked(j,npc,clearRestriction(level,pos,current)); return j.surveyCursor-start;
                }
            }
            j.surveyCursor++;
            j.reason="";
        }
        if(j.surveyCursor==total) {
            j.surveying=false; j.reason="";
            LOGGER.info("NPC Architects job {} completed map survey ({} positions)",j.id,total);
            save();
        } else if(j.surveyCursor!=j.surveyCheckpointCursor && ticks-j.surveyCheckpointTick>=SURVEY_CHECKPOINT_TICKS) {
            if(save()) { j.surveyCheckpointTick=ticks; j.surveyCheckpointCursor=j.surveyCursor; }
        }
        return j.surveyCursor-start;
    }
    private void runSurveys(MinecraftServer server) {
        var active=new ArrayList<Job>();
        for(Job j:jobs.values()) if(j.surveying&&j.status.equals("RUNNING")&&team(j).stream().noneMatch(w->w.respawnRequested)) active.add(j);
        if(active.isEmpty()) return;
        int offset=Math.floorMod(surveyRoundRobin++,active.size());
        long deadline=System.nanoTime()+SURVEY_BUDGET_NS;
        int remaining=SURVEY_BATCH;
        for(int i=0;i<active.size() && remaining>0 && !storageFailed;i++) {
            if(System.nanoTime()-deadline>=0) break;
            Job j=active.get((offset+i)%active.size());
            var level=jobLevel(server,j);
            if(level==null) continue;
            if(!(level.getEntity(UUID.fromString(j.worker)) instanceof Villager npc)||!npc.isAlive()) continue;
            int share=Math.max(1,remaining/(active.size()-i));
            remaining-=surveyStep(level,npc,j,deadline,share);
        }
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
                if(needsClear&&!canClear(level,pos,current)) problem=clearRestriction(level,pos,current);
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
        Villager npc=findSpawn(level,player.blockPosition(),3);
        if(npc==null) return fail(source,"Could not spawn builder: no safe loaded position within 3 blocks. Snow layers are supported.");
        protect(npc);
        remember(j,npc); rememberHome(j,npc);
        j.id=jobs.keySet().stream().mapToInt(i->i).max().orElse(0)+1; j.worker=npc.getUUID().toString(); j.owner=player.getUUID().toString();
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
        j.status="PAUSED"; if(!save()) return fail(source,"Could not save paused job."); stopTeam(level,j);
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
        j.cleared.clear(); j.placed.clear(); j.teamDone.clear(); j.clearDone.clear(); j.status="UNDONE"; if(!save()) return fail(source,"Undo applied but storage failed; inspect server log."); cleanupWorker(source.getServer(),j);
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
    private static String clearRestriction(ServerLevel level,BlockPos pos,BlockState state) {
        String reason;
        if(state.hasBlockEntity()||level.getBlockEntity(pos)!=null) reason="block entity (protected data)";
        else if(state.getDestroySpeed(level,pos)<0) reason="unbreakable (negative hardness)";
        else if(!state.getFluidState().isEmpty()&&!isDrainableFluid(state)) reason="unsupported fluid or waterlogged block";
        else return null;
        return "Cannot clear "+state+" at "+pos.toShortString()+": "+reason;
    }
    private static boolean isDrainableFluid(BlockState state){
        return state.is(Blocks.WATER)||state.is(Blocks.LAVA);
    }
    // Direct replacement displaces liquid instead of using destroyBlock,
    // which can restore a fluid's legacy block state in the same position.
    private static boolean drainFluid(ServerLevel level,BlockPos pos,BlockState state){
        return isDrainableFluid(state)&&level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);
    }
    private static boolean canClear(ServerLevel level,BlockPos pos,BlockState state) {
        return clearRestriction(level,pos,state)==null;
    }
    private void blocked(Job j,Villager npc,String reason) {
        j.status="BLOCKED"; j.reason=reason; setWorking(npc,false); resetMovement(j); if(npc.level() instanceof ServerLevel world) stopTeam(world,j);
        LOGGER.warn("NPC Architects job {} blocked: {}",j.id,reason); save();
    }
    // The construction controller owns movement; ordinary villager brain goals must not compete.
    private static void setWorking(Villager npc,boolean working) {
        if(!npc.isNoAi()) { npc.getNavigation().stop(); npc.setNoAi(true); }
        if(npc.isNoGravity()!=working) npc.setNoGravity(working);
        if(!working) npc.setDeltaMovement(Vec3.ZERO);
    }
    private static void resetMovement(Worker j) {
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
    private boolean approach(Worker j,Villager npc,ServerLevel level,BlockPos pos) {
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
        var movement=delta.normalize().scale(Math.min(MOVE_SPEED,delta.length()));
        var before=npc.position();
        // Move through collision-checked space; elevated positions use temporary hover.
        var sweep=npc.getBoundingBox().expandTowards(movement);
        if(!level.getWorldBorder().isWithinBounds(sweep)
            ||level.isOutsideBuildHeight(BlockPos.containing(sweep.minX,sweep.minY,sweep.minZ))
            ||level.isOutsideBuildHeight(BlockPos.containing(sweep.maxX,sweep.maxY,sweep.maxZ))) {
            j.workStand=null; return false;
        }
        boolean loaded=true;
        for(int cx=((int)Math.floor(sweep.minX))>>4;cx<=((int)Math.floor(sweep.maxX))>>4;cx++)
            for(int cz=((int)Math.floor(sweep.minZ))>>4;cz<=((int)Math.floor(sweep.maxZ))>>4;cz++)
                if(!level.hasChunkAt(new BlockPos(cx<<4,npc.blockPosition().getY(),cz<<4))) loaded=false;
        if(loaded) npc.move(MoverType.SELF,movement);
        npc.setDeltaMovement(Vec3.ZERO);
        if(npc.position().distanceToSqr(before)<0.01 || npc.position().distanceToSqr(j.workStand)>=before.distanceToSqr(j.workStand)-0.01) j.movementFailures++;
        else j.movementFailures=0;
        // A blocked direct route may reposition only to a verified empty loaded stance.
        if(j.movementFailures>=4 && safeStand(level,npc,j.workStand,pos)) {
            npc.teleportTo(j.workStand.x,j.workStand.y,j.workStand.z);
            LOGGER.debug("NPC Architects worker {} repositioned to safe work stance for {}",j.worker,pos);
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
        while(j.clearCursor<total && j.clearCursor-start<CLEAR_BATCH) {
            var pos=clearPosition(j,j.clearCursor);
            if(!loadTarget(level,j,pos)) { waiting(j,"Loading clearing chunk "+new ChunkPos(pos)); return; }
            String problem=siteProblem(level,pos);
            if(problem!=null) { blocked(j,npc,problem+" at "+pos.toShortString()); return; }
            var current=level.getBlockState(pos);
            if(current.isAir()||current.equals(desired.getOrDefault(pos,Blocks.AIR.defaultBlockState()))) { j.clearCursor++; continue; }
            if(!canClear(level,pos,current)) { blocked(j,npc,clearRestriction(level,pos,current)); return; }
            if(approach(j,npc,level,pos)) {
                npc.swing(InteractionHand.MAIN_HAND);
                // No drops: undo can restore cleared terrain without duplicating resources.
                if(isDrainableFluid(current)?drainFluid(level,pos,current):level.destroyBlock(pos,false,npc)) {
                    j.cleared.add(new Cleared(pos.getX(),pos.getY(),pos.getZ(),net.minecraft.world.level.block.Block.getId(current)));
                    j.clearCursor++; j.stalled=0; j.recoveryAttempts=0; save(); return;
                }
            }
            if(++j.stalled>=STUCK_ATTEMPTS) startHomeRecovery(level,j,j,npc,"Cannot reach or clear "+pos.toShortString());
            return;
        }
        if(j.clearCursor<total) { save(); return; }
        j.clearing=false; j.stalled=0; save();
        LOGGER.info("NPC Architects job {} finished site clearing",j.id);
    }
    private static BlockState material(Job j,String name) { if(j.imported!=null) { if(j.importedStates==null) j.importedStates=new HashMap<>(); return j.importedStates.computeIfAbsent(name,SchematicImport::parseState); } return switch(name) { case "floor"->Blocks.COBBLESTONE.defaultBlockState(); case "roof"->Blocks.SPRUCE_PLANKS.defaultBlockState(); case "glass"->Blocks.GLASS.defaultBlockState(); default->Blocks.OAK_PLANKS.defaultBlockState(); }; }
    // Keep terminal job records as durable cleanup tombstones and preserve undo history.
    private void cleanupWorker(MinecraftServer server,Job j) {
        if(storageFailed || !terminal(j)) return;
        var world=jobLevel(server,j);
        if(world!=null) for(Worker w:team(j)) releaseTickets(world,w);
        var ids=new ArrayList<String>(j.retiredWorkers);
        for(Worker w:team(j)) ids.add(w.worker);
        for(String workerId:ids) for(var level:server.getAllLevels()) {
            UUID id=UUID.fromString(workerId);
            if(level.getEntity(id) instanceof Villager worker) {
                worker.getNavigation().stop();
                worker.discard();
                LOGGER.info("NPC Architects removed builder {} for terminal job {}",id,j.id);
            }
        }
    }
    private void workerJoined(EntityJoinLevelEvent event) {
        if(storageFailed || !(event.getLevel() instanceof ServerLevel) || !(event.getEntity() instanceof Villager worker)) return;
        String id=worker.getUUID().toString();
        for(Job j:jobs.values()) if(j.retiredWorkers.contains(id)||(terminal(j)&&team(j).stream().anyMatch(w->id.equals(w.worker)))) {
            // Cancel insertion of an unloaded terminal builder when its chunk is loaded again.
            event.setCanceled(true);
            LOGGER.debug("NPC Architects suppressed returning builder {} for terminal job {}",id,j.id);
            return;
        }
    }
    private void playerJoined(PlayerEvent.PlayerLoggedInEvent event) {
        if(storageFailed || !(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) return;
        for(Job j:jobs.values()) if(player.getUUID().toString().equals(j.owner)) notifyCompletion(player.serverLevel().getServer(),j);
    }
    private void notifyCompletion(MinecraftServer server,Job j) {
        if(storageFailed || j.completionNotified || !j.status.equals("DONE") || j.completedAt<=0) return;
        var players=server.getPlayerList();
        String name=j.schematic==null?"house":j.schematic;
        var message=Component.literal("[NPC Architects] ").withStyle(ChatFormatting.GOLD)
            .append(Component.literal("Job #"+j.id+" ("+name+") completed! ").withStyle(ChatFormatting.GREEN))
            .append(Component.literal(cells(j).size()+" blueprint blocks | "+j.dimension+" | "+j.x+", "+j.y+", "+j.z).withStyle(ChatFormatting.WHITE));
        if(j.owner!=null) {
            UUID owner;
            try { owner=UUID.fromString(j.owner); } catch(IllegalArgumentException ex) { LOGGER.warn("Invalid owner for job {}",j.id); return; }
            var player=players.getPlayer(owner);
            if(player==null) return;
            player.sendSystemMessage(message);
        } else {
            // Older jobs have no owner. Report new completions to online operators only.
            boolean delivered=false;
            for(var player:players.getPlayers()) if(player.hasPermissions(2)) { player.sendSystemMessage(message); delivered=true; }
            if(!delivered) return;
        }
        j.completionNotified=true;
        save();
    }
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
    private static void rememberHome(Worker w,Villager npc) {
        var p=npc.blockPosition(); w.homeX=p.getX(); w.homeY=p.getY(); w.homeZ=p.getZ();
    }
    private static BlockPos home(Worker w) {
        return w.homeX==null||w.homeY==null||w.homeZ==null?workerAnchor(w):new BlockPos(w.homeX,w.homeY,w.homeZ);
    }
    private void startHomeRecovery(ServerLevel level,Job j,Worker w,Villager npc,String reason) {
        if(w.recoveryAttempts>=RECOVERY_LIMIT) {
            blocked(j,npc,reason+"; home recovery limit reached for "+w.worker); return;
        }
        w.recoveryAttempts++; w.returningHome=true; w.homeWait=0;
        w.teamStalled=0; if(w==j) j.stalled=0;
        resetMovement(w); w.assignment=-1;
        setWorking(npc,true);
        ticket(level,w,home(w),false);
        LOGGER.warn("NPC Architects job {} worker {} stuck; returning to {} (attempt {}/{})",j.id,w.worker,home(w),w.recoveryAttempts,RECOVERY_LIMIT);
        save();
    }
    private boolean returnHome(ServerLevel level,Job j,Worker w,Villager npc) {
        if(!w.returningHome) return false;
        var origin=home(w);
        ticket(level,w,origin,false);
        if(!level.hasChunkAt(origin)) {
            if(++w.homeWait>=HOME_ATTEMPTS) blocked(j,npc,"Home chunk unavailable for "+w.worker);
            return true;
        }
        // Prefer exact starting block, then nearby collision-free positions.
        for(int radius=0;radius<=4;radius++) for(int dy:new int[]{0,1,-1,2,-2,3,4}) for(int dx=-radius;dx<=radius;dx++) for(int dz=-radius;dz<=radius;dz++) {
            if(Math.max(Math.abs(dx),Math.abs(dz))!=radius) continue;
            var p=origin.offset(dx,dy,dz);
            if(siteProblem(level,p)!=null||!level.hasChunkAt(p.above())) continue;
            var state=level.getBlockState(p);
            double snowHeight=state.is(Blocks.SNOW)?state.getValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS)/8.0:0.0;
            var feet=new Vec3(p.getX()+0.5,p.getY()+snowHeight,p.getZ()+0.5);
            var box=npc.getBoundingBox().move(feet.subtract(npc.position()));
            if(!level.getWorldBorder().isWithinBounds(box)||level.isOutsideBuildHeight(BlockPos.containing(box.maxX,box.maxY,box.maxZ))) continue;
            boolean loaded=true;
            for(int x=((int)Math.floor(box.minX))>>4;x<=((int)Math.floor(box.maxX))>>4;x++) for(int z=((int)Math.floor(box.minZ))>>4;z<=((int)Math.floor(box.maxZ))>>4;z++) if(!level.hasChunkAt(new BlockPos(x<<4,p.getY(),z<<4))) loaded=false;
            if(!loaded||!level.noCollision(npc,box)||!level.getEntities(npc,box).isEmpty()||!level.getFluidState(p).isEmpty()||!level.getFluidState(p.above()).isEmpty()) continue;
            npc.teleportTo(feet.x,feet.y,feet.z); npc.setDeltaMovement(Vec3.ZERO);
            w.returningHome=false; w.homeWait=0; w.assignment=-1; resetMovement(w);
            remember(w,npc); ticket(level,w,npc.blockPosition(),true);
            LOGGER.info("NPC Architects job {} worker {} recovered at {}",j.id,w.worker,p);
            save(); return true;
        }
        if(++w.homeWait>=HOME_ATTEMPTS) blocked(j,npc,"No safe recovery position near home "+origin.toShortString());
        return true;
    }
    private static void remember(Worker j,Villager npc) {
        var pos=npc.blockPosition(); j.workerX=pos.getX(); j.workerY=pos.getY(); j.workerZ=pos.getZ();
    }
    private void targetChanged(LivingChangeTargetEvent event) {
        if(event.getNewTarget() instanceof Villager villager && villager.getTags().contains("npcarchitects_worker")) event.setCanceled(true);
    }
    private void workerDeath(LivingDeathEvent event) {
        if(storageFailed || !(event.getEntity() instanceof Villager npc)) return;
        for(Job j:jobs.values()) if(!terminal(j)) for(Worker w:team(j)) if(npc.getUUID().toString().equals(w.worker)) {
            remember(w,npc); w.workerDied=true; w.assignment=-1; resetMovement(w);
            if(w==j) j.reason="Builder died; replacement pending";
            LOGGER.warn("NPC Architects job {} lost builder {}",j.id,w.worker); save(); return;
        }
    }
    private void waiting(Job j,String reason) {
        if(!reason.equals(j.reason)) { j.reason=reason; LOGGER.warn("NPC Architects job {}: {}",j.id,reason); save(); }
    }
    // A snow layer is not air, but it can support a villager above its surface.
    // Do not delete snow or any other terrain to make room for a worker.
    private static Vec3 spawnFeet(ServerLevel level,BlockPos pos) {
        if(siteProblem(level,pos)!=null||level.isOutsideBuildHeight(pos.above())||!level.hasChunkAt(pos.below()))return null;
        var state=level.getBlockState(pos);
        boolean snow=state.is(Blocks.SNOW);
        if(!state.isAir()&&!snow)return null;
        if(!level.getBlockState(pos.below()).isFaceSturdy(level,pos.below(),net.minecraft.core.Direction.UP))return null;
        double height=snow?state.getValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS)/8.0:0.0;
        var feet=new Vec3(pos.getX()+.5,pos.getY()+height,pos.getZ()+.5);
        var box=new AABB(feet.x-.3,feet.y,feet.z-.3,feet.x+.3,feet.y+1.95,feet.z+.3);
        if(!level.getWorldBorder().isWithinBounds(box)||level.isOutsideBuildHeight(BlockPos.containing(box.maxX,box.maxY,box.maxZ)))return null;
        for(int y=pos.getY();y<=Math.floor(box.maxY);y++){
            var p=new BlockPos(pos.getX(),y,pos.getZ());
            if(!level.hasChunkAt(p)||!level.getFluidState(p).isEmpty())return null;
        }
        if(!level.noCollision(null,box)||!level.getEntities(null,box).isEmpty())return null;
        return feet;
    }
    private static Villager spawnAt(ServerLevel level,BlockPos pos) {
        var feet=spawnFeet(level,pos);
        if(feet==null)return null;
        // Spawn using the vanilla command path (including initialization), then
        // position at the validated fractional snow surface.
        var npc=EntityType.VILLAGER.spawn(level,BlockPos.containing(feet),MobSpawnType.COMMAND);
        if(npc==null)return null;
        npc.moveTo(feet.x,feet.y,feet.z,npc.getYRot(),npc.getXRot());
        if(!level.noCollision(npc,npc.getBoundingBox())||!level.getEntities(npc,npc.getBoundingBox()).isEmpty()){
            npc.discard();return null;
        }
        return npc;
    }
    private static Villager findSpawn(ServerLevel level,BlockPos anchor,int limit) {
        for(int radius=0;radius<=limit;radius++)for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++){
            if(Math.max(Math.abs(dx),Math.abs(dz))!=radius)continue;
            for(int dy:new int[]{0,1,-1,2,-2}){
                var npc=spawnAt(level,anchor.offset(dx,dy,dz));
                if(npc!=null)return npc;
            }
        }
        return null;
    }
    private Villager replaceWorker(ServerLevel level,Job job,Worker j,BlockPos anchor) {
        if(storageFailed) return null;
        for(int radius=0;radius<=3;radius++) for(int dx=-radius;dx<=radius;dx++) for(int dz=-radius;dz<=radius;dz++) for(int dy:new int[]{0,1,-1,2,-2}) {
            if(Math.max(Math.abs(dx),Math.abs(dz))!=radius) continue;
            var pos=anchor.offset(dx,dy,dz);
            var replacement=spawnAt(level,pos);
            if(replacement==null) continue;
            protect(replacement);
            String previous=j.worker; boolean requested=j.respawnRequested; boolean died=j.workerDied; Integer oldX=j.workerX,oldY=j.workerY,oldZ=j.workerZ;
            releaseTickets(level,j); job.retiredWorkers.add(previous); j.worker=replacement.getUUID().toString(); remember(j,replacement); j.workerDied=false; j.respawnRequested=false; j.assignment=-1; resetMovement(j); j.teamStalled=0; if(j==job) { job.stalled=0; if(!requested) job.reason=""; }
            if(!save()) { replacement.discard(); job.retiredWorkers.remove(previous); j.worker=previous; j.respawnRequested=requested; j.workerDied=died; j.workerX=oldX;j.workerY=oldY;j.workerZ=oldZ; return null; }
            LOGGER.info("NPC Architects job {} replaced builder {} with {}",job.id,previous,j.worker);
            return replacement;
        }
        return null;
    }
    private int requestRespawn(CommandSourceStack source,int id,int slot) {
        if(storageFailed) return fail(source,"Storage unavailable.");
        var selected=new ArrayList<Worker>();
        for(Job j:jobs.values()) {
            if(terminal(j)||(id!=0&&j.id!=id)) continue;
            var members=team(j);
            if(slot>members.size()) return fail(source,"Project "+id+" has only "+members.size()+" Architects.");
            if(slot==0) selected.addAll(members); else selected.add(members.get(slot-1));
        }
        if(selected.isEmpty()) return fail(source,"No matching nonterminal Architects.");
        // Persist intent before touching entities or tickets. A restart resumes the request.
        for(Worker w:selected) w.respawnRequested=true;
        if(!save()) return fail(source,"Could not persist respawn request.");
        source.sendSuccess(()->Component.literal("Queued respawn of "+selected.size()+" Architect(s) at their saved starting locations. Project status and progress preserved."),false);
        return selected.size();
    }
    private boolean processRespawns(MinecraftServer server,ServerLevel level,Job j) {
        boolean pending=team(j).stream().anyMatch(w->w.respawnRequested);
        if(!pending) return false;
        for(Worker w:team(j)) if(level.getEntity(UUID.fromString(w.worker)) instanceof Villager npc) setWorking(npc,false);
        for(Worker w:team(j)) {
            if(!w.respawnRequested) continue;
            // Preserve the old NPC until a safe replacement and its new UUID are saved.
            pending=true;
            w.assignment=-1; resetMovement(w); w.teamStalled=0; w.returningHome=false;
            if(w==j) j.stalled=0;
            var origin=home(w);
            ticket(level,w,origin,false);
            if(level.getEntity(UUID.fromString(w.worker)) instanceof Villager old) {
                setWorking(old,false);
                ticket(level,w,old.blockPosition(),true);
            }
            if(!level.hasChunkAt(origin)) {
                if(++w.homeWait>=HOME_ATTEMPTS) finishFailedRespawn(level,j,w,"Starting chunk unavailable");
                continue;
            }
            // The spawn helper checks loaded chunks, solid ground, headroom and entities.
            String previous=w.worker;
            String oldReason=j.reason;
            var replacement=replaceWorker(level,j,w,origin);
            j.reason=oldReason;
            if(replacement==null) {
                if(storageFailed) return true;
                if(++w.homeWait>=HOME_ATTEMPTS) finishFailedRespawn(level,j,w,"No safe spawn position near "+origin.toShortString());
                continue;
            }
            // The previous UUID is already a durable tombstone. Remove any loaded
            // copy now, including one in a different dimension.
            for(var world:server.getAllLevels()) if(world.getEntity(UUID.fromString(previous)) instanceof Villager old) old.discard();
            w.respawnRequested=false; w.homeWait=0; w.recoveryAttempts=0;
            setWorking(replacement,j.status.equals("RUNNING")&&!j.surveying);
            if(j.status.equals("RUNNING")) ticket(level,w,replacement.blockPosition(),true);
            else releaseTickets(level,w);
            LOGGER.info("NPC Architects job {} manually respawned worker {} as {}",j.id,previous,w.worker);
            if(!save()) return true;
        }
        return pending;
    }
    private void finishFailedRespawn(ServerLevel level,Job j,Worker w,String reason) {
        w.respawnRequested=false; w.homeWait=0;
        releaseTickets(level,w);
        j.reason="Respawn failed for "+w.worker+": "+reason;
        LOGGER.warn("NPC Architects job {}: {}",j.id,j.reason);
        save();
    }
    private int recover(CommandSourceStack source,int id) throws CommandSyntaxException {
        Job j=jobs.get(id);
        if(j==null||terminal(j)||storageFailed) return fail(source,"No recoverable job "+id);
        if(j.respawnRequested) return fail(source,"Primary Architect respawn already queued.");
        var player=source.getPlayerOrException(); var level=source.getLevel();
        if(!level.dimension().location().toString().equals(j.dimension)) return fail(source,"Go to the job dimension first.");
        for(var world:source.getServer().getAllLevels()) if(world.getEntity(UUID.fromString(j.worker)) instanceof Villager npc && npc.isAlive()) {
            protect(npc); return fail(source,"Builder is still alive; no replacement spawned. Use /architect resume "+id);
        }
        if(replaceWorker(level,j,j,player.blockPosition())==null) return fail(source,"No safe loaded spawn position nearby or storage failed.");
        j.status="RUNNING"; if(!save()) return fail(source,"Replacement spawned but job resume could not be saved."); ticket(level,j,workerAnchor(j),true);
        source.sendSuccess(()->Component.literal("Builder replaced for job "+id+"; construction resumed from saved progress."),false); return 1;
    }
    // Between construction actions, keep approaching the same pending target.
    // Never mutate blocks here, advance cursors, or move paused/terminal workers.
    private static List<Worker> team(Job j) {
        var result=new ArrayList<Worker>(1+j.assistants.size()); result.add(j); result.addAll(j.assistants); return result;
    }
    private static void validateTeam(Job j) {
        if(j.assistants==null||j.teamDone==null||j.clearDone==null||j.retiredWorkers==null) throw new IllegalArgumentException("Invalid team");
        var ids=new HashSet<String>();
        for(Worker w:team(j)) {
            UUID.fromString(w.worker);
            if(w.homeX==null||w.homeY==null||w.homeZ==null) { var anchor=workerAnchor(w); w.homeX=anchor.getX(); w.homeY=anchor.getY(); w.homeZ=anchor.getZ(); }
            if(!ids.add(w.worker)||(w!=j&&(w.workerX==null||w.workerY==null||w.workerZ==null))) throw new IllegalArgumentException("Invalid worker");
        }
        for(String id:j.retiredWorkers) { UUID.fromString(id); if(ids.contains(id)) throw new IllegalArgumentException("Retired worker still active"); }
        long volume=(long)j.width*j.depth*j.height;
        for(int i:j.clearDone) if(!j.clearing||i<j.clearCursor||i>=volume) throw new IllegalArgumentException("Invalid parallel clearing progress");
        var plan=cells(j);
        for(int i:j.teamDone) if(i<j.cursor||i>=plan.size()||j.cursor>=plan.size()||(!j.independentChunks&&plan.get(i).y()!=plan.get(j.cursor).y())) throw new IllegalArgumentException("Invalid parallel progress");
    }
    private static void stopTeam(ServerLevel level,Job j) {
        for(Worker w:team(j)) {
            resetMovement(w); w.assignment=-1; w.teamStalled=0;
            if(level.getEntity(UUID.fromString(w.worker)) instanceof Villager npc) setWorking(npc,false);
            releaseTickets(level,w);
        }
    }
    private int addWorkers(CommandSourceStack source,int id,int count) throws CommandSyntaxException {
        Job j=jobs.get(id);
        if(j==null||terminal(j)||storageFailed) return fail(source,"No active project "+id);
        if(team(j).size()+count>MAX_TEAM) return fail(source,"Maximum "+MAX_TEAM+" Architects per project.");
        var player=source.getPlayerOrException(); var level=source.getLevel();
        if(!level.dimension().location().toString().equals(j.dimension)) return fail(source,"Go to the project dimension first.");
        var anchor=player.blockPosition();
        var added=new ArrayList<Villager>();
        var members=new ArrayList<Worker>();
        for(int r=1;r<=8&&added.size()<count;r++) for(int dx=-r;dx<=r&&added.size()<count;dx++) for(int dz=-r;dz<=r&&added.size()<count;dz++) {
            if(Math.max(Math.abs(dx),Math.abs(dz))!=r) continue;
            for(int dy:new int[]{0,1,-1,2,-2}) {
                var pos=anchor.offset(dx,dy,dz);
                var npc=spawnAt(level,pos);
                if(npc==null) continue;
                protect(npc); setWorking(npc,false);
                var w=new Worker(); w.worker=npc.getUUID().toString(); remember(w,npc); rememberHome(w,npc);
                added.add(npc); members.add(w); break;
            }
        }
        if(added.size()!=count) { added.forEach(Villager::discard); return fail(source,"Not enough safe loaded spawn positions near you."); }
        j.assistants.addAll(members);
        if(j.chunkOwners!=null)j.chunkOwners.clear();
        for(Worker w:team(j)) { resetMovement(w); w.assignment=-1; }
        if(!save()) { j.assistants.removeAll(members); added.forEach(Villager::discard); return fail(source,"Could not persist team."); }
        if(j.status.equals("RUNNING")) for(Worker w:members) ticket(level,w,workerAnchor(w),true);
        source.sendSuccess(()->Component.literal("Project #"+id+" now has "+team(j).size()+" Architects. Additional builders join the current work phase after surveying."),false);
        return 1;
    }
    // A layer barrier preserves the original bottom-up build order. Reservations are
    // transient; completed out-of-order indices are durable, and share undo history.
    // Preserve top-down clearing in each vertical column: a lower block cannot
    // be assigned until all higher positions in its local clearing slice finish.
    // The original clearPosition order and cursor are unchanged for old saves.
    private static int clearingSliceEnd(Job j,int index) {
        int w=physicalWidth(j), d=physicalDepth(j);
        if(j.imported==null) {
            long area=(long)w*d;
            return (int)Math.min((long)w*d*j.height,((index/area)+1)*area);
        }
        long stripSpan=16L*d*j.height;
        int strip=(int)(index/stripSpan);
        int sw=Math.min(16,w-strip*16);
        long stripStart=strip*stripSpan;
        long local=index-stripStart;
        long rowSpan=16L*sw*j.height;
        int row=(int)(local/rowSpan);
        int sd=Math.min(16,d-row*16);
        long rowStart=stripStart+row*rowSpan;
        int area=sw*sd;
        return (int)(rowStart+((index-rowStart)/area+1)*area);
    }
    private static String mode(Job j){return WorkZones.MODES.contains(j.distribution==null?"":j.distribution)?j.distribution:"adaptive";}
    private static int zone(Job j,BlockPos pos,int count){
        return WorkZones.zone(mode(j),pos.getX()-j.x,pos.getZ()-j.z,physicalWidth(j),physicalDepth(j),count);
    }
    private int configureZones(CommandSourceStack source,int id,String mode,int spacing){
        Job j=jobs.get(id);
        if(j==null||terminal(j)||storageFailed)return fail(source,"Job unavailable.");
        if(mode!=null&&!WorkZones.MODES.contains(mode))return fail(source,"Use adaptive, quadrants, strips or chunks.");
        if(mode!=null)j.distribution=mode;
        if(spacing>=0)j.spacing=spacing;
        j.zoneQueues=null; j.chunkOwners=null; j.buildChunks=null; j.lifecycleChunks=null; j.lifecycleIndexed=false; j.lifecycleScan=0; j.deferredChunks=null;
        for(Worker w:team(j)){w.assignment=-1;w.teamStalled=0;resetMovement(w);}
        if(!save())return 0;
        source.sendSuccess(()->Component.literal("Project #"+id+" distribution="+mode(j)+", preferred spacing="+j.spacing+" blocks; assignments refreshed."),false);
        return 1;
    }
    private int showZones(CommandSourceStack source,int id){
        Job j=jobs.get(id);if(j==null)return fail(source,"Unknown project.");
        var ws=team(j);var level=jobLevel(source.getServer(),j);
        source.sendSuccess(()->Component.literal("Project #"+id+" | "+mode(j)+" | preferred spacing "+j.spacing+" | "+ws.size()+" zones"),false);
        var player=source.getEntity() instanceof net.minecraft.server.level.ServerPlayer p?p:null;
        // Bounded, requester-only markers at current targets (no force loading).
        for(int i=0;i<ws.size();i++){
            Worker w=ws.get(i);BlockPos pos=w.assignment<0?null:(j.independentChunks?w.assignmentClearing:j.clearing)?clearPosition(j,w.assignment):w.assignment<cells(j).size()?buildPosition(j,cells(j).get(w.assignment)):null;
            Long owned=j.chunkOwners==null?null:j.chunkOwners.get(w);
            String line="Architect "+(i+1)+" | chunk "+(owned==null?"unassigned":ChunkWork.x(owned)+","+ChunkWork.z(owned))+" | "+(j.independentChunks?(w.chunkStage==null?"IDLE":w.chunkStage)+" | ":"")+(pos==null?"no target":pos.toShortString());
            source.sendSuccess(()->Component.literal(line),false);
            if(pos!=null&&player!=null&&level==player.serverLevel()&&player.distanceToSqr(Vec3.atCenterOf(pos))<=96*96){
                float hue=(float)i/ws.size();int rgb=java.awt.Color.HSBtoRGB(hue,.85f,1f);
                var dust=new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(((rgb>>16)&255)/255f,((rgb>>8)&255)/255f,(rgb&255)/255f),1.5f);
                level.sendParticles(player,dust,false,pos.getX()+.5,pos.getY()+1.1,pos.getZ()+.5,12,.35,.35,.35,0);
            }
        }
        return 1;
    }
    private static BlockPos buildPosition(Job j,Blueprint.Cell c){return new BlockPos(j.x+c.x(),j.y+c.y(),j.z+c.z());}
    private static double assignmentScore(Job j,Villager npc,BlockPos pos,int preferred,int count,List<BlockPos> occupied){
        double score=npc.position().distanceToSqr(Vec3.atCenterOf(pos));
        if(zone(j,pos,count)!=preferred)score+=64;
        double min=Math.max(0,j.spacing);double minSq=min*min;
        for(BlockPos other:occupied){
            double dx=(double)pos.getX()-other.getX(),dz=(double)pos.getZ()-other.getZ(),dist=dx*dx+dz*dz;
            if(dist<minSq)score+=(minSq-dist)*8;
        }
        return score;
    }
    private static int clearIndex(Job j,int x,int y,int z){
        return ChunkWork.clearIndex(x-j.x,y-j.y,z-j.z,physicalWidth(j),physicalDepth(j),j.height,j.imported!=null);
    }
    private static long chunkKey(BlockPos p){return ChunkWork.key(p.getX()>>4,p.getZ()>>4);}
    private static void ownershipPhase(Job j,boolean clearing,int layer){
        if(j.chunkOwners==null||j.chunkPhaseClearing!=clearing||j.chunkPhaseLayer!=layer){
            j.chunkOwners=new IdentityHashMap<>();j.chunkPhaseClearing=clearing;j.chunkPhaseLayer=layer;
        }
    }
    private static boolean workerAvailable(ServerLevel level,Worker w){
        return !w.returningHome&&!w.respawnRequested&&level.getEntity(UUID.fromString(w.worker)) instanceof Villager n&&n.isAlive();
    }
    private static void releaseUnavailable(ServerLevel level,Job j){
        j.chunkOwners.entrySet().removeIf(e->!workerAvailable(level,e.getKey()));
    }
    private static Long claimChunk(Job j,Worker w,Villager npc,Collection<Long> available,int ordinal,int count){
        Long existing=j.chunkOwners.get(w);
        if(existing!=null&&available.contains(existing))return existing;
        j.chunkOwners.remove(w);
        var occupied=new HashSet<>(j.chunkOwners.values());
        Long best=null;double bestScore=Double.NEGATIVE_INFINITY;
        for(long key:available){
            if(occupied.contains(key))continue;
            int x=ChunkWork.x(key)*16+8,z=ChunkWork.z(key)*16+8;
            double separation=Double.POSITIVE_INFINITY;
            for(long other:occupied){
                double dx=(double)ChunkWork.x(key)-ChunkWork.x(other),dz=(double)ChunkWork.z(key)-ChunkWork.z(other);
                separation=Math.min(separation,dx*dx+dz*dz);
            }
            if(occupied.isEmpty())separation=0;
            double dx=x-npc.getX(),dz=z-npc.getZ();
            boolean preferred=zone(j,new BlockPos(x,j.y,z),count)==ordinal;
            double score=separation*256+(preferred?64:0)-Math.hypot(dx,dz)*.01;
            if(score>bestScore){bestScore=score;best=key;}
        }
        if(best!=null)j.chunkOwners.put(w,best);
        return best;
    }
    private static void initClearChunks(Job j){
        if(j.clearChunks!=null)return;
        j.clearChunks=new ArrayList<>();
        int maxX=j.x+physicalWidth(j)-1,maxZ=j.z+physicalDepth(j)-1;
        for(int cx=j.x>>4;cx<=maxX>>4;cx++)for(int cz=j.z>>4;cz<=maxZ>>4;cz++){
            j.clearChunks.add(new ClearChunk(ChunkWork.key(cx,cz),Math.max(j.x,cx*16),Math.min(maxX,cx*16+15),Math.max(j.z,cz*16),Math.min(maxZ,cz*16+15),j.y+j.height-1));
        }
    }
    // Both phases share one chunk reservation. Durable legacy indices are retained,
    // so a restart can reconstruct partially cleared and partially built chunks.
    private boolean prepareLifecycle(Job j){
        if(j.lifecycleChunks==null){
            j.clearChunks=null;initClearChunks(j);
            j.lifecycleChunks=new LinkedHashMap<>();
            for(ClearChunk c:j.clearChunks){
                if(!j.clearing)c.x=c.maxX+1;
                j.lifecycleChunks.put(c.key,c);
            }
            j.buildChunks=new LinkedHashMap<>();j.lifecycleScan=j.cursor;j.lifecycleIndexed=false;
            j.chunkOwners=new IdentityHashMap<>();j.deferredChunks=new HashMap<>();
            for(Worker w:team(j)){w.assignment=-1;resetMovement(w);}
        }
        if(j.lifecycleIndexed)return true;
        var plan=cells(j);
        int scanned=0;
        while(j.lifecycleScan<plan.size()&&scanned++<8192){
            int i=j.lifecycleScan++;
            if(i>=j.cursor&&!j.teamDone.contains(i))j.buildChunks.computeIfAbsent(chunkKey(buildPosition(j,plan.get(i))),k->new TreeSet<>()).add(i);
        }
        return j.lifecycleIndexed=j.lifecycleScan>=plan.size();
    }
    private void independentWork(MinecraftServer server,ServerLevel level,Job j){
        if(!j.independentChunks){
            j.independentChunks=true;
            // Older jobs already in the build phase have fully cleared their site.
            if(!j.clearing){j.clearCursor=j.width*j.depth*j.height;j.clearDone.clear();}
            if(!save())return;
        }
        if(!prepareLifecycle(j))return;
        var workers=team(j);var desired=desiredStates(j);var plan=cells(j);
        var air=Blocks.AIR.defaultBlockState();int top=j.y+j.height-1,bottom=j.y,total=j.width*j.depth*j.height;
        for(Worker w:workers)if(w.returningHome&&level.getEntity(UUID.fromString(w.worker)) instanceof Villager npc&&npc.isAlive()){
            returnHome(level,j,w,npc);if(storageFailed||!j.status.equals("RUNNING"))return;
        }
        releaseUnavailable(level,j);
        // Chunks are removed only after their final fluid verification pass.
        j.chunkOwners.entrySet().removeIf(e->!j.lifecycleChunks.containsKey(e.getValue()));
        if(j.lifecycleChunks.isEmpty()){
            if(j.clearCursor!=total||j.cursor!=plan.size()){blocked(j,(Villager)level.getEntity(UUID.fromString(j.worker)),"Chunk progress mismatch; inspect saved job");return;}
            j.clearing=false;j.clearDone.clear();j.status="DONE";j.completedAt=System.currentTimeMillis();j.reason="";
            stopTeam(level,j);if(save()){cleanupWorker(server,j);notifyCompletion(server,j);}return;
        }
        boolean dirty=false;int budget=Math.max(1,CLEAR_BATCH/workers.size());
        long now=System.currentTimeMillis();
        var available=new LinkedHashSet<Long>();
        for(long key:j.lifecycleChunks.keySet())if(j.deferredChunks.getOrDefault(key,0L)<=now)available.add(key);
        int initial=Math.floorMod(j.workerOffset,workers.size());
        for(int turn=0;turn<workers.size();turn++){
            if(turn>0&&System.nanoTime()>=workDeadline)break;
            int wi=(initial+turn)%workers.size();j.workerOffset=(wi+1)%workers.size();
            Worker w=workers.get(wi);
            if(!workerAvailable(level,w))continue;
            Villager npc=(Villager)level.getEntity(UUID.fromString(w.worker));
            Long key=claimChunk(j,w,npc,available,wi,workers.size());
            if(key==null){w.assignment=-1;w.chunkStage="WAITING";resetMovement(w);continue;}
            ClearChunk c=j.lifecycleChunks.get(key);
            boolean clearedBlock=false;
            if(!c.done()){
                w.chunkStage="CLEARING";w.assignmentClearing=true;
                for(int scanned=0;scanned<budget&&!c.done();scanned++){
                    int index=clearIndex(j,c.x,c.y,c.z);
                    if(index<j.clearCursor||j.clearDone.contains(index)){c.advance(top,bottom);continue;}
                    BlockPos pos=new BlockPos(c.x,c.y,c.z);
                    w.assignment=index;
                    if(!loadTarget(level,w,pos))break;
                    String problem=siteProblem(level,pos);
                    if(problem!=null){blocked(j,npc,problem+" at "+pos.toShortString());return;}
                    var current=level.getBlockState(pos);
                    var target=desired.getOrDefault(pos,air);
                    boolean done=current.isAir()||current.equals(target)||(isDrainableFluid(current)&&!target.isAir()),changed=false;
                    if(!done){
                        if(!canClear(level,pos,current)){blocked(j,npc,clearRestriction(level,pos,current));return;}
                        if(approach(w,npc,level,pos)){
                            npc.swing(InteractionHand.MAIN_HAND);
                            if(isDrainableFluid(current)?drainFluid(level,pos,current):level.destroyBlock(pos,false,npc)){
                                j.cleared.add(new Cleared(pos.getX(),pos.getY(),pos.getZ(),net.minecraft.world.level.block.Block.getId(current)));
                                done=true;changed=true;
                            }
                        }
                    }
                    if(!done){
                        if(++w.teamStalled>=STUCK_ATTEMPTS)startHomeRecovery(level,j,w,npc,"Cannot clear "+pos.toShortString());
                        break;
                    }
                    j.clearDone.add(index);while(j.clearDone.remove(j.clearCursor))j.clearCursor++;markProgress(j,index,true);
                    c.advance(top,bottom);w.assignment=-1;w.teamStalled=0;w.recoveryAttempts=0;resetMovement(w);dirty=true;
                    if(changed){clearedBlock=true;break;}
                }
                if(!j.status.equals("RUNNING")||storageFailed)return;
                if(!c.done()||clearedBlock)continue;
                w.assignment=-1;resetMovement(w);
            }
            // Keep the same reservation when switching from clearing to building.
            w.chunkStage="BUILDING";w.assignmentClearing=false;
            var queue=j.buildChunks.get(key);
            if(queue!=null&&!queue.isEmpty()){
                int index=queue.first();var cell=plan.get(index);var pos=buildPosition(j,cell);
                w.assignment=index;
                if(!loadTarget(level,w,pos))continue;
                String problem=siteProblem(level,pos);
                if(problem!=null){blocked(j,npc,problem+" at "+pos.toShortString());return;}
                var wanted=material(j,cell.material());var current=level.getBlockState(pos);
                boolean done=current.equals(wanted);
                if(!done){
                    if(!current.isAir()&&!isDrainableFluid(current)){blocked(j,npc,"New obstruction at "+pos.toShortString()+": "+current);return;}
                    // Attachment-dependent states at a chunk boundary may need a neighbor.
                    // Defer the entire chunk instead of placing unsupported blocks or deadlocking the team.
                    // Only query survival when adjacent chunks are loaded.
                    boolean loaded=true;
                    for(net.minecraft.core.Direction d:net.minecraft.core.Direction.Plane.HORIZONTAL)if(!level.hasChunkAt(pos.relative(d))){loaded=false;break;}
                    if(!loaded)continue;
                    if(!wanted.canSurvive(level,pos)){
                        j.deferredChunks.put(key,now+5000L);available.remove(key);j.chunkOwners.remove(w);
                        w.assignment=-1;w.chunkStage="WAITING FOR SUPPORT";resetMovement(w);
                        j.reason="Waiting for support at "+pos.toShortString();
                        continue;
                    }
                    if(approach(w,npc,level,pos)&&level.getEntities(npc,new AABB(pos)).isEmpty()){
                        npc.swing(InteractionHand.MAIN_HAND);
                        if(level.setBlock(pos,wanted,3)){if(isDrainableFluid(current))j.cleared.add(new Cleared(pos.getX(),pos.getY(),pos.getZ(),net.minecraft.world.level.block.Block.getId(current)));j.placed.add(index);done=true;}
                    }
                }
                if(done){
                    queue.remove(index);j.teamDone.add(index);while(j.teamDone.remove(j.cursor))j.cursor++;markProgress(j,index,false);
                    w.assignment=-1;w.teamStalled=0;w.recoveryAttempts=0;resetMovement(w);j.reason="";
                    continue; // Verify on the next work cycle, retaining the reservation.
                }else if(++w.teamStalled>=STUCK_ATTEMPTS){
                    startHomeRecovery(level,j,w,npc,"Cannot place "+pos.toShortString());if(!j.status.equals("RUNNING"))return;
                }
            }
            if(queue==null||queue.isEmpty()){
                // Verify only intended air positions for refilling vanilla liquids,
                // once all planned solid blocks in this chunk have been placed.
                w.chunkStage="DRAINING";w.assignmentClearing=true;
                int width=c.maxX-c.minX+1,depth=c.maxZ-c.minZ+1,area=width*depth;
                int volume=area*j.height;
                boolean removedFluid=false;
                for(int scanned=0;scanned<budget&&c.verifyOffset<volume;scanned++){
                    int i=c.verifyOffset,layer=i/area,cellIndex=i%area;
                    var pos=new BlockPos(c.minX+cellIndex/depth,top-layer,c.minZ+cellIndex%depth);
                    w.assignment=clearIndex(j,pos.getX(),pos.getY(),pos.getZ());
                    if(!loadTarget(level,w,pos))break;
                    String problem=siteProblem(level,pos);
                    if(problem!=null){blocked(j,npc,problem+" at "+pos.toShortString());return;}
                    var state=level.getBlockState(pos);
                    if(!desired.containsKey(pos)&&isDrainableFluid(state)){
                        if(!approach(w,npc,level,pos)){
                            if(++w.teamStalled>=STUCK_ATTEMPTS)startHomeRecovery(level,j,w,npc,"Cannot drain "+pos.toShortString());
                            break;
                        }
                        npc.swing(InteractionHand.MAIN_HAND);
                        if(!drainFluid(level,pos,state))break;
                        j.cleared.add(new Cleared(pos.getX(),pos.getY(),pos.getZ(),net.minecraft.world.level.block.Block.getId(state)));
                        w.teamStalled=0;w.recoveryAttempts=0;resetMovement(w);removedFluid=true;
                        c.drainPasses++;
                        progressDirty=true;
                        // Restart the bounded scan after a liquid mutation to catch refilling cells.
                        c.verifyOffset=0;
                        if(c.drainPasses>Math.max(64L,volume*4L)){
                            blocked(j,npc,"Fluid keeps refilling chunk "+ChunkWork.x(key)+","+ChunkWork.z(key)+"; seal external source near "+pos.toShortString());return;
                        }
                        break;
                    }
                    c.verifyOffset++;
                }
                if(removedFluid||c.verifyOffset<volume)continue;
                j.lifecycleChunks.remove(key);j.buildChunks.remove(key);j.chunkOwners.remove(w);available.remove(key);
                w.assignment=-1;w.chunkStage="COMPLETE";resetMovement(w);
            }
        }
        if(j.clearCursor>=total&&j.clearing){j.clearing=false;j.clearDone.clear();dirty=true;}
        if(dirty)progressDirty=true;
    }
    private void travelTick(MinecraftServer server) {
        for(Job j:jobs.values()) {
            if(storageFailed) return;
            if(!j.status.equals("RUNNING")||j.surveying||team(j).stream().anyMatch(w->w.respawnRequested)) continue;
            var level=jobLevel(server,j);
            if(level==null) continue;
            for(Worker w:team(j)) {
                if(w.returningHome||w.workTarget==null||!level.hasChunkAt(w.workTarget)) continue;
                if(!(level.getEntity(UUID.fromString(w.worker)) instanceof Villager npc)||!npc.isAlive()) continue;
                approach(w,npc,level,w.workTarget);
            }
        }
    }
    private void tick(TickEvent.ServerTickEvent.Post e) {
        ++ticks;
        tickRedesignPreview(e.getServer());
        if(!storageFailed&&progressDirty&&ticks-lastProgressFlush>=FLUSH_TICKS)flushProgress();
        if(!storageFailed) runSurveys(e.getServer());
        if(ticks%WORK_INTERVAL!=0) { if(!storageFailed) travelTick(e.getServer()); return; }
        if(storageFailed) {
            for(Job j:jobs.values()) { var world=jobLevel(e.getServer(),j); if(world!=null) { stopTeam(world,j); } }
            return;
        }
        workDeadline=System.nanoTime()+WORK_BUDGET_NS;
        var scheduled=new ArrayList<>(jobs.values());
        int start=Math.floorMod(workJobOffset++,Math.max(1,scheduled.size()));
        for(int turn=0;turn<scheduled.size();turn++) {
            if(storageFailed) break;
            Job j=scheduled.get((start+turn)%scheduled.size());
            var level=jobLevel(e.getServer(),j);
            if(level==null) continue;
            if(terminal(j)) { cleanupWorker(e.getServer(),j); notifyCompletion(e.getServer(),j); continue; }
            // A saved retired UUID can outlive a crash between replacement and discard.
            // Loaded retired entities must never resume construction.
            for(String retired:j.retiredWorkers) if(level.getEntity(UUID.fromString(retired)) instanceof Villager old) old.discard();
            if(processRespawns(e.getServer(),level,j)) continue;
            if(level.getEntity(UUID.fromString(j.worker)) instanceof Villager worker && worker.isAlive()) { protect(worker); setWorking(worker,j.status.equals("RUNNING")&&!storageFailed); if(j.status.equals("RUNNING")) checkpointWorker(level,j,worker); }
            for(Worker w:j.assistants) {
                if(j.status.equals("RUNNING")) {
                    ticket(level,w,workerAnchor(w),true);
                    if(level.getEntity(UUID.fromString(w.worker)) instanceof Villager worker&&worker.isAlive()) { protect(worker); setWorking(worker,!j.surveying); checkpointWorker(level,w,worker); }
                    else if(w.workerDied) replaceWorker(level,j,w,workerAnchor(w));
                } else { if(level.getEntity(UUID.fromString(w.worker)) instanceof Villager worker) setWorking(worker,false); releaseTickets(level,w); }
            }
            if(j.status.equals("RUNNING")) {
                ticket(level,j,workerAnchor(j),true);
                if(System.nanoTime()<workDeadline||turn==0)step(e.getServer(),j);
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
                npc=replaceWorker(level,j,j,anchor);
                if(npc==null) { waiting(j,"Builder died; waiting for a safe loaded respawn position. Use /architect recover "+j.id); return; }
            } else {
                waiting(j,"Builder unavailable; loading saved worker chunk "+new ChunkPos(workerAnchor(j))+". Use /architect recover "+j.id+" only if the builder is gone.");
                return;
            }
        }
        if(j.surveying) { return; }
        independentWork(server,level,j);
    }
}
