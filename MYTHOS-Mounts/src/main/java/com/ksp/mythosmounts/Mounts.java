package com.ksp.mythosmounts;

import com.google.gson.*;
import com.ksp.mythoseconomy.EconomyApi;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Strider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.fml.common.Mod;
import java.nio.file.*;
import java.util.*;

@Mod("mythosmounts")
public final class Mounts {
    private static final String TAG="mythos_mount";
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private static final org.slf4j.Logger LOG=com.mojang.logging.LogUtils.getLogger();
    private enum Source { SHOP, QUEST, DUNGEON, RAID, WORLD_BOSS, ACHIEVEMENT, CLAN, EVENT, PREMIUM }
    private record Definition(String id,String name,String entity,Source source,long price,double speed,boolean nativeControl){}
    private static final List<Definition> CATALOG=List.of(
        new Definition("premium_celestial_steed","Celestial Steed","horse",Source.PREMIUM,0,.22,true),
        new Definition("premium_royal_camel","Royal Camel","camel",Source.PREMIUM,0,.18,true),
        new Definition("traveller","Traveller's Horse","horse",Source.SHOP,2500,.22,true),
        new Definition("pack_donkey","Pack Donkey","donkey",Source.SHOP,5000,.20,true),
        new Definition("highland_mule","Highland Mule","mule",Source.SHOP,12000,.24,true),
        new Definition("desert_camel","Desert Camel","camel",Source.SHOP,15000,.18,true),
        new Definition("royal_warhorse","Royal Warhorse","horse",Source.SHOP,75000,.30,true),
        new Definition("boar","Battle Boar","pig",Source.SHOP,10000,.22,false),
        new Definition("lava_strider","Lava Strider","strider",Source.SHOP,40000,.24,false),
        new Definition("undead_charger","Undead Charger","skeleton_horse",Source.DUNGEON,0,.28,true),
        new Definition("grave_stallion","Grave Stallion","zombie_horse",Source.RAID,0,.30,true),
        new Definition("shadowcrawler","Shadowcrawler","spider",Source.DUNGEON,0,.27,false),
        new Definition("cave_crawler","Cave Crawler","cave_spider",Source.QUEST,0,.25,false),
        new Definition("frostfang","Frostfang","polar_bear",Source.WORLD_BOSS,0,.26,false),
        new Definition("ravager","Siege Ravager","ravager",Source.RAID,0,.29,false),
        new Definition("sniffer","Ancient Sniffer","sniffer",Source.ACHIEVEMENT,0,.18,false),
        new Definition("llama","Caravan Llama","llama",Source.SHOP,8000,.20,false),
        new Definition("trader_llama","Royal Caravan","trader_llama",Source.CLAN,0,.23,false),
        new Definition("panda","Jade Guardian","panda",Source.EVENT,0,.18,false),
        new Definition("goat","Mountain Ram","goat",Source.ACHIEVEMENT,0,.25,false),
        new Definition("iron_guardian","Iron Guardian","iron_golem",Source.RAID,0,.22,false),
        new Definition("warden","Deep Guardian","warden",Source.WORLD_BOSS,0,.25,false),
        new Definition("hoglin","Crimson Beast","hoglin",Source.DUNGEON,0,.27,false),
        new Definition("zoglin","Rotting Beast","zoglin",Source.RAID,0,.28,false),
        new Definition("mooshroom","Fungal Steed","mooshroom",Source.EVENT,0,.19,false),
        new Definition("cow","Farm Steed","cow",Source.SHOP,3500,.18,false),
        new Definition("void_dragon","Void Dragon","ender_dragon",Source.RAID,0,0.35,false),
        new Definition("soul_reaper","Soul Reaper","wither",Source.RAID,0,0.32,false),
        new Definition("skywhale","Nether Skywhale","ghast",Source.SHOP,150000,0.24,false),
        new Definition("nightmare","Nightmare","phantom",Source.DUNGEON,0,0.28,false),
        new Definition("infernal_spirit","Infernal Spirit","blaze",Source.DUNGEON,0,0.23,false),
        new Definition("storm_spirit","Storm Spirit","breeze",Source.DUNGEON,0,0.23,false),
        new Definition("ocean_runner","Ocean Runner","dolphin",Source.SHOP,35000,0.3,false),
        new Definition("sea_guardian","Ancient Sea Guardian","elder_guardian",Source.WORLD_BOSS,0,0.25,false),
        new Definition("reef_guardian","Reef Guardian","guardian",Source.DUNGEON,0,0.25,false),
        new Definition("emerald_turtle","Emerald Turtle","turtle",Source.SHOP,18000,0.18,false),
        new Definition("kraken","Abyssal Kraken","squid",Source.QUEST,0,0.23,false),
        new Definition("luminous_kraken","Luminous Kraken","glow_squid",Source.EVENT,0,0.23,false),
        new Definition("colossus","Colossus","giant",Source.WORLD_BOSS,0,0.25,false),
        new Definition("voidwalker","Voidwalker","enderman",Source.DUNGEON,0,0.26,false)
    );
    private enum Movement { NATIVE, GROUND, FLIGHT, WATER, LOCKED }
    // Bosses and special vanilla tick behavior require dedicated isolation, not setNoAi alone.
    private static Movement movement(Definition d){
        if(d.nativeControl)return Movement.NATIVE;
        return switch(d.entity){
            case "ender_dragon","wither","warden","enderman","snow_golem","vex","bat","giant"->Movement.LOCKED;
            case "ghast","phantom","blaze","breeze","bee","allay","parrot"->Movement.FLIGHT;
            case "dolphin","guardian","elder_guardian","squid","glow_squid","axolotl","turtle"->Movement.WATER;
            default->Movement.GROUND;
        };
    }
    private static double scale(Definition d){
        if(d.id.equals("royal_warhorse"))return 1.10;
        return switch(d.entity){
            case "bee","allay","parrot","rabbit","chicken","silverfish","axolotl","frog"->2.5;
            case "fox","ocelot","armadillo","wolf","cave_spider"->1.8;
            default->1.0;
        };
    }
    private static Definition definition(String id){return CATALOG.stream().filter(d->d.id.equals(id)).findFirst().orElse(null);}
    private static class Account {
        // Legacy test wallet retained in the file for backup/reconciliation; never spendable.
        long gold;
        Map<String,String> owned=new LinkedHashMap<>();
    }
    private static class Store {Map<String,Account> players=new HashMap<>();}
    private record Active(UUID owner,String id,Entity entity){}
    private Store store=new Store();
    private Path file;
    private boolean failed=true;
    private final Map<UUID,Active> active=new HashMap<>();
    public Mounts(){
        EconomyApi.registerCreatures("mount",new EconomyApi.CreatureProvider(){
            public boolean available(){return !failed;}
            public boolean legacyOwns(UUID player,String key){
                var a=store.players.get(player.toString());
                return key.startsWith("mount:")&&a!=null&&a.owned.containsKey(key.substring(6));
            }
            public List<EconomyApi.Creature> catalog(){
                return CATALOG.stream().filter(d->movement(d)!=Movement.LOCKED).map(d->new EconomyApi.Creature("mount:"+d.id,d.name,movement(d).name()+" | speed "+d.speed,d.source==Source.SHOP)).toList();
            }
        });
        var bus=MinecraftForge.EVENT_BUS;
        bus.addListener(this::start);bus.addListener(this::stop);bus.addListener(this::commands);
        bus.addListener(this::mount);bus.addListener(this::interact);bus.addListener(this::join);
        bus.addListener(this::logout);bus.addListener(this::dimension);bus.addListener(this::tick);
    }
    private void start(ServerStartedEvent e){
        file=e.getServer().getWorldPath(LevelResource.ROOT).resolve("mythos-mounts.json");
        active.clear();failed=true;
        try{
            store=Files.exists(file)?JSON.fromJson(Files.readString(file),Store.class):new Store();
            if(store==null||store.players==null)throw new IllegalStateException("Invalid mount store");
            var ids=new HashSet<String>();
            for(var entry:store.players.entrySet()){
                UUID.fromString(entry.getKey());var a=entry.getValue();
                if(a==null||a.gold<0||a.owned==null)throw new IllegalStateException("Invalid account");
                for(var m:a.owned.entrySet()){
                    if(definition(m.getKey())==null||!ids.add(UUID.fromString(m.getValue()).toString()))throw new IllegalStateException("Invalid mount ownership");
                }
            }
            failed=false;
        }catch(Exception ex){LOG.error("MYTHOS Mounts storage unavailable; mounting locked",ex);}
    }
    private boolean save(){
        if(failed||file==null)return false;
        try{
            var temp=file.resolveSibling(file.getFileName()+".tmp");
            Files.writeString(temp,JSON.toJson(store));
            Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
            return true;
        }catch(Exception ex){failed=true;LOG.error("MYTHOS Mounts save failed; transactions locked",ex);return false;}
    }
    private Account account(UUID id){return store.players.computeIfAbsent(id.toString(),k->new Account());}
    private boolean owns(UUID owner,String id){var a=store.players.get(owner.toString());return !failed&&(a!=null&&a.owned.containsKey(id)||EconomyApi.ownsCreature(owner,"mount:"+id));}
    private Active registered(Entity entity){
        for(var a:active.values())if(a.entity==entity&&owns(a.owner,a.id))return a;
        return null;
    }
    private void stop(ServerStoppingEvent e){for(var id:List.copyOf(active.keySet()))dismiss(id);active.clear();}
    private void dismiss(UUID id){var a=active.remove(id);if(a!=null){a.entity.ejectPassengers();a.entity.discard();}}
    private void logout(PlayerEvent.PlayerLoggedOutEvent e){dismiss(e.getEntity().getUUID());}
    private void dimension(PlayerEvent.PlayerChangedDimensionEvent e){dismiss(e.getEntity().getUUID());}
    private void join(EntityJoinLevelEvent e){
        if(e.getLevel().isClientSide()||!e.getEntity().getTags().contains(TAG))return;
        // Only a live summon created by this process may join. Saved or copied entities are invalid.
        if(registered(e.getEntity())==null)e.setCanceled(true);
    }
    private void mount(EntityMountEvent e){
        if(e.getLevel().isClientSide()||!e.isMounting()||!(e.getEntityMounting() instanceof ServerPlayer p))return;
        Entity target=e.getEntityBeingMounted();
        // Preserve vehicles, not living mounts. Deny every other living entity by default.
        if(target instanceof LivingEntity){
            var a=registered(target);
            if(a==null||!a.owner.equals(p.getUUID())||!target.getPassengers().isEmpty())e.setCanceled(true);
        }
    }
    private void interact(PlayerInteractEvent.EntityInteract e){
        if(!(e.getEntity() instanceof ServerPlayer p))return;
        var target=e.getTarget();
        if(target.getTags().contains(TAG)){
            e.setCanceled(true);
            var a=registered(target);
            if(a!=null&&a.owner.equals(p.getUUID())&&target.getPassengers().isEmpty()&&!p.isShiftKeyDown())p.startRiding(target,true);
            else if(a==null||!a.owner.equals(p.getUUID()))p.sendSystemMessage(Component.literal("You do not own this mount."));
        }else if(target instanceof AbstractHorse||target instanceof Pig||target instanceof Strider){
            // No vanilla taming, saddling, chest access or mounting through this interaction.
            e.setCanceled(true);
        }
    }
    private void tick(TickEvent.ServerTickEvent.Post e){
        for(var id:List.copyOf(active.keySet())){
            var a=active.get(id);var p=e.getServer().getPlayerList().getPlayer(id);
            if(failed||p==null||!p.isAlive()||!a.entity.isAlive()||a.entity.level()!=p.level()||!owns(id,a.id)){dismiss(id);continue;}
            for(var passenger:List.copyOf(a.entity.getPassengers()))if(passenger!=p)passenger.stopRiding();
            var d=definition(a.id);
            var mode=movement(d);
            if(mode!=Movement.NATIVE&&p.getVehicle()==a.entity){
                a.entity.setYRot(p.getYRot());
                boolean travel=a.entity.getTags().contains("mythos_mount_travel");
                Vec3 look=p.getLookAngle();
                Vec3 delta=mode==Movement.GROUND?new Vec3(look.x,0,look.z).normalize().scale(d.speed):look.normalize().scale(d.speed);
                if(!travel)delta=Vec3.ZERO;
                if(mode==Movement.GROUND){
                    var v=a.entity.getDeltaMovement();
                    a.entity.setDeltaMovement(delta.x,v.y,delta.z);
                }else{
                    var level=p.serverLevel();
                    var sweep=a.entity.getBoundingBox().expandTowards(delta);
                    boolean safe=level.getWorldBorder().isWithinBounds(sweep)&&!level.isOutsideBuildHeight(BlockPos.containing(sweep.minX,sweep.minY,sweep.minZ))&&!level.isOutsideBuildHeight(BlockPos.containing(sweep.maxX,sweep.maxY,sweep.maxZ));
                    for(int x=((int)Math.floor(sweep.minX))>>4;x<=((int)Math.floor(sweep.maxX))>>4;x++)for(int z=((int)Math.floor(sweep.minZ))>>4;z<=((int)Math.floor(sweep.maxZ))>>4;z++)if(!level.hasChunkAt(new BlockPos(x<<4,a.entity.blockPosition().getY(),z<<4)))safe=false;
                    if(safe&&mode==Movement.WATER&&!level.getFluidState(BlockPos.containing(a.entity.position().add(delta))).is(net.minecraft.tags.FluidTags.WATER))safe=false;
                    a.entity.setDeltaMovement(Vec3.ZERO);
                    if(safe&&level.noCollision(a.entity,sweep))a.entity.move(MoverType.SELF,delta);
                }
            }else{
                a.entity.removeTag("mythos_mount_travel");
                if(mode==Movement.FLIGHT||mode==Movement.WATER)a.entity.setDeltaMovement(Vec3.ZERO);
            }
        }
    }
    private int fail(CommandSourceStack s,String message){s.sendFailure(Component.literal(message));return 0;}
    private int ok(CommandSourceStack s,String message){s.sendSuccess(()->Component.literal(message),false);return 1;}
    private int reward(CommandSourceStack s,ServerPlayer p,String id,String source){
        var d=definition(id);
        if(failed||d==null||movement(d)==Movement.LOCKED||(d.source==Source.SHOP||d.source==Source.PREMIUM)||!d.source.name().equalsIgnoreCase(source))return fail(s,"Acquisition source does not match mount definition.");
        var a=account(p.getUUID());if(owns(p.getUUID(),id))return fail(s,"Already owned.");
        a.owned.put(id,UUID.randomUUID().toString());
        if(!save()){a.owned.remove(id);return fail(s,"Reward failed.");}return ok(s,"Granted "+d.name+" to "+p.getScoreboardName());
    }
    private int summon(CommandSourceStack s,String id)throws com.mojang.brigadier.exceptions.CommandSyntaxException{
        var p=s.getPlayerOrException();var d=definition(id);
        if(d==null||movement(d)==Movement.LOCKED||!owns(p.getUUID(),id)||p.isSpectator()||!p.isAlive())return fail(s,"Mount unavailable or not owned.");
        ServerLevel level=p.serverLevel();
        var type=BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.withDefaultNamespace(d.entity));
        var entity=type.create(level);
        if(!(entity instanceof Mob mob))return fail(s,"Unsupported mount entity.");
        var size=mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.SCALE);
        if(size!=null)size.setBaseValue(scale(d));
        mob.refreshDimensions();
        var mode=movement(d);
        boolean found=false;
        for(int radius=2;radius<=5&&!found;radius++)for(int x=-radius;x<=radius&&!found;x++)for(int z=-radius;z<=radius&&!found;z++){
            if(Math.max(Math.abs(x),Math.abs(z))!=radius)continue;
            var pos=p.blockPosition().offset(x,0,z);
            if(!level.hasChunkAt(pos)||!level.hasChunkAt(pos.below()))continue;
            if(mode==Movement.WATER){
                if(!level.getFluidState(pos).is(net.minecraft.tags.FluidTags.WATER))continue;
            }else if(!level.getBlockState(pos.below()).isFaceSturdy(level,pos.below(),net.minecraft.core.Direction.UP))continue;
            mob.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5,p.getYRot(),0);
            var box=mob.getBoundingBox();
            if(!level.getWorldBorder().isWithinBounds(box)||level.isOutsideBuildHeight(BlockPos.containing(box.maxX,box.maxY,box.maxZ))||!level.noCollision(mob,box)||!level.getEntities(mob,box).isEmpty())continue;
            if(mode!=Movement.WATER&&!level.getFluidState(pos).isEmpty())continue;
            found=true;
        }
        if(!found)return fail(s,"No safe nearby mount spawn.");
        dismiss(p.getUUID());
        mob.addTag(TAG);mob.setCustomName(Component.literal(d.name));mob.setCustomNameVisible(true);
        mob.setPersistenceRequired();mob.setNoAi(true);mob.setInvulnerable(true);mob.setSilent(true);mob.setNoGravity(mode==Movement.FLIGHT||mode==Movement.WATER);
        var speed=mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        if(speed!=null)speed.setBaseValue(d.speed);
        if(mob instanceof AbstractHorse horse){horse.setTamed(true);}
        // Native horse appearance and equipment are synchronized to unmodified clients.
        if(d.id.equals("royal_warhorse") && mob instanceof net.minecraft.world.entity.animal.horse.Horse royal){
            royal.setVariantAndMarkings(net.minecraft.world.entity.animal.horse.Variant.BLACK,
                net.minecraft.world.entity.animal.horse.Markings.NONE);
            royal.setBodyArmorItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GOLDEN_HORSE_ARMOR));
            royal.setCustomName(Component.literal(d.name).withStyle(net.minecraft.ChatFormatting.GOLD,net.minecraft.ChatFormatting.BOLD));
        }
        // Native horses can be mounted with force but require saddle equipment for steering.
        if(mob instanceof net.minecraft.world.entity.Saddleable saddle&&d.nativeControl)saddle.equipSaddle(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SADDLE),net.minecraft.sounds.SoundSource.NEUTRAL);
        active.put(p.getUUID(),new Active(p.getUUID(),id,mob));
        if(!level.addFreshEntity(mob)){active.remove(p.getUUID());mob.discard();return fail(s,"Summon failed.");}
        return ok(s,"Summoned "+d.name+(d.nativeControl?". Right-click to ride.":". Right-click to ride; /mount travel toggles look-directed movement."));
    }
    private int travel(CommandSourceStack s)throws com.mojang.brigadier.exceptions.CommandSyntaxException{
        var p=s.getPlayerOrException();var a=active.get(p.getUUID());
        if(a==null||p.getVehicle()!=a.entity||definition(a.id).nativeControl)return fail(s,"Ride a look-controlled mount first.");
        boolean enabled=!a.entity.getTags().contains("mythos_mount_travel");
        if(enabled)a.entity.addTag("mythos_mount_travel");else a.entity.removeTag("mythos_mount_travel");
        return ok(s,"Mount travel "+(enabled?"enabled":"disabled"));
    }
    private int list(CommandSourceStack s,boolean shop)throws com.mojang.brigadier.exceptions.CommandSyntaxException{
        var p=s.getPlayerOrException();if(failed)return fail(s,"Storage locked.");var a=account(p.getUUID());
        s.sendSuccess(()->Component.literal("MYTHOS Mounts | Visit a Stable Master for prices"),false);
        for(var d:CATALOG)if(shop?d.source==Source.SHOP&&movement(d)!=Movement.LOCKED:owns(p.getUUID(),d.id))
            s.sendSuccess(()->Component.literal(d.id+" — "+d.name+(" | "+d.source)),false);
        return 1;
    }
    private int premium(CommandSourceStack s,ServerPlayer p,String id,boolean grant){
        var d=definition(id);if(failed||d==null||d.source!=Source.PREMIUM||movement(d)==Movement.LOCKED)return fail(s,"Not an available premium mount");
        var a=account(p.getUUID());String old=a.owned.get(id);
        if(grant){if(old!=null)return fail(s,"Already owned");a.owned.put(id,UUID.randomUUID().toString());}
        else{if(old==null)return fail(s,"Not owned");a.owned.remove(id);}
        if(!save()){if(old==null)a.owned.remove(id);else a.owned.put(id,old);return fail(s,"Storage failed");}
        var live=active.get(p.getUUID());if(!grant&&live!=null&&live.id.equals(id))dismiss(p.getUUID());
        LOG.info("Premium mount {} actor={} player={} id={}",grant?"grant":"revoke",s.getTextName(),p.getUUID(),id);
        return ok(s,(grant?"Granted ":"Revoked ")+d.name);
    }
    private void commands(RegisterCommandsEvent e){
        var root=Commands.literal("mount").executes(c->list(c.getSource(),false));
        root.then(Commands.literal("list").executes(c->list(c.getSource(),false)));
        root.then(Commands.literal("shop").executes(c->list(c.getSource(),true)));
        root.then(Commands.literal("summon").then(Commands.argument("id",StringArgumentType.word()).executes(c->summon(c.getSource(),StringArgumentType.getString(c,"id")))));
        root.then(Commands.literal("dismiss").executes(c->{dismiss(c.getSource().getPlayerOrException().getUUID());return ok(c.getSource(),"Mount dismissed.");}));
        root.then(Commands.literal("travel").executes(c->travel(c.getSource())));
        root.then(Commands.literal("catalog").executes(c->{for(var d:CATALOG)c.getSource().sendSuccess(()->Component.literal(d.id+" | "+d.name+" | "+d.source+" | "+movement(d).name()),false);return 1;}));
        root.then(Commands.literal("reward").requires(s->s.hasPermission(2)).then(Commands.argument("player",EntityArgument.player()).then(Commands.argument("id",StringArgumentType.word()).then(Commands.argument("source",StringArgumentType.word()).executes(c->reward(c.getSource(),EntityArgument.getPlayer(c,"player"),StringArgumentType.getString(c,"id"),StringArgumentType.getString(c,"source")))))));
        var premiumRoot=Commands.literal("premium").requires(source->source.hasPermission(4));
        for(String action:List.of("grant","revoke")){
            var idArg=Commands.argument("id",StringArgumentType.word()).executes(c->premium(c.getSource(),EntityArgument.getPlayer(c,"player"),StringArgumentType.getString(c,"id"),action.equals("grant")));
            premiumRoot.then(Commands.literal(action).then(Commands.argument("player",EntityArgument.player()).then(idArg)));
        }
        premiumRoot.then(Commands.literal("catalog").executes(c->ok(c.getSource(),String.join(", ",CATALOG.stream().filter(d->d.source==Source.PREMIUM).map(d->d.id).toList()))));
        premiumRoot.then(Commands.literal("list").then(Commands.argument("player",EntityArgument.player()).executes(c->{var target=EntityArgument.getPlayer(c,"player");return ok(c.getSource(),String.join(", ",CATALOG.stream().filter(d->d.source==Source.PREMIUM&&owns(target.getUUID(),d.id)).map(d->d.id).toList()));})));
        root.then(premiumRoot);
        e.getDispatcher().register(root);
    }
}
