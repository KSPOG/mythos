package com.ksp.mythosmerchants;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.ksp.mythoseconomy.EconomyApi;
import com.ksp.mythoseconomy.Ledger;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.*;
import net.minecraft.world.item.trading.*;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

@Mod("mythosmerchants")
public final class Merchants {
    private static final String TAG="mythos_merchant";
    private static final String PREFIX="mythos_shop_";
    private static final int MAX_OFFERS=48;
    private static final int MAX_STOCK=1000000;
    private static final double RANGE_SQ=16;
    private record Product(Item item,int amount,int price){}
    private record Shop(String id,String title,VillagerProfession profession,List<Product> products){}
    private static final List<Shop> SHOPS=List.of(
        new Shop("general","General Merchant",VillagerProfession.NONE,List.of(new Product(Items.TORCH,16,2),new Product(Items.BUCKET,1,4),new Product(Items.LEAD,1,3))),
        new Shop("blacksmith","Blacksmith",VillagerProfession.WEAPONSMITH,List.of(new Product(Items.IRON_SWORD,1,12),new Product(Items.IRON_AXE,1,10),new Product(Items.SHIELD,1,8),new Product(Items.IRON_CHESTPLATE,1,18))),
        new Shop("armorer","Armorer",VillagerProfession.ARMORER,List.of(new Product(Items.IRON_HELMET,1,10),new Product(Items.IRON_LEGGINGS,1,16),new Product(Items.IRON_BOOTS,1,8))),
        new Shop("alchemist","Alchemist",VillagerProfession.CLERIC,List.of(new Product(Items.GLASS_BOTTLE,4,2),new Product(Items.NETHER_WART,4,5),new Product(Items.GLOWSTONE_DUST,4,6))),
        new Shop("food","Food Merchant",VillagerProfession.BUTCHER,List.of(new Product(Items.BREAD,8,2),new Product(Items.COOKED_BEEF,4,3),new Product(Items.GOLDEN_CARROT,4,6))),
        new Shop("farmer","Produce Merchant",VillagerProfession.FARMER,List.of(new Product(Items.WHEAT_SEEDS,8,1),new Product(Items.CARROT,8,2),new Product(Items.POTATO,8,2))),
        new Shop("materials","Material Merchant",VillagerProfession.MASON,List.of(new Product(Items.STONE_BRICKS,32,4),new Product(Items.OAK_PLANKS,32,3),new Product(Items.GLASS,16,3))),
        new Shop("magic","Magic Merchant",VillagerProfession.LIBRARIAN,List.of(new Product(Items.BOOK,4,3),new Product(Items.LAPIS_LAZULI,16,4),new Product(Items.BLAZE_POWDER,2,8))),
        new Shop("fletcher","Fletcher",VillagerProfession.FLETCHER,List.of(new Product(Items.BOW,1,8),new Product(Items.ARROW,32,3),new Product(Items.CROSSBOW,1,14))),
        new Shop("luxury","Luxury Merchant",VillagerProfession.NONE,List.of(new Product(Items.GOLDEN_APPLE,1,24),new Product(Items.DIAMOND,1,32))),
        new Shop("travelling","Travelling Merchant",VillagerProfession.NONE,List.of(new Product(Items.ENDER_PEARL,2,8),new Product(Items.AMETHYST_SHARD,8,5))),
        new Shop("stable","Stable Master",VillagerProfession.LEATHERWORKER,List.of()),
        new Shop("pet","Pet Merchant",VillagerProfession.SHEPHERD,List.of()),
        new Shop("clan","Clan Merchant",VillagerProfession.NONE,List.of()),
        new Shop("dungeon","Dungeon Merchant",VillagerProfession.NONE,List.of()),
        new Shop("raid","Raid Merchant",VillagerProfession.NONE,List.of())
    );
    private final GoldShops gold=new GoldShops();
    private final Presets presets=new Presets();
    private final Map<String,Removal> removals=new HashMap<>();
    private record Removal(UUID npc,long expires){}
    private static final org.slf4j.Logger LOG=com.mojang.logging.LogUtils.getLogger();
    private Map<String,Presets.Preset> defaults(){
        var result=new TreeMap<String,Presets.Preset>();
        for(var type:SHOPS){
            var items=new GoldShops.Shop();items.restockSeconds=3600;
            for(var product:type.products){String id=BuiltInRegistries.ITEM.getKey(product.item).toString();long price=Math.max(1,(product.price+product.amount-1)/product.amount);items.offers.add(new GoldShops.Offer(id,price,0,64,64,false));}
            var creatures=new TreeMap<String,Ledger.CreatureOffer>();
            if(type.id.equals("stable")){
                creatures.put("mount:traveller",new Ledger.CreatureOffer("gold",2500,10,10,false));
                creatures.put("mount:pack_donkey",new Ledger.CreatureOffer("gold",5000,5,5,false));
                creatures.put("mount:desert_camel",new Ledger.CreatureOffer("mount_token",150,5,5,false));
                creatures.put("mount:royal_warhorse",new Ledger.CreatureOffer("gold",75000,2,2,false));
            }else if(type.id.equals("pet")){
                creatures.put("pet:swift_fox",new Ledger.CreatureOffer("gold",20000,5,5,false));
                creatures.put("pet:cat",new Ledger.CreatureOffer("pet_token",250,5,5,false));
                creatures.put("pet:sheep",new Ledger.CreatureOffer("gold",3500,10,10,false));
            }
            result.put(type.id,new Presets.Preset(type.id,items,creatures));
        }
        return result;
    }
    public Merchants(){
        var bus=MinecraftForge.EVENT_BUS;
        bus.addListener((net.minecraftforge.event.server.ServerStartedEvent e)->{gold.start(e.getServer());removals.clear();try{presets.defaults(defaults());}catch(Exception ex){LOG.error("Preset load failed",ex);}});
        bus.addListener((ServerStoppingEvent e)->removals.clear());
        bus.addListener(this::commands);bus.addListener(this::interact);
        bus.addListener(this::specific);bus.addListener(this::join);

    }
    private static boolean managed(Villager v){return v.getTags().contains(TAG);}
    private static void protect(Villager v){
        v.setNoAi(true);v.setInvulnerable(true);v.setPersistenceRequired();v.setSilent(true);
        v.setCustomNameVisible(true);
    }
    private void join(EntityJoinLevelEvent e){
        if(!e.getLevel().isClientSide()&&e.getEntity() instanceof Villager v&&managed(v)){if(gold.available()&&gold.isRemoved(v.getUUID()))e.setCanceled(true);else protect(v);}
    }
    private int fail(CommandSourceStack s,String message){s.sendFailure(Component.literal(message));return 0;}
    private int ok(CommandSourceStack s,String message){s.sendSuccess(()->Component.literal(message),false);return 1;}
    private static Shop shop(String id){return SHOPS.stream().filter(s->s.id.equals(id)).findFirst().orElse(null);}
    private static boolean validId(String id){return id.matches("[a-z0-9_-]{1,32}");}
    private static boolean validName(String name){return !name.isBlank()&&name.length()<=64&&name.chars().noneMatch(Character::isISOControl);}
    private static Item item(String id){
        var key=ResourceLocation.tryParse(id);
        if(key==null||!BuiltInRegistries.ITEM.containsKey(key))return null;
        var value=BuiltInRegistries.ITEM.get(key);
        return value==Items.AIR?null:value;
    }
    private static Villager nearest(CommandSourceStack s){
        return s.getLevel().getEntitiesOfClass(Villager.class,new AABB(s.getPosition(),s.getPosition()).inflate(8),Merchants::managed).stream()
            .min(Comparator.comparingDouble(v->v.position().distanceToSqr(s.getPosition()))).orElse(null);
    }
    private void open(ServerPlayer p,Villager v){if(!gold.available()||gold.isRemoved(v.getUUID())){p.sendSystemMessage(Component.literal("Merchant unavailable"));return;}if(CreatureShops.kind(v).isEmpty())gold.open(p,v);else CreatureShops.open(p,v);}
    private void interact(PlayerInteractEvent.EntityInteract e){
        if(e.getEntity() instanceof ServerPlayer p&&e.getTarget() instanceof Villager v&&managed(v)){
            e.setCanceled(true);open(p,v);
        }
    }
    private void specific(PlayerInteractEvent.EntityInteractSpecific e){
        if(e.getEntity() instanceof ServerPlayer p&&e.getTarget() instanceof Villager v&&managed(v)){
            e.setCanceled(true);open(p,v);
        }
    }
    private int create(CommandSourceStack s,String type,String id,String name){
        var shop=shop(type);
        if(shop==null||!validId(id)||!validName(name))return fail(s,"Invalid shop type, ID or name.");
        var level=s.getLevel();
        // IDs label NPCs; entity UUID is the actual identity. Reject nearby duplicate IDs without force-loading chunks.
        if(!level.getEntitiesOfClass(Villager.class,new AABB(s.getPosition(),s.getPosition()).inflate(128),v->managed(v)&&v.getTags().contains(PREFIX+id)).isEmpty())return fail(s,"Duplicate nearby merchant ID.");
        var pos=BlockPos.containing(s.getPosition());var feet=Vec3.atBottomCenterOf(pos);
        Villager v=EntityType.VILLAGER.create(level);
        if(v==null)return fail(s,"Could not create villager.");
        v.moveTo(feet.x,feet.y,feet.z,s.getRotation().y,0);
        if(!level.hasChunkAt(pos)||!level.getWorldBorder().isWithinBounds(v.getBoundingBox())||!level.noCollision(v,v.getBoundingBox())||!level.getFluidState(pos).isEmpty()||!level.getBlockState(pos.below()).isFaceSturdy(level,pos.below(),net.minecraft.core.Direction.UP))return fail(s,"Stand at a safe, clear shop location.");
        v.addTag(TAG);v.addTag(PREFIX+id);v.addTag("mythos_merchant_type_"+type);
        v.setCustomName(Component.literal(name));protect(v);
        v.setVillagerData(v.getVillagerData().setProfession(shop.profession));
        var offers=v.getOffers();offers.clear();
        // Gold stock starts empty; owner configures every product and price explicitly.
        if(!level.addFreshEntity(v)){v.discard();return fail(s,"Spawn rejected.");}
        return ok(s,"Created "+name+" ("+id+") with "+offers.size()+" offers. UUID "+v.getUUID());
    }
    private int change(CommandSourceStack s,String action,int index,String name){
        var v=nearest(s);if(v==null)return fail(s,"No managed merchant within 8 blocks.");
        switch(action){
            case "info" -> {
                if(!CreatureShops.kind(v).isEmpty())return creatureInfo(s,v);
                s.sendSuccess(()->Component.literal(gold.info(v)),false);
                s.sendSuccess(()->Component.literal(v.getDisplayName().getString()+" | "+v.getUUID()+" | "+v.getTags()),false);
                return 1;
            }
            case "rename" -> {if(!validName(name))return fail(s,"Invalid name.");v.setCustomName(Component.literal(name));}
            default -> {return fail(s,"Invalid action.");}
        }
        return ok(s,"Merchant "+action+" complete.");
    }
    private int creatureInfo(CommandSourceStack s,Villager v){
        if(!EconomyApi.available(s.getServer()))return fail(s,"Economy unavailable");
        var offers=EconomyApi.creatureOffers(s.getServer(),v.getUUID());
        ok(s,v.getDisplayName().getString()+" | "+v.getUUID());
        offers.forEach((key,o)->ok(s,key+" | "+o.price()+" "+o.currency()+" | "+(o.unlimited()?"Unlimited":o.stock()+"/"+o.max())));
        return 1;
    }
    private int creatureEdit(CommandSourceStack s,String kind,String id,String action,String currency,long price,int stock,boolean unlimited){
        var v=nearest(s);if(v==null||!CreatureShops.kind(v).equals(kind))return fail(s,"Stand near the matching merchant");
        var server=s.getServer();if(!EconomyApi.available(server))return fail(s,"Economy unavailable");
        String key=kind+":"+id;var old=EconomyApi.creatureOffers(server,v.getUUID()).get(key);
        Ledger.CreatureOffer offer;
        switch(action){
            case "add" -> offer=new Ledger.CreatureOffer(currency,price,stock,stock,unlimited);
            case "price" -> {if(old==null)return fail(s,"Offer missing");offer=new Ledger.CreatureOffer(old.currency(),price,old.stock(),old.max(),old.unlimited());}
            case "stock" -> {if(old==null)return fail(s,"Offer missing");offer=new Ledger.CreatureOffer(old.currency(),old.price(),stock,Math.max(stock,old.max()),old.unlimited());}
            case "remove" -> offer=null;
            default -> {return fail(s,"Invalid action");}
        }
        var result=EconomyApi.configureCreature(server,v.getUUID(),key,offer);
        return result.success()?ok(s,result.message()):fail(s,result.message());
    }
    private static String type(Villager v){return v.getTags().stream().filter(t->t.startsWith("mythos_merchant_type_")).map(t->t.substring("mythos_merchant_type_".length())).findFirst().orElse("");}
    private List<Villager> nearby(CommandSourceStack s,double radius){return s.getLevel().getEntitiesOfClass(Villager.class,new AABB(s.getPosition(),s.getPosition()).inflate(radius),v->managed(v)&&v.isAlive()&&!gold.isRemoved(v.getUUID()));}
    private Villager target(CommandSourceStack s,String id){var matches=nearby(s,128).stream().filter(v->v.getUUID().toString().equals(id)||v.getTags().contains(PREFIX+id)||v.getDisplayName().getString().equalsIgnoreCase(id)).toList();return matches.size()==1?matches.get(0):null;}
    private int listMerchants(CommandSourceStack s){var list=nearby(s,128);for(var v:list)ok(s,v.getDisplayName().getString()+" | "+v.getTags().stream().filter(t->t.startsWith(PREFIX)).findFirst().orElse("")+" | "+v.blockPosition().toShortString()+" | "+v.getUUID());return ok(s,list.size()+" loaded nearby merchants");}
    private String actor(CommandSourceStack s){return s.getEntity()==null?"console":s.getEntity().getUUID().toString();}
    private int removeRequest(CommandSourceStack s,Villager v){
        if(v==null)return fail(s,"Merchant not found or ambiguous");
        String busy=gold.busy(v);if(busy!=null)return fail(s,busy);
        removals.put(actor(s),new Removal(v.getUUID(),System.currentTimeMillis()+30000));
        return ok(s,"Remove "+v.getDisplayName().getString()+" ("+v.getUUID()+")? /merchant remove confirm within 30 seconds.");
    }
    private int removeConfirm(CommandSourceStack s){
        var request=removals.remove(actor(s));if(request==null||request.expires<System.currentTimeMillis())return fail(s,"No active removal request");
        var v=nearby(s,128).stream().filter(n->n.getUUID().equals(request.npc)).findFirst().orElse(null);
        if(v==null)return fail(s,"Original merchant no longer nearby");
        String busy=gold.busy(v);if(busy!=null)return fail(s,busy);
        if(!EconomyApi.available(s.getServer()))return fail(s,"Economy unavailable");
        if(!gold.markRemoved(v))return fail(s,"Could not save removal");
        var result=EconomyApi.replaceCreatureOffers(s.getServer(),v.getUUID(),Map.of());
        v.discard();
        LOG.info("Merchant removed actor={} npc={} creatureCleanup={}",actor(s),v.getUUID(),result.success());
        return result.success()?ok(s,"Merchant removed; player ownership unaffected"):fail(s,"NPC disabled and removed; creature offer cleanup failed, check economy storage");
    }
    private int move(CommandSourceStack s,String id){
        var v=target(s,id);if(v==null)return fail(s,"No unique loaded merchant within 128 blocks");
        String busy=gold.busy(v);if(busy!=null)return fail(s,busy);
        var pos=BlockPos.containing(s.getPosition());var feet=Vec3.atBottomCenterOf(pos);var level=s.getLevel();
        if(!level.hasChunkAt(pos)||!level.getWorldBorder().isWithinBounds(v.getBoundingBox().move(feet.subtract(v.position())))||!level.noCollision(v,v.getBoundingBox().move(feet.subtract(v.position())))||!level.getFluidState(pos).isEmpty()||!level.getBlockState(pos.below()).isFaceSturdy(level,pos.below(),net.minecraft.core.Direction.UP))return fail(s,"Stand at a safe clear destination");
        v.teleportTo(feet.x,feet.y,feet.z);return ok(s,"Moved "+v.getDisplayName().getString()+"; configuration retained");
    }
    private int presetApply(CommandSourceStack s,Villager v,Presets.Preset p){
        if(v==null||p==null||!type(v).equals(p.type()))return fail(s,"Preset must match merchant type");
        String busy=gold.busy(v);if(busy!=null)return fail(s,busy);
        if(!CreatureShops.kind(v).isEmpty()){
            if(!EconomyApi.available(s.getServer()))return fail(s,"Economy unavailable");
            var result=EconomyApi.replaceCreatureOffers(s.getServer(),v.getUUID(),p.creatures());
            return result.success()?ok(s,"Creature preset applied"):fail(s,result.message());
        }
        return gold.replace(v,p.items())?ok(s,"Item preset applied"):fail(s,"Preset save failed");
    }
    private int presetSave(CommandSourceStack s,String id){
        var v=nearest(s);if(v==null||gold.busy(v)!=null)return fail(s,"No available nearby merchant");
        if(!EconomyApi.available(s.getServer()))return fail(s,"Economy unavailable");
        try{var kind=CreatureShops.kind(v);presets.save(id,new Presets.Preset(type(v),gold.snapshot(v),kind.isEmpty()?Map.of():EconomyApi.creatureOffers(s.getServer(),v.getUUID())));return ok(s,"Saved preset "+id);}catch(Exception ex){return fail(s,"Preset save failed: "+ex.getMessage());}
    }
    private int presetSpawn(CommandSourceStack s,String preset,String id,String name){
        var p=presets.get(preset);if(p==null)return fail(s,"Unknown preset");
        if(create(s,p.type(),id,name)==0)return 0;
        var v=target(s,id);if(v==null)return fail(s,"Created NPC but target ambiguous; apply preset manually");
        return presetApply(s,v,p);
    }
    private void commands(RegisterCommandsEvent e){
        var root=Commands.literal("merchant").requires(s->s.hasPermission(2));
        root.executes(c->ok(c.getSource(),"Use /merchant types, create, info, offer, remove, restock, rename. Edit nearest merchant within 8 blocks."));
        root.then(Commands.literal("types").executes(c->ok(c.getSource(),String.join(", ",SHOPS.stream().map(Shop::id).toList()))));
        root.then(Commands.literal("create").then(Commands.argument("type",StringArgumentType.word()).suggests((c,b)->net.minecraft.commands.SharedSuggestionProvider.suggest(SHOPS.stream().map(Shop::id),b)).then(Commands.argument("id",StringArgumentType.word()).then(Commands.argument("name",StringArgumentType.greedyString()).executes(c->create(c.getSource(),StringArgumentType.getString(c,"type"),StringArgumentType.getString(c,"id"),StringArgumentType.getString(c,"name")))))));
        var unlimitedArg=Commands.argument("unlimited",com.mojang.brigadier.arguments.BoolArgumentType.bool()).executes(c->{
            var v=nearest(c.getSource());if(v==null)return fail(c.getSource(),"No nearby merchant");
            if(!CreatureShops.kind(v).isEmpty())return fail(c.getSource(),"Use /merchant mount or pet add");
            return ok(c.getSource(),gold.configure(v,StringArgumentType.getString(c,"item"),
                com.mojang.brigadier.arguments.LongArgumentType.getLong(c,"buy"),
                com.mojang.brigadier.arguments.LongArgumentType.getLong(c,"sell"),
                IntegerArgumentType.getInteger(c,"stock"),IntegerArgumentType.getInteger(c,"max"),
                com.mojang.brigadier.arguments.BoolArgumentType.getBool(c,"unlimited")));
        });
        var maxArg=Commands.argument("max",IntegerArgumentType.integer(0,GoldShops.MAX_STOCK)).then(unlimitedArg);
        var stockArg=Commands.argument("stock",IntegerArgumentType.integer(0,GoldShops.MAX_STOCK)).then(maxArg);
        var sellArg=Commands.argument("sell",com.mojang.brigadier.arguments.LongArgumentType.longArg(0,GoldShops.MAX_PRICE)).then(stockArg);
        var buyArg=Commands.argument("buy",com.mojang.brigadier.arguments.LongArgumentType.longArg(0,GoldShops.MAX_PRICE)).then(sellArg);
        root.then(Commands.literal("offer").then(Commands.argument("item",StringArgumentType.string()).then(buyArg)));
        root.then(Commands.literal("info").executes(c->change(c.getSource(),"info",0,"")));
        var removeRoot=Commands.literal("remove").executes(c->removeRequest(c.getSource(),nearest(c.getSource())));
        removeRoot.then(Commands.literal("confirm").executes(c->removeConfirm(c.getSource())));
        removeRoot.then(Commands.argument("item",StringArgumentType.string()).executes(c->{var v=nearest(c.getSource());return v==null?fail(c.getSource(),"No nearby merchant"):ok(c.getSource(),gold.remove(v,StringArgumentType.getString(c,"item")));}));
        root.then(removeRoot);
        root.then(Commands.literal("delete").executes(c->removeRequest(c.getSource(),nearest(c.getSource()))).then(Commands.literal("confirm").executes(c->removeConfirm(c.getSource()))).then(Commands.argument("target",StringArgumentType.string()).executes(c->removeRequest(c.getSource(),target(c.getSource(),StringArgumentType.getString(c,"target"))))));
        root.then(Commands.literal("move").then(Commands.argument("target",StringArgumentType.string()).executes(c->move(c.getSource(),StringArgumentType.getString(c,"target")))));
        root.then(Commands.literal("list").executes(c->listMerchants(c.getSource())));
        var presetRoot=Commands.literal("preset");
        presetRoot.then(Commands.literal("list").executes(c->ok(c.getSource(),String.join(", ",presets.ids()))));
        presetRoot.then(Commands.literal("reload").executes(c->{try{presets.reload();return ok(c.getSource(),"Presets reloaded");}catch(Exception ex){return fail(c.getSource(),"Reload failed: "+ex.getMessage());}}));
        presetRoot.then(Commands.literal("save").then(Commands.argument("preset",StringArgumentType.word()).executes(c->presetSave(c.getSource(),StringArgumentType.getString(c,"preset")))));
        presetRoot.then(Commands.literal("delete").then(Commands.argument("preset",StringArgumentType.word()).executes(c->{try{presets.delete(StringArgumentType.getString(c,"preset"));return ok(c.getSource(),"Preset deleted");}catch(Exception ex){return fail(c.getSource(),ex.getMessage());}})));
        presetRoot.then(Commands.literal("info").then(Commands.argument("preset",StringArgumentType.word()).suggests((c,b)->SharedSuggestionProvider.suggest(presets.ids(),b)).executes(c->{var p=presets.get(StringArgumentType.getString(c,"preset"));if(p==null)return fail(c.getSource(),"Unknown preset");ok(c.getSource(),p.type()+" | restock seconds "+p.items().restockSeconds);for(var o:p.items().offers)ok(c.getSource(),o.item+" | "+o.buy+" / "+o.sell+" Gold | "+o.stock+"/"+o.max);p.creatures().forEach((k,v)->ok(c.getSource(),k+" | "+v.price()+" "+v.currency()+" | "+v.stock()+"/"+v.max()));return 1;})));
        presetRoot.then(Commands.literal("apply").then(Commands.argument("preset",StringArgumentType.word()).suggests((c,b)->SharedSuggestionProvider.suggest(presets.ids(),b)).executes(c->presetApply(c.getSource(),nearest(c.getSource()),presets.get(StringArgumentType.getString(c,"preset"))))));
        var spawnName=Commands.argument("name",StringArgumentType.greedyString()).executes(c->presetSpawn(c.getSource(),StringArgumentType.getString(c,"preset"),StringArgumentType.getString(c,"id"),StringArgumentType.getString(c,"name")));
        presetRoot.then(Commands.literal("spawn").then(Commands.argument("preset",StringArgumentType.word()).suggests((c,b)->SharedSuggestionProvider.suggest(presets.ids(),b)).then(Commands.argument("id",StringArgumentType.word()).then(spawnName))));
        root.then(presetRoot);
        root.then(Commands.literal("restock").executes(c->{var v=nearest(c.getSource());return v==null?fail(c.getSource(),"No nearby merchant"):ok(c.getSource(),gold.restock(v,0,true));})
            .then(Commands.argument("seconds",com.mojang.brigadier.arguments.LongArgumentType.longArg(0,31536000)).executes(c->{var v=nearest(c.getSource());return v==null?fail(c.getSource(),"No nearby merchant"):ok(c.getSource(),gold.restock(v,com.mojang.brigadier.arguments.LongArgumentType.getLong(c,"seconds"),false));})));
        root.then(Commands.literal("pending").requires(s->s.hasPermission(4)).executes(c->ok(c.getSource(),gold.pending())));
        root.then(Commands.literal("resolve").requires(s->s.hasPermission(4)).then(Commands.argument("transaction",StringArgumentType.word()).then(Commands.argument("inventoryApplied",com.mojang.brigadier.arguments.BoolArgumentType.bool()).executes(c->ok(c.getSource(),gold.resolve(c.getSource().getServer(),StringArgumentType.getString(c,"transaction"),com.mojang.brigadier.arguments.BoolArgumentType.getBool(c,"inventoryApplied")))))));
        root.then(Commands.literal("rename").then(Commands.argument("name",StringArgumentType.greedyString()).executes(c->change(c.getSource(),"rename",0,StringArgumentType.getString(c,"name")))));
        for(String kind:List.of("mount","pet")){
            var category=Commands.literal(kind);
            var stock=Commands.argument("stock",IntegerArgumentType.integer(0,1000000))
                .executes(c->creatureEdit(c.getSource(),kind,StringArgumentType.getString(c,"id"),"add",StringArgumentType.getString(c,"currency"),LongArgumentType.getLong(c,"price"),IntegerArgumentType.getInteger(c,"stock"),false))
                .then(Commands.argument("unlimited",BoolArgumentType.bool()).executes(c->creatureEdit(c.getSource(),kind,StringArgumentType.getString(c,"id"),"add",StringArgumentType.getString(c,"currency"),LongArgumentType.getLong(c,"price"),IntegerArgumentType.getInteger(c,"stock"),BoolArgumentType.getBool(c,"unlimited"))));
            var price=Commands.argument("price",LongArgumentType.longArg(1,Ledger.MAX_BALANCE)).then(stock);
            var currency=Commands.argument("currency",StringArgumentType.word()).suggests((c,b)->SharedSuggestionProvider.suggest(List.of("gold",kind+"_token"),b)).then(price);
            category.then(Commands.literal("add").then(Commands.argument("id",StringArgumentType.word()).then(currency)));
            var priceEdit=Commands.argument("price",LongArgumentType.longArg(1,Ledger.MAX_BALANCE)).executes(c->creatureEdit(c.getSource(),kind,StringArgumentType.getString(c,"id"),"price","",LongArgumentType.getLong(c,"price"),0,false));
            category.then(Commands.literal("price").then(Commands.argument("id",StringArgumentType.word()).then(priceEdit)));
            var stockEdit=Commands.argument("stock",IntegerArgumentType.integer(0,1000000)).executes(c->creatureEdit(c.getSource(),kind,StringArgumentType.getString(c,"id"),"stock","",0,IntegerArgumentType.getInteger(c,"stock"),false));
            category.then(Commands.literal("stock").then(Commands.argument("id",StringArgumentType.word()).then(stockEdit)));
            category.then(Commands.literal("remove").then(Commands.argument("id",StringArgumentType.word()).executes(c->creatureEdit(c.getSource(),kind,StringArgumentType.getString(c,"id"),"remove","",0,0,false))));
            category.then(Commands.literal("catalog").executes(c->{if(!EconomyApi.available(c.getSource().getServer()))return fail(c.getSource(),"Economy unavailable");return ok(c.getSource(),String.join(", ",EconomyApi.creatures(c.getSource().getServer(),kind).stream().filter(EconomyApi.Creature::shop).map(x->x.key().substring(kind.length()+1)).toList()));}));
            root.then(category);
        }
        root.then(Commands.literal("edit").executes(c->change(c.getSource(),"info",0,"")));
        e.getDispatcher().register(root);
    }
}
