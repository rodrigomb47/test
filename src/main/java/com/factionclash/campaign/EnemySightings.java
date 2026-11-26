package com.factionclash.campaign;

import com.factionclash.config.FCConfig;
import com.factionclash.faction.FactionType;
import com.factionclash.hero.HeroEntity;
import com.factionclash.logic.campaign.Sightings;
import com.factionclash.npc.FactionNpc;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * What a faction's people see of enemy soldiers and heroes away from their base ({@code docs/design/recon.md} 5.5, task
 * R11): the game side of the sightings ledger ({@link Sightings}, kept in {@link FactionState#sightings}). The war
 * council's plan reads the ledger only upward, and only on top of a fresh good recon: a sighting never makes an estimate.
 *
 * <ul>
 * <li><b>At home</b> ({@link #atHome}, from the alarm's sweep every second): an enemy soldier or hero inside a home area
 * of ours that the alarm noticed, or that a member NPC or hero standing in a home area sees within the witness range
 * (32 blocks). Known at once.</li>
 * <li><b>A scout on any trip</b> ({@link #field}, with its looks every 40 ticks outside its home area): an enemy soldier
 * or hero in sight within 64 blocks, outside the walls box (half + 8) of every base of that faction the scout knows of or
 * sees. Carried with its news, told at the station, lost if it dies.</li>
 * <li><b>A recon scout on its watch</b> ({@link #watch}, each sample): every hero of the target's faction it sees, and
 * the target's soldiers in sight outside the target's box (a party leaving, a patrol below the pillar). Told with its
 * report at the station.</li>
 * <li><b>Falls</b> ({@link #fell}): an enemy soldier ({@code BaseWatch.enemyFell}) or hero ({@code HeroManager.onDeath})
 * seen to fall: at once by the factions whose people saw it at home (in a home area of theirs, or seen by a member
 * standing in one), carried home by each of their scouts away that saw it.</li>
 * </ul>
 *
 * A sighting keeps the entity's UUID (never the runtime id, which changes at a reload), its faction, a hero's id, its
 * visible power (a soldier's armour and weapon, a hero's war power at full strength), the time and place, how it was
 * seen and by whom. Member players file nothing yet (recon.md 5.5's table). Server thread only.
 */
public final class EnemySightings {
    private EnemySightings() {
    }

    /** A sighting at home or in the field is made within this many blocks of the one who sees it ({@code BaseWatch.SIGHT}). */
    public static final double SIGHT = BaseWatch.SIGHT;
    /** The walls box of a base a sighting counts outside of: its walls' half plus this (recon.md 5.5). */
    public static final int BOX = 8;
    /** At most this many of the nearest members are asked whether they see an enemy at home (as the alarm asks four). */
    static final int LOOKOUTS = 4;

    /**
     * Whether {@code e} is a fighter whose sighting a faction of {@code ours} keeps: an enemy soldier or hero (not a display
     * copy or preview, not a worker passing through) of a human faction at war with ours.
     */
    public static boolean enemyFighter(Entity e, FactionType ours) {
        if (!(e instanceof FactionNpc n) || !n.isAlive() || n.isDisplayOnly() || !n.fightsLikeSoldier()) {
            return false;
        }
        if (n instanceof HeroEntity h && h.isPreview()) {
            return false;
        }
        FactionType theirs = n.getFaction();
        return theirs != null && theirs.human && ours != null && theirs != ours && Diplomacy.hostile(ours, theirs);
    }

    /**
     * A sighting of {@code e} at {@code now}: its UUID, faction, hero id ("" for a soldier) and visible power (a soldier's
     * armour worn and weapon held, {@code FactionDirector.power}; a hero's war power at full strength, as a watch counts
     * it), where it stands, how it was seen, by whom ("" at home) and the base watched ({@link Sightings#NO_BASE} unless a
     * watch's).
     */
    public static Sightings.Sighting of(FactionNpc e, long now, Sightings.Source source, String by, int base) {
        String hero = e instanceof HeroEntity h ? h.heroId() : "";
        double power = e instanceof HeroEntity h ? BaseWatch.seenHeroPower(h) : FactionDirector.power(e);
        BlockPos p = e.blockPosition();
        return new Sightings.Sighting(e.getUUID(), e.getFaction().id, hero, power, now, p.getX(), p.getZ(), source, by, base, false);
    }

    // ------------------------------------------------------------------ at home

    /**
     * The home sweep (recon.md 5.5, the alarm's one pass a second; {@code FactionDirector.defenceAlarm}): every enemy
     * soldier or hero inside {@code b}'s home area that the alarm noticed ({@code noticed}: its intruders, seen or heard),
     * or that one of the nearest members of {@code b}'s faction standing in a home area of it sees within the witness
     * range, is a sighting at our walls. One outside the home area is not, whatever the alarm's box holds.
     */
    static void atHome(ServerLevel level, CampaignData data, BaseState b, Collection<? extends LivingEntity> noticed, long now) {
        if (b.faction == null || !b.faction.human || b.center == null) {
            return;
        }
        FactionState f = data.faction(b.faction);
        if (!f.alive) {
            return;
        }
        int r = RollCall.homeRadius(b);
        AABB area = new AABB(b.center).inflate(r + 1, 48, r + 1);
        List<FactionNpc> enemies = level.getEntitiesOfClass(FactionNpc.class, area,
                n -> enemyFighter(n, b.faction) && BaseZones.chebyshev(b, n.blockPosition()) <= r);
        boolean changed = false;
        for (FactionNpc e : enemies) {
            if (noticed.contains(e) || seenFromHome(level, data, b.faction, e)) {
                f.sightings.file(of(e, now, Sightings.Source.HOME, "", Sightings.NO_BASE));
                changed = true;
            }
        }
        if (changed) {
            data.setDirty();
        }
    }

    /**
     * Whether a member NPC or hero of {@code faction} standing in one of its home areas sees {@code e} (a line of sight
     * within the witness range, the roll call's rule): the nearest {@link #LOOKOUTS} such members are asked.
     */
    static boolean seenFromHome(ServerLevel level, CampaignData data, FactionType faction, LivingEntity e) {
        double r = FCConfig.WITNESS_RANGE.get();
        List<FactionNpc> members = level.getEntitiesOfClass(FactionNpc.class, e.getBoundingBox().inflate(r),
                n -> n != e && n.isAlive() && !n.isDisplayOnly() && n.getFaction() == faction && n.distanceToSqr(e) <= r * r
                        && RollCall.inHomeArea(data, faction, level, n.blockPosition()));
        members.sort(Comparator.comparingDouble(n -> n.distanceToSqr(e)));
        for (int i = 0; i < Math.min(LOOKOUTS, members.size()); i++) {
            if (members.get(i).hasLineOfSight(e)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ falls

    /**
     * An enemy fighter fell (recon.md 5.5's fall hooks): a soldier from {@code BaseWatch.enemyFell}, a hero from
     * {@code HeroManager.onDeath}. Every human faction at war with the victim's whose people saw it at home (it fell in a
     * home area of theirs, or a member NPC or hero standing in one saw it, the roll call's witness rule) files the fall at
     * once; each of its scouts away that saw it carries the fall home ({@code ScoutJob.carry}); other members away carry no
     * sighting yet. A soldier seen to fall stays fallen; a hero's fall counts until half a day after its expected return.
     */
    public static void fell(ServerLevel level, CampaignData data, FactionNpc victim, long now) {
        FactionType theirs = victim.getFaction();
        if (victim.isDisplayOnly() || !victim.fightsLikeSoldier() || theirs == null || !theirs.human
                || victim instanceof HeroEntity h && h.isPreview()) {
            return;
        }
        BlockPos at = victim.blockPosition();
        for (FactionType seer : FactionType.humans()) {
            FactionState f = data.faction(seer);
            if (seer == theirs || !f.alive || !Diplomacy.hostile(seer, theirs)) {
                continue;
            }
            boolean home = RollCall.inHomeArea(data, seer, level, at);
            List<RollCall.Witness> w = home ? List.of() : RollCall.witnesses(level, data, victim, seer);
            if (!home) {
                home = w.stream().anyMatch(x -> x.home() && !x.player());
            }
            if (home) {
                f.sightings.file(of(victim, now, Sightings.Source.HOME, "", Sightings.NO_BASE).fell(now));
                data.setDirty();
                continue;
            }
            for (RollCall.Witness x : w) {
                if (!x.player() && level.getEntity(x.uuid()) instanceof FactionNpc n && n.isAlive()
                        && n.job() instanceof com.factionclash.work.ScoutJob scout) {
                    scout.carry(of(victim, now, Sightings.Source.FIELD, n.npcName(), Sightings.NO_BASE).fell(now));
                }
            }
        }
    }

    // ------------------------------------------------------------------ a scout away

    /**
     * A scout's look for enemy fighters on any trip (recon.md 5.5), with its looks every 40 ticks while it is outside its
     * home area: each enemy soldier or hero in its sight within {@link #SIGHT} blocks, outside the walls box of every base
     * of that faction it knows of (its faction's reports, and the ones it carries, {@code pending}), goes into
     * {@code carried} as met by it. A fighter in its own yard is that base's garrison, which only a focused recon counts.
     */
    public static void field(ServerLevel level, CampaignData data, FactionNpc scout, Sightings carried, Collection<Report> pending,
                             long now) {
        FactionType ours = scout.getFaction();
        List<FactionNpc> seen = level.getEntitiesOfClass(FactionNpc.class, scout.getBoundingBox().inflate(SIGHT, 24, SIGHT),
                n -> n != scout && enemyFighter(n, ours) && horizontal(scout, n) <= SIGHT);
        if (seen.isEmpty()) {
            return;
        }
        List<BaseState> known = new ArrayList<>();
        List<Report> reports = new ArrayList<>(data.faction(ours).knowledge);
        reports.addAll(pending);
        for (Report r : reports) {
            if (r.kind == Report.Kind.ENEMY_BASE && r.subject != null) {
                BaseState b = FactionDirector.reportedBase(data, r);
                if (b != null && !known.contains(b)) {
                    known.add(b);
                }
            }
        }
        for (FactionNpc n : seen) {
            if (atItsBase(n, known) || !scout.hasLineOfSight(n)) {
                continue;
            }
            carried.file(of(n, now, Sightings.Source.FIELD, scout.npcName(), Sightings.NO_BASE));
        }
    }

    /** Whether {@code n} stands inside the walls box (half + {@link #BOX}) of one of {@code bases} of its own faction. */
    static boolean atItsBase(FactionNpc n, Collection<BaseState> bases) {
        for (BaseState b : bases) {
            if (b.faction == n.getFaction() && b.center != null && BaseZones.chebyshev(b, n.blockPosition()) <= BaseZones.half(b) + BOX) {
                return true;
            }
        }
        return false;
    }

    /**
     * A recon scout's look for sightings at one sample of its watch of {@code target} (recon.md 5.5): every hero of the
     * target's faction it sees in the sample's area, and every soldier or hero of that faction in its sight within
     * {@link #SIGHT} blocks outside the sample's box (the walls, or a lair's ground, plus 8: a party leaving, a patrol below
     * the pillar), go into {@code carried} as seen by it on its watch of that base. The soldiers inside the box are the
     * watch's count, not sightings.
     */
    public static void watch(ServerLevel level, FactionNpc scout, BaseState target, Sightings carried, long now) {
        if (target == null || target.center == null || !target.isSettlement() || target.faction == null) {
            return;
        }
        FactionType ours = scout.getFaction();
        int box = Math.max(BaseZones.half(target), BaseWatch.reconReach(target)) + BOX;
        List<FactionNpc> seen = new ArrayList<>(level.getEntitiesOfClass(HeroEntity.class,
                new AABB(target.center).inflate(box, 12, box), h -> h.getFaction() == target.faction && enemyFighter(h, ours)));
        for (FactionNpc n : level.getEntitiesOfClass(FactionNpc.class, scout.getBoundingBox().inflate(SIGHT, 24, SIGHT),
                n -> n.getFaction() == target.faction && enemyFighter(n, ours) && horizontal(scout, n) <= SIGHT
                        && BaseZones.chebyshev(target, n.blockPosition()) > box)) {
            if (!seen.contains(n)) {
                seen.add(n);
            }
        }
        for (FactionNpc n : seen) {
            if (scout.hasLineOfSight(n)) {
                carried.file(of(n, now, Sightings.Source.WATCH, scout.npcName(), target.id));
            }
        }
    }

    private static double horizontal(Entity a, Entity b) {
        double dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    // ------------------------------------------------------------------ saving

    /** The ledger as saved (with the faction's state, or with a scout's job: what it carries home). */
    public static ListTag save(Sightings s) {
        ListTag l = new ListTag();
        for (Sightings.Sighting x : s.all()) {
            CompoundTag t = new CompoundTag();
            t.putUUID("id", x.id());
            t.putString("faction", x.faction());
            if (x.isHero()) {
                t.putString("hero", x.hero());
            }
            t.putDouble("power", x.power());
            t.putLong("at", x.at());
            t.putInt("x", x.x());
            t.putInt("z", x.z());
            t.putString("source", x.source().name());
            if (!x.by().isEmpty()) {
                t.putString("by", x.by());
            }
            t.putInt("base", x.base());
            t.putBoolean("fallen", x.fallen());
            l.add(t);
        }
        return l;
    }

    /** Files the sightings {@link #save} wrote into {@code into} (one without a UUID or of an unknown source is left out). */
    public static void load(ListTag l, Sightings into) {
        for (int i = 0; i < l.size(); i++) {
            CompoundTag t = l.getCompound(i);
            if (!t.hasUUID("id")) {
                continue;
            }
            Sightings.Source source;
            try {
                source = Sightings.Source.valueOf(t.getString("source"));
            } catch (IllegalArgumentException e) {
                continue;
            }
            UUID id = t.getUUID("id");
            into.file(new Sightings.Sighting(id, t.getString("faction"), t.getString("hero"), t.getDouble("power"), t.getLong("at"),
                    t.getInt("x"), t.getInt("z"), source, t.getString("by"), t.contains("base") ? t.getInt("base") : Sightings.NO_BASE,
                    t.getBoolean("fallen")));
        }
    }

    /** The ledger's list tag in {@code t} under {@code key}, read into {@code into}. */
    public static void load(CompoundTag t, String key, Sightings into) {
        load(t.getList(key, Tag.TAG_COMPOUND), into);
    }
}
