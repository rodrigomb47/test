package com.factionclash.campaign;

import com.factionclash.faction.FactionType;
import com.factionclash.npc.NpcRole;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Faction-wide state: survival, delivered knowledge, roster and war plans. */
public final class FactionState {
    public static final int MAX_REPORTS = 160;

    public FactionType type;
    public boolean alive = true;
    public long eliminatedAt;
    public final List<Report> knowledge = new ArrayList<>();
    public final Map<UUID, NpcRecord> roster = new LinkedHashMap<>();
    public long lastRaidAt;
    /** Active raid: target base id (-1 none), members and launch time. */
    public int raidTarget = -1;
    public final List<UUID> raiders = new ArrayList<>();
    public long raidStartedAt;
    public int raidsLaunched;
    /** Raid muster: party size at launch, rally point outside the target, and when the party set off together (0 = gathering). */
    public int raidParty;
    public long raidRally = Long.MIN_VALUE;
    public long raidMusteredAt;
    /**
     * The council's view of its last raid ({@code docs/design/missing_npcs.md} 6): the id of the news of its end that has
     * not reached home yet (carried by the survivors, or by nobody when none lived), null when the council knows it is
     * over. While set, and until the raid's own bound has passed, the council believes the raid is still out.
     */
    public UUID raidEndNews;
    /** The last raid's party as launched (the council's own list, kept after the raid ends). */
    public final List<UUID> lastParty = new ArrayList<>();
    /**
     * The raider whose leaving (home with its loot, or falling back wounded) emptied the party: the last one there, it saw
     * the raid's end and carries it home (live check of 2026-11-19: a party that came home one by one never told its end,
     * and the council waited out the raid's whole bound). Null while others are still in the party.
     */
    public UUID raidLast;
    /**
     * The council's plan for its raid, in ticks from the launch (knowledge review of 2026-11-19, known leak 5): the march
     * there, the muster, the assault and the march home ({@code Whereabouts.raidPlanTicks}). Its raiders are expected back
     * by then; 0 for a raid launched before the plan was kept (the raid's hard limit then, as before).
     */
    public long raidPlanTicks;
    /**
     * What the raid party saw of its target (its best look, or its ruins), carried home with the raid's end: the faction's
     * report of that base is refreshed when the end's news is told at home ({@code RollCall.tell}); lost with a party
     * nobody lived to report on.
     */
    public Report raidIntel;
    /** When the council expects its raid home ({@code Whereabouts.raidPlannedBy}); {@code blocksBack}: an old raid's walk home. */
    public long raidPlannedBy(double blocksBack) {
        return com.factionclash.logic.campaign.Whereabouts.raidPlannedBy(raidStartedAt, raidPlanTicks, blocksBack);
    }
    public int basesDestroyed;
    /** Average fighting power of the faction's soldiers at home (1 = bare-handed and unarmoured, up to 3), last seen. */
    public float soldierPower = 1.0F;
    /** 32x32 map sectors covered by delivered scout reports. */
    public final java.util.Set<Long> explored = new java.util.HashSet<>();
    /** How much further than the configured scout range the scouts now go (grows while no rival has been found). */
    public int scoutReach;
    public int reportsDelivered;
    /** Experience the faction's NPCs picked up as orbs (mined ores, smelting, breeding), for enchanting. */
    public long xpBank;
    /** Vanilla advancements the faction has made (id -> when and by whom), in the order they were made. */
    public final Map<String, Achieved> achievements = new LinkedHashMap<>();
    /** Criteria met so far of advancements not yet made (id -> criterion names). */
    public final Map<String, java.util.Set<String>> advancementProgress = new java.util.HashMap<>();

    public record Achieved(long time, String by) {
    }

    /**
     * A raider leaves the party alive (home with its loot, falling back wounded, a hero's way home): when it was the last
     * one, it is the one who saw the raid end ({@link #raidLast}). Returns whether it was in the party.
     */
    public boolean leaveRaid(UUID raider) {
        if (!raiders.remove(raider)) {
            return false;
        }
        raidLast = raiders.isEmpty() ? raider : null;
        return true;
    }

    /**
     * News carried home, by carrier (a member NPC, hero or player): deaths its members saw away from home and lines only
     * someone there could tell ({@code docs/design/missing_npcs.md} 5). Told by {@code RollCall} when the carrier is home.
     */
    public final Map<UUID, List<News>> news = new LinkedHashMap<>();

    /**
     * The war council's open recon orders ({@code docs/design/recon.md} 2.1): at most one ordinary and one urgent
     * ({@link com.factionclash.logic.campaign.ReconRules#ORDERS_MAX}). Kept and saved since task R2; R3 places and settles
     * them, R4b's scouts take them at home.
     */
    public final List<ReconOrder> reconOrders = new ArrayList<>();
    /**
     * When the faction last saw each enemy faction strike it at home (recon.md 6): an enemy soldier or hero falling in its
     * home area or seen falling by a member standing in it ({@code BaseWatch.enemyFell}), or an enemy fighter its alarm
     * noticed inside a settlement's walls box ({@code FactionDirector.defenceAlarm}). The faction's own observation,
     * saved; it never lowers an estimate. Read from task R3 on (the urgent recon and the counter-attack).
     */
    public final Map<FactionType, Long> struckBy = new java.util.EnumMap<>(FactionType.class);

    /** {@code striker} was seen striking us at {@code at}: the newest strike is kept. Returns whether that changed anything. */
    public boolean struck(FactionType striker, long at) {
        if (striker == null || striker == type) {
            return false;
        }
        Long old = struckBy.get(striker);
        if (old != null && old >= at) {
            return false;
        }
        struckBy.put(striker, at);
        return true;
    }

    /** When {@code striker} was last seen striking us (0: never). */
    public long struckAt(FactionType striker) {
        Long at = striker == null ? null : struckBy.get(striker);
        return at == null ? 0 : at;
    }

    public FactionState() {
    }

    public static long sector(int x, int z) {
        return ((long) (x >> 5) << 32) | ((z >> 5) & 0xffffffffL);
    }

    public static int sectorCenterX(long key) {
        return (int) (key >> 32) * 32 + 16;
    }

    public static int sectorCenterZ(long key) {
        return (int) key * 32 + 16;
    }

    public FactionState(FactionType type) {
        this.type = type;
    }

    public long living(NpcRole role) {
        return roster.values().stream().filter(r -> r.alive && r.role == role).count();
    }

    public long living() {
        return roster.values().stream().filter(r -> r.alive).count();
    }

    /**
     * Adds or refreshes a delivered report: a newer observation of the same subject replaces the older one (see
     * {@link Report#merge}: a village seen without traders keeps the traders seen before). Over {@link #MAX_REPORTS}
     * the oldest report is forgotten - the oldest that is not a village, pillager outpost or woodland mansion, which
     * stay where they are and which traders and the workers' dangerous-ground rules rely on.
     *
     * <p>A settlement's or lair's focused recon ({@code docs/design/recon.md} 4) follows its own table, by delivery and the
     * watches' times rather than by {@code observedAt} ({@link Report#fileRecon}): the kept report holds only the newest
     * good recon, with the failed attempts since it; a first report of a base that brought a blind recon keeps no recon
     * and one failed attempt.
     */
    public boolean addReport(Report r) {
        for (int i = 0; i < knowledge.size(); i++) {
            Report old = knowledge.get(i);
            if (old.sameSubject(r)) {
                Report merged = r.observedAt >= old.observedAt ? Report.merge(old, r) : Report.merge(r, old);
                if (merged.base()) {
                    merged.reconFile(Report.fileRecon(old, r));
                }
                knowledge.set(i, merged);
                return false;
            }
        }
        if (r.base()) {
            r.reconFile(Report.fileRecon(null, r));
        }
        knowledge.add(r);
        while (knowledge.size() > MAX_REPORTS) {
            int drop = 0;
            for (int i = 0; i < knowledge.size(); i++) {
                if (!knowledge.get(i).kind.structure()) {
                    drop = i;
                    break;
                }
            }
            knowledge.remove(drop);
        }
        return true;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putString("type", type.id);
        t.putBoolean("alive", alive);
        t.putLong("eliminatedAt", eliminatedAt);
        ListTag k = new ListTag();
        for (Report r : knowledge) {
            k.add(r.save());
        }
        t.put("knowledge", k);
        ListTag ros = new ListTag();
        for (NpcRecord r : roster.values()) {
            ros.add(r.save());
        }
        t.put("roster", ros);
        t.putLong("lastRaid", lastRaidAt);
        t.putInt("raidTarget", raidTarget);
        ListTag rd = new ListTag();
        for (UUID u : raiders) {
            rd.add(NbtUtils.createUUID(u));
        }
        t.put("raiders", rd);
        t.putLong("raidStarted", raidStartedAt);
        t.putInt("raidsLaunched", raidsLaunched);
        t.putInt("raidParty", raidParty);
        t.putLong("raidRally", raidRally);
        t.putLong("raidMusteredAt", raidMusteredAt);
        if (raidEndNews != null) {
            t.putUUID("raidEndNews", raidEndNews);
        }
        if (raidLast != null) {
            t.putUUID("raidLast", raidLast);
        }
        ListTag lp = new ListTag();
        for (UUID u : lastParty) {
            lp.add(NbtUtils.createUUID(u));
        }
        t.put("lastParty", lp);
        t.putLong("raidPlan", raidPlanTicks);
        if (raidIntel != null) {
            t.put("raidIntel", raidIntel.save());
        }
        t.putInt("basesDestroyed", basesDestroyed);
        t.putFloat("soldierPower", soldierPower);
        t.putLongArray("explored", explored.stream().mapToLong(Long::longValue).toArray());
        t.putInt("reportsDelivered", reportsDelivered);
        t.putInt("scoutReach", scoutReach);
        t.putLong("xpBank", xpBank);
        ListTag ach = new ListTag();
        achievements.forEach((id, a) -> {
            CompoundTag e = new CompoundTag();
            e.putString("id", id);
            e.putLong("time", a.time());
            e.putString("by", a.by());
            ach.add(e);
        });
        t.put("achievements", ach);
        CompoundTag prog = new CompoundTag();
        advancementProgress.forEach((id, set) -> {
            ListTag l = new ListTag();
            set.forEach(c -> l.add(net.minecraft.nbt.StringTag.valueOf(c)));
            prog.put(id, l);
        });
        t.put("advancementProgress", prog);
        ListTag nl = new ListTag();
        news.forEach((carrier, items) -> {
            for (News n : items) {
                CompoundTag e = n.save();
                e.putUUID("carrier", carrier);
                nl.add(e);
            }
        });
        t.put("news", nl);
        ListTag ro = new ListTag();
        for (ReconOrder o : reconOrders) {
            ro.add(o.save());
        }
        t.put("reconOrders", ro);
        CompoundTag sb = new CompoundTag();
        struckBy.forEach((faction, at) -> sb.putLong(faction.id, at));
        t.put("struckBy", sb);
        return t;
    }

    public static FactionState load(CompoundTag t) {
        FactionState f = new FactionState();
        f.type = FactionType.byId(t.getString("type"));
        if (f.type == null) {
            return null;
        }
        f.alive = t.getBoolean("alive");
        f.eliminatedAt = t.getLong("eliminatedAt");
        ListTag k = t.getList("knowledge", Tag.TAG_COMPOUND);
        for (int i = 0; i < k.size(); i++) {
            Report r = Report.load(k.getCompound(i));
            if (r != null) {
                f.knowledge.add(r);
            }
        }
        ListTag ros = t.getList("roster", Tag.TAG_COMPOUND);
        for (int i = 0; i < ros.size(); i++) {
            NpcRecord r = NpcRecord.load(ros.getCompound(i));
            if (r != null) {
                f.roster.put(r.uuid, r);
            }
        }
        f.lastRaidAt = t.getLong("lastRaid");
        f.raidTarget = t.contains("raidTarget") ? t.getInt("raidTarget") : -1;
        for (Tag u : t.getList("raiders", Tag.TAG_INT_ARRAY)) {
            f.raiders.add(NbtUtils.loadUUID(u));
        }
        f.raidStartedAt = t.getLong("raidStarted");
        f.raidsLaunched = t.getInt("raidsLaunched");
        f.raidParty = t.getInt("raidParty");
        f.raidRally = t.contains("raidRally") ? t.getLong("raidRally") : Long.MIN_VALUE;
        f.raidMusteredAt = t.getLong("raidMusteredAt");
        f.raidEndNews = t.hasUUID("raidEndNews") ? t.getUUID("raidEndNews") : null; // absent before the review of 2026-11-19
        f.raidLast = t.hasUUID("raidLast") ? t.getUUID("raidLast") : null; // absent before the live check of 2026-11-19
        for (Tag u : t.getList("lastParty", Tag.TAG_INT_ARRAY)) {
            f.lastParty.add(NbtUtils.loadUUID(u));
        }
        // the raid's plan and what the party saw: absent before the knowledge review of 2026-11-19 ("pactOffensives", the
        // pact allies' offensives of a save from before 2026-11-25, is left unread: the owner removed pacts)
        f.raidPlanTicks = t.getLong("raidPlan");
        f.raidIntel = t.contains("raidIntel") ? Report.load(t.getCompound("raidIntel")) : null;
        f.basesDestroyed = t.getInt("basesDestroyed");
        f.soldierPower = t.contains("soldierPower") ? t.getFloat("soldierPower") : 1.0F;
        f.xpBank = t.getLong("xpBank");
        ListTag ach = t.getList("achievements", Tag.TAG_COMPOUND);
        for (int i = 0; i < ach.size(); i++) {
            CompoundTag e = ach.getCompound(i);
            f.achievements.put(e.getString("id"), new Achieved(e.getLong("time"), e.getString("by")));
        }
        CompoundTag prog = t.getCompound("advancementProgress");
        for (String id : prog.getAllKeys()) {
            java.util.Set<String> set = new java.util.HashSet<>();
            ListTag l = prog.getList(id, Tag.TAG_STRING);
            for (int i = 0; i < l.size(); i++) {
                set.add(l.getString(i));
            }
            f.advancementProgress.put(id, set);
        }
        for (long l : t.getLongArray("explored")) {
            f.explored.add(l);
        }
        f.reportsDelivered = t.getInt("reportsDelivered");
        f.scoutReach = t.getInt("scoutReach");
        ListTag nl = t.getList("news", Tag.TAG_COMPOUND); // absent before 2026-11-19: no news carried
        for (int i = 0; i < nl.size(); i++) {
            CompoundTag e = nl.getCompound(i);
            News n = News.load(e);
            if (n != null && e.hasUUID("carrier")) {
                f.news.computeIfAbsent(e.getUUID("carrier"), c -> new ArrayList<>()).add(n);
            }
        }
        // the recon orders and the strikes seen: absent before campaign format 5 (2026-11-26: none open, none seen)
        ListTag ro = t.getList("reconOrders", Tag.TAG_COMPOUND);
        for (int i = 0; i < ro.size(); i++) {
            ReconOrder o = ReconOrder.load(ro.getCompound(i));
            if (o != null) {
                f.reconOrders.add(o);
            }
        }
        CompoundTag sb = t.getCompound("struckBy");
        for (String id : sb.getAllKeys()) {
            FactionType striker = FactionType.byId(id);
            if (striker != null) { // (a faction this version does not know is left out)
                f.struckBy.put(striker, sb.getLong(id));
            }
        }
        return f;
    }
}
