package com.factionclash.campaign;

import com.factionclash.config.FCConfig;
import com.factionclash.faction.FactionType;
import com.factionclash.logic.campaign.Whereabouts;
import com.factionclash.logic.campaign.Whereabouts.Event;
import com.factionclash.logic.campaign.Whereabouts.Knowing;
import com.factionclash.logic.campaign.Whereabouts.State;
import com.factionclash.logic.campaign.Whereabouts.Step;
import com.factionclash.npc.FactionNpc;
import com.factionclash.util.Messages;
import com.factionclash.work.Job;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A faction's roll call (the owner's request of 2026-11-13; {@code docs/design/missing_npcs.md}): what the faction knows of
 * where its members are and whether they live. It only knows what its people saw, and knowledge travels home with
 * someone. A member's death is known at once inside a home area of its faction or when a member at home saw it; one
 * seen only by members away is told when one of them comes home; one nobody saw is learned by waiting: a member not back
 * by its trip's expected return (plus a grace) is missing, after a further wait presumed dead and replaced, and it
 * rejoins if it comes home after all. One rule for ordinary NPCs and heroes; a hero's revival at home is how its faction
 * learns of a fall nobody saw. The pure rules are {@link Whereabouts}; this class applies them to the records, carries
 * the news and says the faction's lines. Server thread only.
 *
 * <p>The faction's AI reads only the records' knowledge ({@link NpcRecord#alive}, {@link NpcRecord#where}); it never reads
 * the kill feed or kill credit, which are player-side presentation and truth.
 */
public final class RollCall {
    private RollCall() {
    }

    /** Ticks without a sighting after which a member's entity that is not where it was last seen counts as vanished. */
    static final long VANISH_TICKS = 2400;

    // ------------------------------------------------------------------ settings and the home area

    /** {@code campaign.farDeathsUnknown} (true before the config loads). */
    public static boolean farDeathsUnknown() {
        try {
            return FCConfig.FAR_DEATHS_UNKNOWN.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }

    static Whereabouts.Waits waits() {
        return new Whereabouts.Waits(FCConfig.MISSING_GRACE_TICKS.get(), FCConfig.PRESUMED_DEAD_TICKS.get());
    }

    /** The home area round a settlement: {@code campaign.homeAreaRadius} (a GameTest base: its own radius), at least its walls plus 4. */
    public static int homeRadius(BaseState b) {
        return Math.max(BaseZones.half(b) + 4, BaseZones.workRadius(b, FCConfig.HOME_AREA_RADIUS.get()));
    }

    /** Inside a home area of {@code faction}: within {@link #homeRadius} (horizontally) of a standing settlement's centre. */
    public static boolean inHomeArea(CampaignData data, FactionType faction, Level level, BlockPos p) {
        return edge(data, faction, level, p)[0] <= 0;
    }

    /**
     * How far {@code p} lies past the edge of the nearest home area of {@code faction} (0 or less: inside it) and that
     * area's edge band ({@link Whereabouts#edgeBand}); {Integer.MAX_VALUE, 0} with no home area (another dimension, no
     * settlement standing).
     */
    public static int[] edge(CampaignData data, FactionType faction, Level level, BlockPos p) {
        int[] best = {Integer.MAX_VALUE, 0};
        if (faction == null || level == null || p == null || level.dimension() != Level.OVERWORLD) {
            return best;
        }
        for (BaseState b : data.bases.values()) {
            if (!b.destroyed && b.faction == faction && b.isSettlement()) {
                int r = homeRadius(b);
                int beyond = BaseZones.chebyshev(b, p) - r;
                if (beyond < best[0]) {
                    best[0] = beyond;
                    best[1] = Whereabouts.edgeBand(r);
                }
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ sightings

    /**
     * Every 2 s from a living member (its record timer): inside a home area it is seen at home (back, rejoining, telling
     * the news it carries); outside, a member last seen at home has left, expected back by its trip's plan.
     */
    public static void sighting(FactionNpc npc, NpcRecord rec) {
        if (!(npc.level() instanceof ServerLevel level)) {
            return;
        }
        MinecraftServer server = level.getServer();
        CampaignData data = CampaignData.get(server);
        long now = level.getGameTime();
        rec.dead = false; // truth: it lives (an old record's or a lost entity's mark is wrong)
        rec.vanished = false;
        int[] edge = edge(data, npc.getFaction(), level, npc.blockPosition());
        if (edge[0] <= 0) {
            rec.seenHomeAt = now;
            tell(server, data, npc.getFaction(), npc.getUUID(), npc.npcName());
        }
        // a member near the edge changes side only past its band (knowledge review of 2026-11-19, known leak 6: a miner at
        // a branch face just past the edge crossed it dozens of times a day, each crossing a new plan)
        Step s = Whereabouts.sighted(rec.where, edge[0], edge[1]);
        if (s.event() == Event.LEFT) {
            leaves(data, npc, rec, now);
        } else if (s.changed(rec.where)) {
            apply(server, data, npc.getFaction(), rec, s, now, null, null);
        }
    }

    /** Seen leaving now: away, expected back by its trip's plan. */
    private static void leaves(CampaignData data, FactionNpc npc, NpcRecord rec, long now) {
        rec.leftAt = now;
        rec.expectedBy = now + plan(data, npc, now);
        rec.where(State.AWAY, now);
        data.setDirty();
    }

    /** The plan of the trip a member sets out on now: a raider's raid, else its job's ({@link Job#tripPlanTicks}). */
    public static long plan(CampaignData data, FactionNpc npc, long now) {
        FactionState f = data.faction(npc.getFaction());
        if (f.raidTarget >= 0 && f.raiders.contains(npc.getUUID())) {
            // the council's own plan for the raid (knowledge review of 2026-11-19, known leak 5), not its hard limit
            BaseState target = data.base(f.raidTarget), home = data.base(npc.baseId());
            double back = target != null && home != null ? Math.sqrt(target.center.distSqr(home.center)) : 0;
            return Whereabouts.raidPlan(now, f.raidPlannedBy(back));
        }
        Job job = npc.job();
        return job != null ? job.tripPlanTicks(now) : Whereabouts.DEFAULT_TRIP_TICKS;
    }

    // ------------------------------------------------------------------ the director's waits

    /**
     * Every director pass of a settlement, for its members: one whose entity is gone from a loaded place it was last seen
     * in has vanished (the engine lost it; at home its faction sees it is gone and presumes it dead at once, as before);
     * one away past its expected return and the grace is missing; one missing for the wait is presumed dead.
     */
    public static void upkeep(ServerLevel level, CampaignData data, BaseState b, long t) {
        FactionState f = data.faction(b.faction);
        boolean far = farDeathsUnknown();
        Whereabouts.Waits w = waits();
        MinecraftServer server = level.getServer();
        for (NpcRecord r : List.copyOf(f.roster.values())) {
            if (r.baseId != b.id) {
                continue;
            }
            if (!r.gone() && level.getEntity(r.uuid) == null && level.isPositionEntityTicking(r.lastPos) && t - r.lastSeen > VANISH_TICKS) {
                r.vanished = true;
                data.setDirty();
                if (r.where.counted() && (!far || inHomeArea(data, b.faction, level, r.lastPos))) {
                    apply(server, data, b.faction, r, new Step(State.PRESUMED_DEAD, Event.PRESUMED_DEAD), t, null, null);
                }
            }
            if (far && (r.where == State.AWAY || r.where == State.MISSING)) {
                Step s = Whereabouts.waits(r.where, t, r.expectedBy, r.since, w);
                if (s.changed(r.where)) {
                    apply(server, data, b.faction, r, s, t, null, null);
                }
            } else if (!far) {
                // campaign.farDeathsUnknown turned off: the old rule knows every death at once, those not yet known too
                // (review of 2026-11-19: they stayed counted, holding their workplaces, for ever)
                Step s = Whereabouts.knownUnderOldRule(r.dead, r.where);
                if (s.changed(r.where)) {
                    apply(server, data, b.faction, r, s, t, null, null);
                }
            }
        }
    }

    // ------------------------------------------------------------------ deaths

    /** A member of the victim's faction who saw a death: who, and whether it stood at home. */
    public record Witness(UUID uuid, String name, boolean home, boolean player) {
    }

    /**
     * A member died ({@code FactionNpc.die}): the truth is written, its own news dies with it (a dead witness carries
     * nothing), and its faction knows at once, gets the news through the members away who saw it, or does not know.
     * A hero's line at once is {@code HeroManager}'s ("has fallen ... and returns in ..."), which reads {@link #known}.
     */
    public static Knowing onDeath(FactionNpc victim) {
        if (!(victim.level() instanceof ServerLevel level) || victim.isDisplayOnly()) {
            return Knowing.UNSEEN;
        }
        MinecraftServer server = level.getServer();
        CampaignData data = CampaignData.get(server);
        FactionType faction = victim.getFaction();
        FactionState f = data.faction(faction);
        long now = level.getGameTime();
        f.news.remove(victim.getUUID()); // a dead witness carries nothing
        NpcRecord rec = f.roster.get(victim.getUUID());
        if (rec == null) {
            return Knowing.UNSEEN;
        }
        BlockPos pos = victim.blockPosition();
        rec.dead = true;
        rec.diedAt = now;
        rec.diedPos = pos;
        rec.lastPos = pos;
        rec.lastSeen = now;
        List<Witness> seen = witnesses(level, data, victim, faction);
        rec.witnessed = !seen.isEmpty();
        int atHome = 0, away = 0;
        for (Witness w : seen) {
            if (w.home() && !w.player()) {
                atHome++; // a player decides itself when to tell: it always carries (section 5)
            } else {
                away++;
            }
        }
        boolean inArea = inHomeArea(data, faction, level, pos);
        Knowing k = Whereabouts.atDeath(farDeathsUnknown(), inArea, atHome, away);
        if (k != Knowing.AT_ONCE && rec.where == State.HERE) {
            leaves(data, victim, rec, now); // seen at home a moment ago: it had just left
        }
        switch (k) {
            case AT_ONCE -> {
                Step s = Whereabouts.learnsDeath(rec.where, false);
                if (s.event() != Event.NONE) {
                    // (a hero's line is HeroManager's "has fallen ... and returns in ...", which reads known())
                    apply(server, data, faction, rec, rec.isHero() ? new Step(s.state(), Event.NONE) : s, now, null, null);
                }
            }
            case CARRIED -> {
                News n = new News(rec.uuid, News.Kind.DEATH, who(rec), rec.isHero(), pos.getX(), pos.getZ(), now);
                for (Witness w : seen) {
                    if (!w.home() || w.player()) {
                        carry(f, w.uuid(), n);
                    }
                }
            }
            case UNSEEN -> {
            }
        }
        data.setDirty();
        return k;
    }

    /**
     * The members of {@code faction} who see {@code victim} die: living NPCs and heroes (not display copies) and member
     * players (not spectators) within {@code campaign.witnessRange} with a clear line of sight, eye to eye.
     */
    public static List<Witness> witnesses(ServerLevel level, CampaignData data, LivingEntity victim, FactionType faction) {
        double r = FCConfig.WITNESS_RANGE.get();
        List<Witness> out = new ArrayList<>();
        AABB box = victim.getBoundingBox().inflate(r);
        for (FactionNpc n : level.getEntitiesOfClass(FactionNpc.class, box, n -> n != victim && n.isAlive() && !n.isDisplayOnly()
                && n.getFaction() == faction)) {
            if (n.distanceToSqr(victim) <= r * r && n.hasLineOfSight(victim)) {
                out.add(new Witness(n.getUUID(), n.npcName(), inHomeArea(data, faction, level, n.blockPosition()), false));
            }
        }
        for (ServerPlayer p : level.players()) {
            if (p != victim && data.players.get(p.getUUID()) == faction && p.isAlive() && !p.isSpectator()
                    && p.distanceToSqr(victim) <= r * r && p.hasLineOfSight(victim)) {
                out.add(new Witness(p.getUUID(), p.getName().getString(), inHomeArea(data, faction, level, p.blockPosition()), true));
            }
        }
        return out;
    }

    /** Whether the faction knows of this member's death. */
    public static boolean known(NpcRecord r) {
        return r != null && r.where == State.KNOWN_DEAD;
    }

    // ------------------------------------------------------------------ news

    /** {@code carrier} carries {@code n} (once). */
    static void carry(FactionState f, UUID carrier, News n) {
        List<News> l = f.news.computeIfAbsent(carrier, k -> new ArrayList<>());
        for (News o : l) {
            if (o.id.equals(n.id)) {
                return;
            }
        }
        l.add(n);
    }

    /** The others' copies of news already told. */
    private static void dropEverywhere(FactionState f, UUID id) {
        Iterator<Map.Entry<UUID, List<News>>> it = f.news.entrySet().iterator();
        while (it.hasNext()) {
            List<News> l = it.next().getValue();
            l.removeIf(n -> n.id.equals(id));
            if (l.isEmpty()) {
                it.remove();
            }
        }
    }

    /** A carrier at home tells everything it carries; the first carrier home tells each piece. Returns how much it told. */
    public static int tell(MinecraftServer server, CampaignData data, FactionType faction, UUID carrier, String carrierName) {
        FactionState f = data.faction(faction);
        List<News> items = f.news.remove(carrier);
        if (items == null || items.isEmpty()) {
            return 0;
        }
        long now = server.overworld().getGameTime();
        int told = 0;
        for (News n : items) {
            dropEverywhere(f, n.id);
            if (n.kind == News.Kind.DEATH) {
                NpcRecord r = f.roster.get(n.id);
                if (r == null) {
                    continue; // a hero risen since (a new entry), or a record no longer on the roster
                }
                Step s = Whereabouts.learnsDeath(r.where, true);
                if (s.event() == Event.NONE) {
                    continue; // known already
                }
                apply(server, data, faction, r, s, now, carrierName, n);
                told++;
            } else {
                // a line about the carrier itself is told as it is; another line as the carrier's news
                say(server, faction, n.kind == News.Kind.OWN ? carrier : null, Event.NONE,
                        n.kind == News.Kind.OWN ? n.text : Whereabouts.carried(carrierName, n.text), null);
                if (n.id.equals(f.raidEndNews)) {
                    f.raidEndNews = null; // the council hears that its raid is over (docs/design/missing_npcs.md 6)
                    FactionDirector.deliverRaidIntel(f, now); // and what the party saw of its target (knowledge review)
                }
                told++;
            }
        }
        data.setDirty();
        return told;
    }

    /**
     * A line only someone there could tell (a raid's end, an enemy hero's fall): said at once when one of the
     * {@code carriers} (members' UUIDs) is at home, else carried home by each of them. No carrier: never told. With
     * {@code campaign.farDeathsUnknown} off it is said at once, as before. Returns null when it was said at once, else the
     * id of the news (carried, or by nobody: then it is never told), which {@link #tell} matches when it reaches home.
     */
    public static UUID tellOrCarry(ServerLevel level, CampaignData data, FactionType faction, Collection<UUID> carriers, String line,
                                   ChatFormatting style) {
        MinecraftServer server = level.getServer();
        if (!farDeathsUnknown()) {
            say(server, faction, null, Event.NONE, line, style);
            return null;
        }
        FactionState f = data.faction(faction);
        List<UUID> away = new ArrayList<>();
        for (UUID u : carriers) {
            Entity e = level.getEntity(u);
            NpcRecord r = f.roster.get(u);
            if (e instanceof LivingEntity l ? !l.isAlive() : r == null || r.gone()) {
                continue;
            }
            BlockPos at = e != null ? e.blockPosition() : r.lastPos;
            if (inHomeArea(data, faction, level, at)) {
                say(server, faction, null, Event.NONE, line, style);
                return null;
            }
            away.add(u);
        }
        News n = new News(UUID.randomUUID(), News.Kind.LINE, line, false, 0, 0, level.getGameTime());
        for (UUID u : away) {
            carry(f, u, n);
        }
        data.setDirty();
        return n.id;
    }

    /**
     * Who can tell how a raid ended ({@code docs/design/missing_npcs.md} 6): the raiders still in the party and alive
     * there, or, when the party emptied because its last member left it alive (home with its loot, or falling back
     * wounded; {@link FactionState#raidLast}), that member, who saw it end (live check of 2026-11-19: a party that came
     * home one by one carried its end to nobody, and the council waited out the raid's whole bound). Nobody when the
     * party ended in deaths.
     */
    public static List<UUID> raidEndCarriers(FactionState f, List<UUID> survivors) {
        if (!survivors.isEmpty() || !f.raiders.isEmpty() || f.raidLast == null) {
            return survivors;
        }
        NpcRecord last = f.roster.get(f.raidLast);
        return last != null && !last.gone() ? List.of(f.raidLast) : survivors;
    }

    /**
     * A line about a member itself (a level it gained): said at once when it is inside a home area of its faction (or
     * with {@code campaign.farDeathsUnknown} off), else it tells it when it comes home (review of 2026-11-19: a member
     * away, perhaps presumed dead, was heard levelling up at once).
     */
    public static void ownLine(FactionNpc m, String line) {
        if (!(m.level() instanceof ServerLevel level) || m.isDisplayOnly()) {
            return;
        }
        CampaignData data = CampaignData.get(level.getServer());
        if (!farDeathsUnknown() || inHomeArea(data, m.getFaction(), level, m.blockPosition())) {
            say(level.getServer(), m.getFaction(), m.getUUID(), Event.NONE, line, null);
            return;
        }
        BlockPos p = m.blockPosition();
        carry(data.faction(m.getFaction()), m.getUUID(), new News(UUID.randomUUID(), News.Kind.OWN, line, false, p.getX(), p.getZ(),
                level.getGameTime()));
        data.setDirty();
    }

    /** A member player using a station of its own faction's base reports what it saw. Returns how much it told. */
    public static int playerReport(ServerPlayer p, BaseState base) {
        CampaignData data = CampaignData.get(p.server);
        if (data.players.get(p.getUUID()) != base.faction) {
            return 0;
        }
        return tell(p.server, data, base.faction, p.getUUID(), p.getName().getString());
    }

    /** A carrier died (a player; an NPC's own death does this itself): what it carried dies with it. */
    public static void forget(MinecraftServer server, UUID carrier) {
        CampaignData data = CampaignData.get(server);
        for (FactionState f : data.factions.values()) {
            if (f.news.remove(carrier) != null) {
                data.setDirty();
            }
        }
    }

    // ------------------------------------------------------------------ heroes

    /**
     * A fallen hero rises (its revival): the old entry's news is moot now. Returns the old entry (its faction's view of the
     * hero before it rises), or null.
     */
    public static NpcRecord heroRises(CampaignData data, FactionType owner, UUID oldUuid) {
        if (owner == null || oldUuid == null) {
            return null;
        }
        FactionState f = data.faction(owner);
        NpcRecord old = f.roster.get(oldUuid);
        dropEverywhere(f, oldUuid);
        return old;
    }

    /** Whether the faction did not know of the fall its hero rises from (the old entry {@link #heroRises} returned). */
    public static boolean fallUnknown(NpcRecord old) {
        return old != null && old.where != State.KNOWN_DEAD && farDeathsUnknown();
    }

    /**
     * A hero whose fall nobody of its faction saw rises where none of them is (a {@code where_it_fell} revival outside
     * every home area; review of 2026-11-19): its faction learns nothing now. Its new entry keeps the faction's view of the
     * old one (away, missing or presumed dead, with the same expected return), and it carries its own fall home, told
     * when it comes back (the hero knows where and when it fell).
     */
    public static void heroRisesAway(CampaignData data, NpcRecord old, FactionNpc risen) {
        FactionState f = data.faction(risen.getFaction());
        NpcRecord r = f.roster.get(risen.getUUID());
        if (r == null || old == null) {
            return;
        }
        r.where = old.where;
        r.alive = old.where.counted();
        r.leftAt = old.leftAt;
        r.expectedBy = old.expectedBy;
        r.since = old.since;
        r.seenHomeAt = old.seenHomeAt;
        BlockPos fell = old.diedPos != null ? old.diedPos : risen.blockPosition();
        carry(f, risen.getUUID(), new News(UUID.randomUUID(), News.Kind.OWN,
                Whereabouts.ownFall(risen.npcName(), fell.getX(), fell.getZ(), old.diedAt), true, fell.getX(), fell.getZ(), old.diedAt));
        data.setDirty();
    }

    // ------------------------------------------------------------------ applying and telling

    /** "Name (Role)", a hero its name. */
    public static String who(NpcRecord r) {
        return r.isHero() ? r.name : r.name + " (" + r.role.displayName + ")";
    }

    /** Applies a step to a record and says its line. */
    static void apply(MinecraftServer server, CampaignData data, FactionType faction, NpcRecord r, Step s, long now, String carrier,
                      News n) {
        // "missing since": when it went missing (a member lost at home is presumed dead the moment it is missed)
        long missingSince = r.where == State.MISSING ? r.since : now;
        boolean learnsDeath = s.state() == State.KNOWN_DEAD && r.where != State.KNOWN_DEAD;
        r.where(s.state(), now);
        data.setDirty();
        if (learnsDeath) {
            // "Adventure" (killed by something) when the faction learns of a member's death, not at the death itself
            // (review of 2026-11-19: the death event made it, naming the dead, at once wherever it fell)
            com.factionclash.achieve.FactionAchievements.criterion(server, faction, "adventure/root", "killed_by_something", r.name);
        }
        String line = Whereabouts.line(s.event(), who(r), carrier, n == null ? 0 : n.x, n == null ? 0 : n.z, n == null ? now : n.at,
                r.expectedBy, missingSince, r.isHero());
        if (line == null) {
            return;
        }
        if (r.isHero() && n != null) {
            line += heroReturn(data, r.uuid, now);
        }
        ChatFormatting style = switch (s.event()) {
            case KILLED, KILLED_REPORTED, KILLED_CONFIRMED, PRESUMED_DEAD -> ChatFormatting.RED;
            case MISSING -> ChatFormatting.YELLOW;
            case BACK, REJOINED -> ChatFormatting.GREEN;
            default -> null;
        };
        say(server, faction, r.uuid, s.event(), line, style);
    }

    /** " and returns in M:SS" for a fallen hero whose return is still to come (heroes return after a fixed time). */
    private static String heroReturn(CampaignData data, UUID uuid, long now) {
        for (HeroState s : data.heroes.values()) {
            if (uuid.equals(s.uuid) && !s.alive && s.reviveAt > now) {
                return " and returns in " + com.factionclash.logic.hero.HeroRevival.countdown(s.reviveAt - now);
            }
        }
        return "";
    }

    private static void say(MinecraftServer server, FactionType faction, UUID who, Event e, String line, ChatFormatting style) {
        Component c = style == null ? Component.literal(line) : Component.literal(line).withStyle(style);
        Messages.toFaction(server, faction, c);
        note(faction, who, e, line);
    }

    // ------------------------------------------------------------------ GameTests

    /** What the roll call told a faction (GameTests). */
    public record Told(FactionType faction, UUID who, Event event, String line) {
    }

    private static List<Told> recorder;

    public static synchronized List<Told> record() {
        recorder = Collections.synchronizedList(new ArrayList<>());
        return recorder;
    }

    public static synchronized void stopRecording() {
        recorder = null;
    }

    /** A line about a member that another class says itself (a hero's fall and return, {@code HeroManager}): for the recorder. */
    public static void heard(FactionType faction, UUID who, Event e, String line) {
        note(faction, who, e, line);
    }

    private static synchronized void note(FactionType faction, UUID who, Event e, String line) {
        if (recorder != null) {
            recorder.add(new Told(faction, who, e, line));
        }
    }
}
