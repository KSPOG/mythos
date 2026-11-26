package com.ksp.mythosequipment;

import com.ksp.mythosclasses.ClassApi;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import java.util.*;
import com.ksp.mythosequipment.EquipmentRules.*;

final class EquipmentServer {
    private final EquipmentEffects effects=new EquipmentEffects();
    private static final ResourceLocation BONUS=ResourceLocation.fromNamespaceAndPath(MythosEquipment.ID,"gear_bonus");
    private static final EquipmentSlot[] SLOTS={EquipmentSlot.MAINHAND,EquipmentSlot.OFFHAND,EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET};
    private static final Map<Stat,Double> SCALE=Map.of(Stat.ATK,0.25,Stat.STR,0.15,Stat.DEF,0.25,Stat.VIT,0.5,Stat.DEX,0.002);
    static boolean allowed(ServerPlayer p,Definition d){
        if(d==null)return true;
        if(!net.minecraftforge.fml.ModList.get().isLoaded("mythosclasses")){
            // Local creative preview only. Never bypass class checks on a dedicated server.
            return !p.getServer().isDedicatedServer()&&p.isCreative();
        }
        return ClassApi.canUse(p.getUUID(),List.of(d.owner().id))&&ClassApi.progression(p.getUUID()).map(v->v.level()>=d.level()).orElse(false);
    }
    private static boolean allowed(ServerPlayer p,ItemStack s){return allowed(p,MythosEquipment.definition(s));}
    static boolean fits(Definition d,EquipmentSlot slot){
        return switch(d.slot()){
            case WEAPON -> slot==EquipmentSlot.MAINHAND;
            case SHIELD -> slot==EquipmentSlot.OFFHAND;
            case HEAD -> slot==EquipmentSlot.HEAD;
            case CHEST -> slot==EquipmentSlot.CHEST;
            case LEGS -> slot==EquipmentSlot.LEGS;
            case FEET -> slot==EquipmentSlot.FEET;
        };
    }
    static boolean twoHanded(ServerPlayer p){
        Definition d=MythosEquipment.definition(p.getMainHandItem());
        return d!=null&&d.kind()!=null&&d.kind().twoHanded;
    }
    private static boolean blockedOffhand(ServerPlayer p,ItemStack stack){
        return twoHanded(p)&&stack.getItem() instanceof net.minecraft.world.item.ShieldItem;
    }
    private static void deny(ServerPlayer p){p.displayClientMessage(Component.literal("This MYTHOS item requires its assigned class and level."),true);}
    @SubscribeEvent public void attack(AttackEntityEvent e){
        if(e.getEntity() instanceof ServerPlayer p&&!allowed(p,p.getMainHandItem())){e.setCanceled(true);deny(p);}
    }
    @SubscribeEvent public void use(PlayerInteractEvent.RightClickItem e){
        if(e.getEntity() instanceof ServerPlayer p&&(!allowed(p,e.getItemStack())||blockedOffhand(p,e.getItemStack()))){e.setCanceled(true);deny(p);}
    }
    @SubscribeEvent public void block(PlayerInteractEvent.RightClickBlock e){
        if(e.getEntity() instanceof ServerPlayer p&&(!allowed(p,e.getItemStack())||blockedOffhand(p,e.getItemStack()))){e.setCanceled(true);deny(p);}
    }
    @SubscribeEvent public void projectile(EntityJoinLevelEvent e){
        if(e.getLevel().isClientSide()||e.loadedFromDisk()||!(e.getEntity() instanceof Projectile projectile)||!(projectile.getOwner() instanceof ServerPlayer p))return;
        if(!allowed(p,p.getMainHandItem())||!allowed(p,p.getOffhandItem())){e.setCanceled(true);deny(p);}
    }
    @SubscribeEvent public void damage(LivingAttackEvent e){
        if(e.getSource().getEntity() instanceof ServerPlayer p&&!(e.getSource().getDirectEntity() instanceof Projectile)&&!allowed(p,p.getMainHandItem()))e.setCanceled(true);
    }
    void hurt(net.minecraftforge.event.entity.living.LivingHurtEvent e){
        if(e.isCanceled()||e.getAmount()<=0||e.getEntity().level().isClientSide())return;
        if(e.getSource().getEntity() instanceof ServerPlayer p&&allowed(p,p.getMainHandItem()))effects.hit(p,e.getEntity());
    }
    void logout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e){
        effects.forget(e.getEntity().getUUID());
    }
    @SubscribeEvent public void tick(TickEvent.ServerTickEvent.Post e){
        for(ServerPlayer p:e.getServer().getPlayerList().getPlayers()){
            for(int i=0;i<p.getInventory().getContainerSize();i++){
                ItemStack stack=p.getInventory().getItem(i);Definition d=MythosEquipment.definition(stack);
                if(d!=null)GearRolls.ensure(stack,d);
            }
            var totals=new EnumMap<Stat,Integer>(Stat.class);
            for(EquipmentSlot slot:SLOTS){
                ItemStack stack=p.getItemBySlot(slot);Definition d=MythosEquipment.definition(stack);
                boolean conflict=slot==EquipmentSlot.OFFHAND&&blockedOffhand(p,stack);
                if(d==null&&!conflict)continue;
                if(conflict||!allowed(p,d)||!fits(d,slot)){
                    if(p.isUsingItem())p.stopUsingItem();
                    p.setItemSlot(slot,ItemStack.EMPTY);
                    boolean moved=false;
                    for(int i=0;i<36;i++){
                        if(i==p.getInventory().selected||!p.getInventory().getItem(i).isEmpty())continue;
                        p.getInventory().setItem(i,stack);moved=true;break;
                    }
                    if(!moved)p.drop(stack,false);
                    deny(p);continue;
                }
                GearRolls.ensure(stack,d).stats().forEach((stat,value)->totals.merge(stat,value,Integer::sum));
            }
            apply(p,totals);
        }
    }
    private static double val(Map<Stat,Integer> values,Stat stat){return values.getOrDefault(stat,0)*SCALE.getOrDefault(stat,0.0);}
    private static void modifier(AttributeInstance a,double amount){
        if(a==null)return;
        var old=a.getModifier(BONUS);
        if(old!=null&&Double.compare(old.amount(),amount)==0)return;
        if(old!=null)a.removeModifier(BONUS);
        if(amount!=0)a.addTransientModifier(new AttributeModifier(BONUS,amount,AttributeModifier.Operation.ADD_VALUE));
    }
    private static void apply(ServerPlayer p,Map<Stat,Integer> values){
        modifier(p.getAttribute(Attributes.ATTACK_DAMAGE),val(values,Stat.ATK)+val(values,Stat.STR));
        modifier(p.getAttribute(Attributes.ARMOR),val(values,Stat.DEF));
        modifier(p.getAttribute(Attributes.MAX_HEALTH),val(values,Stat.VIT));
        modifier(p.getAttribute(Attributes.MOVEMENT_SPEED),val(values,Stat.DEX));
        if(p.getHealth()>p.getMaxHealth())p.setHealth(p.getMaxHealth());
    }
    void cleanup(MinecraftServer server){effects.clear();for(ServerPlayer p:server.getPlayerList().getPlayers())apply(p,Map.of());}
    void commands(RegisterCommandsEvent e){
        var root=Commands.literal("equipment");
        root.then(Commands.literal("list").executes(c->{
            for(Definition d:EquipmentRules.CATALOG)c.getSource().sendSuccess(()->Component.literal(d.id()+" | "+d.name()),false);return 1;
        }));
        var item=Commands.argument("item",StringArgumentType.word()).suggests((c,b)->SharedSuggestionProvider.suggest(EquipmentRules.CATALOG.stream().map(Definition::id),b))
            .executes(c->give(c.getSource(),EntityArgument.getPlayer(c,"player"),StringArgumentType.getString(c,"item"),null));
        item.then(Commands.argument("rarity",StringArgumentType.word()).suggests((c,b)->SharedSuggestionProvider.suggest(Arrays.stream(Rarity.values()).map(r->r.name().toLowerCase(Locale.ROOT)),b)).executes(c->{
            Rarity r;try{r=Rarity.valueOf(StringArgumentType.getString(c,"rarity").toUpperCase(Locale.ROOT));}
            catch(IllegalArgumentException ex){c.getSource().sendFailure(Component.literal("Unknown rarity."));return 0;}
            return give(c.getSource(),EntityArgument.getPlayer(c,"player"),StringArgumentType.getString(c,"item"),r);
        }));
        root.then(Commands.literal("give").requires(s->s.hasPermission(2)).then(Commands.argument("player",EntityArgument.player()).then(item)));
        e.getDispatcher().register(root);
    }
    private int give(CommandSourceStack source,ServerPlayer player,String id,Rarity rarity){
        var d=EquipmentRules.CATALOG.stream().filter(v->v.id().equals(id)).findFirst().orElse(null);
        if(d==null){source.sendFailure(Component.literal("Unknown equipment."));return 0;}
        ItemStack stack=new ItemStack(MythosEquipment.GEAR.get(id).get());
        var roll=GearRolls.generate(stack,d,rarity);
        if(!player.getInventory().add(stack)){source.sendFailure(Component.literal("Inventory full."));return 0;}
        source.sendSuccess(()->Component.literal("Issued "+roll.rarity().label()+" "+d.name()+" "+roll.stats()),false);return 1;
    }
}
