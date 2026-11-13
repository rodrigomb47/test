package com.factionclash.logic.hero;

import com.factionclash.logic.AffixRoller;
import com.factionclash.logic.hero.HeroCatalogue.GrowthStep;
import com.factionclash.logic.hero.HeroDef.Amount;
import com.factionclash.logic.hero.HeroDef.Element;
import com.factionclash.logic.hero.HeroDef.Growth;
import com.factionclash.logic.hero.HeroDef.LineEvent;
import com.factionclash.logic.hero.HeroDef.Rarity;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.Set;

/**
 * The heroes' runtime arithmetic, in one place so the game code only gathers inputs (HERO_FORMAT.md "v1"; HEROES_V2
 * 3.4, 6.2 and 8): the damage formula and its clamps (v1 6.3), Amounts at hit time with the gear cap (6.2, 12), element
 * multipliers, energy (14), {@code cooldown_cut} (13.5), growth and ability power per level (5.3, 22.2), combat XP
 * (22.1), the dialogue rate limit (23), rarity draws (25.3; the leader draft's weights, HEROES_V2 5.2), the revival
 * time with the owner's ten-minute rule and the death-related abilities (HEROES_V2 3.4), the rewards at a death
 * (6.13) and the war power (8.1). Pure Java, unit-tested ({@code HeroRulesTest}). Numbers marked PROPOSED in the
 * designs go to BALANCE.md when the game uses them.
 *
 * <p><b>Equal treatment</b> (the owner's rule of 2026-11-12, HEROES_V2 6): nothing here reads whether an attacker or
 * a target is a player. A hero's hit goes through the same formula, the same clamps and {@code heroes.maxHit} against
 * a player, an NPC, a hero or a monster; there is no multiplier against players and no protection of them. The slot
 * caps of v1 16.1 are applied to the definition when it loads ({@link HeroBalance}), so they too hold against any
 * target.
 */
public final class HeroRules {
    private HeroRules() {
    }

    // ------------------------------------------------------------------ levels and growth (v1 5.3, 22)

    public static int clampLevel(int level) {
        return Math.max(1, Math.min(HeroCatalogue.MAX_LEVEL, level));
    }

    /** What a growth profile adds at a level (nothing at level 1, the most at 30). */
    public record GrowthBonus(double health, double attack, double armor, double speedPercent,
                               double abilityPowerPercent) {
    }

    public static GrowthBonus growth(Growth g, int level) {
        GrowthStep s = HeroCatalogue.GROWTH.get(g);
        int n = clampLevel(level) - 1;
        return new GrowthBonus(s.health() * n, s.attack() * n, s.armor() * n, s.speedPercent() * n,
                s.abilityPowerPercent() * n);
    }

    /**
     * The ability power multiplier at a level: 1 + per level x (level - 1). Every ability works from level 1 (the
     * owner's rule, HEROES_V2 8); only its power grows.
     */
    public static double abilityPower(Growth g, int level) {
        return 1 + growth(g, level).abilityPowerPercent() / 100;
    }

    /**
     * A hero's own numbers at a level, before gear, affixes and buffs (v1 22.2: {@code /fc hero info} and
     * {@code check} print them at level 1, the current level and level 30): max health, the attack and armour it adds,
     * walking speed in percent of a player's, and the ability power multiplier.
     */
    public record LevelStats(double health, double attack, double armor, double speedPercent, double abilityPower) {
    }

    public static LevelStats statsAt(HeroDef.Stats s, int level) {
        GrowthBonus g = growth(s.growth(), level);
        return new LevelStats(s.health() + g.health(), s.attack() + g.attack(), s.armor() + g.armor(),
                s.speed() + g.speedPercent(), 1 + g.abilityPowerPercent() / 100);
    }

    /**
     * A reference Amount ({@link HeroBalance#reference}) as it reads at a level: x ability power x
     * {@code heroes.powerScale}. "Shield Slam: 5.8 damage; at level 30: 6.6 damage" (v1 26.4).
     */
    public static double referenceAt(double reference, Growth g, int level, int powerScale) {
        return raw(reference, g, level, powerScale);
    }

    // ------------------------------------------------------------------ Amounts at hit time (v1 6.2, 12)

    /**
     * Everything an Amount reads when it lands: the hero's attack power, max health and armour (worn + natural), the
     * receiver's max and missing health and stacks of the Amount's mark, whether the receiver has boss health, and the
     * hero's reference attack and armour (for the 1.5x gear and buff cap). {@code targetIsBoss}: a vanilla boss (v1
     * 11's list) or a hero, which has its boss health against everyone (HEROES_V2 6.2). Whether the receiver is a
     * player is not an input.
     */
    public record HitContext(double attackPower, double heroMaxHealth, double heroArmor, double targetMaxHealth,
                             double targetMissingHealth, int markStacks, boolean targetIsBoss, double referenceAttack,
                             double referenceArmor) {
    }

    /**
     * Against a boss or a hero, {@code target_max_health_percent} counts at most this many percent (v1 6.3; HEROES_V2
     * 6.2).
     */
    public static final double BOSS_MAX_HEALTH_PERCENT = 2;

    /** An Amount's value when it lands, before ability power and {@code powerScale}. */
    public static double amountAtHit(Amount a, HitContext c) {
        double attack = Math.min(c.attackPower(), HeroCatalogue.GEAR_CAP * c.referenceAttack());
        double armor = Math.min(c.heroArmor(), HeroCatalogue.GEAR_CAP * c.referenceArmor());
        double tmh = c.targetIsBoss() ? Math.min(a.targetMaxHealthPercent(), BOSS_MAX_HEALTH_PERCENT)
                : a.targetMaxHealthPercent();
        double v = a.base() + a.attackPercent() / 100 * attack + a.maxHealthPercent() / 100 * c.heroMaxHealth()
                + a.armorPercent() / 100 * armor + tmh / 100 * c.targetMaxHealth()
                + a.targetMissingHealthPercent() / 100 * Math.max(0, c.targetMissingHealth());
        if (a.mark() != null) {
            v += a.perMarkStack() * Math.max(0, c.markStacks());
        }
        return Math.max(0, v);
    }

    /** {@code raw} of v1 6.3: the Amount at hit time x ability power x {@code heroes.powerScale}. */
    public static double raw(double amountAtHit, Growth g, int level, int powerScale) {
        return HeroBalance.powerScaled(amountAtHit * abilityPower(g, level), powerScale);
    }

    // ------------------------------------------------------------------ the damage formula (v1 6.3)

    public static final double DEALT_MIN = -50, DEALT_MAX = 80, TAKEN_MIN = -60, TAKEN_MAX = 50;
    public static final double PRODUCT_MAX = 2.0;
    public static final double WEAK_FACTOR = 1.3;
    /** {@code heroes.maxHit} default (PROPOSED, 10-100). */
    public static final double MAX_HIT_DEFAULT = 40;

    /**
     * What a target is, as far as the elements care: the game side classifies an entity (fire-immune mobs, vanilla's
     * freeze tags, undead, wither-immune, snow golems, and whether it stands in water or rain).
     */
    public enum Nature {
        FIRE_IMMUNE, SNOW_GOLEM, FREEZE_IMMUNE, FREEZE_HURTS_EXTRA, WET, UNDEAD, WITHER_IMMUNE
    }

    /** The element column of v1 6.1: 0 for an immune target, 1.3 for a weak one, else 1. */
    public static double elementFactor(Element e, Set<Nature> n) {
        return switch (e) {
            case PHYSICAL, ARCANE -> 1;
            case FIRE -> n.contains(Nature.FIRE_IMMUNE) ? 0 : n.contains(Nature.SNOW_GOLEM) ? WEAK_FACTOR : 1;
            case FROST -> n.contains(Nature.FREEZE_IMMUNE) ? 0 : n.contains(Nature.FREEZE_HURTS_EXTRA) ? WEAK_FACTOR : 1;
            case SHOCK -> n.contains(Nature.WET) ? WEAK_FACTOR : 1;
            case VENOM -> n.contains(Nature.UNDEAD) ? 0 : 1;
            case BLIGHT -> n.contains(Nature.WITHER_IMMUNE) ? 0 : 1;
            case RADIANT -> n.contains(Nature.UNDEAD) ? WEAK_FACTOR : 1;
        };
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /**
     * The four factors of v1 6.3 multiplied and clamped to 0..2: the attacker's {@code damage_dealt} (-50..+80%), the
     * element, the target's resistance to it (-30..+60%) and its {@code damage_taken} (-60..+50%).
     */
    public static double damageFactor(double dealtPercent, double element, double resistPercent, double takenPercent) {
        double f = (1 + clamp(dealtPercent, DEALT_MIN, DEALT_MAX) / 100) * element
                * (1 - clamp(resistPercent, HeroCatalogue.RESIST_MIN, HeroCatalogue.RESIST_MAX) / 100)
                * (1 + clamp(takenPercent, TAKEN_MIN, TAKEN_MAX) / 100);
        return clamp(f, 0, PRODUCT_MAX);
    }

    /** {@code heroes.maxHit} is kept within these (v1 28). */
    public static final double MAX_HIT_MIN = 10, MAX_HIT_MAX = 100;

    /** {@code heroes.maxHit} within 10-100. */
    public static double clampMaxHit(double maxHit) {
        return clamp(maxHit, MAX_HIT_MIN, MAX_HIT_MAX);
    }

    /**
     * {@code dealt} of v1 6.3: raw x the clamped factors, at most {@code heroes.maxHit}, never negative; the same
     * whoever is hit.
     */
    public static double dealt(double raw, double factor, double maxHit) {
        return Math.max(0, Math.min(maxHit, raw * factor));
    }

    /** {@code drain}: the hero heals this share of the damage really dealt (health before minus after). */
    public static double drain(double healthBefore, double healthAfter, int drainPercent) {
        return Math.max(0, healthBefore - healthAfter) * Math.max(0, drainPercent) / 100.0;
    }

    /** {@code heroes.maxDotPerSecond} default (PROPOSED): one hero's DoTs on one target per second. */
    public static final double MAX_DOT_PER_SECOND = 6;

    /** One second of a hero's DoTs on a target: their sum (amount x stacks each), at most the cap. */
    public static double dotTick(List<Double> perSecond, double cap) {
        double s = 0;
        for (double v : perSecond) {
            s += Math.max(0, v);
        }
        return Math.min(cap, s);
    }

    /** A shield holds at most this share of the receiver's max health (v1 13.2). */
    public static final double SHIELD_MAX_SHARE = 0.6;

    /** A new shield: at most 60% of max health; a stronger one replaces a weaker one, they never add. */
    public static double shieldAfter(double current, double incoming, double receiverMaxHealth) {
        return Math.max(current, Math.min(incoming, SHIELD_MAX_SHARE * receiverMaxHealth));
    }

    // ------------------------------------------------------------------ energy (v1 14)

    public static final int ENERGY_MAX = 100;
    public static final int ENERGY_BASIC_HIT = 5, ENERGY_KILL = 10, ENERGY_PER_EVENT_CAP = 8;

    /** A gain x the ultimate's {@code charge_rate} x (1 + the {@code energy_gain} stat), rounded to whole energy. */
    public static int energyGain(double base, int chargeRate, int energyGainStatPercent) {
        return (int) Math.round(Math.max(0, base) * chargeRate / 100.0 * (1 + Math.max(0, energyGainStatPercent) / 100.0));
    }

    /** Energy for damage taken: 1 per 2 health points lost, at most 8 per hit (before the multipliers). */
    public static double energyFromDamage(double healthLost) {
        return Math.min(ENERGY_PER_EVENT_CAP, Math.max(0, healthLost) / 2);
    }

    /** Energy for healing or shielding allies: 1 per 2 points restored, at most 8 per use. */
    public static double energyFromHealing(double restored) {
        return Math.min(ENERGY_PER_EVENT_CAP, Math.max(0, restored) / 2);
    }

    public static int addEnergy(int current, int gain) {
        return Math.max(0, Math.min(ENERGY_MAX, current + gain));
    }

    /** The ultimate fires at 100 energy, and no sooner than its least gap after the last one (ticks). */
    public static boolean ultimateReady(int energy, long now, long lastUltimateAt, double minGapSeconds) {
        return energy >= ENERGY_MAX && (lastUltimateAt <= 0 || now - lastUltimateAt >= Math.round(minGapSeconds * 20));
    }

    // ------------------------------------------------------------------ cooldowns (v1 13.5, 15)

    /**
     * {@code cooldown_cut}: a running cooldown, ready at {@code readyAt}, shortened by {@code cutTicks}, but never to
     * less than half of the full cooldown since the skill's last use (so the floors still hold: at most twice the
     * rate). Returns the new ready-at game time.
     */
    public static long cutCooldown(long readyAt, long lastUseAt, long fullCooldownTicks, long cutTicks) {
        long earliest = lastUseAt + (fullCooldownTicks + 1) / 2;
        long cut = readyAt - Math.max(0, cutTicks);
        return Math.min(readyAt, Math.max(cut, earliest));
    }

    /** Seconds to whole game ticks, rounded to the nearest tick (v1 3). */
    public static long ticks(double seconds) {
        return Math.round(seconds * 20);
    }

    // ------------------------------------------------------------------ combat XP (v1 22.1; HEROES_V2 8)

    public static final int XP_KILL_BASE = 10, XP_ULTIMATE = 5, XP_HEAL_PER = 5, XP_RAID_WON = 100,
            XP_DEFENCE = 30, XP_DAY_ALIVE = 40;
    /** A leader's XP for faction events (HEROES_V2 8, PROPOSED). */
    public static final int XP_BASE_BUILT = 40, XP_LEADER_RAID_WON = 100, XP_PACT = 40, XP_ADVANCEMENT = 20,
            XP_FESTIVAL = 20;

    /** A kill: 10 + the victim's max health / 2 (a zombie 20). */
    public static double killXp(double victimMaxHealth) {
        return XP_KILL_BASE + Math.max(0, victimMaxHealth) / 2;
    }

    /** Healing or shielding allies: 1 XP per 5 points. */
    public static double healXp(double restored) {
        return Math.max(0, restored) / XP_HEAL_PER;
    }

    /** An XP award x {@code heroes.xpRate} (0-500%), rounded to whole XP. */
    public static int xp(double base, int xpRatePercent) {
        return (int) Math.round(Math.max(0, base) * Math.max(0, Math.min(500, xpRatePercent)) / 100.0);
    }

    /** A named token for a hero its faction already has: XP worth two levels at its current level (v1 22.4). */
    public static int duplicateTokenXp(int level) {
        return AffixRoller.xpToNext(level) + AffixRoller.xpToNext(level + 1);
    }

    // ------------------------------------------------------------------ dialogue (v1 23)

    /** {@code heroes.dialogueCooldownSeconds} default. */
    public static final double DIALOGUE_COOLDOWN = 20;

    /**
     * Whether a hero may say a line of {@code event} now: {@code summoned}, {@code fallen}, {@code revived} and
     * {@code ultimate} always; {@code greeting} 5 s after the last greeting; {@code idle} at most every 15 minutes;
     * every other event one line per {@code cooldownSeconds} (game ticks; a 0 time means never).
     */
    public static boolean maySpeak(LineEvent event, long now, long lastLineAt, long lastGreetingAt, long lastIdleAt,
                                   double cooldownSeconds) {
        if (HeroCatalogue.UNLIMITED_EVENTS.contains(event)) {
            return true;
        }
        if (event == LineEvent.GREETING) {
            return passed(now, lastGreetingAt, HeroCatalogue.GREETING_COOLDOWN_SECONDS);
        }
        if (event == LineEvent.IDLE && !passed(now, lastIdleAt, HeroCatalogue.IDLE_EVERY_SECONDS)) {
            return false;
        }
        return passed(now, lastLineAt, cooldownSeconds);
    }

    private static boolean passed(long now, long last, double seconds) {
        return last <= 0 || now - last >= Math.round(seconds * 20);
    }

    /** Whether the event's chance passes for a roll in [0, 100): fight_start 50%, skill 25%, kill 30%, else always. */
    public static boolean lineRoll(LineEvent event, double roll0to100) {
        return roll0to100 < HeroCatalogue.lineChance(event);
    }

    // ------------------------------------------------------------------ rarity draws (v1 25.3; HEROES_V2 5.2)

    /** Token draws: common 8, rare 4, epic 2, legendary 1. */
    public static final Map<Rarity, Integer> TOKEN_WEIGHTS = weights(8, 4, 2, 1);
    /** The leader draft (HEROES_V2 5.2, PROPOSED): 4, 4, 2, 1. */
    public static final Map<Rarity, Integer> LEADER_WEIGHTS = weights(4, 4, 2, 1);

    private static Map<Rarity, Integer> weights(int c, int r, int e, int l) {
        Map<Rarity, Integer> m = new EnumMap<>(Rarity.class);
        m.put(Rarity.COMMON, c);
        m.put(Rarity.RARE, r);
        m.put(Rarity.EPIC, e);
        m.put(Rarity.LEGENDARY, l);
        return m;
    }

    /**
     * One weighted draw among candidates by their rarity, from a seed (the same seed always picks the same index;
     * neighbouring seeds draw independently, which {@code new Random(seed)}'s first value does not). Returns -1 when
     * there is no candidate or every weight is 0.
     */
    public static int drawIndex(List<Rarity> candidates, Map<Rarity, Integer> weights, long seed) {
        return drawIndex(candidates, weights, new SplittableRandom(seed).nextDouble());
    }

    /** The same with the roll given directly (0 <= roll < 1). */
    public static int drawIndex(List<Rarity> candidates, Map<Rarity, Integer> weights, double roll) {
        long total = 0;
        for (Rarity r : candidates) {
            total += Math.max(0, weights.getOrDefault(r, 0));
        }
        if (total <= 0) {
            return -1;
        }
        double target = roll * total;
        double acc = 0;
        for (int i = 0; i < candidates.size(); i++) {
            acc += Math.max(0, weights.getOrDefault(candidates.get(i), 0));
            if (target < acc) {
                return i;
            }
        }
        for (int i = candidates.size() - 1; i >= 0; i--) {
            if (weights.getOrDefault(candidates.get(i), 0) > 0) {
                return i;
            }
        }
        return -1;
    }

    /** A random starter is never legendary (v1 25.2). */
    public static boolean mayBeRandomStarter(Rarity r) {
        return r != Rarity.LEGENDARY;
    }

    // ------------------------------------------------------------------ revival (HEROES_V2 3.4; v1 25.4)

    /** The owner's rule (2026-11-12): every hero, leaders included, returns ten minutes after its death. */
    public static final int REVIVE_SECONDS = 600;
    /** {@code heroes.minReviveSeconds} (PROPOSED): no death-related ability brings a hero back sooner. */
    public static final int MIN_REVIVE_SECONDS = 30;
    /** The {@code revival_rite} talent: fallen heroes of the faction return this many percent sooner, by tier. */
    public static final int[] REVIVAL_RITE_PERCENT = {0, 20, 30, 40};

    /** A talent's tier at a hero level: I from level 1, II from 10, III from 20 (HEROES_V2 7.2). */
    public static int talentTier(int level) {
        return level >= 20 ? 3 : level >= 10 ? 2 : 1;
    }

    /** A hero's own {@code revival} block may set 30 to 3600 seconds (HEROES_V2 12.3). */
    public static final int OWN_REVIVE_MAX_SECONDS = 3600;

    /**
     * Seconds from a hero's death to its return: {@code ownSeconds} (its own {@code revival} block; 0 when it has
     * none) or else {@code configSeconds} ({@code heroes.reviveSeconds}, 600 by default), then shortened by the
     * faction's best {@code revival_rite} tier (0: none, 1-3), and never under {@link #MIN_REVIVE_SECONDS}, so a hero
     * cannot rise in the fight that killed it. Nothing else changes it: not who the hero is (a leader has no timer of
     * its own) and not who killed it.
     */
    public static int reviveDelaySeconds(int configSeconds, int ownSeconds, int riteTier) {
        return reviveDelaySeconds(configSeconds, ownSeconds, riteTier, MIN_REVIVE_SECONDS);
    }

    /**
     * The same with {@code heroes.minReviveSeconds} as the floor ({@code floorSeconds}; it is never below
     * {@link #MIN_REVIVE_SECONDS}, so a config cannot bring a hero back in the fight that killed it).
     */
    public static int reviveDelaySeconds(int configSeconds, int ownSeconds, int riteTier, int floorSeconds) {
        int base = ownSeconds > 0 ? Math.min(ownSeconds, OWN_REVIVE_MAX_SECONDS) : Math.max(1, configSeconds);
        int tier = Math.max(0, Math.min(3, riteTier));
        double s = base * (100 - REVIVAL_RITE_PERCENT[tier]) / 100.0;
        return (int) Math.max(Math.max(MIN_REVIVE_SECONDS, floorSeconds), Math.round(s));
    }

    /**
     * The faction's {@code revival_rite} tier (0: none): the best of its heroes that have the talent, each at the tier
     * of its own level (7.2: the strongest copy counts, never the sum). {@code holderLevels} are those heroes' levels.
     */
    public static int riteTier(List<Integer> holderLevels) {
        int best = 0;
        for (int level : holderLevels) {
            best = Math.max(best, talentTier(level));
        }
        return best;
    }

    /**
     * The game time a hero that fell at {@code now} is due back ({@link #reviveDelaySeconds} later). A hero whose
     * {@code revival} block says {@code at: night} is due at this time and then waits for nightfall
     * ({@link #revivalDue}).
     */
    public static long reviveAt(long now, int configSeconds, int ownSeconds, int riteTier) {
        return HeroRevival.reviveAt(now, reviveDelaySeconds(configSeconds, ownSeconds, riteTier));
    }

    /** Vanilla day time of nightfall and dawn, as the NPCs' night rule ({@code Job.NIGHT_START/END}). */
    public static final int NIGHT_START = 13000, NIGHT_END = 23000;

    public static boolean isNight(long dayTime) {
        long t = Math.floorMod(dayTime, 24000L);
        return t >= NIGHT_START && t < NIGHT_END;
    }

    /**
     * Whether a fallen hero returns now: its time has come and, for a {@code revival} block with {@code at: night},
     * it is night (it rises at the first nightfall after its delay).
     */
    public static boolean revivalDue(long now, long reviveAt, boolean atNight, long dayTime) {
        return HeroRevival.due(now, reviveAt) && (!atNight || isNight(dayTime));
    }

    /** Game ticks from {@code dayTime} to the next nightfall (0 at night), with the daylight cycle running. */
    public static long ticksToNightfall(long dayTime) {
        if (isNight(dayTime)) {
            return 0;
        }
        long t = Math.floorMod(dayTime, 24000L);
        return Math.floorMod(NIGHT_START - t, 24000L);
    }

    // ------------------------------------------------------------------ rewards at a death (HEROES_V2 6.13)

    /** XP orbs at a hero's death: 7 x level, at most 100. */
    public static final int REWARD_XP_PER_LEVEL = 7, REWARD_XP_MAX = 100;
    /** The winning side is the side of the last person that hurt the hero within this many ticks (15 s). */
    public static final int WINNER_MEMORY_TICKS = 15 * 20;

    public static int rewardXp(int level) {
        return Math.min(REWARD_XP_MAX, REWARD_XP_PER_LEVEL * clampLevel(level));
    }

    /**
     * A side for the rewards: a faction (its members, players or NPCs, are all of it) or a factionless player, who is
     * a side of one. The game builds the key ({@link #faction} or {@link #loner}); nothing else tells sides apart.
     */
    public record Side(String key) {
        public static Side faction(String factionId) {
            return new Side("faction:" + factionId);
        }

        public static Side loner(String uniqueId) {
            return new Side("alone:" + uniqueId);
        }
    }

    /** One blow on the hero: when, and the side of the person who struck it (null: a monster, a fall, lava...). */
    public record Blow(long tick, Side side) {
    }

    /**
     * Who won a hero's death (6.13): the side of the last person (a player, an NPC or a hero) that hurt it in its last
     * 15 s, so a monster's blow or a fall after it counts for that side; null when no person hurt it in that time
     * (then it drops only its non-kit gear: no orbs, no crest). An escape is not a death and never reaches this.
     */
    public static Side winningSide(List<Blow> blows, long deathTick) {
        Side best = null;
        long bestTick = Long.MIN_VALUE;
        for (Blow b : blows) {
            if (b.side() != null && b.tick() <= deathTick && deathTick - b.tick() < WINNER_MEMORY_TICKS
                    && b.tick() >= bestTick) {
                best = b.side();
                bestTick = b.tick();
            }
        }
        return best;
    }

    /** The {@code hero_crest} drops once per hero per winning side per in-game day: this key says which. */
    public record CrestKey(String heroId, Side side, long day) {
    }

    public static CrestKey crestKey(String heroId, Side side, long gameTime) {
        return new CrestKey(heroId, side, Math.floorDiv(gameTime, 24000L));
    }

    // ------------------------------------------------------------------ war power (HEROES_V2 8.1)

    /** {@code FactionDirector.power} of a soldier in full iron (armour 15, an iron sword): 1 + 0.75 + 0.5. */
    public static final double IRON_SOLDIER_POWER = 2.25;
    /** {@code war.heroPowerPercent}: 100 by default, 0-200 (0 counts a hero by heads only, as before). */
    public static final int HERO_POWER_PERCENT_DEFAULT = 100, HERO_POWER_PERCENT_MAX = 200;
    /** The soldier backbone: in an odds check a side's heroes count at most this many times its soldiers' power. */
    public static final double BACKBONE = 1.5;

    /**
     * A hero in iron soldiers (8.1): {@code sqrt(maxHealth / 20) x (1.5 + level / 30)}, fitted to the estimate
     * {@code sqrt(2 H D / (h d))} of one hero against soldiers who all reach it. It reads the hero's real maximum, so
     * the rarity, {@code boss.healthScale} and a leader's +50 move it with no rule of their own.
     */
    public static double heroSoldiers(double maxHealth, int level) {
        return Math.sqrt(Math.max(0, maxHealth) / 20) * (1.5 + clampLevel(level) / 30.0);
    }

    /**
     * A hero's war power in the director's units ({@code FactionDirector.power}): {@code 2.25 x heroSoldiers x
     * war.heroPowerPercent / 100}. A level-10 Benika leading (165 health) is 11.8; a common marksman at level 1 (78)
     * is 6.8. Who the hero fights does not enter.
     */
    public static double heroPower(double maxHealth, int level, int percent) {
        int p = Math.max(0, Math.min(HERO_POWER_PERCENT_MAX, percent));
        return IRON_SOLDIER_POWER * heroSoldiers(maxHealth, level) * p / 100.0;
    }

    /** A hero under half health counts half in its base's defence (8.1), the same line as an escaped hero's return. */
    public static double defencePower(double heroPower, double healthShare) {
        return healthShare < 0.5 ? heroPower / 2 : heroPower;
    }

    /**
     * A side's counted power in an odds check (8.1, the soldier backbone): its ordinary soldiers' power plus its
     * heroes' power, the heroes counting at most 1.5 times the soldiers (so at most 60% of the total). Two level-10
     * heroes (23.0) beside three iron soldiers (6.75) count 6.75 + 10.1 = 16.9.
     */
    public static double countedPower(double soldiersPower, double heroesPower) {
        double s = Math.max(0, soldiersPower);
        return s + Math.min(Math.max(0, heroesPower), BACKBONE * s);
    }
}
