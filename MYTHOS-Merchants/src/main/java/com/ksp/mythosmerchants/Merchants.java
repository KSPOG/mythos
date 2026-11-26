package com.ksp.mythosmerchants;

import com.mojang.brigadier.arguments.IntegerArgumentType;
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
    public Merchants(){
        var bus=MinecraftForge.EVENT_BUS;
        bus.addListener((net.minecraftforge.event.server.ServerStartedEvent e)->gold.start(e.getServer()));
        bus.addListener(this::commands);bus.addListener(this::interact);
        bus.addListener(this::specific);bus.addListener(this::join);

    }
    private static boolean managed(Villager v){return v.getTags().contains(TAG);}
    private static void protect(Villager v){
        v.setNoAi(true);v.setInvulnerable(true);v.setPersistenceRequired();v.setSilent(true);
        v.setCustomNameVisible(true);
    }
    private void join(EntityJoinLevelEvent e){
        if(!e.getLevel().isClientSide()&&e.getEntity() instanceof Villager v&&managed(v))protect(v);
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
    private void open(ServerPlayer p,Villager v){gold.open(p,v);}
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
                s.sendSuccess(()->Component.literal(gold.info(v)),false);
                s.sendSuccess(()->Component.literal(v.getDisplayName().getString()+" | "+v.getUUID()+" | "+v.getTags()),false);
                return 1;
            }
            case "rename" -> {if(!validName(name))return fail(s,"Invalid name.");v.setCustomName(Component.literal(name));}
            default -> {return fail(s,"Invalid action.");}
        }
        return ok(s,"Merchant "+action+" complete.");
    }
    private void commands(RegisterCommandsEvent e){
        var root=Commands.literal("merchant").requires(s->s.hasPermission(2));
        root.executes(c->ok(c.getSource(),"Use /merchant types, create, info, offer, remove, restock, rename. Edit nearest merchant within 8 blocks."));
        root.then(Commands.literal("types").executes(c->ok(c.getSource(),String.join(", ",SHOPS.stream().map(Shop::id).toList()))));
        root.then(Commands.literal("create").then(Commands.argument("type",StringArgumentType.word()).suggests((c,b)->net.minecraft.commands.SharedSuggestionProvider.suggest(SHOPS.stream().map(Shop::id),b)).then(Commands.argument("id",StringArgumentType.word()).then(Commands.argument("name",StringArgumentType.greedyString()).executes(c->create(c.getSource(),StringArgumentType.getString(c,"type"),StringArgumentType.getString(c,"id"),StringArgumentType.getString(c,"name")))))));
        var unlimitedArg=Commands.argument("unlimited",com.mojang.brigadier.arguments.BoolArgumentType.bool()).executes(c->{
            var v=nearest(c.getSource());if(v==null)return fail(c.getSource(),"No nearby merchant");
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
        root.then(Commands.literal("remove").then(Commands.argument("item",StringArgumentType.string()).executes(c->{var v=nearest(c.getSource());return v==null?fail(c.getSource(),"No nearby merchant"):ok(c.getSource(),gold.remove(v,StringArgumentType.getString(c,"item")));})));
        root.then(Commands.literal("restock").executes(c->{var v=nearest(c.getSource());return v==null?fail(c.getSource(),"No nearby merchant"):ok(c.getSource(),gold.restock(v,0,true));})
            .then(Commands.argument("seconds",com.mojang.brigadier.arguments.LongArgumentType.longArg(0,31536000)).executes(c->{var v=nearest(c.getSource());return v==null?fail(c.getSource(),"No nearby merchant"):ok(c.getSource(),gold.restock(v,com.mojang.brigadier.arguments.LongArgumentType.getLong(c,"seconds"),false));})));
        root.then(Commands.literal("pending").requires(s->s.hasPermission(4)).executes(c->ok(c.getSource(),gold.pending())));
        root.then(Commands.literal("resolve").requires(s->s.hasPermission(4)).then(Commands.argument("transaction",StringArgumentType.word()).then(Commands.argument("inventoryApplied",com.mojang.brigadier.arguments.BoolArgumentType.bool()).executes(c->ok(c.getSource(),gold.resolve(c.getSource().getServer(),StringArgumentType.getString(c,"transaction"),com.mojang.brigadier.arguments.BoolArgumentType.getBool(c,"inventoryApplied")))))));
        root.then(Commands.literal("rename").then(Commands.argument("name",StringArgumentType.greedyString()).executes(c->change(c.getSource(),"rename",0,StringArgumentType.getString(c,"name")))));
        e.getDispatcher().register(root);
    }
}
