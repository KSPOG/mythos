package com.ksp.mythospets;

import com.google.gson.*;
import com.ksp.mythoseconomy.EconomyApi;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import java.nio.file.*;
import java.util.*;

final class PetServer {
    private static final String TAG="mythos_pet";
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private static final org.slf4j.Logger LOG=com.mojang.logging.LogUtils.getLogger();
    private enum Source { SHOP, QUEST, DUNGEON, RAID, WORLD_BOSS, ACHIEVEMENT, CLAN, EVENT, PREMIUM }
    private enum Ability { STRIKE, VENOM, SLOW, WEAKEN, HEAL, SHIELD, HASTE, LUCK, BURN, SCOUT }
    private record Definition(String id,String name,String mob,Source source,int price,Ability ability,int cooldown,float power,boolean locked){}
    private static final List<Definition> CATALOG=List.of(
        new Definition("ember_dragon","Ember Dragon","blaze",Source.PREMIUM,0,Ability.BURN,120,3,false),
        new Definition("premium_mooncat","Celestial Mooncat","cat",Source.PREMIUM,0,Ability.HASTE,240,0,false),
        new Definition("premium_sunbird","Sunbird","parrot",Source.PREMIUM,0,Ability.HASTE,240,0,false),
        new Definition("wolf","Dire Wolf","wolf",Source.QUEST,0,Ability.STRIKE,60,4,false),
        new Definition("swift_fox","Swift Fox","fox",Source.SHOP,20000,Ability.STRIKE,80,6,false),
        new Definition("jungle_stalker","Jungle Stalker","ocelot",Source.SHOP,22000,Ability.STRIKE,80,5,false),
        new Definition("bumblebee","Royal Bumblebee","bee",Source.SHOP,45000,Ability.VENOM,120,2,false),
        new Definition("twilight_bat","Twilight Bat","bat",Source.SHOP,35000,Ability.SCOUT,200,0,true),
        new Definition("tropical_roc","Tropical Companion","parrot",Source.SHOP,50000,Ability.HASTE,240,0,false),
        new Definition("celestial_sprite","Celestial Sprite","allay",Source.ACHIEVEMENT,0,Ability.HEAL,200,4,false),
        new Definition("wraith","Spectral Wraith","vex",Source.QUEST,0,Ability.STRIKE,100,6,true),
        new Definition("burrower","Burrower","silverfish",Source.DUNGEON,0,Ability.WEAKEN,120,2,false),
        new Definition("armored_roller","Armored Roller","armadillo",Source.SHOP,15000,Ability.SHIELD,240,0,false),
        new Definition("marsh_hopper","Marsh Hopper","frog",Source.QUEST,0,Ability.SLOW,120,2,false),
        new Definition("giant_hare","Swift Hare","rabbit",Source.SHOP,12000,Ability.HASTE,240,0,false),
        new Definition("coral_dragon","Coral Companion","axolotl",Source.QUEST,0,Ability.HEAL,200,3,false),
        new Definition("arctic_golem","Arctic Golem","snow_golem",Source.EVENT,0,Ability.SLOW,100,3,true),
        new Definition("slime","Slime Companion","slime",Source.EVENT,0,Ability.SHIELD,240,0,false),
        new Definition("magma_cube","Magma Companion","magma_cube",Source.DUNGEON,0,Ability.BURN,120,3,false),
        new Definition("royal_fowl","Royal Fowl","chicken",Source.SHOP,10000,Ability.LUCK,240,0,false),
        new Definition("sheep","Woolly Companion","sheep",Source.SHOP,3500,Ability.SHIELD,240,0,false),
        new Definition("cat","Mooncat","cat",Source.SHOP,18000,Ability.HASTE,240,0,false),
        new Definition("blaze","Ember Familiar","blaze",Source.DUNGEON,0,Ability.BURN,100,3,false),
        new Definition("breeze","Storm Familiar","breeze",Source.DUNGEON,0,Ability.SLOW,100,3,false)
    );
    private static String description(Definition d){
        return switch(d.id){
            case "ember_dragon" -> "Flame Breath (6 damage, 6s cooldown) | Ember Guard (5s Resistance I + Fire Resistance at <=70% health, 12s cooldown)";
            case "premium_mooncat" -> "Lunar Pounce (4 damage, Slowness I, 5s cooldown) | Moon Blessing (heal 4 + 5s Speed I at <=70% health, 12s cooldown)";
            default -> d.ability.name()+" | cooldown "+d.cooldown+" ticks";
        };
    }
    private static Definition def(String id){return CATALOG.stream().filter(d->d.id.equals(id)).findFirst().orElse(null);}
    private static class Account { long gold; Map<String,String> owned=new LinkedHashMap<>(); Map<String,String> names=new HashMap<>(); }
    private static class Store { Map<String,Account> accounts=new HashMap<>(); }
    private static final class Active {
        final UUID owner; final Definition definition; final Mob entity;
        UUID target; long targetUntil,nextAbility;
        UUID castTarget; int cast; long impactAt,finishAt,nextSupport; boolean impacted;
        Active(UUID owner,Definition definition,Mob entity){this.owner=owner;this.definition=definition;this.entity=entity;}
    }
    private Store store=new Store();
    private final Map<UUID,Long> combatLock=new HashMap<>();
    private static final int BREATH=1, GUARD=2, POUNCE=3, BLESS=4;
    private Path file; private boolean failed=true; private long tick;
    private final Map<UUID,Active> active=new HashMap<>();
    PetServer(){
        EconomyApi.registerCreatures("pet",new EconomyApi.CreatureProvider(){
            public boolean available(){return !failed;}
            public boolean legacyOwns(UUID player,String key){
                var a=store.accounts.get(player.toString());
                return key.startsWith("pet:")&&a!=null&&a.owned.containsKey(key.substring(4));
            }
            public List<EconomyApi.Creature> catalog(){
                return CATALOG.stream().filter(d->!d.locked).map(d->new EconomyApi.Creature("pet:"+d.id,d.name,description(d),d.source==Source.SHOP)).toList();
            }
        });
        var b=MinecraftForge.EVENT_BUS;
        b.addListener(this::start);b.addListener(this::stop);b.addListener(this::commands);
        b.addListener(this::join);b.addListener(this::mount);b.addListener(this::attack);
        b.addListener(this::death);b.addListener(this::logout);b.addListener(this::dimension);b.addListener(this::tick);
    }
    private void start(ServerStartedEvent e){
        file=e.getServer().getWorldPath(LevelResource.ROOT).resolve("mythos-pets.json");
        active.clear();combatLock.clear();failed=true;tick=0;
        try{
            store=Files.exists(file)?JSON.fromJson(Files.readString(file),Store.class):new Store();
            if(store==null||store.accounts==null)throw new IllegalStateException("Invalid store");
            var ids=new HashSet<String>();
            for(var entry:store.accounts.entrySet()){
                UUID.fromString(entry.getKey());var a=entry.getValue();
                if(a==null||a.gold<0||a.owned==null)throw new IllegalStateException("Invalid account");
                if(a.names==null)a.names=new HashMap<>();
                for(var name:a.names.entrySet())if(def(name.getKey())==null||!validName(name.getValue()))throw new IllegalStateException("Invalid pet name");
                for(var pet:a.owned.entrySet())if(def(pet.getKey())==null||!ids.add(UUID.fromString(pet.getValue()).toString()))throw new IllegalStateException("Invalid ownership");
            }
            failed=false;
        }catch(Exception ex){LOG.error("Pet storage locked",ex);}
    }
    private boolean save(){
        if(failed)return false;
        try{
            var temp=file.resolveSibling(file.getFileName()+".tmp");
            Files.writeString(temp,JSON.toJson(store));Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);return true;
        }catch(Exception ex){failed=true;LOG.error("Pet storage locked",ex);return false;}
    }
    private Account account(UUID id){return store.accounts.computeIfAbsent(id.toString(),k->new Account());}
    private boolean owns(UUID id,String pet){var a=store.accounts.get(id.toString());return !failed&&(a!=null&&a.owned.containsKey(pet)||EconomyApi.ownsCreature(id,"pet:"+pet));}
    private void dismiss(UUID id){var a=active.remove(id);if(a!=null)a.entity.discard();}
    private void stop(ServerStoppingEvent e){for(var id:List.copyOf(active.keySet()))dismiss(id);}
    private void logout(PlayerEvent.PlayerLoggedOutEvent e){dismiss(e.getEntity().getUUID());}
    private void dimension(PlayerEvent.PlayerChangedDimensionEvent e){dismiss(e.getEntity().getUUID());}
    private void death(LivingDeathEvent e){
        if(e.getEntity() instanceof ServerPlayer p)dismiss(p.getUUID());
    }
    private void join(EntityJoinLevelEvent e){
        if(!e.getLevel().isClientSide()&&(e.getEntity().getTags().contains(TAG)||e.getEntity() instanceof MooncatEntity||e.getEntity() instanceof DragonEntity)&&active.values().stream().noneMatch(a->a.entity==e.getEntity()&&owns(a.owner,a.definition.id)))e.setCanceled(true);
    }
    private void mount(EntityMountEvent e){if(!e.getLevel().isClientSide()&&e.isMounting()&&e.getEntityBeingMounted().getTags().contains(TAG))e.setCanceled(true);}
    private static boolean eligible(ServerPlayer owner,LivingEntity target){
        return target instanceof Monster&&target.isAlive()&&target.level()==owner.level()&&!target.getTags().contains(TAG)&&!target.getTags().contains("mythos_mount")&&!target.isAlliedTo(owner)&&!owner.isAlliedTo(target);
    }
    private void attack(LivingAttackEvent e){
        if(e.getEntity().level().isClientSide())return;
        // Pet controller never accepts arbitrary PvP targets. Only owner attacks or hostile attacks against owner.
        if(e.getSource().getEntity() instanceof ServerPlayer p&&eligible(p,e.getEntity())){
            var a=active.get(p.getUUID());if(a!=null){a.target=e.getEntity().getUUID();a.targetUntil=tick+200;}
        }else if(e.getEntity() instanceof ServerPlayer p&&e.getSource().getEntity() instanceof LivingEntity target&&eligible(p,target)){
            var a=active.get(p.getUUID());if(a!=null){a.target=target.getUUID();a.targetUntil=tick+200;}
        }
    }
    private static boolean safe(ServerLevel level,Mob mob,Vec3 feet){
        var box=mob.getBoundingBox().move(feet.subtract(mob.position()));
        if(!level.getWorldBorder().isWithinBounds(box)||level.isOutsideBuildHeight(BlockPos.containing(box.minX,box.minY,box.minZ))||level.isOutsideBuildHeight(BlockPos.containing(box.maxX,box.maxY,box.maxZ)))return false;
        for(int x=((int)Math.floor(box.minX))>>4;x<=((int)Math.floor(box.maxX))>>4;x++)for(int z=((int)Math.floor(box.minZ))>>4;z<=((int)Math.floor(box.maxZ))>>4;z++)if(!level.hasChunkAt(new BlockPos(x<<4,(int)feet.y,z<<4)))return false;
        return level.noCollision(mob,box)&&level.getEntities(mob,box).isEmpty();
    }
    private static Vec3 nearby(ServerPlayer p,Mob mob){
        for(int dy:new int[]{0,1,-1,2})for(int r=1;r<=4;r++)for(int x=-r;x<=r;x++)for(int z=-r;z<=r;z++){
            if(Math.max(Math.abs(x),Math.abs(z))!=r)continue;
            var pos=p.blockPosition().offset(x,dy,z);
            var feet=Vec3.atBottomCenterOf(pos);
            if(safe(p.serverLevel(),mob,feet)&&p.serverLevel().getFluidState(pos).isEmpty())return feet;
        }
        return null;
    }
    /** Minecraft yaw: +Z = 0, -X = +90, -Z = 180, +X = -90. */
    private static void face(Mob mob,Vec3 direction){
        if(direction.x*direction.x+direction.z*direction.z<1.0e-8)return;
        float yaw=(float)Math.toDegrees(Math.atan2(-direction.x,direction.z));
        mob.setYRot(yaw);
        mob.setYBodyRot(yaw);
        mob.setYHeadRot(yaw);
        mob.setXRot(0);
    }

    private void tick(TickEvent.ServerTickEvent.Post e){
        tick++;
        for(var id:List.copyOf(active.keySet())){
            var a=active.get(id);var p=e.getServer().getPlayerList().getPlayer(id);
            if(p==null||!p.isAlive()||p.isSpectator()||!a.entity.isAlive()||a.entity.level()!=p.level()||!owns(id,a.definition.id)){dismiss(id);continue;}
            var mob=a.entity;mob.setDeltaMovement(Vec3.ZERO);
            boolean custom=mob instanceof DragonEntity||mob instanceof MooncatEntity;
            if(custom&&combat(p,a))continue;
            if(mob.distanceToSqr(p)>9){
                var delta=p.position().subtract(mob.position());
                // Preserve the old nominal speed (0.65 blocks per five ticks),
                // but distribute movement over individual ticks.
                var step=delta.normalize().scale(Math.min(.13,Math.max(0,delta.length()-3)));
                if(delta.lengthSqr()>144||!safe(p.serverLevel(),mob,mob.position().add(step))){
                    // Keep catch-up teleports on the original five-tick cadence.
                    if(tick%5==0){
                        var feet=nearby(p,mob);
                        if(feet!=null){
                            mob.teleportTo(feet.x,feet.y,feet.z);
                            face(mob,p.position().subtract(mob.position()));
                        }
                    }
                }else{
                    var before=mob.position();
                    face(mob,step);
                    mob.move(MoverType.SELF,step);
                    // Collision resolution can change the real movement vector.
                    face(mob,mob.position().subtract(before));
                }
            }
            if(!custom&&tick>=a.nextAbility)ability(p,a);
        }
    }
    private LivingEntity engagedTarget(ServerPlayer p,Active a,UUID id){
        if(id==null||tick>a.targetUntil)return null;
        var found=p.serverLevel().getEntity(id);
        return found instanceof LivingEntity l&&eligible(p,l)?l:null;
    }
    private LivingEntity combatTarget(ServerPlayer p,Active a,UUID id,double range){
        LivingEntity target=engagedTarget(p,a,id);
        return target!=null&&target.distanceToSqr(a.entity)<=range*range
            &&a.entity.hasLineOfSight(target)?target:null;
    }
    private void finishCast(Active a){
        a.cast=0;a.castTarget=null;a.impacted=false;
        if(a.entity instanceof DragonEntity d)d.endCombat();
        if(a.entity instanceof MooncatEntity m)m.endCombat();
    }
    private void startCast(Active a,LivingEntity target,int action,int windup,int duration){
        a.cast=action;a.castTarget=target.getUUID();a.impactAt=tick+windup;a.finishAt=tick+duration;a.impacted=false;
        combatLock.merge(a.owner,Math.max(a.nextAbility,a.nextSupport),Math::max);
        face(a.entity,target.position().subtract(a.entity.position()));
        if(a.entity instanceof DragonEntity d)d.beginCombat(action==BREATH?DragonEntity.BREATH:DragonEntity.GUARD);
        if(a.entity instanceof MooncatEntity m)m.beginCombat(action==POUNCE?MooncatEntity.POUNCE:MooncatEntity.BLESS);
    }
    /** Exclusive cast lifecycle: target validated at windup and again at impact. */
    private boolean combat(ServerPlayer p,Active a){
        boolean dragon=a.entity instanceof DragonEntity;
        if(a.cast!=0){
            boolean support=a.cast==GUARD||a.cast==BLESS;
            LivingEntity target=combatTarget(p,a,a.castTarget,support?8:dragon?6:2.6);
            if(!a.impacted&&target==null){
                finishCast(a);return false;
            }
            if(tick>=a.finishAt){finishCast(a);return false;}
            if(target!=null)face(a.entity,target.position().subtract(a.entity.position()));
            if(!a.impacted&&tick>=a.impactAt){
                a.impacted=true;
                if(target!=null)impact(p,a,target);
            }
            return true;
        }
        LivingEntity target=engagedTarget(p,a,a.target);
        if(target==null)return false;
        face(a.entity,target.position().subtract(a.entity.position()));
        // Support retains its local casting requirements, but owner distance
        // is irrelevant once the owner has established a valid engagement.
        if(tick>=a.nextSupport&&p.getHealth()<=p.getMaxHealth()*0.70F
            &&combatTarget(p,a,a.target,8)!=null){
            a.nextSupport=tick+240;
            startCast(a,target,dragon?GUARD:BLESS,10,24);return true;
        }
        double range=dragon?6:2.6;
        if(a.entity.distanceToSqr(target)>range*range){
            // Pursue the engaged target, never the owner, while combat is active.
            var delta=target.position().subtract(a.entity.position());
            var step=delta.normalize().scale(Math.min(0.16,Math.max(0,delta.length()-range+0.15)));
            var dest=a.entity.position().add(step);
            if(safe(p.serverLevel(),a.entity,dest)){
                var before=a.entity.position();a.entity.move(MoverType.SELF,step);
                face(a.entity,a.entity.position().subtract(before));
            }
            return true;
        }
        // Remain with the enemy through cooldowns; no owner-follow teleport.
        if(tick<a.nextAbility||!a.entity.hasLineOfSight(target))return true;
        a.nextAbility=tick+(dragon?120:100);
        startCast(a,target,dragon?BREATH:POUNCE,dragon?12:8,dragon?30:20);
        return true;
    }
    private void impact(ServerPlayer p,Active a,LivingEntity target){
        var level=p.serverLevel();
        switch(a.cast){
            case BREATH -> {
                if(target.hurt(level.damageSources().mobAttack(a.entity),6))target.igniteForSeconds(3);
                Vec3 from=a.entity.getEyePosition(),to=target.getEyePosition();
                for(int i=1;i<=12;i++){
                    Vec3 q=from.lerp(to,i/12.0);
                    level.sendParticles(ParticleTypes.FLAME,q.x,q.y,q.z,3,0.10,0.10,0.10,0.015);
                }
                level.playSound(null,a.entity.getX(),a.entity.getY(),a.entity.getZ(),SoundEvents.BLAZE_SHOOT,SoundSource.NEUTRAL,0.75F,0.8F);
            }
            case POUNCE -> {
                if(target.hurt(level.damageSources().mobAttack(a.entity),4))
                    target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,60,0));
                level.sendParticles(ParticleTypes.CRIT,target.getX(),target.getY()+0.7,target.getZ(),12,0.25,0.25,0.25,0.1);
                level.playSound(null,a.entity.getX(),a.entity.getY(),a.entity.getZ(),SoundEvents.CAT_HISS,SoundSource.NEUTRAL,0.6F,1.1F);
            }
            case GUARD -> {
                p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,100,0));
                p.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE,100,0));
                level.sendParticles(ParticleTypes.FLAME,p.getX(),p.getY()+1,p.getZ(),24,0.45,0.6,0.45,0.01);
            }
            case BLESS -> {
                p.heal(4);
                p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED,100,0));
                level.sendParticles(ParticleTypes.END_ROD,p.getX(),p.getY()+1,p.getZ(),24,0.45,0.6,0.45,0.025);
                level.playSound(null,p.getX(),p.getY(),p.getZ(),SoundEvents.CAT_PURR,SoundSource.NEUTRAL,0.7F,1.1F);
            }
            default -> {}
        }
    }
    private void ability(ServerPlayer p,Active a){
        var d=a.definition;var level=p.serverLevel();
        var entity=a.target==null||tick>a.targetUntil?null:level.getEntity(a.target);
        LivingEntity target=entity instanceof LivingEntity l&&eligible(p,l)&&l.distanceToSqr(a.entity)<=64&&a.entity.hasLineOfSight(l)?l:null;
        // Pet-attributed damage; no direct player damage.
        boolean used=false;
        switch(d.ability){
            case HEAL -> {if(p.getHealth()<p.getMaxHealth()){p.heal(d.power);used=true;}}
            case SHIELD -> {if(target!=null){p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,80,0));used=true;}}
            case HASTE -> {if(target!=null){p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED,100,0));used=true;}}
            case LUCK -> {if(target!=null){p.addEffect(new MobEffectInstance(MobEffects.LUCK,100,0));used=true;}}
            case SCOUT -> {}
            default -> {
                if(target!=null){
                    // Pet damage does not renew the owner's engagement window.
                    used=target.hurt(level.damageSources().mobAttack(a.entity),d.power);
                    if(used)switch(d.ability){
                        case VENOM -> target.addEffect(new MobEffectInstance(MobEffects.POISON,60,0));
                        case SLOW -> target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,60,0));
                        case WEAKEN -> target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,60,0));
                        case BURN -> target.igniteForSeconds(3);
                        default -> {}
                    }
                }
            }
        }
        if(used)a.nextAbility=tick+d.cooldown;else a.nextAbility=tick+10;
    }
    private int fail(CommandSourceStack s,String m){s.sendFailure(Component.literal(m));return 0;}
    private int ok(CommandSourceStack s,String m){s.sendSuccess(()->Component.literal(m),false);return 1;}
    private int acquire(CommandSourceStack s,ServerPlayer p,String id,String source){
        var d=def(id);if(failed||d==null||d.locked||d.source==Source.SHOP||d.source==Source.PREMIUM||!d.source.name().equalsIgnoreCase(source))return fail(s,"Pet unavailable from this source.");
        var a=account(p.getUUID());if(owns(p.getUUID(),id))return fail(s,"Already owned.");
        a.owned.put(id,UUID.randomUUID().toString());
        if(!save()){a.owned.remove(id);return fail(s,"Acquisition failed.");}
        return ok(s,"Unlocked "+d.name);
    }
    private static boolean validName(String name){
        return name!=null&&name.equals(name.strip())&&!name.isEmpty()&&name.codePointCount(0,name.length())<=24&&name.codePoints().allMatch(c->Character.isLetterOrDigit(c)||c==' '||c=='-'||c=='_'||c=='\'');
    }
    private String petName(UUID owner,Definition d){
        return account(owner).names.getOrDefault(d.id,d.name);
    }
    private int rename(CommandSourceStack s,String id,String requested)throws com.mojang.brigadier.exceptions.CommandSyntaxException{
        var p=s.getPlayerOrException();var d=def(id);
        if(d==null||!owns(p.getUUID(),id))return fail(s,"Pet not owned.");
        if(requested!=null&&!validName(requested))return fail(s,"Use 1–24 letters, digits, spaces, apostrophes, underscores or hyphens.");
        var a=account(p.getUUID());String old=a.names.get(id);
        if(requested==null)a.names.remove(id);else a.names.put(id,requested);
        if(!save()){if(old==null)a.names.remove(id);else a.names.put(id,old);return fail(s,"Name change failed.");}
        var live=active.get(p.getUUID());
        String name=petName(p.getUUID(),d);
        if(live!=null&&live.definition.id.equals(id))live.entity.setCustomName(Component.literal(name));
        return ok(s,"Pet "+id+" named "+name+".");
    }
    private int summon(CommandSourceStack s,String id)throws com.mojang.brigadier.exceptions.CommandSyntaxException{
        var p=s.getPlayerOrException();var d=def(id);
        if(d==null||d.locked||!owns(p.getUUID(),id)||!p.isAlive()||p.isSpectator())return fail(s,"Pet not available or owned.");
        var type=d.id.equals("ember_dragon") ? PetEntities.DRAGON.get() : d.id.equals("premium_mooncat") ? PetEntities.MOONCAT.get() : BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.withDefaultNamespace(d.mob));
        if(!(type.create(p.serverLevel()) instanceof Mob mob))return fail(s,"Invalid pet entity.");
        var feet=nearby(p,mob);if(feet==null)return fail(s,"No safe pet spawn nearby.");
        dismiss(p.getUUID());
        mob.moveTo(feet.x,feet.y,feet.z,p.getYRot(),0);face(mob,p.position().subtract(mob.position()));mob.setNoAi(true);mob.setNoGravity(true);mob.setInvulnerable(true);mob.setSilent(true);mob.setPersistenceRequired();
        mob.addTag(TAG);mob.setCustomName(Component.literal(petName(p.getUUID(),d)));mob.setCustomNameVisible(true);
        var a=new Active(p.getUUID(),d,mob);a.nextAbility=Math.max(tick+d.cooldown,combatLock.getOrDefault(p.getUUID(),0L));a.nextSupport=Math.max(tick+240,combatLock.getOrDefault(p.getUUID(),0L));active.put(p.getUUID(),a);
        if(!p.serverLevel().addFreshEntity(mob)){dismiss(p.getUUID());return fail(s,"Summon failed.");}
        return ok(s,"Summoned "+petName(p.getUUID(),d));
    }
    private int list(CommandSourceStack s,boolean shop,boolean catalog)throws com.mojang.brigadier.exceptions.CommandSyntaxException{
        var p=s.getPlayerOrException();if(failed)return fail(s,"Storage locked.");var a=account(p.getUUID());
        s.sendSuccess(()->Component.literal("MYTHOS Pets | Visit a Pet Merchant for current prices"),false);
        for(var d:CATALOG)if(catalog||(shop?d.source==Source.SHOP&&!d.locked:owns(p.getUUID(),d.id)))s.sendSuccess(()->Component.literal(d.id+" | "+(owns(p.getUUID(),d.id)?petName(p.getUUID(),d):d.name)+" | "+description(d)+" | "+d.source+(d.locked?" LOCKED":"")),false);
        return 1;
    }
    private int premium(CommandSourceStack s,ServerPlayer p,String id,boolean grant){
        var d=def(id);if(failed||d==null||d.source!=Source.PREMIUM||d.locked)return fail(s,"Not an available premium pet");
        var a=account(p.getUUID());String old=a.owned.get(id);
        if(grant){if(old!=null)return fail(s,"Already owned");a.owned.put(id,UUID.randomUUID().toString());}
        else{if(old==null)return fail(s,"Not owned");a.owned.remove(id);}
        String oldName=grant?null:a.names.remove(id);
        if(!save()){if(old==null)a.owned.remove(id);else a.owned.put(id,old);if(oldName!=null)a.names.put(id,oldName);return fail(s,"Storage failed");}
        var live=active.get(p.getUUID());if(!grant&&live!=null&&live.definition.id.equals(id))dismiss(p.getUUID());
        LOG.info("Premium pet {} actor={} player={} id={}",grant?"grant":"revoke",s.getTextName(),p.getUUID(),id);
        return ok(s,(grant?"Granted ":"Revoked ")+d.name);
    }
    private void commands(RegisterCommandsEvent e){
        var root=Commands.literal("pet").executes(c->list(c.getSource(),false,false));
        root.then(Commands.literal("list").executes(c->list(c.getSource(),false,false)));
        root.then(Commands.literal("shop").executes(c->list(c.getSource(),true,false)));
        root.then(Commands.literal("catalog").executes(c->list(c.getSource(),false,true)));
        root.then(Commands.literal("summon").then(Commands.argument("id",StringArgumentType.word()).executes(c->summon(c.getSource(),StringArgumentType.getString(c,"id")))));
        root.then(Commands.literal("rename").then(Commands.argument("id",StringArgumentType.word()).then(Commands.argument("name",StringArgumentType.greedyString()).executes(c->rename(c.getSource(),StringArgumentType.getString(c,"id"),StringArgumentType.getString(c,"name"))))));
        root.then(Commands.literal("resetname").then(Commands.argument("id",StringArgumentType.word()).executes(c->rename(c.getSource(),StringArgumentType.getString(c,"id"),null))));
        root.then(Commands.literal("dismiss").executes(c->{dismiss(c.getSource().getPlayerOrException().getUUID());return ok(c.getSource(),"Pet dismissed.");}));
        root.then(Commands.literal("reward").requires(s->s.hasPermission(2)).then(Commands.argument("player",EntityArgument.player()).then(Commands.argument("id",StringArgumentType.word()).then(Commands.argument("source",StringArgumentType.word()).executes(c->{String source=StringArgumentType.getString(c,"source");if(source.equalsIgnoreCase("SHOP"))return fail(c.getSource(),"Shop-exclusive pet.");return acquire(c.getSource(),EntityArgument.getPlayer(c,"player"),StringArgumentType.getString(c,"id"),source);})))));
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
