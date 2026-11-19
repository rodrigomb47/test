package com.factionclash.combat;

import com.factionclash.hero.HeroEntity;
import com.factionclash.logic.hero.KillCredit;
import com.factionclash.logic.hero.KillCredit.Kind;
import com.factionclash.logic.hero.KillCredit.Ledger;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Who gets the kill when someone dies by terrain (HEROES_V2 6.17, {@code docs/design/breaching.md} 8.5; the rules are
 * {@link KillCredit}'s): every receiver's newest enemy push, throw, pull or blow, kept 15 s, for players, faction NPCs,
 * heroes and monsters alike. A push is written into the receiver's combat tracker as a hit of 0 by the pusher (vanilla's
 * fall message then reads "was doomed to fall by") and sets its last attacker; once a second the last attacker of a
 * receiver that falls, burns or is in lava, the void or water is set again, up to the 15 s, since vanilla forgets it
 * after 5 s. At the death the credited enemy is the receiver's last attacker, so vanilla's own message for a player and
 * vanilla's kill score name it; {@code KillCreditEvents} credits a hero's terrain kills and the kill feed names it.
 *
 * <p>Server thread only. A receiver's ledger is made once (the first push or blow it takes) and dropped once its credit
 * is 15 s old or it is gone, so nothing is allocated per tick.
 */
public final class KillCredits {
    private KillCredits() {
    }

    private static final Map<LivingEntity, Ledger<LivingEntity>> LEDGERS = new IdentityHashMap<>();

    /** A push, throw or pull of {@code receiver} by {@code pusher} (a hero's move today; any pusher counts the same). */
    public static void push(LivingEntity pusher, LivingEntity receiver, Kind kind) {
        if (!enemies(pusher, receiver)) {
            return; // its own side is never credited (6.17: no move pushes its own side; a shockwave's ally is no kill)
        }
        long now = receiver.level().getGameTime();
        Ledger<LivingEntity> l = LEDGERS.computeIfAbsent(receiver, k -> new Ledger<>());
        l.record(pusher, now, kind);
        if (l.track(pusher, now)) {
            // a hit of 0 by the pusher in its combat tracker (at most once a second per pusher: a tether pulls every tick)
            receiver.getCombatTracker().recordDamage(attackBy(pusher), 0F);
        }
        receiver.setLastHurtByMob(pusher);
        if (receiver instanceof HeroEntity h) {
            h.creditedBy(pusher); // the rewards of its death go to the pusher's side as to a blow's (6.13)
        }
    }

    /** A blow on {@code receiver} by a living {@code attacker} (any damage it causes: melee, a shot, a move). */
    public static void blow(Entity attacker, LivingEntity receiver) {
        if (attacker instanceof LivingEntity a && enemies(a, receiver)) {
            LEDGERS.computeIfAbsent(receiver, k -> new Ledger<>()).record(a, receiver.level().getGameTime(), Kind.BLOW);
        }
    }

    private static boolean enemies(LivingEntity a, LivingEntity r) {
        return a != null && r != null && a != r && !r.level().isClientSide() && !CombatRules.sameSide(a, r);
    }

    /** The damage source of a push's hit of 0: the pusher's attack, a player's or a mob's, as vanilla would name it. */
    private static DamageSource attackBy(LivingEntity pusher) {
        return pusher instanceof Player p ? pusher.damageSources().playerAttack(p) : pusher.damageSources().mobAttack(pusher);
    }

    /**
     * The enemy credited with a death by {@code msgId} now: the newest push or blow within 15 s for terrain, within
     * vanilla's 5 s for any other death with no attacker; null when there is none.
     */
    public static LivingEntity credited(LivingEntity victim, String msgId) {
        Ledger<LivingEntity> l = LEDGERS.get(victim);
        return l == null ? null : l.credited(victim.level().getGameTime(), msgId);
    }

    /** The killer of a death by {@code source}: its own attacker (causing, else direct), else the credited enemy. */
    public static Entity killer(LivingEntity victim, DamageSource source) {
        Entity attacker = attacker(source);
        return KillCredit.killer(attacker, attacker != null ? null : credited(victim, source.getMsgId()));
    }

    /** The killing damage's attacker as vanilla's message names it: the causing entity, else the direct one. */
    public static Entity attacker(DamageSource source) {
        if (source == null) {
            return null;
        }
        return source.getEntity() != null ? source.getEntity() : source.getDirectEntity();
    }

    /** The receiver's ledger (GameTests). */
    public static Ledger<LivingEntity> ledger(LivingEntity e) {
        return LEDGERS.get(e);
    }

    /**
     * Once a second: the last attacker of every receiver that falls, burns or is in lava, below the world or in water
     * is its credited enemy again (up to the 15 s); ledgers whose credit is over or whose receiver is gone are dropped.
     */
    public static void tick(MinecraftServer server) {
        if (LEDGERS.isEmpty() || server.overworld().getGameTime() % KillCredit.REFRESH_TICKS != 0) {
            return;
        }
        Iterator<Map.Entry<LivingEntity, Ledger<LivingEntity>>> it = LEDGERS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<LivingEntity, Ledger<LivingEntity>> e = it.next();
            LivingEntity r = e.getKey();
            Ledger<LivingEntity> l = e.getValue();
            long now = r.level().getGameTime();
            if (r.isRemoved() || l.expired(now) || r.isDeadOrDying() && r.deathTime > 20) {
                it.remove();
                continue;
            }
            if (!r.isDeadOrDying() && l.who() != null && l.who().isAlive()
                    && KillCredit.refreshes(l.tick(), now, !r.onGround() && r.fallDistance > 0, r.isOnFire(), r.isInLava(),
                    r.getY() < r.level().getMinBuildHeight(), r.isInWater())) {
                r.setLastHurtByMob(l.who());
            }
        }
    }

    /** A new server (a world closed in single-player and another opened): nothing carries over. */
    public static void clear() {
        LEDGERS.clear();
    }
}
