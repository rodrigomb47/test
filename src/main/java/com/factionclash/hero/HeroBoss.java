package com.factionclash.hero;

import com.factionclash.campaign.CampaignData;
import com.factionclash.campaign.HeroState;
import com.factionclash.hero.entity.HeroDecoy;
import com.factionclash.logic.hero.BossRules;
import com.factionclash.logic.hero.HeroActions;
import com.factionclash.logic.hero.HeroDef;
import com.factionclash.logic.hero.HeroDef.LineEvent;
import com.factionclash.logic.hero.HeroDef.Slot;
import com.factionclash.logic.hero.HeroDef.StatBonus;
import com.factionclash.logic.hero.HeroDef.Trigger;
import com.factionclash.logic.hero.HeroDef.WeakPoint;
import com.factionclash.logic.hero.HeroRules;
import com.factionclash.logic.hero.HeroTimings;
import com.factionclash.logic.hero.MovePlan.MoveStage;
import com.factionclash.logic.hero.PassiveBook;
import com.factionclash.logic.hero.UseLedger;
import com.factionclash.npc.FactionNpc;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntPredicate;

/**
 * A hero as a boss, part one (HEROES_V2 6.1-6.4, 6.7, 6.9, 6.10; task A6a): one mode in every fight, whoever the enemy
 * is. Nothing here reads whether anyone is a player.
 * <ul>
 * <li><b>The fight</b> (6.1): the engaged enemies are the living enemies that struck at the hero in the last 15 s or
 * are its target, within 32 blocks ({@link BossRules.Engagement}, kept by entity id); the fight runs from the first
 * until 10 s after the last ({@link BossRules.Fight}). It drives the move gap, the poise bar's reset and the
 * {@code enemies_engaged} condition.</li>
 * <li><b>The hurt cooldown per attacker</b> (6.2, 6.16): vanilla's half-second cooldown is kept per attacking entity
 * ({@link BossRules.HurtLedger}; every hit with no attacker shares one record), so six soldiers' blows all land.
 * {@link HeroEntity#hurt} sets the source's own record on the entity before vanilla's check and stores what vanilla made
 * of it after. A hero's own damage keeps v1's save-and-restore and is not counted here.</li>
 * <li><b>The incoming path</b> ({@link #incoming}, from the damage event after armour, resistances and a counter's
 * absorb): the damage-taken windows (a stagger +25%, a recovery +15% and +15% more from behind, a transition's super
 * armour -50%; the {@code recovery}, {@code back}, {@code iron} and {@code gold} weak points as {@link BossRules#land}
 * holds them), then the per-hit cap for a hit with any attacking entity ({@code boss.max_hit_share}, 12%), the excess
 * and the hit's weapon factor to poise. Damage with no attacker is not capped; {@code /kill} and the void are never
 * touched.</li>
 * <li><b>Poise and stagger</b> (6.9): every hit fills the bar whoever lands it (a hero's damage over time does not), a
 * parry adds 25 (a shield raised 5-11 ticks before a heavy move's hit, a player's or an NPC's); at full poise the hero
 * staggers: the running move is interrupted, it kneels 2.5 s ({@code combat/HeroHoldGoal}) taking +25%, stars over
 * its head, {@code on_stagger}. {@code boss.poise: 0} is no bar.</li>
 * <li><b>Phases</b> (6.7): {@link BossRules.Phases}; the transition plays between moves (never inside one or a
 * stagger) for its 0.8-2.0 s: super armour (half damage, no stagger, a glint), the hero holds still, the shockwave
 * pushes everyone within its radius back (hazard-aware, no damage), the phase line, face and look ({@code DATA_STATE}'s
 * phase, which the renderer reads: {@code p2_}/{@code p3_} bones, the phase texture, glowing eyes), the
 * {@code phase.N} clip, {@code on_phase} and the transition's effects; at its end the phase's stats (held, every
 * phase reached adds its own), its state (for the rest of the fight) and adds apply and the new phase's gap begins.</li>
 * <li><b>Reset</b> (6.10): 30 s out of a fight and unhurt, it heals 1% of its base (v1) health a second; the phases
 * re-arm 10% above their starts, their look, stats and states going with them.</li>
 * </ul>
 * Task A6b adds the rest of section 6, still one mode against anyone:
 * <ul>
 * <li><b>The intro</b> (6.13): its first fight against people (a player, a faction NPC, a hero) in an in-game day
 * opens, once the hero is free, with its {@code intro} clip (1.0-1.5 s), super armour (half damage, no stagger), its
 * intro line and {@code on_intro}; the fight's opening gap counts from its end. Nothing for monsters alone.</li>
 * <li><b>Enrage</b> (6.10): after {@code boss.enrage.after} seconds of fighting (180, 240 while leading), or at once by
 * a hard trigger ({@code ally_hero_died}, {@code heart_attacked}, {@code favoured_hurt}, by anyone): +15% damage (all
 * bonuses within +25%), the move gap x0.7, the poise maximum x1.25, the bar red, an angry face and line,
 * {@code on_enrage}; it ends with the fight.</li>
 * <li><b>Event weak points</b> (6.9), each caused alike by a player and an NPC: a snowball or water douses (a fire hero's
 * state ends, any other flinches 1 s; 10% less damage for 5 s; once in 20 s), a bell rung within 8 blocks stuns 1 s
 * (once in 30 s), a light of 12 or more placed by an enemy within 4 blocks weakens 10% for 10 s, a falling critical
 * from above its eyes staggers at once, its own charge into a wall staggers at once.</li>
 * <li><b>Arena</b> (6.12): the ceiling and the open sky sampled every 2 s while fighting; a target no link has reached
 * for 30 s is given up with a taunt.</li>
 * <li><b>The end of a fight</b> ({@link HeroEscape}): escape or last stand, never a yield; the winning side of a death
 * (the last person that hurt it in its last 15 s) for {@link HeroRewards}.</li>
 * </ul>
 * The bar and the title are {@link HeroBossBars}'.
 */
public final class HeroBoss {
    /** Engaged enemies are within this many blocks (6.1). */
    public static final double ENGAGE_RADIUS = BossRules.ENGAGE_RADIUS;
    /** The source a phase's stat bonuses carry in the hero's status book. */
    static final String PHASE_SOURCE = "phase";

    private final HeroEntity hero;
    private final HeroCombat combat;
    private final BossRules.Fight fight = new BossRules.Fight();
    private final BossRules.Engagement engagement = new BossRules.Engagement();
    private final BossRules.HurtLedger<Integer> hurts = new BossRules.HurtLedger<>();
    private final IntPredicate engagedNow = this::engagedNow;
    private BossRules.Phases phases;
    private HeroDef phasesFor;
    private int loadedPhase;
    private Set<WeakPoint> weak = EnumSet.noneOf(WeakPoint.class);
    private HeroDef weakFor;
    /** Poise this hit's damage event worked out; it lands after the hurt (so a stagger never strikes into it). */
    private double pendingPoise;
    /** A parry caught inside the blocker's hurt: its 25 poise lands on the hero's next tick. */
    private boolean parried;
    /** The last tick damage landed on it (-1: never), for the out-of-fight healing. */
    private long lastHurt = -1;
    /** The transition playing (null: none) and the mood it holds. */
    private HeroDef.Transition transition;
    private int transitionPhase;
    /** The last hit through the incoming path (tests, the operator's sheet). */
    private BossRules.Landed lastLanded;
    private int staggerCount, transitionCount;
    /** A6b: how a losing fight ends (escape or last stand). */
    private final HeroEscape escape;
    /** A6b: the intro playing (its end, -1: none), the in-game day of the last one, an intro looked for. */
    private long introEnds = -1;
    private long introDay = Long.MIN_VALUE;
    private boolean introCheck;
    /** A6b: enraged now; a hard trigger fired in this fight. */
    private boolean enraged, hardEnraged;
    /** A6b: the event weak points' clocks, a flinch's end, a douse's state to come back, a pending stagger. */
    private long lastDouseAt, lastBellAt, flinchUntil = Long.MIN_VALUE / 4, douseEnds = -1;
    private String dousedState;
    private boolean wasWet, staggerPending, wallStagger;
    /** A6b: a face held for a moment (the last stand's, the enrage's), its end. */
    private int heldMood = -1;
    private long heldMoodUntil;
    /** A6b: the arena's sample (6.12) and the unreachable clock. */
    private final BossRules.UnreachableClock unreachable = new BossRules.UnreachableClock();
    private boolean lowCeiling, openSky = true, arenaSampled;
    /** A6b: the last person that hurt it (the winning side of a death), when and their name. */
    private long lastPersonBlow = -1;
    private HeroRules.Side lastPersonSide;
    private String lastPersonName = "";
    private int intros, enrages, reactions, douses, bells, lights, disengages;

    HeroBoss(HeroEntity hero, HeroCombat combat) {
        this.hero = hero;
        this.combat = combat;
        this.escape = new HeroEscape(hero, combat);
    }

    /** How its losing fights end (task A6b). */
    public HeroEscape escape() {
        return escape;
    }

    private long now() {
        return hero.level().getGameTime();
    }

    // ------------------------------------------------------------------ the fight (6.1)

    /** Whether a fight is on (from the first engaged enemy until 10 s after the last). */
    public boolean fighting() {
        return fight.active();
    }

    /** The game time the fight began (its first engaged enemy). */
    public long fightStart() {
        return fight.start();
    }

    /** Seconds of the fight so far (0 out of one): the enrage clock (A6b). */
    public double fightSeconds() {
        return fight.seconds(now());
    }

    /** The engaged enemies at the last count, of every kind (the {@code enemies_engaged} condition). */
    public int engaged() {
        return engagement.engaged();
    }

    /**
     * Something struck at the hero (from {@link HeroEntity#hurt}, before anything else, a barrier's block included): an
     * attacking entity is remembered for 15 s.
     */
    void struckBy(DamageSource src) {
        if (src.getEntity() instanceof LivingEntity a && a != hero) {
            engagement.hurtBy(a.getId(), now());
        }
    }

    private boolean engagedNow(int id) {
        Entity e = hero.level().getEntity(id);
        return e instanceof LivingEntity l && l.isAlive() && !l.isRemoved() && HeroForms.enemy(hero, l)
                && l.distanceToSqr(hero) <= ENGAGE_RADIUS * ENGAGE_RADIUS;
    }

    /**
     * Every tick: counts the engaged enemies and moves the fight clock. While it runs from a lost fight no fight starts
     * (6.13; a cornered hero fights back and its fight clock runs again).
     */
    BossRules.FightEvent tickFight() {
        if (escape.fleeing()) {
            engagement.clear();
            if (fight.end(now())) {
                fightEnded();
                return BossRules.FightEvent.ENDED;
            }
            return BossRules.FightEvent.NONE;
        }
        LivingEntity t = hero.getTarget();
        int n = engagement.count(now(), t == null ? -1 : t.getId(), engagedNow);
        BossRules.FightEvent ev = fight.update(now(), n > 0);
        if (ev == BossRules.FightEvent.STARTED) {
            introCheck = !escape.running();
            hardEnraged = false;
            escape.newFight();
            unreachable.reset();
            arenaSampled = false;
        } else if (ev == BossRules.FightEvent.ENDED) {
            fightEnded();
        }
        return ev;
    }

    /** A fight ended (10 s after its last enemy, or an escape): the enrage and the intro go with it. */
    private void fightEnded() {
        unenrage();
        hardEnraged = false;
        introCheck = false;
        if (introEnds >= 0) {
            endIntro();
        }
    }

    /**
     * The feet height of the highest engaged enemy standing on a block (NaN: none), whoever it is: the fight's altitude
     * ceiling counts from it (MOBILITY_AND_WORK 2.3, task A10), so an enemy on a pillar is always in a flyer's reach.
     */
    public double highestEngagedFeet() {
        double top = Double.NaN;
        LivingEntity t = hero.getTarget();
        if (t != null && t.onGround() && engagedNow(t.getId())) {
            top = t.getY();
        }
        for (int i = 0; i < engagement.remembered(); i++) {
            Entity e = hero.level().getEntity(engagement.id(i));
            if (e instanceof LivingEntity l && l.onGround() && engagedNow(l.getId()) && !(l.getY() <= top)) {
                top = l.getY();
            }
        }
        return top;
    }

    /** The UUIDs of the enemies engaged now (whom an escape flees). */
    List<java.util.UUID> engagedEnemies() {
        List<java.util.UUID> out = new java.util.ArrayList<>();
        for (int i = 0; i < engagement.remembered(); i++) {
            Entity e = hero.level().getEntity(engagement.id(i));
            if (e instanceof LivingEntity l && engagedNow(l.getId())) {
                out.add(l.getUUID());
            }
        }
        return out;
    }

    /** Operators and tests: the fight began {@code ticks} earlier (the soft enrage without waiting its minutes). */
    public void backdateFight(int ticks) {
        fight.backdate(ticks);
    }

    // ------------------------------------------------------------------ the hurt cooldown per attacker (6.2)

    /** The ledger's key for a hit: the attacking entity's id (an arrow's shooter), null for every hit with no attacker. */
    static Integer hurtKey(DamageSource src) {
        Entity a = src.getEntity();
        return a == null ? null : a.getId();
    }

    /** The source's record inside its 10-tick window, or null. */
    BossRules.HurtRecord hurtRecord(Integer key, long now) {
        return hurts.record(key, now);
    }

    /** What vanilla's check made of the hit: a new window, or the excess's new amount. */
    void hurtLanded(Integer key, BossRules.HurtRecord r) {
        hurts.remember(key, r);
    }

    // ------------------------------------------------------------------ the incoming path (6.2, 6.9)

    private HeroDef def() {
        return combat.def();
    }

    private Set<WeakPoint> weak(HeroDef d) {
        if (weakFor != d) {
            Set<WeakPoint> w = EnumSet.noneOf(WeakPoint.class);
            for (HeroDef.WeakSpot s : d.v2().boss().weakPoints()) {
                w.add(s.point());
            }
            weak = w;
            weakFor = d;
        }
        return weak;
    }

    /**
     * One hit's damage on the hero after armour, resistances and a counter (the damage event): the damage-taken window,
     * the per-hit cap for a hit with an attacking entity and its poise (stored for {@link #afterHurt}). Returns what
     * lands. Never touches {@code /kill} or the void; a dormant hero is a plain soldier.
     */
    float incoming(DamageSource src, float amount) {
        HeroDef d = def();
        if (d == null || d.v2() == null || !(amount > 0) || src.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return amount;
        }
        Entity attacker = src.getEntity();
        boolean hasAttacker = attacker != null && attacker != hero;
        // MOBILITY_AND_WORK 2.4 (task A10): a downed hero's fall counts as one hit for the cap, whoever downed it
        boolean downedFall = src.is(DamageTypeTags.IS_FALL) && hero.movement().downedFall();
        BossRules.Window w = new BossRules.Window(combat.staggered(), combat.stage() == MoveStage.RECOVERY, behind(src),
                armoured(), hero.movement().downed(), tier(src));
        BossRules.Landed l = BossRules.land(amount, hasAttacker || downedFall, hero.getMaxHealth(), d.v2().boss().maxHitShare(), w,
                weak(d), weaponKind(src));
        lastLanded = l;
        if (hasAttacker && !HeroDamage.isDot(src)) {
            pendingPoise += l.poise();
        }
        return (float) l.health();
    }

    /** Whoever struck stands more than 120 degrees from the hero's facing (its body). */
    private boolean behind(DamageSource src) {
        Vec3 at = src.getSourcePosition();
        return at != null && BossRules.behind(hero.yBodyRot, at.x - hero.getX(), at.z - hero.getZ());
    }

    /** The attacker's main-hand weapon tier, for a melee blow (the {@code iron} and {@code gold} weak points). */
    private static BossRules.WeaponTier tier(DamageSource src) {
        if (!(src.getEntity() instanceof LivingEntity a) || src.getDirectEntity() != a || src.is(HeroDamage.HERO)) {
            return BossRules.WeaponTier.NONE;
        }
        ItemStack s = a.getMainHandItem();
        if (!(s.getItem() instanceof TieredItem t)) {
            return BossRules.WeaponTier.NONE;
        }
        if (t.getTier() == Tiers.IRON) {
            return BossRules.WeaponTier.IRON;
        }
        if (t.getTier() == Tiers.GOLD) {
            return BossRules.WeaponTier.GOLD;
        }
        if (t.getTier() == Tiers.WOOD) {
            return BossRules.WeaponTier.WOOD;
        }
        if (t.getTier() == Tiers.STONE) {
            return BossRules.WeaponTier.STONE;
        }
        if (t.getTier() == Tiers.DIAMOND) {
            return BossRules.WeaponTier.DIAMOND;
        }
        return t.getTier() == Tiers.NETHERITE ? BossRules.WeaponTier.NETHERITE : BossRules.WeaponTier.OTHER;
    }

    /**
     * The poise factor of what struck (6.9), the same whoever holds it: a sword 1.0, an axe 1.6, a mace 2.0, a trident
     * 1.2 (thrown or in the hand), an arrow 0.6, a crossbow bolt 0.8; a hero's move and anything else 1.0.
     */
    static BossRules.WeaponKind weaponKind(DamageSource src) {
        if (src.is(HeroDamage.HERO)) {
            return BossRules.WeaponKind.OTHER;
        }
        Entity direct = src.getDirectEntity();
        if (direct instanceof ThrownTrident) {
            return BossRules.WeaponKind.TRIDENT;
        }
        if (direct instanceof AbstractArrow arrow) {
            return arrow.shotFromCrossbow() ? BossRules.WeaponKind.BOLT : BossRules.WeaponKind.ARROW;
        }
        if (direct instanceof LivingEntity l && direct == src.getEntity()) {
            var item = l.getMainHandItem().getItem();
            if (item instanceof MaceItem) {
                return BossRules.WeaponKind.MACE;
            }
            if (item instanceof AxeItem) {
                return BossRules.WeaponKind.AXE;
            }
            if (item instanceof TridentItem) {
                return BossRules.WeaponKind.TRIDENT;
            }
            if (item instanceof SwordItem) {
                return BossRules.WeaponKind.SWORD;
            }
        }
        return BossRules.WeaponKind.OTHER;
    }

    /** The last hit through the incoming path (null before any). */
    public BossRules.Landed lastLanded() {
        return lastLanded;
    }

    /**
     * After a hurt (from {@link HeroEntity#hurt}): the poise it worked out lands now, the health it lost counts, the
     * person who hurt it is remembered for the winning side, and under its threshold the end of the fight is decided
     * (6.13: read after every hit that lands).
     */
    void afterHurt(float lost, DamageSource src) {
        if (lost > 0) {
            lastHurt = now();
            hurtBy(src);
        }
        double p = pendingPoise;
        pendingPoise = 0;
        if (p > 0 && hero.isAlive()) {
            combat.poiseDamage(p);
        }
        if (lost > 0 && hero.isAlive()) {
            boolean enemy = src.getEntity() instanceof LivingEntity l && l != hero && HeroForms.enemy(hero, l);
            escape.check(fight.active() || enemy);
        }
    }

    /**
     * A blow that hurt it (or killed it: the death comes inside vanilla's hurt, before {@link #afterHurt}): a person's
     * (a player, a faction NPC or a hero) is remembered with its side for the rewards of a death.
     */
    void hurtBy(DamageSource src) {
        Entity a = src == null ? null : src.getEntity();
        if (a != null && a != hero && (a instanceof Player || a instanceof FactionNpc n && !n.isDisplayOnly())) {
            HeroRules.Side side = side(a);
            if (side != null) {
                lastPersonBlow = now();
                lastPersonSide = side;
                lastPersonName = HeroEntity.nameOf(a);
            }
        }
    }

    /** The side of a person for the rewards: a faction (a member player is of it), or a factionless player alone. */
    static HeroRules.Side side(Entity person) {
        if (person instanceof FactionNpc n) {
            return HeroRules.Side.faction(n.getFaction().id);
        }
        if (person instanceof Player p && p.getServer() != null) {
            var f = CampaignData.get(p.getServer()).players.get(p.getUUID());
            return f != null ? HeroRules.Side.faction(f.id) : HeroRules.Side.loner(p.getUUID().toString());
        }
        return null;
    }

    /** The side that won its death at {@code now} (null: no person hurt it in its last 15 s). */
    HeroRules.Side winningSide(long now) {
        if (lastPersonSide == null) {
            return null;
        }
        return HeroRules.winningSide(List.of(new HeroRules.Blow(lastPersonBlow, lastPersonSide)), now);
    }

    /** The name of the last person that hurt it (the crest's winner). */
    String winnerName() {
        return lastPersonName;
    }

    /**
     * A shield blocked one of this hero's moves (inside the blocker's hurt): a heavy move (or heavier) blocked by a
     * shield raised 5-11 ticks before is a parry, 25 poise on the hero's next tick (6.9), whoever held it.
     */
    void blocked(LivingEntity blocker) {
        HeroDef.Move m = combat.runningMove();
        if (m != null && BossRules.parry(true, blocker.getTicksUsingItem(), m.cls())) {
            parried = true;
            HeroSounds.parry(blocker); // SOUND.md 7: at the one who parried, a player or an NPC alike
        }
    }

    // ------------------------------------------------------------------ stagger (6.9)

    /** It staggered (from {@link HeroCombat#stagger}): counted for the campaign. */
    void staggered() {
        staggerCount++;
        HeroState rec = hero.campaignRecord();
        if (rec != null) {
            rec.staggers++;
            CampaignData.get(hero.getServer()).setDirty();
        }
    }

    /** How many times it staggered since it was made (tests). */
    public int staggerCount() {
        return staggerCount;
    }

    /** Whether it is held still now: a stagger's kneel, a phase transition, its intro or a flinch. */
    public boolean held() {
        return combat.staggered() || transitioning() || introducing() || now() < flinchUntil || hero.movement().holdsStill();
    }

    /** Super armour of the boss layer: a transition or the intro (half damage, no stagger, 6.7, 6.13). */
    public boolean armoured() {
        return transitioning() || introducing();
    }

    // ------------------------------------------------------------------ phases (6.7)

    /** Its phases (null while it has no definition). */
    BossRules.Phases phases() {
        HeroDef d = def();
        if (d == null || d.v2() == null) {
            return null;
        }
        if (phases == null || phasesFor != d) {
            int keep = phases != null ? phases.reached() : loadedPhase;
            List<HeroDef.Phase> list = d.v2().boss().phases();
            Integer[] starts = new Integer[Math.max(1, list.size())];
            for (int i = 0; i < list.size(); i++) {
                starts[i] = list.get(i).at();
            }
            if (list.isEmpty()) {
                starts[0] = 100;
            }
            phases = new BossRules.Phases(List.of(starts));
            phasesFor = d;
            if (keep > 1) {
                phases.set(keep);
                showPhase(phases.shown());
            }
            loadedPhase = 0;
            if (transition != null) {
                endTransition(d); // a reload in a transition: it ends now, its phase applied
            }
        }
        return phases;
    }

    /** The phase its look and moves show (1 without a definition). */
    public int phase() {
        BossRules.Phases p = phases();
        return p == null ? 1 : p.shown();
    }

    /** Whether a phase transition plays now (super armour, no stagger, held still). */
    public boolean transitioning() {
        return transition != null && phases != null && phases.transitioning(now());
    }

    /** How many transitions it has played since it was made (tests). */
    public int transitionCount() {
        return transitionCount;
    }

    /** The mood a transition holds on its face (-1: none). */
    int mood() {
        if (transition != null && transition.face() != null) {
            return HeroActions.moodCode(transition.face());
        }
        return heldMood >= 0 && now() < heldMoodUntil ? heldMood : -1;
    }

    /** Holds a face for {@code ticks} (the last stand's determined face, the enrage's angry one). */
    void holdMood(int mood, int ticks) {
        heldMood = mood;
        heldMoodUntil = now() + Math.max(1, ticks);
    }

    private void showPhase(int p) {
        if (HeroActions.phase(hero.state()) != p) {
            hero.setState(HeroActions.withPhase(hero.state(), p));
        }
    }

    private void beginTransition(BossRules.Phases ph, HeroDef d) {
        int target = ph.reached();
        HeroDef.Phase phase = d.v2().boss().phases().get(Math.min(target, d.v2().boss().phases().size()) - 1);
        HeroDef.Transition tr = phase.transition() == null ? HeroDef.Transition.DEFAULT : phase.transition();
        int ticks = (int) Math.max(1, HeroRules.ticks(BossRules.transitionSeconds(tr.seconds())));
        int p = ph.begin(now(), ticks);
        transition = tr;
        transitionPhase = p;
        transitionCount++;
        showPhase(p);
        hero.setStateFlag(HeroActions.STATE_SUPER_ARMOUR, true);
        hero.getNavigation().stop();
        hero.playAction(HeroActions.actionCode(p >= 3 ? "phase.3" : "phase.2"), 1.0, 0);
        LineEvent event = p >= 3 ? LineEvent.PHASE_3 : LineEvent.PHASE_2;
        if (tr.line() != null && !tr.line().isBlank()) {
            hero.speak(event, tr.line());
        } else {
            hero.say(event);
        }
        shockwave(tr.shockRadius() > 0 ? tr.shockRadius() : BossRules.SHOCK_RADIUS,
                tr.shockStrength() > 0 ? tr.shockStrength() : BossRules.SHOCK_STRENGTH);
        if (!tr.effects().isEmpty()) {
            UseLedger ledger = UseLedger.of(Slot.PASSIVE, combat.abilityPower(), HeroEffects.powerScale(), 1);
            HeroUse use = combat.newUse(d, null, Slot.PASSIVE, HeroDef.Form.SELF, "phase." + p, ledger);
            HeroEffects.apply(use, tr.effects(), 0, List.of(hero), hero);
        }
        HeroState rec = hero.campaignRecord();
        if (rec != null && p > rec.phaseReached) {
            rec.phaseReached = p;
            CampaignData.get(hero.getServer()).setDirty();
        }
        double share = hero.getHealth() / Math.max(1F, hero.getMaxHealth());
        combat.trigger(new PassiveBook.Event(Trigger.ON_PHASE, null, share, null, p, false), hero.getTarget(), null);
        HeroSounds.phase(hero); // cues.phase as a notification, the shockwave and the super armour at the hero
    }

    /**
     * The transition's shockwave (6.7): everyone within {@code radius} but the hero, its own adds, display copies and
     * players outside the fight's modes (creative, spectator) is pushed back with the given strength, softened toward
     * lava or a drop for every receiver (6.12); no damage.
     */
    private void shockwave(double radius, double strength) {
        List<LivingEntity> near = hero.level().getEntitiesOfClass(LivingEntity.class, hero.getBoundingBox().inflate(radius),
                e -> e != hero && e.isAlive() && !(e instanceof ArmorStand)
                        && !(e instanceof FactionNpc n && n.isDisplayOnly())
                        && !(e instanceof HeroDecoy dc && dc.owner() == hero)
                        && !(e instanceof Player pl && (pl.isSpectator() || pl.isCreative()))
                        && e.distanceToSqr(hero) <= radius * radius);
        for (LivingEntity e : near) {
            HeroEffects.knockback(hero, e, strength, 0);
        }
        HeroEffects.particleRing(hero.level(), hero.position().add(0, 0.2, 0), radius, ParticleTypes.CLOUD);
    }

    /** The transition is over: the phase's stats, state and adds; the new phase's gap begins. */
    private void endTransition(HeroDef d) {
        transition = null;
        if (d.v2().boss().phases().isEmpty()) {
            hero.setStateFlag(HeroActions.STATE_SUPER_ARMOUR, false);
            return;
        }
        if (!(combat.running() && combat.runningMove().superArmor())) {
            hero.setStateFlag(HeroActions.STATE_SUPER_ARMOUR, false);
        }
        int p = transitionPhase;
        applyPhaseStats(d, p);
        HeroDef.Phase phase = d.v2().boss().phases().get(Math.min(p, d.v2().boss().phases().size()) - 1);
        String state = null;
        for (int i = Math.min(p, d.v2().boss().phases().size()); i >= 1 && state == null; i--) {
            List<String> s = d.v2().boss().phases().get(i - 1).states();
            state = s.isEmpty() ? null : s.get(0);
        }
        if (state != null && !state.equals(combat.stateName())) {
            combat.enterState(state, BossRules.PHASE_HOLD_SECONDS);
        }
        for (HeroDef.AddSpec a : phase.adds()) {
            combat.spawnAdds(a.id(), a.count());
        }
        var s = combat.selector();
        if (s != null) {
            s.moveEnded(now(), p, hero.stateFlag(HeroActions.STATE_ENRAGED));
        }
    }

    /** Every phase up to {@code p} adds its own stat bonuses, held; those of deeper phases go. */
    private void applyPhaseStats(HeroDef d, int p) {
        List<HeroDef.Phase> list = d.v2().boss().phases();
        HeroStatus status = HeroStatus.of(hero);
        long now = now();
        boolean any = false;
        for (int i = 1; i <= list.size(); i++) {
            for (StatBonus b : list.get(i - 1).stats()) {
                String key = "phase." + i + "." + b.stat().id();
                if (i <= p) {
                    status.book().stat(PHASE_SOURCE, key, b.stat(), b.percent(), 0, 1, now);
                    any = true;
                } else {
                    any |= status.book().removeStat(PHASE_SOURCE, key);
                }
            }
        }
        if (any) {
            status.book().consumeStatsDirty();
            HeroStatus.applyStats(hero, now);
        }
    }

    /** Re-armed to phase {@code p}: its look goes back, deeper phases' stats go, a deeper phase's state ends. */
    private void rearmed(HeroDef d, int p) {
        transition = null;
        hero.setStateFlag(HeroActions.STATE_SUPER_ARMOUR, false);
        showPhase(p);
        applyPhaseStats(d, p);
        String st = combat.stateName();
        if (st == null) {
            return;
        }
        List<HeroDef.Phase> list = d.v2().boss().phases();
        boolean ownedByLower = false, ownedByDeeper = false;
        for (int i = 1; i <= list.size(); i++) {
            if (list.get(i - 1).states().contains(st)) {
                if (i <= p) {
                    ownedByLower = true;
                } else {
                    ownedByDeeper = true;
                }
            }
        }
        if (ownedByDeeper && !ownedByLower) {
            combat.states.exit();
        }
    }

    // ------------------------------------------------------------------ the tick

    /** Every tick after the move runner: phases, the reset's healing and re-arm, a parry, the stagger's stars. */
    void tick() {
        long now = now();
        if (parried) {
            parried = false;
            combat.poiseDamage(BossRules.PARRY_POISE);
        }
        HeroDef d = def();
        BossRules.Phases ph = d == null ? null : phases();
        if (ph != null && hero.isAlive()) {
            double share = hero.getHealth() / Math.max(1F, hero.getMaxHealth());
            ph.health(share);
            if (transition != null && ph.ends(now)) {
                endTransition(d);
            }
            if (transition == null && ph.due() && !combat.running() && !combat.staggered() && !HeroStatus.stunned(hero)
                    && !introducing()) {
                beginTransition(ph, d);
            }
            if (!fight.active() && hero.tickCount % 20 == 0) {
                if (ph.rearm(share)) {
                    rearmed(d, ph.shown());
                }
                if (fight.mayHeal(now, lastHurt) && hero.getHealth() < hero.getMaxHealth()) {
                    hero.heal((float) (baseMaxHealth() * BossRules.HEAL_PERCENT_PER_SECOND / 100));
                }
            }
        }
        if (hero.level() instanceof ServerLevel sl) {
            if (combat.staggered() && hero.tickCount % 5 == 0) {
                // the stars of a stagger, circling over its head
                double a = hero.tickCount * 0.35, top = hero.getY() + hero.getBbHeight() + 0.25;
                for (int i = 0; i < 3; i++) {
                    double b = a + i * Math.PI * 2 / 3;
                    sl.sendParticles(ParticleTypes.CRIT, hero.getX() + Math.cos(b) * 0.45, top, hero.getZ() + Math.sin(b) * 0.45,
                            1, 0, 0, 0, 0);
                }
            }
            if (transition != null && hero.tickCount % 4 == 0) {
                // super armour's glint
                sl.sendParticles(ParticleTypes.ENCHANTED_HIT, hero.getX(), hero.getY(0.6), hero.getZ(), 3, 0.35, 0.5, 0.35, 0.02);
            }
        }
        if (hero.tickCount % 20 == 0) {
            hurts.prune(now);
        }
        if (d != null && d.v2() != null && hero.isAlive()) {
            tickA6b(d, now);
        }
        escape.tick();
    }

    /** Its v1 health: the boss maximum without the {@code boss_health} modifier (6.2). */
    double baseMaxHealth() {
        AttributeInstance a = hero.getAttribute(Attributes.MAX_HEALTH);
        AttributeModifier m = a == null ? null : a.getModifier(HeroEntity.BOSS_HEALTH);
        double k = m == null ? 0 : m.amount();
        return hero.getMaxHealth() / Math.max(0.01, 1 + k);
    }

    /** Death or a reload: the transition ends with the run (its super armour goes). */
    void reset() {
        if (transition != null) {
            transition = null;
            hero.setStateFlag(HeroActions.STATE_SUPER_ARMOUR, false);
        }
        pendingPoise = 0;
        parried = false;
        introEnds = -1;
        introCheck = false;
        unenrage();
        hardEnraged = false;
        staggerPending = false;
        flinchUntil = Long.MIN_VALUE / 4;
        dousedState = null;
        escape.reset();
    }

    // ------------------------------------------------------------------ A6b: intro, enrage, weak points, arena

    /** Every tick after the phases (a hero with a definition, alive): the A6b parts of the boss layer. */
    private void tickA6b(HeroDef d, long now) {
        // the intro (6.13): looked for once the hero is free in a fight's first moments
        if (introEnds >= 0 && now >= introEnds) {
            endIntro();
        }
        if (introCheck && fight.active()) {
            if (fight.seconds(now) > 3) {
                introCheck = false; // never free in time: this fight has no intro
            } else if (!combat.running() && !combat.staggered() && transition == null && !HeroStatus.stunned(hero)) {
                introCheck = false;
                long day = BossRules.day(hero.level().getDayTime());
                if (BossRules.introDue(lastIntroDay(), hero.level().getDayTime(), personEngaged(now))) {
                    startIntro(d, day, now);
                }
            }
        }
        // the end of a fight (6.13): read once a tick too (damage over time, a heal undone), never above the threshold
        if (fight.active()) {
            escape.check(true);
        }
        // enrage (6.10)
        if (fight.active() && !enraged && !escape.running()
                && BossRules.enraged(fight.seconds(now), BossRules.enrageAfter(d.v2().boss().enrage().after(), false), hardEnraged)) {
            enrage(d);
        }
        // a stagger a weak point caused inside another entity's hurt or the move runner lands here
        if (staggerPending) {
            staggerPending = false;
            staggerNow(wallStagger);
            wallStagger = false;
        }
        // a douse ended: a phase's state the douse put out comes back
        if (douseEnds >= 0 && now >= douseEnds) {
            douseEnds = -1;
            String st = dousedState;
            dousedState = null;
            if (st != null && fight.active() && combat.stateName() == null) {
                combat.enterState(st, BossRules.PHASE_HOLD_SECONDS);
            }
        }
        // water (6.9): stepping into water, rain or a bubble column, the rising edge only
        if (weak(d).contains(WeakPoint.WATER)) {
            boolean wet = hero.isInWaterRainOrBubble();
            if (wet && !wasWet) {
                douse(BossRules.Douse.WATER);
            }
            wasWet = wet;
        }
        // the arena (6.12): sampled every 2 s while fighting; the unreachable target given up after 30 s
        if (fight.active() && (!arenaSampled || hero.tickCount % BossRules.ARENA_SAMPLE_TICKS == 0)) {
            sampleArena();
        }
        if (fight.active() && hero.tickCount % 20 == 0) {
            LivingEntity t = hero.getTarget();
            com.factionclash.combat.Pursuit p = hero.pursuit();
            boolean out = t != null && t.isAlive() && p.target() == t && p.mode() != com.factionclash.combat.Pursuit.Mode.CHASE;
            if (unreachable.tick(t == null ? -1 : t.getId(), out, 20)) {
                disengage(t);
            }
        }
    }

    /** A person (a player, a faction NPC or a hero) among the engaged enemies: the intro is for people, not monsters. */
    private boolean personEngaged(long now) {
        LivingEntity t = hero.getTarget();
        if (person(t) && HeroForms.enemy(hero, t)) {
            return true;
        }
        return engagement.any(now, id -> {
            Entity e = hero.level().getEntity(id);
            return e instanceof LivingEntity l && person(l) && engagedNow(id);
        });
    }

    private static boolean person(Entity e) {
        return e instanceof Player p && !p.isSpectator() && !p.isCreative() || e instanceof FactionNpc n && !n.isDisplayOnly();
    }

    /** Whether the intro plays now. */
    public boolean introducing() {
        return introEnds >= 0 && now() < introEnds;
    }

    /** How many intros, enrages, reactions answered, douses, bell stuns, light weakenings and give-ups (tests). */
    public int intros() {
        return intros;
    }

    public int enrages() {
        return enrages;
    }

    public int reactions() {
        return reactions;
    }

    public int douses() {
        return douses;
    }

    public int bellStuns() {
        return bells;
    }

    public int lightWeakenings() {
        return lights;
    }

    public int disengages() {
        return disengages;
    }

    /** The day its intro last played: this entity's, or the hero's record's (a revival the same day is a new entity). */
    private long lastIntroDay() {
        HeroState rec = hero.campaignRecord();
        return rec != null && rec.introDay != Long.MIN_VALUE && (introDay == Long.MIN_VALUE || rec.introDay > introDay)
                ? rec.introDay : introDay;
    }

    private void startIntro(HeroDef d, long day, long now) {
        introDay = day;
        HeroState rec = hero.campaignRecord();
        if (rec != null) {
            rec.introDay = day;
            CampaignData.get(hero.getServer()).setDirty();
        }
        HeroTimings.Clip clip = HeroTimingFiles.clip(hero.entry(), "intro");
        int ticks = BossRules.introTicks(clip == null ? 0 : clip.ticks());
        introEnds = now + ticks;
        intros++;
        hero.setStateFlag(HeroActions.STATE_SUPER_ARMOUR, true);
        hero.getNavigation().stop();
        hero.playAction(HeroActions.actionCode("intro"), 1.0, 0);
        LivingEntity t = hero.getTarget();
        String enemy = t == null ? "" : HeroEntity.nameOf(t);
        HeroDef.Intro intro = d.v2().boss().intro();
        if (intro != null && intro.line() != null && !intro.line().isBlank()) {
            hero.speak(LineEvent.INTRO, intro.line().replace("{enemy}", enemy).replace("{player}", enemy));
        } else {
            hero.say(LineEvent.INTRO, java.util.Map.of("enemy", enemy), null);
        }
        combat.trigger(PassiveBook.Event.of(Trigger.ON_INTRO), t, null);
    }

    private void endIntro() {
        introEnds = -1;
        if (transition == null && !(combat.running() && combat.runningMove().superArmor())) {
            hero.setStateFlag(HeroActions.STATE_SUPER_ARMOUR, false);
        }
        var s = combat.selector();
        if (s != null && fight.active()) {
            s.introEnded(now()); // the fight's opening gap counts from the end of the intro (6.3)
        }
    }

    /** Operators and tests: today's intro has played (or not, {@code false}: the next fight against people opens with it). */
    public void introPlayedToday(boolean played) {
        introDay = played ? BossRules.day(hero.level().getDayTime()) : Long.MIN_VALUE;
        HeroState rec = hero.campaignRecord();
        if (rec != null) {
            rec.introDay = introDay;
        }
    }

    // ------------------------------------------------------------------ enrage (6.10)

    /** Whether it is enraged now. */
    public boolean enraged() {
        return enraged;
    }

    /**
     * A hard enrage trigger happened ({@code ally_hero_died}, {@code heart_attacked}, {@code favoured_hurt}, by a player or
     * an NPC alike): a fighting hero whose file names it enrages at once. True when it applies.
     */
    public boolean hardTrigger(HeroDef.EnrageTrigger t) {
        HeroDef d = def();
        if (d == null || d.v2() == null || !fight.active() || escape.running() || !d.v2().boss().enrage().triggers().contains(t)) {
            return false;
        }
        hardEnraged = true;
        return true;
    }

    /** The enrage source and key in the status book. */
    static final String ENRAGE_SOURCE = "enrage", ENRAGE_KEY = "enrage.damage_dealt";

    private void enrage(HeroDef d) {
        enraged = true;
        enrages++;
        hero.setStateFlag(HeroActions.STATE_ENRAGED, true);
        HeroStatus status = HeroStatus.of(hero);
        long now = now();
        double other = Math.max(0, HeroStatus.percent(hero, HeroDef.Stat.DAMAGE_DEALT));
        int bonus = (int) Math.round(BossRules.enrageBonus(other));
        if (bonus > 0) {
            status.book().stat(ENRAGE_SOURCE, ENRAGE_KEY, HeroDef.Stat.DAMAGE_DEALT, bonus, 0, 1, now);
        }
        BossRules.Poise p = combat.poise();
        if (p != null) {
            p.enraged(true);
        }
        String line = d.v2().boss().enrage().line();
        if (line != null && !line.isBlank()) {
            hero.speak(LineEvent.ENRAGE, line);
        } else {
            hero.say(LineEvent.ENRAGE);
        }
        holdMood(HeroActions.moodCode("angry"), 60);
        HeroSounds.enraged(hero);
        combat.trigger(PassiveBook.Event.of(Trigger.ON_ENRAGE), hero.getTarget(), null);
        if (hero.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.ANGRY_VILLAGER, hero.getX(), hero.getY() + hero.getBbHeight(), hero.getZ(), 4, 0.4, 0.2, 0.4, 0);
        }
        com.factionclash.FactionClash.LOGGER.info("[Heroes] {} enrages after {} s{}", hero.heroId(), Math.round(fight.seconds(now)),
                hardEnraged ? " (a hard trigger)" : "");
    }

    private void unenrage() {
        if (!enraged) {
            return;
        }
        enraged = false;
        hero.setStateFlag(HeroActions.STATE_ENRAGED, false);
        HeroStatus st = HeroStatus.peek(hero);
        if (st != null && st.book().removeStat(ENRAGE_SOURCE, ENRAGE_KEY)) {
            st.book().consumeStatsDirty();
        }
        BossRules.Poise p = combat.poise();
        if (p != null) {
            p.enraged(false);
        }
    }

    // ------------------------------------------------------------------ the event weak points (6.9)

    /** Whether the hero has this weak point (its definition's). */
    public boolean weakTo(WeakPoint w) {
        HeroDef d = def();
        return d != null && d.v2() != null && weak(d).contains(w);
    }

    /**
     * A snowball hit it or water reached it (a thrown water bottle, stepping into water, rain, a bubble column), thrown
     * or caused by a player or an NPC alike: with the matching weak point, at most once in 20 s, a fire hero's state ends
     * (and a phase's comes back when the douse ends), any other flinches 1 s (a wind-up is cut); either deals 10% less
     * for 5 s. Returns whether it worked.
     */
    public boolean douse(BossRules.Douse kind) {
        HeroDef d = def();
        long now = now();
        if (d == null || d.v2() == null || !hero.isAlive() || !BossRules.douses(weak(d), kind, now, lastDouseAt)) {
            return false;
        }
        lastDouseAt = now;
        douses++;
        BossRules.DouseEffect fx = BossRules.douse(d.element() == HeroDef.Element.FIRE);
        if (fx.endsState() && combat.stateName() != null) {
            String st = combat.stateName();
            combat.states.exit();
            if (phaseState(d, st)) {
                dousedState = st;
                douseEnds = now + fx.ticks();
            }
        }
        if (fx.flinches()) {
            flinchUntil = now + BossRules.FLINCH_TICKS;
            combat.flinch();
        }
        HeroStatus.of(hero).book().stat("weak", "douse." + HeroDef.Stat.DAMAGE_DEALT.id(), HeroDef.Stat.DAMAGE_DEALT,
                (int) Math.round(fx.dealtPercent()), fx.ticks() / 20.0, 1, now);
        boolean own = HeroSounds.weakPoint(hero, kind == BossRules.Douse.SNOWBALL ? WeakPoint.SNOWBALL : WeakPoint.WATER);
        if (hero.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.SPLASH, hero.getX(), hero.getY(0.6), hero.getZ(), 12, 0.3, 0.4, 0.3, 0.1);
            if (!own) {
                // no sound of its own for it: the vanilla hiss of water on fire tells it
                sl.playSound(null, hero.getX(), hero.getY(), hero.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.HOSTILE, 0.7F, 1.2F);
            }
        }
        return true;
    }

    /** Whether a phase of the hero names this state (it holds for the fight, so a douse only puts it out for a while). */
    private boolean phaseState(HeroDef d, String st) {
        for (HeroDef.Phase ph : d.v2().boss().phases()) {
            if (ph.states().contains(st)) {
                return true;
            }
        }
        return false;
    }

    /** A bell rang at {@code distance} blocks (by anyone): with the {@code bell} weak point, a 1 s stun once in 30 s. */
    public boolean bellRung(double distance) {
        HeroDef d = def();
        long now = now();
        if (d == null || d.v2() == null || !hero.isAlive() || !BossRules.bellStuns(weak(d), distance, now, lastBellAt)) {
            return false;
        }
        lastBellAt = now;
        if (HeroStatus.stun(hero, BossRules.BELL_STUN_TICKS / 20.0)) {
            bells++;
            HeroSounds.weakPoint(hero, WeakPoint.BELL);
            return true;
        }
        return false;
    }

    /**
     * {@code placer} put a block of {@code emission} light at {@code distance} blocks: an enemy's (a player's or an NPC's
     * alike) of 12 or more within 4 weakens a hero with the {@code light} weak point 10% for 10 s (a new light renews it).
     */
    public boolean lightPlaced(Entity placer, int emission, double distance) {
        HeroDef d = def();
        if (d == null || d.v2() == null || !hero.isAlive() || !(placer instanceof LivingEntity l) || !HeroForms.enemy(hero, l)
                || !BossRules.lightWeakens(weak(d), emission, distance)) {
            return false;
        }
        lights++;
        HeroStatus.of(hero).book().stat("weak", "light." + HeroDef.Stat.DAMAGE_DEALT.id(), HeroDef.Stat.DAMAGE_DEALT,
                (int) -Math.round(BossRules.LIGHT_WEAKEN_PERCENT), BossRules.LIGHT_WEAKEN_TICKS / 20.0, 1, now());
        HeroSounds.weakPoint(hero, WeakPoint.LIGHT);
        if (hero.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.SMOKE, hero.getX(), hero.getY(0.7), hero.getZ(), 8, 0.3, 0.4, 0.3, 0.02);
        }
        return true;
    }

    /**
     * A critical hit on it ({@code crit_above}): from an attacker falling from above its eyes, a player's or an NPC's
     * jump critical alike, it staggers at once (on its next tick: the hit is still being worked out).
     */
    public boolean criticalHit(LivingEntity attacker) {
        HeroDef d = def();
        if (d == null || d.v2() == null || !hero.isAlive()) {
            return false;
        }
        boolean falling = attacker.fallDistance > 0 && !attacker.onGround();
        if (BossRules.critAboveStaggers(weak(d), true, falling, attacker.getY(), hero.getEyeY())) {
            staggerPending = true;
            HeroSounds.weakPoint(hero, WeakPoint.CRIT_ABOVE);
            return true;
        }
        return false;
    }

    /**
     * A stagger asked for from outside the incoming path (its barrier's focus killed, 6.6): on its next tick, through the
     * same protections as a weak point's ({@link #staggerNow}).
     */
    void staggerSoon() {
        staggerPending = true;
    }

    /** Its own charge ended against a wall ({@code charge_wall}): it staggers at once, after the move runner's tick. */
    void chargeWall() {
        if (weakTo(WeakPoint.CHARGE_WALL)) {
            staggerPending = true;
            wallStagger = true;
            HeroSounds.weakPoint(hero, WeakPoint.CHARGE_WALL);
        }
    }

    /**
     * A weak point's stagger: at once, unless a transition, the intro, an ultimate's wind-up or a {@code super_armor} move
     * protects it (6.9); a charge into a wall staggers through its own charge's armour (it is that charge that ended).
     */
    private void staggerNow(boolean throughOwnMove) {
        if (hero.isAlive() && !armoured() && !combat.staggered() && (throughOwnMove || !combat.protectedFromStagger())) {
            combat.stagger();
        }
    }

    // ------------------------------------------------------------------ the arena (6.12)

    private void sampleArena() {
        arenaSampled = true;
        BlockPos feet = hero.blockPosition();
        lowCeiling = solid(feet.above(2)) || solid(feet.above(3));
        openSky = hero.level().canSeeSky(feet.above());
    }

    private boolean solid(BlockPos p) {
        return !hero.level().getBlockState(p).getCollisionShape(hero.level(), p).isEmpty();
    }

    /** The sampled ceiling: 3 blocks or less over its head (no leap, no knock-up); null out of a fight (sense it now). */
    public Boolean sampledLowCeiling() {
        return fight.active() && arenaSampled ? lowCeiling : null;
    }

    /** The sampled open sky over it (a fight's lightning and rain look outdoors). */
    public boolean openSky() {
        return openSky;
    }

    /** A target no link has reached for 30 s: a taunt, it lets it be for a while, and its fight resets (6.12). */
    private void disengage(LivingEntity t) {
        disengages++;
        unreachable.reset();
        hero.speak(LineEvent.SMALL_TALK, "Hiding up there? Then stay there.");
        hero.pursuit().giveUp(t);
        hero.setTarget(null);
        hero.getNavigation().stop();
        com.factionclash.FactionClash.LOGGER.info("[Heroes] {} gives up on {}: out of reach for 30 s", hero.heroId(),
                HeroEntity.nameOf(t));
    }

    /** A reaction's answer started (6.8): the operator's log line counts them. */
    void reacted(HeroDef.Reaction r, HeroDef.Move m) {
        reactions++;
        com.factionclash.FactionClash.LOGGER.info("[Heroes] reaction {} {} -> {}", hero.heroId(), r.when().type().id(), m.id());
    }

    // ------------------------------------------------------------------ an escape (6.13)

    /** Its escape began: the fight ends for it (the bar shows "fled"), the enrage and the intro with it. */
    void escapeStarted() {
        engagement.clear();
        fight.end(now());
        fightEnded();
        HeroBossBars.end(hero, true);
    }

    // ------------------------------------------------------------------ saving

    /**
     * Saves the last phase whose transition has played (a transition in progress or still waiting plays again after a
     * load, from the health the hero has then, so its state and stats are never skipped).
     */
    void save(CompoundTag h) {
        int p = phases == null ? loadedPhase : transition != null ? transitionPhase - 1 : phases.shown();
        if (p > 1) {
            h.putInt("phase", p);
        }
        if (introDay != Long.MIN_VALUE) {
            h.putLong("introDay", introDay);
        }
    }

    void load(CompoundTag h) {
        introDay = h.contains("introDay") ? h.getLong("introDay") : Long.MIN_VALUE;
        loadedPhase = h.getInt("phase");
        phases = null;
        phasesFor = null;
        transition = null;
    }
}
