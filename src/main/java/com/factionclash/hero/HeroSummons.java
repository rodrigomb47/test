package com.factionclash.hero;

import com.factionclash.combat.CombatRules;
import com.factionclash.config.FCConfig;
import com.factionclash.faction.FactionType;
import com.factionclash.logic.hero.HeroDef.Creature;
import com.factionclash.logic.hero.SummonRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A hero's summons (HERO_FORMAT.md 13.5 and 20; task A4): plain vanilla wolves, snow golems and iron golems that fight
 * for its side for the effect's seconds (their own looks are A8's). Each carries {@code factionclash:summon} in its
 * persistent data (owner hero, faction, expiry as a game time). Its target goals are replaced by "the hero's target,
 * else the nearest enemy of its side within 16 blocks" and it follows its hero (leash 16). It counts as its faction for
 * {@link CombatRules#sameSide} (no friendly fire, no retaliation between it and its side; a hero's moves treat an
 * enemy hero's summon as an enemy). It vanishes in a puff when its time is up, when its hero dies or is gone, or 48
 * blocks from its hero; it drops nothing, gives no experience, cannot be tamed, changes no block (a snow golem leaves
 * no trail) and never counts for the war council or recruitment (it is no faction NPC). Caps: {@link SummonRules}.
 * Off with {@code heroes.summons = false}. Dedicated-server safe; ticked every 10 ticks, nothing allocated while no
 * summon lives.
 */
public final class HeroSummons {
    private HeroSummons() {
    }

    public static final String TAG = "factionclash:summon";
    /** A summon whose hero is not found for this long after it loads vanishes (chunk loads come in any order). */
    public static final int OWNERLESS_GRACE = 100;

    /** Each hero's living summons (by the hero's UUID), oldest first. */
    private static final Map<UUID, List<Mob>> BY_HERO = new HashMap<>();
    private static final List<Mob> SWEEP = new ArrayList<>();

    // ------------------------------------------------------------------ reading the tag

    public static boolean isSummon(Entity e) {
        return e instanceof Mob m && m.getPersistentData().contains(TAG);
    }

    /** The faction a summon fights for, or null for anything else. */
    public static FactionType factionOf(Entity e) {
        if (!(e instanceof Mob m) || !m.getPersistentData().contains(TAG)) {
            return null;
        }
        return FactionType.byId(m.getPersistentData().getCompound(TAG).getString("faction"));
    }

    static UUID ownerOf(Mob m) {
        CompoundTag t = m.getPersistentData().getCompound(TAG);
        return t.hasUUID("owner") ? t.getUUID("owner") : null;
    }

    static long expiresAt(Mob m) {
        return m.getPersistentData().getCompound(TAG).getLong("expires");
    }

    /** The hero a summon serves, if it is loaded and alive. */
    public static HeroEntity owner(Mob m) {
        UUID id = ownerOf(m);
        return id != null && m.level() instanceof ServerLevel sl && sl.getEntity(id) instanceof HeroEntity h && h.isAlive()
                ? h : null;
    }

    /** The living summons of a hero (a copy). */
    public static List<Mob> of(HeroEntity hero) {
        List<Mob> l = BY_HERO.get(hero.getUUID());
        if (l == null) {
            return List.of();
        }
        l.removeIf(m -> !m.isAlive() || m.isRemoved());
        return List.copyOf(l);
    }

    // ------------------------------------------------------------------ summoning

    /**
     * {@code summon}: calls up to {@code count} of {@code creature} beside the hero for {@code seconds}, within the
     * caps (the hero's oldest go to make room; a new iron golem replaces its old one; a faction at 12 calls fewer).
     * Returns how many appeared.
     */
    public static int summon(HeroEntity hero, Creature creature, int count, double seconds, String name) {
        if (!FCConfig.HERO_SUMMONS.get() || !(hero.level() instanceof ServerLevel sl) || hero.isPreview()) {
            return 0;
        }
        List<Mob> mine = BY_HERO.computeIfAbsent(hero.getUUID(), k -> new ArrayList<>());
        mine.removeIf(m -> !m.isAlive() || m.isRemoved());
        int iron = 0;
        for (Mob m : mine) {
            if (m instanceof IronGolem) {
                iron++;
            }
        }
        int faction = 0;
        for (Map.Entry<UUID, List<Mob>> e : BY_HERO.entrySet()) {
            for (Mob m : e.getValue()) {
                if (m.isAlive() && hero.getFaction() == factionOf(m)) {
                    faction++;
                }
            }
        }
        SummonRules.Plan plan = SummonRules.plan(creature, count, mine.size(), iron, faction);
        if (plan.spawn() <= 0) {
            return 0;
        }
        for (int i = 0; i < plan.retireIron(); i++) {
            for (Mob m : new ArrayList<>(mine)) {
                if (m instanceof IronGolem) {
                    vanish(m);
                    mine.remove(m);
                    break;
                }
            }
        }
        for (int i = 0; i < plan.retireOldest() && !mine.isEmpty(); i++) {
            vanish(mine.remove(0));
        }
        EntityType<? extends Mob> type = switch (creature) {
            case WOLF -> EntityType.WOLF;
            case SNOW_GOLEM -> EntityType.SNOW_GOLEM;
            case IRON_GOLEM -> EntityType.IRON_GOLEM;
        };
        long expires = sl.getGameTime() + Math.max(1, com.factionclash.logic.hero.HeroRules.ticks(seconds));
        int made = 0;
        for (int i = 0; i < plan.spawn(); i++) {
            Mob m = type.create(sl);
            if (m == null) {
                continue;
            }
            Vec3 at = spot(hero, m, i);
            m.moveTo(at.x, at.y, at.z, hero.getYRot(), 0F);
            CompoundTag t = new CompoundTag();
            t.putUUID("owner", hero.getUUID());
            t.putString("hero", hero.heroId());
            t.putString("faction", hero.getFaction() == null ? "" : hero.getFaction().id);
            t.putLong("expires", expires);
            m.getPersistentData().put(TAG, t);
            if (name != null && !name.isBlank()) {
                m.setCustomName(Component.literal(name));
            }
            m.setPersistenceRequired();
            t.putLong("joined", sl.getGameTime());
            adopt(m);
            mine.add(m); // listed before it joins, so the join hook does not list it twice
            if (!sl.addFreshEntity(m)) {
                mine.remove(m);
                continue;
            }
            sl.sendParticles(ParticleTypes.POOF, at.x, at.y + 0.5, at.z, 8, 0.3, 0.3, 0.3, 0.02);
            made++;
        }
        return made;
    }

    /** A free spot within 2 blocks of the hero (its own feet when none is free). */
    private static Vec3 spot(HeroEntity hero, Mob m, int i) {
        Vec3 base = hero.position();
        for (int k = 0; k < 8; k++) {
            double a = (i * 2.4 + k * 0.8) % (Math.PI * 2);
            Vec3 p = base.add(Math.cos(a) * SummonRules.SPAWN_SPREAD, 0, Math.sin(a) * SummonRules.SPAWN_SPREAD);
            AABB box = m.getType().getDimensions().makeBoundingBox(p);
            if (hero.level().noCollision(m, box) && !hero.level().getBlockState(BlockPos.containing(p).below()).isAir()) {
                return p;
            }
        }
        return base;
    }

    /** Its goals as a summon: the hero's enemies and following the hero replace its own targets. */
    static void adopt(Mob m) {
        m.targetSelector.removeAllGoals(g -> true);
        m.targetSelector.addGoal(1, new SummonTargetGoal(m));
        if (m instanceof PathfinderMob pm) {
            m.goalSelector.addGoal(3, new FollowHeroGoal(pm));
        }
    }

    /** It goes in a puff of smoke (time up, its hero gone or too far, replaced). */
    public static void vanish(Mob m) {
        if (m.level() instanceof ServerLevel sl && m.isAlive()) {
            sl.sendParticles(ParticleTypes.POOF, m.getX(), m.getY(0.5), m.getZ(), 10, 0.3, 0.4, 0.3, 0.02);
        }
        m.discard();
    }

    /** The hero died or left: all its summons go. */
    public static void dismissAll(HeroEntity hero) {
        List<Mob> l = BY_HERO.remove(hero.getUUID());
        if (l != null) {
            for (Mob m : l) {
                vanish(m);
            }
        }
    }

    // ------------------------------------------------------------------ joining and ticking

    /**
     * A summon joins the level (spawned, or loaded with its chunk): an expired one is refused (false), any other is
     * adopted again and listed under its hero.
     */
    public static boolean onJoin(Mob m) {
        if (!(m.level() instanceof ServerLevel sl) || !isSummon(m)) {
            return true;
        }
        if (sl.getGameTime() >= expiresAt(m) || !FCConfig.HERO_SUMMONS.get()) {
            return false;
        }
        UUID owner = ownerOf(m);
        if (owner == null) {
            return false;
        }
        List<Mob> l = BY_HERO.computeIfAbsent(owner, k -> new ArrayList<>());
        if (!l.contains(m)) {
            boolean adopted = false;
            for (var g : m.targetSelector.getAvailableGoals()) {
                adopted |= g.getGoal() instanceof SummonTargetGoal;
            }
            if (!adopted) {
                adopt(m);
            }
            m.getPersistentData().getCompound(TAG).putLong("joined", sl.getGameTime());
            l.add(m);
        }
        return true;
    }

    /** Every 10 ticks: summons whose time is up, whose hero is gone or which strayed 48 blocks vanish. */
    public static void tick(MinecraftServer server) {
        if (BY_HERO.isEmpty() || server.overworld().getGameTime() % 10 != 0) {
            return;
        }
        Iterator<Map.Entry<UUID, List<Mob>>> it = BY_HERO.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, List<Mob>> e = it.next();
            List<Mob> l = e.getValue();
            l.removeIf(m -> !m.isAlive() || m.isRemoved());
            if (l.isEmpty()) {
                it.remove();
                continue;
            }
            SWEEP.clear();
            SWEEP.addAll(l);
            for (Mob m : SWEEP) {
                long now = m.level().getGameTime();
                HeroEntity h = owner(m);
                long joined = m.getPersistentData().getCompound(TAG).getLong("joined");
                boolean ownerless = h == null && now - joined > OWNERLESS_GRACE;
                if (now >= expiresAt(m) || ownerless || h != null && SummonRules.tooFar(m.distanceTo(h))) {
                    vanish(m);
                    l.remove(m);
                }
            }
            SWEEP.clear();
        }
    }

    /** Forgets every list (server stop). */
    public static void clear() {
        BY_HERO.clear();
    }

    // ------------------------------------------------------------------ goals

    /** The hero's target when it is an enemy within 16 blocks, else the nearest enemy of the hero's side within 16. */
    static final class SummonTargetGoal extends Goal {
        private final Mob mob;
        private int next;

        SummonTargetGoal(Mob mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            if (--next > 0) {
                return false;
            }
            next = 10;
            HeroEntity h = owner(mob);
            if (h == null) {
                return false;
            }
            LivingEntity cur = mob.getTarget();
            if (cur != null && cur.isAlive() && HeroForms.enemy(h, cur) && mob.distanceTo(cur) <= SummonRules.LEASH) {
                return false;
            }
            LivingEntity pick = null;
            LivingEntity ht = h.getTarget();
            if (ht != null && ht.isAlive() && HeroForms.enemy(h, ht) && mob.distanceTo(ht) <= SummonRules.LEASH) {
                pick = ht;
            } else {
                double best = Double.MAX_VALUE;
                for (LivingEntity e : HeroForms.enemiesNear(h, mob.position(), SummonRules.LEASH)) {
                    double d = e.distanceToSqr(mob);
                    if (d < best && mob.getSensing().hasLineOfSight(e) && !(e instanceof Player p && (p.isCreative() || p.isSpectator()))) {
                        best = d;
                        pick = e;
                    }
                }
            }
            if (pick != null && pick != mob.getTarget()) {
                mob.setTarget(pick);
            }
            return false;
        }
    }

    /** Back to the hero when it strays more than 16 blocks, as a tamed wolf follows its owner (never a teleport). */
    static final class FollowHeroGoal extends Goal {
        private final PathfinderMob mob;
        private HeroEntity hero;
        private int repath;

        FollowHeroGoal(PathfinderMob mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (mob.tickCount % 10 != 0) {
                return false;
            }
            hero = owner(mob);
            return hero != null && SummonRules.shouldFollow(mob.distanceTo(hero));
        }

        @Override
        public boolean canContinueToUse() {
            return hero != null && hero.isAlive() && mob.distanceTo(hero) > 4;
        }

        @Override
        public void start() {
            repath = 0;
            mob.setTarget(null);
        }

        @Override
        public void stop() {
            mob.getNavigation().stop();
            hero = null;
        }

        @Override
        public void tick() {
            if (--repath <= 0) {
                repath = 10;
                mob.getNavigation().moveTo(hero, 1.2);
            }
        }
    }
}
