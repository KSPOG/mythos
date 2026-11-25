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
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.MoverType;
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
    private Path file;
    private int ticks;
    private boolean storageFailed;
    private static final class Job {
        int id, x, y, z, width, depth, cursor, stalled;
        int height=5, rotation, clearCursor;
        transient BlockPos workTarget;
        transient Vec3 workStand;
        transient int movementFailures;
        boolean clearing, workerDied;
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
        LOGGER.info("NPC Architects 0.1.5 initialized; Forge event listeners registered");
    }
    private void started(ServerStartedEvent e) {
        jobs.clear(); selections.clear(); ticks=0; storageFailed=false;
        LOGGER.info("NPC Architects server started");
        file=e.getServer().getWorldPath(LevelResource.ROOT).resolve("npcarchitects-jobs.json");
        try {
            if (Files.exists(file)) {
                List<Job> loaded=JSON.fromJson(Files.readString(file),new TypeToken<List<Job>>(){}.getType());
                if (loaded==null) throw new IllegalStateException("Empty job store");
                for (Job j:loaded) { cells(j); UUID.fromString(j.worker); if(j.cursor<0 || j.cursor>cells(j).size() || j.clearCursor<0 || j.clearCursor>j.width*j.depth*j.height || j.cleared==null || j.placed==null || jobs.putIfAbsent(j.id,j)!=null) throw new IllegalStateException("Invalid job"); }
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
            c.getSource().sendSuccess(()->Component.literal("NPC Architects 0.1.5 active. Commands: status, house <width> <depth> [height], pos1, pos2, rotate, preview house, build house, undo [id], recover <id>, pause, resume, cancel. Construction requires operator level 2."),false); return 1;
        });
        root.then(Commands.literal("ping").executes(c->{ c.getSource().sendSuccess(()->Component.literal("NPC Architects 0.1.5 is responding."),false); return 1; }));
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
        root.then(Commands.literal("recover").requires(src->src.hasPermission(2))
            .then(Commands.argument("id",IntegerArgumentType.integer(1)).executes(c->recover(c.getSource(),IntegerArgumentType.getInteger(c,"id")))));
        root.then(Commands.literal("status").requires(src->src.hasPermission(2)).executes(c->{ c.getSource().sendSuccess(()->Component.literal("NPC Architects: "+jobs.size()+" job(s)"+(storageFailed?" | storage error":" | storage ready")),false); for(Job j:jobs.values()) c.getSource().sendSuccess(()->Component.literal(j.id+": "+j.status+" "+(j.clearing?"CLEARING "+j.clearCursor+"/"+(j.width*j.depth*j.height):"BUILDING "+j.cursor+"/"+cells(j).size())+(j.reason.isEmpty()?"":" | "+j.reason)),false); return 1; }));
        for(String action:List.of("pause","resume","cancel")) root.then(Commands.literal(action).requires(src->src.hasPermission(2)).then(Commands.argument("id",IntegerArgumentType.integer(1)).executes(c->{ Job j=jobs.get(IntegerArgumentType.getInteger(c,"id")); if(j==null || storageFailed || j.status.equals("DONE") || j.status.equals("CANCELLED") || j.status.equals("UNDONE")) { c.getSource().sendFailure(Component.literal("Job unavailable, terminal or storage failed")); return 0; } j.status=switch(action){case "pause"->"PAUSED";case "cancel"->"CANCELLED";default->"RUNNING";}; j.stalled=0; j.reason=""; resetMovement(j); var world=jobLevel(c.getSource().getServer(),j); if(world!=null && world.getEntity(UUID.fromString(j.worker)) instanceof Villager worker) setWorking(worker,j.status.equals("RUNNING")); if(!save()) return 0; c.getSource().sendSuccess(()->Component.literal("Job "+j.id+": "+j.status),false); return 1; })));
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
            if(preview && level.hasChunkAt(pos) && (problem!=null||needsClear||desired.containsKey(pos)) && (i%3==0))
                level.sendParticles(problem!=null?ParticleTypes.FLAME:needsClear?ParticleTypes.SMOKE:ParticleTypes.END_ROD,pos.getX()+0.5,pos.getY()+0.5,pos.getZ()+0.5,1,0,0,0,0);
        }
        if(preview) {
            final String message="Preview: "+plan.size()+" blueprint blocks, "+removable+" blocks to clear, "+blocked+" blocked positions. "+(firstProblem==null?"":"First issue: "+firstProblem+". ")+"No blocks changed.";
            source.sendSuccess(()->Component.literal(message),false); return 1;
        }
        if(file==null || storageFailed || jobs.size()>=100) return fail(source,"Storage unavailable or job limit reached");
        if(blocked>0) return fail(source,firstProblem);
        j.clearing=true;
        var npc=EntityType.VILLAGER.spawn(level,player.blockPosition(),MobSpawnType.COMMAND);
        if(npc==null) return fail(source,"Could not spawn builder");
        protect(npc);
        remember(j,npc);
        j.id=jobs.keySet().stream().mapToInt(i->i).max().orElse(0)+1; j.worker=npc.getUUID().toString(); jobs.put(j.id,j);
        if(!save()) { jobs.remove(j.id); npc.discard(); return fail(source,"Failed to save job"); }
        LOGGER.info("NPC Architects created job {} at {}",j.id,origin);
        source.sendSuccess(()->Component.literal("Created job "+j.id+" at "+origin.toShortString()+" ("+plan.size()+" blocks)"),false); return 1;
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
        j.status="PAUSED"; if(!save()) return fail(source,"Could not save paused job.");
        if(level.getEntity(UUID.fromString(j.worker)) instanceof Villager npc) setWorking(npc,false);
        int removed=0, skipped=0;
        for(int i=j.placed.size()-1;i>=0;i--) {
            var c=plan.get(j.placed.get(i)); var pos=new BlockPos(j.x+c.x(),j.y+c.y(),j.z+c.z());
            if(level.getBlockState(pos).equals(material(c.material())) && level.setBlock(pos,Blocks.AIR.defaultBlockState(),3)) removed++; else skipped++;
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
        Map<BlockPos,BlockState> result=new HashMap<>();
        for(var c:cells(j)) result.put(new BlockPos(j.x+c.x(),j.y+c.y(),j.z+c.z()),material(c.material()));
        return result;
    }
    private static BlockPos clearPosition(Job j,int i) {
        int w=j.rotation%180==0?j.width:j.depth, d=j.rotation%180==0?j.depth:j.width;
        return new BlockPos(j.x+(i/d)%w,j.y+j.height-1-i/(w*d),j.z+i%d);
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
        j.status="BLOCKED"; j.reason=reason; setWorking(npc,false); resetMovement(j);
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
        npc.getLookControl().setLookAt(pos.getX()+0.5,pos.getY()+0.5,pos.getZ()+0.5);
        return false;
    }
    private void clearStep(ServerLevel level,Villager npc,Job j) {
        int total=j.width*j.depth*j.height;
        var desired=desiredStates(j);
        while(j.clearCursor<total) {
            var pos=clearPosition(j,j.clearCursor);
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
        j.clearing=false; j.stalled=0; save();
        LOGGER.info("NPC Architects job {} finished site clearing",j.id);
    }
    private static BlockState material(String name) { return switch(name) { case "floor"->Blocks.COBBLESTONE.defaultBlockState(); case "roof"->Blocks.SPRUCE_PLANKS.defaultBlockState(); case "glass"->Blocks.GLASS.defaultBlockState(); default->Blocks.OAK_PLANKS.defaultBlockState(); }; }
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
        j.status="RUNNING"; if(!save()) return fail(source,"Replacement spawned but job resume could not be saved.");
        source.sendSuccess(()->Component.literal("Builder replaced for job "+id+"; construction resumed from saved progress."),false); return 1;
    }
    private void tick(TickEvent.ServerTickEvent.Post e) {
        if(++ticks%10!=0) return;
        if(storageFailed) {
            for(Job j:jobs.values()) { var world=jobLevel(e.getServer(),j); if(world!=null && world.getEntity(UUID.fromString(j.worker)) instanceof Villager worker) setWorking(worker,false); }
            return;
        }
        for(Job j:jobs.values()) {
            if(storageFailed) break;
            var level=jobLevel(e.getServer(),j);
            if(level==null) continue;
            if(level.getEntity(UUID.fromString(j.worker)) instanceof Villager worker && worker.isAlive()) { protect(worker); setWorking(worker,j.status.equals("RUNNING")&&!storageFailed); }
            if(j.status.equals("RUNNING")) step(e.getServer(),j);
        }
    }
    private void step(MinecraftServer server,Job j) {
        ServerLevel level=null; for(var l:server.getAllLevels()) if(l.dimension().location().toString().equals(j.dimension)) {level=l;break;}
        if(level==null) return;
        var cells=cells(j);
        Villager npc;
        if(level.getEntity(UUID.fromString(j.worker)) instanceof Villager living && living.isAlive()) {
            npc=living; protect(npc); remember(j,npc);
            if(j.workerDied || !j.reason.isEmpty()) { j.workerDied=false; j.reason=""; if(!save()) return; }
        } else {
            if(j.workerDied) {
                var anchor=j.workerX==null?new BlockPos(j.x,j.y,j.z):new BlockPos(j.workerX,j.workerY,j.workerZ);
                npc=replaceWorker(level,j,anchor);
                if(npc==null) { waiting(j,"Builder died; waiting for a safe loaded respawn position. Use /architect recover "+j.id); return; }
            } else {
                waiting(j,"Builder unavailable (possibly unloaded). Load its chunk or use /architect recover "+j.id+" if it is gone.");
                return;
            }
        }
        if(j.clearing) { clearStep(level,npc,j); return; }
        if(j.cursor>=cells.size()) { j.status="DONE"; j.reason=""; setWorking(npc,false); resetMovement(j); LOGGER.info("NPC Architects job {} completed",j.id); save(); return; }
        var c=cells.get(j.cursor); var pos=new BlockPos(j.x+c.x(),j.y+c.y(),j.z+c.z());
        String problem=siteProblem(level,pos);
        if(problem!=null) { blocked(j,npc,problem+" at "+pos.toShortString()); return; }
        var wanted=material(c.material()); var current=level.getBlockState(pos);
        if(current.equals(wanted)) { j.cursor++; j.stalled=0; save(); return; }
        if(!current.isAir() || !level.getWorldBorder().isWithinBounds(pos)) { blocked(j,npc,"New obstruction at "+pos.toShortString()); return; }
        if(approach(j,npc,level,pos) && level.getEntities(npc,new AABB(pos)).isEmpty()) {
            npc.swing(InteractionHand.MAIN_HAND);
            if(level.setBlock(pos,wanted,3)) {
                j.placed.add(j.cursor); j.cursor++; j.stalled=0;
                if(j.cursor==cells.size() || cells.get(j.cursor).y()!=c.y()) LOGGER.info("NPC Architects job {} finished layer {} of {}",j.id,c.y()+1,j.height);
                save(); return;
            }
        }
        if(++j.stalled>=60) blocked(j,npc,"Cannot reach or place "+wanted.getBlock().getDescriptionId()+" at "+pos.toShortString()+" (layer "+(c.y()+1)+"/"+j.height+")");
    }
}
