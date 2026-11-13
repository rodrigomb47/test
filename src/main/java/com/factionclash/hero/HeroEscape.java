package com.factionclash.hero;

import com.factionclash.FactionClash;
import com.factionclash.campaign.BaseState;
import com.factionclash.campaign.CampaignData;
import com.factionclash.campaign.HeroState;
import com.factionclash.config.FCConfig;
import com.factionclash.logic.hero.EscapeRules;
import com.factionclash.logic.hero.EscapeRules.EscapeStage;
import com.factionclash.logic.hero.HeroActions;
import com.factionclash.logic.hero.HeroDef;
import com.factionclash.logic.hero.HeroDef.LineEvent;
import com.factionclash.logic.hero.HeroDef.Trigger;
import com.factionclash.logic.hero.PassiveBook;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * How a losing fight ends for a hero (HEROES_V2 6.13, MOBILITY_AND_WORK 7.7; task A6b). There is no yield: the owner's
 * "they either try to escape or fight until die depending of what they want". After every hit that lands and once a
 * tick, {@link EscapeRules#lowHealth} decides from the hero's own nature (its kind from aggression, honour and caution
 * or its authored {@code when_losing}; where it stands anyway, its {@code stands_for}; its threshold {@code flee_at})
 * and the situation (inside its home base while the heart stands, the 10-minute window, {@code heroes.escape}):
 * <ul>
 * <li><b>Escape</b>: it breaks off for good. A move in its wind-up is cancelled as a stun cancels it, one in its active
 * part plays out that tick; its fight ends (the bar shows {@code fled}); it says its {@code escape} line and its
 * {@code on_escape} passives fire; then {@code combat/HeroEscapeGoal} runs it away with ordinary navigation toward its
 * refuge (its home base, never the base it flees; another base of its faction; else the wild, 64 blocks and more from
 * the fight). With no way away from its pursuers for 3 s it is cornered and fights back where it stands, and it runs
 * again as soon as a way opens. The escape is over inside its refuge's walls or 10 s with no pursuer within 48
 * blocks; for {@code heroes.escapeAwaySeconds} (300) and until it is above half health it does not take those it fled
 * as targets again. It stays a boss while it runs (the cap, poise and weak points hold). An escape is no death:
 * nothing drops and nobody is rewarded.</li>
 * <li><b>Last stand</b>: a hero that will not or cannot leave says its {@code last_stand} line once in the fight with a
 * determined face, its {@code on_last_stand} passives fire, and it fights on until it dies; nothing cancels the
 * killing blow.</li>
 * </ul>
 * The enemy's kind is never an input: soldiers, a player, a hero and monsters are the same. MOBILITY_AND_WORK's
 * superhuman escape movement is A12's: {@code HeroEscapeGoal} keeps a movement hook for it.
 */
public final class HeroEscape {
    /** How far the wild escape heads beyond the 64 blocks it must keep from the fight. */
    static final double WILD_MARGIN = 8;
    /** The escape's checks (pursuers within 48 blocks, the refuge's walls) run this often. */
    static final int CHECK_TICKS = 20;
    /** The last stand's determined face is held this long. */
    static final int BEAT_FACE_TICKS = 60;

    private final HeroEntity hero;
    private final HeroCombat combat;
    private EscapeRules.Escape escape;
    /** Whom it fled: the enemies engaged in that fight and its target then. */
    private final List<UUID> fled = new ArrayList<>();
    /** The game time its last escape began here (-1: none since it was made; the campaign keeps it across deaths). */
    private long escapedAt = -1;
    private boolean wild;
    private BlockPos refugePos;
    private int refugeBase = -1;
    private boolean beatDone;
    /** What the goal (or a cornered check) saw last: whether a way away is open. */
    private boolean wayAway = true;
    private int escapes, lastStands;

    HeroEscape(HeroEntity hero, HeroCombat combat) {
        this.hero = hero;
        this.combat = combat;
    }

    private long now() {
        return hero.level().getGameTime();
    }

    // ------------------------------------------------------------------ what it is doing

    /** Escaping now (running or cornered). */
    public boolean running() {
        return escape != null;
    }

    /** Cornered: no way away for 3 s, it fights back where it stands. */
    public boolean cornered() {
        return escape != null && escape.stage() == EscapeStage.CORNERED;
    }

    /** Running away now (an escape that is not cornered): it starts no moves, takes no target, strikes no blow. */
    public boolean fleeing() {
        return escape != null && escape.stage() != EscapeStage.CORNERED;
    }

    /** The escape in progress (null: none). */
    public EscapeRules.Escape current() {
        return escape;
    }

    /** How many escapes and last-stand beats it has had since it was made (tests). */
    public int escapes() {
        return escapes;
    }

    public int lastStands() {
        return lastStands;
    }

    /** Whether it said its last stand in this fight. */
    public boolean beatDone() {
        return beatDone;
    }

    /**
     * Whether it keeps away from {@code e}: one it fled, for its stay-away time and until it is above half health
     * (MOBILITY_AND_WORK 7.7: it does not re-engage those it fled).
     */
    public boolean keepsAwayFrom(Entity e) {
        if (fled.isEmpty() || e == null) {
            return false;
        }
        if (escape == null && escapedAt >= 0 && EscapeRules.mayReturn(now(), escapedAt, FCConfig.HERO_ESCAPE_AWAY_SECONDS.get(),
                hero.getHealth() / Math.max(1F, hero.getMaxHealth()))) {
            fled.clear();
            return false;
        }
        return fled.contains(e.getUUID());
    }

    /** A new fight: the last-stand beat may come once again. */
    void newFight() {
        beatDone = false;
    }

    // ------------------------------------------------------------------ the decision (6.13)

    /** The situation, nothing of who the enemy is: home and its heart, the window, {@code heroes.escape}. */
    EscapeRules.Context context() {
        BaseState home = hero.base();
        boolean heart = home != null && !home.destroyed;
        boolean atHome = heart && home.contains(hero.blockPosition(), (int) EscapeRules.NEAR_TARGET_MARGIN);
        HeroState rec = hero.campaignRecord();
        long last = Math.max(rec == null ? 0 : rec.lastEscapeAt, Math.max(0, escapedAt));
        // leaders and a fighting leader beside it come with L4; a rebirth with its legendary heroes (none in the slice)
        return new EscapeRules.Context(atHome, heart, false, false, FCConfig.HERO_ESCAPE.get(), now(), last,
                FCConfig.HERO_ESCAPE_COOLDOWN_SECONDS.get());
    }

    /**
     * After a hit that landed and once a tick ({@code inFight}: a fight is on, or the hit came from an enemy): under its
     * threshold an escaping-kind hero that may go escapes, any other says its last stand once. Nothing is decided
     * above the threshold, and nothing allocates there.
     */
    void check(boolean inFight) {
        if (escape != null || !inFight || !hero.isAlive()) {
            return;
        }
        HeroDef d = combat.def();
        HeroDef.EndOfFight e = d == null || d.v2() == null ? null : d.v2().end();
        if (e == null) {
            return;
        }
        double share = hero.getHealth() / Math.max(1F, hero.getMaxHealth());
        if (share * 100 >= e.fleeAt() || beatDone && e.kind() == HeroDef.WhenLosing.LAST_STAND) {
            return; // nothing more to decide: above the threshold, or a last stand already said
        }
        switch (EscapeRules.lowHealth(e, share, beatDone, context())) {
            case ESCAPE -> start(EscapeRules.Trigger.LOW_HEALTH, false);
            case LAST_STAND_BEAT -> lastStand();
            default -> {
            }
        }
    }

    /** The heart of its home base fell (a {@code home} stand ends with it): an escaping hero goes now, whatever its health. */
    void lostBase(boolean fighting) {
        HeroDef d = combat.def();
        HeroDef.EndOfFight e = d == null || d.v2() == null ? null : d.v2().end();
        if (escape != null || e == null || !hero.isAlive()) {
            return;
        }
        if (EscapeRules.lostBase(e, fighting, context()) == EscapeRules.Outcome.ESCAPE) {
            start(EscapeRules.Trigger.LOST_BASE, false);
        }
    }

    /** The last-stand beat (6.13): its line, a determined face, {@code on_last_stand}, once a fight. No number changes. */
    private void lastStand() {
        beatDone = true;
        lastStands++;
        LivingEntity t = hero.getTarget();
        hero.say(LineEvent.LAST_STAND, t == null ? Map.of() : Map.of("enemy", HeroEntity.nameOf(t)), null);
        combat.boss().holdMood(HeroActions.moodCode("determined"), BEAT_FACE_TICKS);
        combat.trigger(PassiveBook.Event.of(Trigger.ON_LAST_STAND), t, null);
        HeroState rec = hero.campaignRecord();
        if (rec != null) {
            rec.lastStands++;
            CampaignData.get(hero.getServer()).setDirty();
        }
        FactionClash.LOGGER.info("[Heroes] {} makes its last stand at {}%", hero.heroId(),
                Math.round(100 * hero.getHealth() / Math.max(1F, hero.getMaxHealth())));
    }

    // ------------------------------------------------------------------ the escape (MOBILITY_AND_WORK 7.7)

    /**
     * Starts an escape now ({@code forced}: an operator's, whatever its kind and health). Returns whether one began.
     */
    public boolean start(EscapeRules.Trigger trigger, boolean forced) {
        if (escape != null || !hero.isAlive() || hero.isPreview() || !(hero.level() instanceof net.minecraft.server.level.ServerLevel)) {
            return false;
        }
        long now = now();
        CampaignData data = CampaignData.get(hero.getServer());
        BaseState home = hero.base();
        BlockPos feet = hero.blockPosition();
        BaseState flees = null;
        List<EscapeRules.BaseOption> standing = new ArrayList<>();
        for (BaseState b : data.bases.values()) {
            if (b.destroyed || !b.isSettlement()) {
                continue;
            }
            if (flees == null && b.contains(feet, (int) EscapeRules.NEAR_TARGET_MARGIN)) {
                flees = b;
            }
            if (b.faction == hero.getFaction()) {
                standing.add(new EscapeRules.BaseOption(Integer.toString(b.id), Math.sqrt(b.center.distSqr(feet))));
            }
        }
        String r = EscapeRules.refuge(trigger, home == null ? null : Integer.toString(home.id),
                flees == null ? null : Integer.toString(flees.id), standing);
        BaseState refuge = r == null ? null : data.bases.get(Integer.parseInt(r));
        refugeBase = refuge == null ? -1 : refuge.id;
        refugePos = refuge == null ? null : refuge.point(BaseState.SPAWN) != null ? refuge.point(BaseState.SPAWN) : refuge.center;
        wild = refuge == null;
        fled.clear();
        LivingEntity t = hero.getTarget();
        if (t != null) {
            fled.add(t.getUUID());
        }
        for (UUID u : combat.boss().engagedEnemies()) {
            if (!fled.contains(u)) {
                fled.add(u);
            }
        }
        // the fight it leaves is where its enemies stand (their middle), so "away" is away from them
        double cx = 0, cz = 0;
        int n = 0;
        if (hero.level() instanceof net.minecraft.server.level.ServerLevel sl) {
            for (UUID u : fled) {
                Entity e = sl.getEntity(u);
                if (e != null && e.distanceToSqr(hero) <= EscapeRules.CLEAR_RADIUS * EscapeRules.CLEAR_RADIUS) {
                    cx += e.getX();
                    cz += e.getZ();
                    n++;
                }
            }
        }
        escape = new EscapeRules.Escape(now, n > 0 ? cx / n : hero.getX(), n > 0 ? cz / n : hero.getZ(), r);
        escapedAt = now;
        wayAway = true;
        hero.setStateFlag(HeroActions.STATE_ESCAPING, true);
        combat.escapeStarted(); // a wind-up is cancelled as a stun cancels it, an active part plays out its tick
        hero.setTarget(null);
        hero.setAggressive(false);
        hero.getNavigation().stop();
        hero.pursuit().reset();
        combat.boss().escapeStarted(); // the fight ends for it: the bar shows "fled"
        hero.say(LineEvent.ESCAPE, t == null ? Map.of() : Map.of("enemy", HeroEntity.nameOf(t)), null);
        combat.trigger(PassiveBook.Event.of(Trigger.ON_ESCAPE), t, null);
        escapes++;
        HeroState rec = hero.campaignRecord();
        if (rec != null) {
            rec.escapes++;
            rec.lastEscapeAt = now;
            data.setDirty();
        }
        FactionClash.LOGGER.info("[Heroes] {} escapes ({}{}) at {}% toward {}", hero.heroId(), trigger.name().toLowerCase(java.util.Locale.ROOT),
                forced ? ", forced" : "", Math.round(100 * hero.getHealth() / Math.max(1F, hero.getMaxHealth())),
                refuge == null ? "the wild" : refuge.name);
        return true;
    }

    /** Every tick: the escape's checks every second (pursuers within 48 blocks, its refuge's walls, a way away). */
    void tick() {
        if (escape == null || !hero.isAlive() || hero.tickCount % CHECK_TICKS != 0) {
            return;
        }
        EscapeStage before = escape.stage();
        if (before == EscapeStage.CORNERED) {
            wayAway = com.factionclash.combat.HeroEscapeGoal.wayOpen(hero, destination());
        }
        boolean pursuer = pursuerWithin(EscapeRules.CLEAR_RADIUS);
        BaseState refuge = refugeBase < 0 ? null : CampaignData.get(hero.getServer()).bases.get(refugeBase);
        boolean inside = refuge != null && !refuge.destroyed && refuge.contains(hero.blockPosition(), 0);
        EscapeStage now = escape.tick(CHECK_TICKS, wayAway, pursuer, inside);
        if (now == EscapeStage.OVER) {
            end();
        } else if (now == EscapeStage.CORNERED && before != EscapeStage.CORNERED) {
            // cornered: it fights back where it stands, the nearest pursuer first, and runs again once a way opens
            LivingEntity p = nearestPursuer();
            if (p != null) {
                hero.setTarget(p);
            }
            FactionClash.LOGGER.info("[Heroes] {} is cornered", hero.heroId());
        } else if (now == EscapeStage.RUNNING && before == EscapeStage.CORNERED) {
            hero.setTarget(null);
            hero.getNavigation().stop();
        }
    }

    /** The goal's report: whether it could move away from its pursuers since the last one. */
    public void reportWay(boolean way) {
        wayAway = way;
    }

    /** The escape is over: it is no longer escaping; the stay away goes on. */
    private void end() {
        escape = null;
        hero.setStateFlag(HeroActions.STATE_ESCAPING, false);
        FactionClash.LOGGER.info("[Heroes] {} got away", hero.heroId());
    }

    /** Where it runs: its refuge's spawn point, or in the wild a point well past 64 blocks from the fight it left. */
    public Vec3 destination() {
        if (refugePos != null) {
            return Vec3.atBottomCenterOf(refugePos);
        }
        double ox = escape != null ? escape.originX() : hero.getX(), oz = escape != null ? escape.originZ() : hero.getZ();
        double[] p = EscapeRules.awayPoint(hero.getX(), hero.getZ(), ox, oz, hero.getYRot(),
                EscapeRules.WILD_KEEP_AWAY + WILD_MARGIN);
        return new Vec3(p[0], hero.getY(), p[1]);
    }

    /** Where the fight it left was (or where it stands with none). */
    public Vec3 origin() {
        return escape == null ? hero.position() : new Vec3(escape.originX(), hero.getY(), escape.originZ());
    }

    private double wildOriginX = Double.NaN, wildOriginZ;

    /**
     * In the wild, after the escape and during its stay-away time, it keeps at least 64 blocks from the fight it left
     * (MOBILITY_AND_WORK 7.7): true while it must move farther off.
     */
    public boolean keepFarther() {
        if (escape != null) {
            wildOriginX = escape.originX();
            wildOriginZ = escape.originZ();
            return false;
        }
        if (!wild || escapedAt < 0 || Double.isNaN(wildOriginX)) {
            return false;
        }
        double d = Math.hypot(hero.getX() - wildOriginX, hero.getZ() - wildOriginZ);
        return EscapeRules.keepFarther(d, now(), escapedAt, FCConfig.HERO_ESCAPE_AWAY_SECONDS.get());
    }

    /** The wild point to keep away toward, after the escape (null: none). */
    public Vec3 keepAwayPoint() {
        if (Double.isNaN(wildOriginX)) {
            return null;
        }
        double[] p = EscapeRules.awayPoint(hero.getX(), hero.getZ(), wildOriginX, wildOriginZ, hero.getYRot(),
                EscapeRules.WILD_KEEP_AWAY + WILD_MARGIN);
        return new Vec3(p[0], hero.getY(), p[1]);
    }

    /** Pursuers: those it fled and anything whose target it is, within {@code r} blocks, alive and still its enemies. */
    private boolean pursuerWithin(double r) {
        return !hero.level().getEntitiesOfClass(LivingEntity.class, hero.getBoundingBox().inflate(r, r / 2, r),
                this::pursuer).isEmpty();
    }

    private boolean pursuer(LivingEntity e) {
        return e != hero && e.isAlive() && (fled.contains(e.getUUID()) || e instanceof Mob m && m.getTarget() == hero)
                && HeroForms.enemy(hero, e) && e.distanceToSqr(hero) <= EscapeRules.CLEAR_RADIUS * EscapeRules.CLEAR_RADIUS;
    }

    private LivingEntity nearestPursuer() {
        LivingEntity best = null;
        double bd = Double.MAX_VALUE;
        for (LivingEntity e : hero.level().getEntitiesOfClass(LivingEntity.class, hero.getBoundingBox().inflate(16), this::pursuer)) {
            double d = e.distanceToSqr(hero);
            if (d < bd) {
                bd = d;
                best = e;
            }
        }
        return best;
    }

    /** Death, a reload or a release: the escape ends where it is (the window, kept by the campaign, stays). */
    void reset() {
        if (escape != null) {
            escape = null;
            hero.setStateFlag(HeroActions.STATE_ESCAPING, false);
        }
        beatDone = false;
    }
}
