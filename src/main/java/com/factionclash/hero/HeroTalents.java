package com.factionclash.hero;

import com.factionclash.campaign.BaseState;
import com.factionclash.campaign.CampaignData;
import com.factionclash.campaign.FactionState;
import com.factionclash.combat.Alerts;
import com.factionclash.logic.Affix;
import com.factionclash.logic.hero.HeroDef;
import com.factionclash.logic.hero.HeroDef.TalentId;
import com.factionclash.logic.hero.HeroRules;
import com.factionclash.logic.leader.Talents;
import com.factionclash.npc.FactionNpc;
import com.factionclash.npc.NpcRole;
import com.factionclash.util.Messages;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A hero's base abilities (HERO_FORMAT.md 21; HEROES_V2 7.2 reads them as talents; task A4): the five that v1's
 * {@code base_abilities} become, with the talents' tier numbers ({@link Talents}: tier I from level 1, II from 10, III
 * from 20). They work while the hero is alive at its home base (inside the walls or within 16 blocks of them); at one
 * base the strongest copy of each counts, never the sum ({@link Talents#atBase}):
 * <ul>
 * <li>{@code aura}: NPCs of the listed roles (all when none) at the base count 1-3 extra stacks of one work affix,
 * within the affix's own cap and job rule ({@link #auraStacks}, read by {@link FactionNpc#affix});</li>
 * <li>{@code rally}: recruits at the base cost 10-20% less food ({@link #rallyPercent});</li>
 * <li>{@code drill}: soldiers at the base gain 1-3 work XP every in-game hour by day;</li>
 * <li>{@code mend}: out of a fight, every ally within 16 blocks of the hero, players and NPCs alike, heals 5-10% of its
 * max health every 10 s;</li>
 * <li>{@code lookout}: enemies the hero can see within 32-48 blocks call its side's fighters on them and give one
 * faction line a minute; it reveals nothing it does not see.</li>
 * </ul>
 * The other ten milestone-1 talents are L5's. Rebuilt every 40 ticks from the heroes in the world; nothing is
 * allocated between rebuilds.
 */
public final class HeroTalents {
    private HeroTalents() {
    }

    /** The five base-ability talents A4 runs. */
    public static final List<TalentId> RUNS = List.of(TalentId.AURA, TalentId.RALLY, TalentId.DRILL, TalentId.MEND,
            TalentId.LOOKOUT);
    public static final int REBUILD_TICKS = 40, DRILL_EVERY = 1000, LOOKOUT_LINE_EVERY = 1200;

    /** One aura at a base: its stacks and the roles it reaches (empty: every role). */
    record Aura(int stacks, List<NpcRole> roles) {
    }

    /** What works at one base now. */
    static final class AtBase {
        final Map<TalentId, Integer> tiers = new EnumMap<>(TalentId.class);
        final Map<Affix, Aura> auras = new EnumMap<>(Affix.class);
    }

    private static final Map<Integer, AtBase> BASES = new HashMap<>();
    private static final Map<java.util.UUID, Long> LOOKOUT_SAID = new HashMap<>();
    private static long builtAt = Long.MIN_VALUE;

    /** Whether the hero works its base abilities now: alive, a campaign hero, at its home base, not on a raid. */
    static boolean atHome(HeroEntity h) {
        BaseState b = h.base();
        if (b == null || b.destroyed || !h.isAlive() || h.isPreview() || h.dormant()) {
            return false;
        }
        FactionState f = h.getServer() == null ? null : CampaignData.get(h.getServer()).factions.get(h.getFaction());
        return !(f != null && f.raiders.contains(h.getUUID())) && b.contains(h.blockPosition(), (int) Talents.AT_BASE_MARGIN);
    }

    static List<HeroDef.Talent> talents(HeroEntity h) {
        HeroDef d = h.dormant() ? null : h.definition();
        return d == null || d.v2() == null ? List.of() : d.v2().talents();
    }

    /** Every 40 ticks: which talents work at which base; every tick due: mend, lookout and drill. */
    public static void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        List<HeroEntity> heroes = HeroEntity.live();
        if (heroes.isEmpty()) {
            if (!BASES.isEmpty()) {
                BASES.clear();
            }
            return;
        }
        if (now - builtAt >= REBUILD_TICKS) {
            builtAt = now;
            rebuild(heroes);
        }
        for (HeroEntity h : heroes) {
            if (!h.isAlive() || h.isPreview()) {
                continue;
            }
            List<HeroDef.Talent> ts = talents(h);
            if (ts.isEmpty()) {
                continue;
            }
            int tier = HeroRules.talentTier(h.npcLevel());
            for (HeroDef.Talent t : ts) {
                if (t.id() == TalentId.MEND) {
                    mend(h, tier, now);
                } else if (t.id() == TalentId.LOOKOUT && atHome(h) && (now + h.getId()) % 20 == 0) {
                    lookout(h, tier, now);
                }
            }
        }
        if (now % DRILL_EVERY == 0 && server.overworld().isDay()) {
            drill(server, now);
        }
    }

    private static void rebuild(List<HeroEntity> heroes) {
        BASES.clear();
        Map<Integer, List<Talents.Copy>> copies = new HashMap<>();
        for (HeroEntity h : heroes) {
            List<HeroDef.Talent> ts = talents(h);
            if (ts.isEmpty() || h.base() == null) {
                continue;
            }
            boolean home = atHome(h);
            int baseId = h.base().id;
            AtBase at = BASES.computeIfAbsent(baseId, k -> new AtBase());
            for (HeroDef.Talent t : ts) {
                if (!RUNS.contains(t.id())) {
                    continue;
                }
                copies.computeIfAbsent(baseId, k -> new ArrayList<>()).add(new Talents.Copy(t.id(), h.npcLevel(), t.realm(), home));
                if (t.id() == TalentId.AURA && home && t.affix() != null) {
                    Affix a = affix(t.affix());
                    if (a != null) {
                        int stacks = (int) Talents.value(TalentId.AURA, "stacks", HeroRules.talentTier(h.npcLevel()));
                        Aura cur = at.auras.get(a);
                        if (cur == null || stacks > cur.stacks()) {
                            List<NpcRole> roles = new ArrayList<>();
                            for (String r : t.roles()) {
                                NpcRole nr = NpcRole.byId(r);
                                if (nr != null) {
                                    roles.add(nr);
                                }
                            }
                            at.auras.put(a, new Aura(stacks, List.copyOf(roles)));
                        }
                    }
                }
            }
        }
        copies.forEach((id, l) -> BASES.computeIfAbsent(id, k -> new AtBase()).tiers.putAll(Talents.atBase(l)));
    }

    static Affix affix(String id) {
        try {
            return Affix.valueOf(id.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** The tier a talent works at, at a base (0: not there). */
    public static int tier(BaseState b, TalentId id) {
        AtBase at = b == null ? null : BASES.get(b.id);
        return at == null ? 0 : at.tiers.getOrDefault(id, 0);
    }

    /**
     * {@code aura}: the extra stacks of {@code a} an NPC counts at its base (0 for a hero, an NPC of an unlisted role,
     * or an affix its job does not use). The affix's own cap still holds ({@link FactionNpc#affix}).
     */
    public static int auraStacks(FactionNpc npc, Affix a) {
        if (BASES.isEmpty() || npc instanceof HeroEntity || !a.appliesTo(npc.getRole().affixJob)) {
            return 0;
        }
        AtBase at = BASES.get(npc.baseId());
        Aura aura = at == null ? null : at.auras.get(a);
        if (aura == null || !aura.roles().isEmpty() && !aura.roles().contains(npc.getRole())) {
            return 0;
        }
        BaseState b = npc.base();
        return b != null && b.contains(npc.blockPosition(), (int) Talents.AT_BASE_MARGIN) ? aura.stacks() : 0;
    }

    /** {@code rally}: how much less food a recruit at this base costs, in percent (0: none). */
    public static double rallyPercent(BaseState b) {
        int t = tier(b, TalentId.RALLY);
        return t <= 0 ? 0 : Talents.value(TalentId.RALLY, "recruit_food_cheaper_percent", t);
    }

    /** {@code drill}: soldiers at a base with it gain its XP every in-game hour by day. */
    private static void drill(MinecraftServer server, long now) {
        CampaignData data = CampaignData.get(server);
        ServerLevel level = server.overworld();
        for (Map.Entry<Integer, AtBase> e : BASES.entrySet()) {
            int t = e.getValue().tiers.getOrDefault(TalentId.DRILL, 0);
            BaseState b = data.base(e.getKey());
            if (t <= 0 || b == null || b.destroyed || !level.isLoaded(b.center)) {
                continue;
            }
            int xp = (int) Talents.value(TalentId.DRILL, "soldier_xp_per_hour", t);
            for (FactionNpc n : level.getEntitiesOfClass(FactionNpc.class, new net.minecraft.world.phys.AABB(b.center).inflate(48, 16, 48),
                    n -> n.isAlive() && !(n instanceof HeroEntity) && !n.isDisplayOnly() && n.getFaction() == b.faction
                            && n.getRole().combatant && b.contains(n.blockPosition(), (int) Talents.AT_BASE_MARGIN))) {
                n.awardXp(xp);
            }
        }
    }

    /** {@code mend}: out of a fight, every {@code every} seconds, its allies within the radius heal a share. */
    private static void mend(HeroEntity h, int tier, long now) {
        int every = (int) HeroRules.ticks(Talents.value(TalentId.MEND, "every_seconds", tier));
        if (every <= 0 || (now + h.getId()) % every != 0 || h.inFight()) {
            return;
        }
        double pct = Talents.value(TalentId.MEND, "heal_percent", tier);
        double r = Talents.value(TalentId.MEND, "radius", tier);
        for (LivingEntity a : HeroForms.alliesNear(h, h.position(), r)) {
            if (a.getHealth() < a.getMaxHealth() && !(a instanceof Player p && p.isSpectator())) {
                a.heal((float) (a.getMaxHealth() * pct / 100.0));
                HeroEffects.particles(h.level(), a, ParticleTypes.HEART, 2);
            }
        }
    }

    /**
     * {@code lookout}: enemies the hero sees within its radius: its side's free fighters near take them on (the
     * soldiers' and heroes' alarm), and its faction hears one line a minute at most. Line of sight only: it never
     * reveals what it cannot see.
     */
    private static void lookout(HeroEntity h, int tier, long now) {
        double r = Talents.value(TalentId.LOOKOUT, "alarm_radius", tier);
        List<LivingEntity> seen = new ArrayList<>();
        for (LivingEntity e : h.level().getEntitiesOfClass(LivingEntity.class, h.getBoundingBox().inflate(r, 16, r),
                e -> e != h && HeroForms.enemy(h, e) && h.distanceTo(e) <= r)) {
            if (h.getSensing().hasLineOfSight(e)) {
                seen.add(e);
            }
        }
        if (seen.isEmpty()) {
            return;
        }
        LivingEntity nearest = seen.get(0);
        for (LivingEntity e : seen) {
            if (e.distanceToSqr(h) < nearest.distanceToSqr(h)) {
                nearest = e;
            }
        }
        Alerts.callGuards(h, nearest, r);
        Long said = LOOKOUT_SAID.get(h.getUUID());
        if (said == null || now - said >= LOOKOUT_LINE_EVERY) {
            LOOKOUT_SAID.put(h.getUUID(), now);
            String dir = direction(nearest.getX() - h.getX(), nearest.getZ() - h.getZ());
            Messages.toFaction(h.getServer(), h.getFaction(), Component.literal(h.npcName() + " spots " + seen.size()
                    + (seen.size() == 1 ? " enemy" : " enemies") + " to the " + dir));
        }
    }

    static String direction(double dx, double dz) {
        if (Math.abs(dx) > 2 * Math.abs(dz)) {
            return dx > 0 ? "east" : "west";
        }
        if (Math.abs(dz) > 2 * Math.abs(dx)) {
            return dz > 0 ? "south" : "north";
        }
        return (dz > 0 ? "south" : "north") + (dx > 0 ? "east" : "west");
    }

    /** Forgets everything (server stop, tests). */
    public static void clear() {
        BASES.clear();
        LOOKOUT_SAID.clear();
        builtAt = Long.MIN_VALUE;
    }

    /** Rebuilds now (tests that change a hero and read at once). */
    public static void refresh() {
        rebuild(HeroEntity.live());
    }
}
