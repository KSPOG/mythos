package com.ksp.playercleanup;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.Clearable;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.player.FillBucketEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;
import java.nio.file.*;
import java.util.*;

@Mod("playercleanup")
public final class PlayerCleanup {
    private static final org.slf4j.Logger LOG=com.mojang.logging.LogUtils.getLogger();
    private static final Gson JSON=new Gson();
    private static final TicketType<Integer> TICKET=TicketType.create("playercleanup",Integer::compare,40);
    private static final int BATCH=Math.max(1,Math.min(1024,Integer.getInteger("playercleanup.batch",64)));
    private final Map<String,Entry> entries=new LinkedHashMap<>();
    private final Map<String,String> names=new HashMap<>();
    private final List<Pending> pending=new ArrayList<>();
    private final List<BucketChange> buckets=new ArrayList<>();
    record BucketChange(String owner,String dimension,BlockPos pos,String before,boolean pickup,boolean lava){}
    private Path file;
    private boolean failed,dirty;
    private boolean removeContainerItems;
    private Task task;
    private int ticks;
    record Entry(String owner,String dimension,int x,int y,int z,String before,String after,String entity) {
        String key(){return entity==null?dimension+":"+x+":"+y+":"+z:"entity:"+entity;}
        BlockPos pos(){return new BlockPos(x,y,z);}
    }
    record Pending(Entry entry, boolean broken){}
    static final class Store { Map<String,String> names=new HashMap<>(); List<Entry> entries=new ArrayList<>(); }
    static final class Task {
        final String owner; final ArrayDeque<Entry> queue; final int total;
        final boolean deleteContents;
        int removed,skipped,wait; ServerLevel level; ChunkPos chunk;
        Task(String owner,List<Entry> list,boolean deleteContents){this.owner=owner;queue=new ArrayDeque<>(list);total=list.size();this.deleteContents=deleteContents;}
    }
    public PlayerCleanup(){
        var bus=MinecraftForge.EVENT_BUS;
        bus.addListener(this::start);bus.addListener(this::stop);bus.addListener(this::commands);
        bus.addListener(this::tick);bus.addListener(this::login);
        bus.addListener(EventPriority.LOWEST,this::place);
        bus.addListener(EventPriority.LOWEST,this::broken);
        bus.addListener(EventPriority.LOWEST,this::entityJoin);
        bus.addListener(EventPriority.LOWEST,this::bucket);
    }
    private void loadConfig(){
        // Fail safe: an absent or invalid option never authorizes inventory deletion.
        removeContainerItems=false;
        Path config=FMLPaths.CONFIGDIR.get().resolve("playercleanup.properties");
        try {
            Files.createDirectories(config.getParent());
            if(!Files.exists(config)) Files.writeString(config,
                "# true: delete container contents; false: preserve contents as drops or retain unsupported containers.\nremoveContainerItems=false\n",
                StandardOpenOption.CREATE_NEW);
            var props=new Properties();
            try(var reader=Files.newBufferedReader(config)){props.load(reader);}
            String value=props.getProperty("removeContainerItems","false").trim();
            if(!value.equalsIgnoreCase("true")&&!value.equalsIgnoreCase("false"))
                throw new IllegalArgumentException("removeContainerItems must be true or false");
            removeContainerItems=Boolean.parseBoolean(value);
        }catch(Exception ex){LOG.warn("Player Cleanup config invalid; preserving container items",ex);}
        LOG.info("Player Cleanup removeContainerItems={}",removeContainerItems);
    }
    private void start(ServerStartedEvent e){
        entries.clear();names.clear();pending.clear();buckets.clear();task=null;failed=false;dirty=false;ticks=0;
        loadConfig();
        file=e.getServer().getWorldPath(LevelResource.ROOT).resolve("playercleanup.json");
        try {
            if(Files.exists(file)){
                Store data=JSON.fromJson(Files.readString(file),Store.class);
                if(data==null||data.names==null||data.entries==null)throw new IllegalStateException("Invalid store");
                names.putAll(data.names);
                for(Entry row:data.entries){UUID.fromString(row.owner());entries.put(row.key(),row);}
            }
        }catch(Exception ex){failed=true;LOG.error("Player Cleanup disabled: cannot load ownership store",ex);}
    }
    private boolean save(){
        if(failed||file==null)return false;
        if(!dirty)return true;
        try{
            Store data=new Store();data.names.putAll(names);data.entries.addAll(entries.values());
            Path tmp=file.resolveSibling(file.getFileName()+".tmp");
            Files.writeString(tmp,JSON.toJson(data));
            try{Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException ex){Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING);}
            dirty=false;return true;
        }catch(Exception ex){failed=true;LOG.error("Player Cleanup disabled: storage write failed",ex);return false;}
    }
    private void stop(ServerStoppingEvent e){flush(e.getServer());release();save();file=null;}
    private void remember(ServerPlayer p){names.put(p.getGameProfile().getName().toLowerCase(Locale.ROOT),p.getUUID().toString());dirty=true;}
    private void login(PlayerEvent.PlayerLoggedInEvent e){if(!failed&&e.getEntity() instanceof ServerPlayer p)remember(p);}
    private static String dimension(ServerLevel l){return l.dimension().location().toString();}
    private static String state(BlockState s){return BlockStateParser.serialize(s);}
    private void place(BlockEvent.EntityPlaceEvent e){
        if(failed||e.isCanceled()||!(e.getEntity() instanceof ServerPlayer p)||!(e.getLevel() instanceof ServerLevel l))return;
        remember(p);
        var snapshots=e instanceof BlockEvent.EntityMultiPlaceEvent multi?multi.getReplacedBlockSnapshots():List.of(e.getBlockSnapshot());
        for(var snapshot:snapshots){
            var pos=snapshot.getPos();
            pending.add(new Pending(new Entry(p.getUUID().toString(),dimension(l),pos.getX(),pos.getY(),pos.getZ(),state(snapshot.getReplacedBlock()),state(l.getBlockState(pos)),null),false));
        }
    }
    private void broken(BlockEvent.BreakEvent e){
        if(failed||e.isCanceled()||!(e.getPlayer() instanceof ServerPlayer p)||!(e.getLevel() instanceof ServerLevel l))return;
        var pos=e.getPos();
        pending.add(new Pending(new Entry(p.getUUID().toString(),dimension(l),pos.getX(),pos.getY(),pos.getZ(),state(e.getState()),null,null),true));
    }
    private void bucket(FillBucketEvent e){
        if(failed||e.isCanceled()||!(e.getEntity() instanceof ServerPlayer p)||!(e.getTarget() instanceof BlockHitResult hit))return;
        if(!(p.level() instanceof ServerLevel l))return;
        var item=e.getEmptyBucket();
        boolean pickup=item.is(Items.BUCKET), lava=item.is(Items.LAVA_BUCKET);
        if(!pickup&&!lava&&!item.is(Items.WATER_BUCKET)&&!item.is(Items.COD_BUCKET)&&!item.is(Items.SALMON_BUCKET)&&!item.is(Items.PUFFERFISH_BUCKET)&&!item.is(Items.TROPICAL_FISH_BUCKET)&&!item.is(Items.AXOLOTL_BUCKET)&&!item.is(Items.TADPOLE_BUCKET))return;
        remember(p);
        var clicked=hit.getBlockPos();
        for(var pos:pickup?List.of(clicked):List.of(clicked,clicked.relative(hit.getDirection()))){
            if(l.hasChunkAt(pos))buckets.add(new BucketChange(p.getUUID().toString(),dimension(l),pos,state(l.getBlockState(pos)),pickup,lava));
        }
    }
    private void flushBuckets(MinecraftServer server){
        for(var b:buckets){
            var l=level(server,b.dimension());if(l==null||!l.hasChunkAt(b.pos()))continue;
            var current=l.getBlockState(b.pos());
            String after=state(current);
            if(after.equals(b.before()))continue;
            var row=new Entry(b.owner(),b.dimension(),b.pos().getX(),b.pos().getY(),b.pos().getZ(),b.before(),after,null);
            if(b.pickup()){entries.remove(row.key());dirty=true;continue;}
            // Record confirmed source fluid or waterlogging only, never spread by proximity.
            var fluid=current.getFluidState();
            if(fluid.isSource()&&fluid.is(b.lava()?net.minecraft.tags.FluidTags.LAVA:net.minecraft.tags.FluidTags.WATER)){
                entries.put(row.key(),row);dirty=true;
            }
        }
        buckets.clear();
    }
    // Only explicitly owner-tagged entities are attributed; never guess from proximity.
    private void entityJoin(EntityJoinLevelEvent e){
        if(failed||e.isCanceled()||!(e.getLevel() instanceof ServerLevel l))return;
        Entity entity=e.getEntity();
        if(entity instanceof net.minecraft.world.entity.TamableAnimal tame && tame.getOwnerUUID()!=null && !entity.getTags().contains("npcarchitects_worker")){
            // Taming is not placement. Do not attribute pets to cleanup.
            return;
        }
        // Explicitly tagged entities are supported for integrations, not automatically inferred.
        for(String tag:entity.getTags())if(tag.startsWith("playercleanup_owner_")){
            try{
                String owner=UUID.fromString(tag.substring("playercleanup_owner_".length())).toString();
                if(entity instanceof ServerPlayer||entity.getTags().contains("npcarchitects_worker"))return;
                var pos=entity.blockPosition();Entry row=new Entry(owner,dimension(l),pos.getX(),pos.getY(),pos.getZ(),null,null,entity.getUUID().toString());
                entries.put(row.key(),row);dirty=true;return;
            }catch(IllegalArgumentException ignored){}
        }
    }
    private static ServerLevel level(MinecraftServer server,String name){for(var l:server.getAllLevels())if(dimension(l).equals(name))return l;return null;}
    private void flush(MinecraftServer server){
        if(failed){pending.clear();buckets.clear();return;}
        flushBuckets(server);
        for(Pending p:pending){
            Entry row=p.entry();var l=level(server,row.dimension());
            if(l==null||!l.hasChunkAt(row.pos()))continue;
            String current=state(l.getBlockState(row.pos()));
            if(p.broken()){
                entries.remove(row.key());dirty=true;
            }else if(sameBlock(current,row.after())){
                // Retain only the latest owner; do not inherit another player's placement.
                entries.put(row.key(),row);dirty=true;
            }else{entries.remove(row.key());dirty=true;}
        }
        pending.clear();
    }
    private static BlockState parse(String value)throws Exception{
        return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(),value,false).blockState();
    }
    private static boolean sameBlock(String a,String b){
        if(a==null||b==null)return false;
        return a.split("\\[",2)[0].equals(b.split("\\[",2)[0]);
    }
    private boolean removeBlock(ServerLevel l,Entry row,boolean deleteContents){
        var pos=row.pos();
        var current=l.getBlockState(pos);
        if(!sameBlock(state(current),row.after()))return false;
        try{
            var before=parse(row.before());
            // Original block entity NBT was not recorded by v0.1.0. Never reconstruct
            // a former container as an empty container and claim its contents were restored.
            if(before.hasBlockEntity())return false;
            var be=l.getBlockEntity(pos);
            if(be!=null){
                if(deleteContents){
                    // Clear supported inventories and discard the block entity before removal.
                    Clearable.tryClear(be);
                    l.removeBlockEntity(pos);
                }else if(be instanceof Container inventory){
                    // Vanilla container drop logic empties the stacks. This operates on
                    // the placed block's own inventory, not a combined double chest.
                    Containers.dropContents(l,pos,inventory);
                    Clearable.tryClear(be);
                    l.removeBlockEntity(pos);
                }else{
                    // Special item holders and modded block entities may not implement
                    // Container. Preserve their data through vanilla loot/onRemove
                    // rather than deleting it before those hooks run.
                    if(!l.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DOBLOCKDROPS)) return false;
                    if(!l.destroyBlock(pos,true)) return false;
                    if(l.getBlockState(pos).equals(before)) return true;
                }
            }
            return l.setBlock(pos,before,Block.UPDATE_ALL);
        }catch(Exception ex){LOG.warn("Cannot restore {}",pos,ex);return false;}
    }
    private void commands(RegisterCommandsEvent e){
        var target=Commands.argument("player",StringArgumentType.word())
            .executes(c->request(c.getSource(),StringArgumentType.getString(c,"player"),"all",false))
            .then(Commands.literal("preview").executes(c->request(c.getSource(),StringArgumentType.getString(c,"player"),"all",true)))
            .then(Commands.literal("blocks").executes(c->request(c.getSource(),StringArgumentType.getString(c,"player"),"blocks",false)))
            .then(Commands.literal("entities").executes(c->request(c.getSource(),StringArgumentType.getString(c,"player"),"entities",false)));
        e.getDispatcher().register(Commands.literal("remove").requires(s->s.hasPermission(2))
            .then(target)
            .then(Commands.literal("status").executes(c->{say(c.getSource(),task==null?"No active cleanup.":"Cleanup: "+(task.total-task.queue.size())+"/"+task.total+" | removed "+task.removed+" | skipped "+task.skipped);return 1;}))
            .then(Commands.literal("reload").executes(c->{loadConfig();say(c.getSource(),"Configuration reloaded. Container items: "+(removeContainerItems?"DELETE":"PRESERVE")+". Active cleanup keeps its original setting.");return 1;}))
            .then(Commands.literal("cancel").executes(c->{release();task=null;say(c.getSource(),"Cleanup cancelled; completed changes remain.");return 1;})));
    }
    private static void say(CommandSourceStack s,String m){s.sendSuccess(()->Component.literal("[Player Cleanup] "+m),false);}
    private int request(CommandSourceStack s,String name,String mode,boolean preview){
        if(failed||file==null){s.sendFailure(Component.literal("Ownership storage unavailable"));return 0;}
        flush(s.getServer());
        String owner=names.get(name.toLowerCase(Locale.ROOT));
        try{owner=UUID.fromString(name).toString();}catch(IllegalArgumentException ignored){}
        if(owner==null){s.sendFailure(Component.literal("Unknown player; no tracked login."));return 0;}
        var rows=new ArrayList<Entry>();
        for(Entry row:entries.values())if(owner.equals(row.owner())&&(mode.equals("all")||mode.equals("blocks")&&row.entity()==null||mode.equals("entities")&&row.entity()!=null))rows.add(row);
        if(preview){long blocks=rows.stream().filter(r->r.entity()==null).count();say(s,name+": "+blocks+" tracked blocks, "+(rows.size()-blocks)+" tracked entities. Container items: "+(removeContainerItems?"DELETE":"PRESERVE")+". Protected or changed placements may be skipped.");return 1;}
        if(task!=null){s.sendFailure(Component.literal("Cleanup already running."));return 0;}
        if(!save())return 0;
        rows.sort(Comparator.comparing(Entry::dimension).thenComparingInt(r->r.x()>>4).thenComparingInt(r->r.z()>>4).thenComparing(Comparator.comparingInt(Entry::y).reversed()));
        task=new Task(owner,rows,removeContainerItems);say(s,"Queued "+rows.size()+" tracked placements for "+name+". Container items: "+(task.deleteContents?"DELETE":"PRESERVE")+".");return 1;
    }
    private void release(){if(task!=null&&task.level!=null&&task.chunk!=null){task.level.getChunkSource().removeRegionTicket(TICKET,task.chunk,2,0);task.level=null;task.chunk=null;}}
    private void tick(TickEvent.ServerTickEvent.Post e){
        if(file==null)return;
        flush(e.getServer());
        if(++ticks%20==0)save();
        if(failed){release();task=null;return;}
        if(task==null)return;
        for(int i=0;i<BATCH&&!task.queue.isEmpty();i++){
            Entry row=task.queue.peek();
            if(!row.equals(entries.get(row.key()))){task.skipped++;task.queue.remove();continue;}
            var l=level(e.getServer(),row.dimension());
            if(l==null){task.skipped++;task.queue.remove();continue;}
            var chunk=new ChunkPos(row.pos());
            if(task.level!=l||!chunk.equals(task.chunk)){release();task.level=l;task.chunk=chunk;task.wait=0;}
            l.getChunkSource().addRegionTicket(TICKET,chunk,2,0);
            if(!l.hasChunkAt(row.pos())){if(++task.wait<200)break;task.skipped++;task.queue.remove();task.wait=0;continue;}
            task.wait=0;
            boolean removed=false;
            if(row.entity()!=null){
                // Search loaded dimensions; never remove players, Architect NPCs or untagged entities.
                for(var world:e.getServer().getAllLevels()){
                    var entity=world.getEntity(UUID.fromString(row.entity()));
                    if(entity!=null&&!(entity instanceof ServerPlayer)&&!entity.getTags().contains("npcarchitects_worker")&&entity.getTags().contains("playercleanup_owner_"+row.owner())){entity.discard();removed=true;break;}
                }
            }else{
                removed=removeBlock(l,row,task.deleteContents);
            }
            if(removed){entries.remove(row.key());dirty=true;task.removed++;}else task.skipped++;
            task.queue.remove();
        }
        if(task.queue.isEmpty()){
            String msg="[Player Cleanup] Complete: "+task.removed+" removed, "+task.skipped+" skipped.";
            release();task=null;save();LOG.info(msg);
            for(var p:e.getServer().getPlayerList().getPlayers())if(p.hasPermissions(2))p.sendSystemMessage(Component.literal(msg));
        }
    }
}
