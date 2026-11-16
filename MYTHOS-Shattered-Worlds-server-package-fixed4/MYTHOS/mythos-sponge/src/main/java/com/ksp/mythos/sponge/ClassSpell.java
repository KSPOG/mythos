package com.ksp.mythos.sponge;

import java.time.Duration;
import org.spongepowered.api.data.Keys;
import org.spongepowered.api.effect.potion.PotionEffect;
import org.spongepowered.api.effect.potion.PotionEffectType;
import org.spongepowered.api.effect.potion.PotionEffectTypes;
import org.spongepowered.api.entity.living.Hostile;
import org.spongepowered.api.entity.living.player.server.ServerPlayer;
import org.spongepowered.api.event.cause.entity.damage.DamageTypes;
import org.spongepowered.api.event.cause.entity.damage.source.DamageSource;
import org.spongepowered.api.registry.DefaultedRegistryReference;
import org.spongepowered.api.util.Ticks;
import org.spongepowered.api.util.blockray.RayTrace;

enum ClassSpell {
    BULWARK("Bulwark", 20, PotionEffectTypes.RESISTANCE, 6, 0),
    BATTLE_FURY("Battle Fury", 25, PotionEffectTypes.STRENGTH, 6, 0),
    WINDSTEP("Windstep", 18, PotionEffectTypes.SPEED, 6, 1),
    VEILSTEP("Veilstep", 20, PotionEffectTypes.INVISIBILITY, 4, 0),
    EMBER_BOLT("Ember Bolt", 8, null, 0, 0),
    MEND("Mend", 22, PotionEffectTypes.REGENERATION, 5, 1),
    AEGIS("Aegis", 25, PotionEffectTypes.ABSORPTION, 10, 0),
    SPIRIT_ASCENT("Spirit Ascent", 18, PotionEffectTypes.SLOW_FALLING, 8, 0);

    private final String displayName;
    private final Duration cooldown;
    private final DefaultedRegistryReference<PotionEffectType> effect;
    private final int seconds;
    private final int amplifier;

    ClassSpell(final String displayName, final int cooldownSeconds,
               final DefaultedRegistryReference<PotionEffectType> effect, final int seconds, final int amplifier) {
        this.displayName = displayName;
        this.cooldown = Duration.ofSeconds(cooldownSeconds);
        this.effect = effect;
        this.seconds = seconds;
        this.amplifier = amplifier;
    }

    String displayName() {
        return this.displayName;
    }

    Duration cooldown() {
        return this.cooldown;
    }

    boolean apply(final ServerPlayer player) {
        if (this.effect != null) {
            final PotionEffect effect = PotionEffect.of(this.effect, this.amplifier, Ticks.of(this.seconds * 20L));
            if (player.potionEffects().get().stream().anyMatch(active -> active.type().equals(effect.type())
                    && (active.amplifier() > effect.amplifier()
                    || (active.amplifier() == effect.amplifier() && (active.duration().isInfinite() || active.duration().ticks() >= effect.duration().ticks()))))) {
                return false;
            }
            return player.offerSingle(Keys.POTION_EFFECTS, effect).isSuccessful();
        }

        final var target = RayTrace.entity()
                .world(player.world())
                .sourceEyePosition(player)
                .direction(player)
                .limit(16)
                .continueWhileBlock(RayTrace.onlyAir())
                .select(entity -> entity instanceof Hostile && !entity.isRemoved())
                .execute();
        return target.isPresent() && target.get().selectedObject().damage(6.0,
                DamageSource.builder().type(DamageTypes.INDIRECT_MAGIC).entity(player).build());
    }
}
