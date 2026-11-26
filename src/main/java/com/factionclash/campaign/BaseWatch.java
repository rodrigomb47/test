package com.factionclash.campaign;

import com.factionclash.combat.CombatRules;
import com.factionclash.config.FCConfig;
import com.factionclash.faction.FactionType;
import com.factionclash.logic.campaign.Intel;
import com.factionclash.logic.campaign.ReconOptic;
import com.factionclash.logic.campaign.ReconRules;
import com.factionclash.logic.campaign.ReconWatch;
import com.factionclash.npc.FactionNpc;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What a faction's people see of an enemy settlement or a Hollow lair ({@code docs/design/recon.md}): a passing look
 * (where it is and that it stands, no count), a focused recon's samples (the yard points in view and the defenders in
 * sight, counted by face over a watch), its ruins once its heart is gone, and the enemy fighters its people saw fall at
 * home (a strike by their faction). The war council judges an enemy base only by a focused recon ({@link Intel}); it never
 * reads another faction's roster, raid party, gear or heroes. Server thread only.
 */
public final class BaseWatch {
    private BaseWatch() {
    }

    /** How far (horizontally, from the centre) an observer looks at a base: the scouts' 64 blocks. */
    public static final double SIGHT = 64;
    /** PROPOSED: a raider sees its target's heart (standing or gone) within this many blocks with a clear view. */
    public static final double HEART_SIGHT = 48;
    /** The yard's look-out points per side (5 x 5 over the walls' inside; a lair: its clearing). */
    static final int GRID = ReconWatch.GRID;
    /**
     * A focused look's reach ({@code docs/design/recon.md} 3.5): a scout sent to watch a base sees it from up to 72 blocks
     * (horizontally, from the centre), so its vantage band runs from the standoff (61) to 72. A passing look keeps
     * {@link #SIGHT}.
     */
    public static final double RECON_SIGHT = ReconRules.SIGHT;
    /**
     * A recon sample's grid at a lair (recon.md 3.7): the monsters' ground, out to their leash
     * ({@link BaseBuilder#LAIR_LEASH}), not the roofed hall the passing look's grid covers (half - 2 = 5).
     */
    public static final int LAIR_REACH = BaseBuilder.LAIR_LEASH;

    /**
     * A passing look (recon.md 3.6, task R5): what {@code observer} sees of {@code b} now, as a report of its faction
     * (ENEMY_BASE or LAIR, by nobody yet), or null when nothing of it is in sight: it is beyond {@link #SIGHT}, or neither
     * its walls, its yard, its heart's place nor one of its people is in view. It tells where the base is and that it
     * stands: it carries no count and no heroes (only a focused recon estimates a defence, {@link #reconSample}). A base
     * whose heart is gone and whose heart's place is in view is reported fallen.
     */
    public static Report look(ServerLevel level, LivingEntity observer, BaseState b, long now) {
        if (b == null || b.center == null || observer.level() != level || horizontal(observer.position(), b.center) > SIGHT) {
            return null;
        }
        Report.Kind kind = b.isSettlement() ? Report.Kind.ENEMY_BASE : Report.Kind.LAIR;
        BlockPos heart = b.point(BaseState.HEART) != null ? b.point(BaseState.HEART) : b.center;
        boolean heartInView = clear(level, observer, Vec3.atCenterOf(heart));
        if (b.destroyed && heartInView) {
            return ruins(b, now);
        }
        int half = BaseZones.half(b);
        int reach = Math.max(1, half - 2);
        boolean yardInView = inView(level, observer, points(b, reach)) != 0;
        boolean wallsInView = b.isSettlement() && wallsInView(level, observer, b, half);
        if (!yardInView && !heartInView && !wallsInView) {
            // nothing of the place itself in view: one of its people seen near it still shows where it is
            Map<UUID, Double> soldiers = new LinkedHashMap<>();
            Map<String, Double> heroes = new LinkedHashMap<>();
            defenders(level, observer, b, new AABB(b.center).inflate(half + 8, 12, half + 8), soldiers, heroes);
            if (soldiers.isEmpty() && heroes.isEmpty()) {
                return null; // nothing of it in sight (behind a hill)
            }
        }
        return new Report(kind, b.center, now, b.faction, 0, Report.positionNote(b.faction, kind));
    }

    // ------------------------------------------------------------------ the focused look (docs/design/recon.md 3.6)

    /**
     * One sample of a focused watch of {@code b} from where {@code observer} stands (recon.md 3.6; task R2: taken by R4b's
     * recon scout every 40 ticks of its watch and after each block of its climb): the yard points in view (a mask of the
     * 5 x 5 grid, bit x * 5 + z, the middle at {@link ReconWatch#MIDDLE}), the soldiers in sight by UUID with their visible
     * power (armour worn and weapon held), the heroes in sight by hero id with their war power at full strength (a lair:
     * its hostile monsters in sight, power 1 each, and no heroes), and the time. The same rays and the same
     * {@code hasLineOfSight} as a passing look ({@link #look}), but out to {@link #RECON_SIGHT} and, at a lair, over the
     * monsters' ground ({@link #LAIR_REACH}). A sample counts nothing by itself: {@link ReconWatch} counts each face once
     * over the watch. Null when the base is beyond {@link #RECON_SIGHT} or in another level; otherwise a sample, even with
     * nothing in view (behind a hill: an empty mask).
     */
    public static ReconWatch.Sample reconSample(ServerLevel level, LivingEntity observer, BaseState b, long now) {
        return reconSample(level, observer, b, now, ReconOptic.NONE);
    }

    /**
     * {@link #reconSample(ServerLevel, LivingEntity, BaseState, long)} through the instrument {@code optic} (task R12): the
     * sample is taken out to {@link #reconSight} for it, 96 blocks with a spyglass rather than the eyes' 72. Null beyond.
     */
    public static ReconWatch.Sample reconSample(ServerLevel level, LivingEntity observer, BaseState b, long now, ReconOptic optic) {
        if (b == null || b.center == null || observer.level() != level || horizontal(observer.position(), b.center) > reconSight(b, optic)) {
            return null;
        }
        int reach = reconReach(b);
        int mask = inView(level, observer, points(b, reach));
        int box = Math.max(BaseZones.half(b), reach) + 8;
        Map<UUID, Double> soldiers = new LinkedHashMap<>();
        Map<String, Double> heroes = new LinkedHashMap<>();
        defenders(level, observer, b, new AABB(b.center).inflate(box, 12, box), soldiers, heroes);
        return new ReconWatch.Sample(mask, soldiers, heroes, now);
    }

    /**
     * How far (horizontally, from the centre) a focused look of {@code b} through {@code optic} reaches (recon.md 5.6, task
     * R12), the one reach both the vantage band ({@code work.ReconVantage.band}) and the samples' gate read: a settlement's
     * is the instrument's ({@link ReconOptic#sight}: 72 with the eyes, 96 with a spyglass), and a GameTest settlement's
     * scaled one ({@link BaseState#testSight} times the same ratio, {@link ReconOptic#scaled}). A lair's stays
     * {@link #RECON_SIGHT} whatever the scout carries: it is watched from the ground through the gaps in its walls, inside
     * its own band of 46 to 64 ({@code ReconRules.Band.of}).
     */
    public static double reconSight(BaseState b, ReconOptic optic) {
        if (!b.isSettlement()) {
            return RECON_SIGHT;
        }
        return b.testSight > 0 ? ReconOptic.scaled(b.testSight, optic) : optic.sight();
    }

    /**
     * The reach of a recon sample's grid from the centre: a settlement's walls' inside (half - 2, as a passing look); a
     * lair's monsters' ground, out to their leash ({@link #LAIR_REACH}), except a GameTest lair with a small
     * {@link BaseState#testHalf}, whose grid stays inside its own small walls.
     */
    public static int reconReach(BaseState b) {
        int half = BaseZones.half(b);
        if (b.isSettlement() || b.testHalf > 0) {
            return Math.max(1, half - 2);
        }
        return LAIR_REACH;
    }

    /**
     * The 25 look-out points of {@code b}'s grid of reach {@code reach}: 1.5 above its floor at its centre block's middle
     * plus {@code reach} times -1, -1/2, 0, 1/2 and 1 on each axis; point i * 5 + j lies i steps east and j steps south of
     * the north-west corner (the middle is 12).
     */
    public static List<Vec3> points(BaseState b, int reach) {
        List<Vec3> out = new ArrayList<>(GRID * GRID);
        double y = b.center.getY() + 1.5;
        for (int i = 0; i < GRID; i++) {
            for (int j = 0; j < GRID; j++) {
                double dx = reach * (2.0 * i / (GRID - 1) - 1), dz = reach * (2.0 * j / (GRID - 1) - 1);
                out.add(new Vec3(b.center.getX() + 0.5 + dx, y, b.center.getZ() + 0.5 + dz));
            }
        }
        return out;
    }

    /** The mask of {@code points} (at most 32) that {@code observer} has a clear view of. */
    static int inView(Level level, Entity observer, List<Vec3> points) {
        int mask = 0;
        for (int k = 0; k < points.size() && k < 32; k++) {
            if (clear(level, observer, points.get(k))) {
                mask |= 1 << k;
            }
        }
        return mask;
    }

    /**
     * The defenders of {@code b} in {@code area} that {@code observer} has in sight: a settlement's soldiers (into
     * {@code soldiers}, by UUID, with their visible power: what anyone sees, the armour worn and the weapon held) and its
     * heroes (into {@code heroes}, by hero id, at their war power at full strength); a lair's hostile monsters (power 1
     * each).
     */
    private static void defenders(ServerLevel level, LivingEntity observer, BaseState b, AABB area, Map<UUID, Double> soldiers,
                                  Map<String, Double> heroes) {
        if (b.isSettlement()) {
            for (FactionNpc n : level.getEntitiesOfClass(FactionNpc.class, area, n -> n.isAlive() && n.getFaction() == b.faction
                    && !n.isDisplayOnly() && n.fightsLikeSoldier())) {
                if (!observer.hasLineOfSight(n)) {
                    continue;
                }
                if (n instanceof com.factionclash.hero.HeroEntity h) {
                    heroes.put(h.heroId(), seenHeroPower(h));
                } else {
                    soldiers.put(n.getUUID(), FactionDirector.power(n));
                }
            }
        } else {
            for (Monster m : level.getEntitiesOfClass(Monster.class, area, CombatRules::isHostileMonster)) {
                if (observer.hasLineOfSight(m)) {
                    soldiers.put(m.getUUID(), 1.0);
                }
            }
        }
    }

    /**
     * The operators' truth of what a recon sample's box holds now (recon.md 10.4, task R6; never read by a faction): a
     * settlement's living soldiers in it whatever is in sight (heroes apart), its heroes in it by hero id, every hostile
     * monster in a lair's box, and the lair's own monsters on its record. The box is the sample's
     * ({@link #reconSample}: the walls' half or the monsters' ground, plus 8, 12 up and down), so a watch's count can be
     * set beside it. The scout's watch logs it at its last sample for {@code tools/warcheck.py}.
     */
    public record Truth(int soldiers, List<String> heroes, int monsters, int lairMobs) {
    }

    /** {@link Truth} of {@code b} now (the operators' log line at a watch's end; truth: never a faction's knowledge). */
    public static Truth truth(ServerLevel level, BaseState b) {
        int box = Math.max(BaseZones.half(b), reconReach(b)) + 8;
        AABB area = new AABB(b.center).inflate(box, 12, box);
        int soldiers = 0, monsters = 0;
        List<String> heroes = new ArrayList<>();
        if (b.isSettlement()) {
            for (FactionNpc n : level.getEntitiesOfClass(FactionNpc.class, area, n -> n.isAlive() && n.getFaction() == b.faction
                    && !n.isDisplayOnly() && n.fightsLikeSoldier())) {
                if (n instanceof com.factionclash.hero.HeroEntity h) {
                    heroes.add(h.heroId());
                } else {
                    soldiers++;
                }
            }
        } else {
            monsters = level.getEntitiesOfClass(Monster.class, area, CombatRules::isHostileMonster).size();
        }
        return new Truth(soldiers, heroes, monsters, b.isSettlement() ? 0 : b.lairMobs.size());
    }

    /**
     * A watch as saved with its scout's job (recon.md 7): the yard points seen, the faces counted by UUID with their
     * power, the heroes with theirs, and the first and newest sample times. The runtime entity id is never saved: it
     * changes when a chunk reloads, and a watch resumed after a save would count one soldier twice.
     */
    public static CompoundTag saveWatch(ReconWatch w) {
        CompoundTag t = new CompoundTag();
        t.putInt("mask", w.mask());
        t.putLong("first", w.first());
        t.putLong("last", w.last());
        ListTag faces = new ListTag();
        w.soldiers().forEach((id, power) -> {
            CompoundTag f = new CompoundTag();
            f.putUUID("id", id);
            f.putDouble("power", power);
            faces.add(f);
        });
        t.put("faces", faces);
        CompoundTag heroes = new CompoundTag();
        w.heroes().forEach(heroes::putDouble);
        t.put("heroes", heroes);
        return t;
    }

    /** A watch as {@link #saveWatch} wrote it: later samples go on counting the same faces once. */
    public static ReconWatch loadWatch(CompoundTag t) {
        Map<UUID, Double> soldiers = new LinkedHashMap<>();
        ListTag faces = t.getList("faces", Tag.TAG_COMPOUND);
        for (int i = 0; i < faces.size(); i++) {
            CompoundTag f = faces.getCompound(i);
            if (f.hasUUID("id")) {
                soldiers.put(f.getUUID("id"), f.getDouble("power"));
            }
        }
        Map<String, Double> heroes = new LinkedHashMap<>();
        CompoundTag h = t.getCompound("heroes");
        for (String id : h.getAllKeys()) {
            heroes.put(id, h.getDouble(id));
        }
        return ReconWatch.restore(t.getInt("mask"), soldiers, heroes, t.contains("first") ? t.getLong("first") : -1,
                t.contains("last") ? t.getLong("last") : -1);
    }

    /** {@code b} seen in ruins, its heart gone, at {@code at}: a fallen report of its faction (by nobody yet). */
    public static Report ruins(BaseState b, long at) {
        Report.Kind kind = b.isSettlement() ? Report.Kind.ENEMY_BASE : Report.Kind.LAIR;
        String what = b.faction.displayName + (kind == Report.Kind.LAIR ? " lair" : " settlement");
        Report r = new Report(kind, b.center, at, b.faction, 0, what + " in ruins, its heart gone");
        r.fallen = true;
        return r;
    }

    /** A hero's war power as seen: its full strength (its health bar and level show it; it heals before a defence). */
    static double seenHeroPower(com.factionclash.hero.HeroEntity h) {
        return com.factionclash.logic.hero.HeroRules.heroPower(h.getMaxHealth(), h.npcLevel(), FCConfig.HERO_POWER_PERCENT.get());
    }

    /** The middle of each wall's top, any one in view. */
    private static boolean wallsInView(ServerLevel level, LivingEntity observer, BaseState b, int half) {
        double y = b.center.getY() + 4.5;
        double cx = b.center.getX() + 0.5, cz = b.center.getZ() + 0.5;
        // just outside each wall, at its top: a ray that reaches it has the wall in view
        double[][] at = {{cx - half - 1.2, cz}, {cx + half + 1.2, cz}, {cx, cz - half - 1.2}, {cx, cz + half + 1.2}};
        for (double[] p : at) {
            if (clear(level, observer, new Vec3(p[0], y, p[1]))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether {@code e} sees that {@code target} has fallen: its heart is gone and the heart's place is in view within
     * {@link #HEART_SIGHT} (a raider marching on a base learns of its fall there, not from the far side of the map).
     */
    public static boolean seesFall(Entity e, BaseState target) {
        if (target == null || !target.destroyed || !(e.level() instanceof ServerLevel level)) {
            return false;
        }
        BlockPos heart = target.point(BaseState.HEART) != null ? target.point(BaseState.HEART) : target.center;
        Vec3 spot = Vec3.atCenterOf(heart);
        return e.getEyePosition().distanceToSqr(spot) <= HEART_SIGHT * HEART_SIGHT && clear(level, e, spot);
    }

    /** A clear view from {@code e}'s eyes to {@code to} (blocks with a collision shape block it; fluids do not). */
    static boolean clear(Level level, Entity e, Vec3 to) {
        Vec3 eye = e.getEyePosition();
        if (eye.distanceToSqr(to) > 128 * 128) {
            return false;
        }
        return level.clip(new ClipContext(eye, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, e)).getType() == HitResult.Type.MISS;
    }

    private static double horizontal(Vec3 p, BlockPos c) {
        double dx = p.x - (c.getX() + 0.5), dz = p.z - (c.getZ() + 0.5);
        return Math.sqrt(dx * dx + dz * dz);
    }

    // ------------------------------------------------------------------ a strike seen at home

    /**
     * An enemy fighter fell ({@code FactionNpc.die}; docs/design/recon.md 6, task R5): every faction whose people saw it
     * fall at home (inside its home area, or seen by a member standing in it; {@link RollCall#witnesses}) notes that the
     * dead fighter's faction struck it ({@link FactionState#struckBy}), a hero's fall included, whether or not it knows
     * where that faction lives. It touches no report: a fall seen at our walls never lowers an estimate (only a focused
     * recon makes one); it makes the council send a scout to that faction's nearest base once the fight is over. A
     * soldier's fall is also marked in the sightings of the factions that saw it ({@link EnemySightings#fell}, task R11).
     */
    public static void enemyFell(ServerLevel level, CampaignData data, FactionNpc victim) {
        if (victim.isDisplayOnly() || !victim.fightsLikeSoldier() || victim.getFaction() == null || !victim.getFaction().human) {
            return;
        }
        BlockPos at = victim.blockPosition();
        long now = level.getGameTime();
        for (FactionType seer : FactionType.humans()) {
            FactionState f = data.faction(seer);
            if (seer == victim.getFaction() || !f.alive || !Diplomacy.hostile(seer, victim.getFaction())) {
                continue;
            }
            boolean seen = RollCall.inHomeArea(data, seer, level, at);
            if (!seen) {
                List<RollCall.Witness> w = RollCall.witnesses(level, data, victim, seer);
                seen = w.stream().anyMatch(x -> x.home() && !x.player());
            }
            if (seen && f.struck(victim.getFaction(), now)) {
                data.setDirty();
            }
        }
        if (!(victim instanceof com.factionclash.hero.HeroEntity)) {
            // a soldier seen to fall is no reserve (recon.md 5.5, task R11): at once where it was seen from home, carried
            // home by a scout away that saw it (a hero's fall is filed from HeroManager's death path)
            EnemySightings.fell(level, data, victim, now);
        }
    }
}
