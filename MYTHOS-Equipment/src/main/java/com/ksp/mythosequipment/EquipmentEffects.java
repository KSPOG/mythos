package com.ksp.mythosequipment;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.EquipmentSlot;
import java.util.*;
import com.ksp.mythosequipment.EquipmentRules.*;

/** Signature procs are evaluated only on accepted hostile damage. */
final class EquipmentEffects {
    private static final EquipmentSlot[] SLOTS={EquipmentSlot.MAINHAND,EquipmentSlot.OFFHAND,EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET};
    private final Map<UUID,Long> cooldown=new HashMap<>();
    void clear(){cooldown.clear();}
    void forget(UUID player){cooldown.remove(player);}
    void hit(ServerPlayer player,LivingEntity enemy){
        if(!(enemy instanceof Monster)||!enemy.isAlive()||!player.isAlive()||player.isSpectator()||player.isCreative()
            ||player.isAlliedTo(enemy)||enemy.isAlliedTo(player)||enemy.getTags().contains("mythos_pet")||enemy.getTags().contains("mythos_mount"))return;
        long tick=player.serverLevel().getGameTime();
        if(tick<cooldown.getOrDefault(player.getUUID(),0L))return;
        Definition chosen=null;Rarity rarity=null;
        // At most one proc per hit, regardless of how many matching pieces are worn.
        for(EquipmentSlot slot:SLOTS){
            var stack=player.getItemBySlot(slot);var d=MythosEquipment.definition(stack);
            if(d==null||(slot==EquipmentSlot.OFFHAND&&EquipmentServer.twoHanded(player))||!EquipmentServer.allowed(player,d)||!EquipmentServer.fits(d,slot))continue;
            var roll=GearRolls.read(stack,d);
            if(roll.isEmpty()||!roll.get().rarity().hasEffect())continue;
            Rarity candidate=roll.get().rarity();
            if(rarity==null||candidate.ordinal()>rarity.ordinal()){chosen=d;rarity=candidate;}
        }
        if(chosen==null)return;
        int rank=rarity.effectRank();
        // Stable cooldown across equipment swaps; no extra rolls per armor piece.
        cooldown.put(player.getUUID(),tick+200);
        int duration=40+20*rank,amp=rank>=4?1:0;
        switch(chosen.effect().kind){
            case "strength" -> buff(player,MobEffects.DAMAGE_BOOST,duration,amp);
            case "heal" -> player.heal(1F+rank);
            case "burn" -> enemy.igniteForSeconds(1+rank);
            case "resistance" -> buff(player,MobEffects.DAMAGE_RESISTANCE,duration,amp);
            case "absorption" -> buff(player,MobEffects.ABSORPTION,duration,amp);
            case "guard" -> {buff(player,MobEffects.DAMAGE_RESISTANCE,duration,amp);buff(player,MobEffects.ABSORPTION,duration,amp);}
            case "slow" -> buff(enemy,MobEffects.MOVEMENT_SLOWDOWN,duration,amp);
            case "speed" -> buff(player,MobEffects.MOVEMENT_SPEED,duration,amp);
            case "hunt" -> {buff(player,MobEffects.MOVEMENT_SPEED,duration,amp);buff(enemy,MobEffects.MOVEMENT_SLOWDOWN,duration,amp);}
            case "frost" -> {buff(enemy,MobEffects.MOVEMENT_SLOWDOWN,duration,amp);buff(enemy,MobEffects.WEAKNESS,duration,amp);}
            case "surge" -> {buff(player,MobEffects.DAMAGE_BOOST,duration,amp);buff(player,MobEffects.REGENERATION,duration,0);}
            case "poison" -> buff(enemy,MobEffects.POISON,duration,amp);
            case "veil" -> {buff(player,MobEffects.INVISIBILITY,duration,0);buff(player,MobEffects.MOVEMENT_SPEED,duration,amp);}
            case "shelter" -> {buff(player,MobEffects.DAMAGE_RESISTANCE,duration,amp);buff(player,MobEffects.REGENERATION,duration,0);}
            case "renew" -> {player.heal(1F+rank);buff(player,MobEffects.REGENERATION,duration,amp);}
            case "weakness" -> buff(enemy,MobEffects.WEAKNESS,duration,amp);
            case "harvest" -> {buff(enemy,MobEffects.WITHER,duration,amp);player.heal(1F+rank);}
            case "curse" -> {buff(enemy,MobEffects.WEAKNESS,duration,amp);enemy.igniteForSeconds(1+rank);}
            case "fracture" -> {buff(enemy,MobEffects.WEAKNESS,duration,amp);buff(enemy,MobEffects.POISON,duration,amp);}
            case "doom" -> {buff(enemy,MobEffects.WITHER,duration,amp);buff(enemy,MobEffects.MOVEMENT_SLOWDOWN,duration,amp);}
            default -> throw new IllegalStateException("Unknown gear effect");
        }
        player.displayClientMessage(net.minecraft.network.chat.Component.literal(chosen.effect().label+" activated"),true);
    }
    private static void buff(LivingEntity entity,Holder<MobEffect> effect,int ticks,int amp){
        entity.addEffect(new MobEffectInstance(effect,ticks,amp));
    }
}
