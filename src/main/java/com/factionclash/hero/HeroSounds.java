package com.factionclash.hero;

import com.factionclash.FactionClash;
import com.factionclash.config.FCConfig;
import com.factionclash.logic.hero.BossRules;
import com.factionclash.logic.hero.HeroDef;
import com.factionclash.logic.hero.JsonTree;
import com.factionclash.logic.hero.SoundIndex;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Hero sounds on the server (SOUND.md sections 7 and 15, task S2).
 * <ul>
 * <li><b>Registration</b>: every event of the index ({@code assets/factionclash/sounds/hero_sfx.json}, read from the mod
 * file in the mod constructor) is registered with {@link SoundEvent#createFixedRangeEvent} at the index's range, the
 * range the game really gives it; {@code sounds.json} needs nothing more.</li>
 * <li><b>Playing</b> ({@link #play}): pitch and volume picked from the event's ranges, the volume times
 * {@code heroes.soundVolume}, the event's category as the sound source, positional for everyone in range; each
 * companion after its delay at the same place (a small queue ticked by the server tick), at most two deep. A
 * <b>notification</b> ({@link #notifyPlayers}) is heard in the head by each player who sees the hero's boss bar.</li>
 * <li><b>Hooks</b> by the slot rule of section 7: the move runner's wind-up (the move's {@code windup} at once and its
 * class cue timed by {@code hit_at}), the release ({@code plays: server} sounds), impacts, a broken channel, states,
 * passives, weak points, the boss beats (parry, counter, poise, stagger, phase, enrage, intro, the end of a fight,
 * the last stand, ultimate ready, wall, decoy, barrier), the revival, steps, hurt and death.</li>
 * </ul>
 * <b>Who causes a cue does not matter</b> (HEROES_V2 6.15): nothing here reads whether an enemy is a player. Only the
 * listeners are players (NPCs hear nothing), so a fight with no player near plays to nobody.
 *
 * <p>GameTests may give a fixture hero a sound map of its own ({@link #registerTransient}) and record what plays
 * ({@link #record}). Common code with no client class.
 */
public final class HeroSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, FactionClash.MODID);
    private static final String INDEX_PATH = "assets/" + FactionClash.MODID + "/sounds/hero_sfx.json";

    private static volatile SoundIndex index;
    /** Sound maps GameTests register for their fixtures (they win over the index). */
    private static final Map<String, SoundIndex.HeroMap> TRANSIENT = new ConcurrentHashMap<>();
    /** Ids looked up already (a vanilla id or one of ours), so a play parses nothing. */
    private static final Map<String, Holder<SoundEvent>> HOLDERS = new ConcurrentHashMap<>();
    private static final Holder<SoundEvent> MISSING = Holder.direct(SoundEvent.createVariableRangeEvent(
            ResourceLocation.fromNamespaceAndPath(FactionClash.MODID, "missing")));

    private HeroSounds() {
    }

    // ------------------------------------------------------------------ the index and registration

    /** Registers every event of the index (the mod constructor). */
    public static void register(IEventBus modBus) {
        SoundIndex idx = index();
        int n = 0;
        for (SoundIndex.Event e : idx.events().values()) {
            ResourceLocation rl = ResourceLocation.tryParse(e.id());
            if (rl == null || !rl.getNamespace().equals(FactionClash.MODID)) {
                continue;
            }
            float range = (float) e.range();
            SOUNDS.register(rl.getPath(), () -> SoundEvent.createFixedRangeEvent(rl, range));
            n++;
        }
        SOUNDS.register(modBus);
        FactionClash.LOGGER.info("Hero sounds: {} events registered from the index", n);
    }

    /** The mod's index (an empty one when the file cannot be read: no hero sound plays then). */
    public static SoundIndex index() {
        SoundIndex i = index;
        if (i == null) {
            i = read();
            index = i;
        }
        return i;
    }

    private static SoundIndex read() {
        try {
            String text = null;
            Path p = HeroRegistry.modPath(INDEX_PATH.split("/"));
            if (p != null && Files.isRegularFile(p)) {
                text = Files.readString(p, StandardCharsets.UTF_8);
            } else {
                try (InputStream in = HeroSounds.class.getResourceAsStream("/" + INDEX_PATH)) {
                    if (in != null) {
                        text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                    }
                }
            }
            if (text == null) {
                FactionClash.LOGGER.warn("Hero sounds: no index at {}; heroes play no sounds of their own", INDEX_PATH);
                return SoundIndex.EMPTY;
            }
            return SoundIndex.parse(text);
        } catch (IOException | RuntimeException e) {
            FactionClash.LOGGER.warn("Hero sounds: the index could not be read: {}", e.toString());
            return SoundIndex.EMPTY;
        }
    }

    /** A hero's sound map (a test's own first), or null: a hero with no sounds of its own (the test rig). */
    public static SoundIndex.HeroMap map(String heroId) {
        if (heroId == null || heroId.isEmpty()) {
            return null;
        }
        SoundIndex.HeroMap t = TRANSIENT.isEmpty() ? null : TRANSIENT.get(heroId);
        return t != null ? t : index().hero(heroId);
    }

    /**
     * A GameTest fixture's sound map ({@code json}: a hero map as the index writes it, full ids) until the handle is
     * closed. Never used for the mod's own heroes.
     */
    public static AutoCloseable registerTransient(String heroId, String json) {
        SoundIndex.HeroMap m = SoundIndex.hero(heroId, JsonTree.object(JsonTree.parse(json)));
        TRANSIENT.put(heroId, m);
        return () -> TRANSIENT.remove(heroId, m);
    }

    /** The game's sound event for an id (one of ours or a vanilla one), or null when the game has none. */
    public static Holder<SoundEvent> holder(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        Holder<SoundEvent> h = HOLDERS.get(id);
        if (h == null) {
            ResourceLocation rl = ResourceLocation.tryParse(id);
            Holder<SoundEvent> found = rl == null ? null
                    : BuiltInRegistries.SOUND_EVENT.getHolder(ResourceKey.create(Registries.SOUND_EVENT, rl)).orElse(null);
            h = found == null ? MISSING : found;
            HOLDERS.put(id, h);
        }
        return h == MISSING ? null : h;
    }

    /** The vanilla category of an event as a sound source (hostile when the index does not know it). */
    public static SoundSource source(SoundIndex.Event e) {
        if (e == null) {
            return SoundSource.HOSTILE;
        }
        return switch (e.category().toLowerCase(Locale.ROOT)) {
            case "neutral" -> SoundSource.NEUTRAL;
            case "voice" -> SoundSource.VOICE;
            case "players" -> SoundSource.PLAYERS;
            case "ambient" -> SoundSource.AMBIENT;
            case "blocks" -> SoundSource.BLOCKS;
            case "weather" -> SoundSource.WEATHER;
            case "record" -> SoundSource.RECORDS;
            case "music" -> SoundSource.MUSIC;
            case "master" -> SoundSource.MASTER;
            default -> SoundSource.HOSTILE;
        };
    }

    // ------------------------------------------------------------------ playing

    /** What a GameTest records of one play: the event, the game time, where, and who heard it as a notification. */
    public record Played(String event, long time, double x, double y, double z, String notified) {
        public double distanceTo(Entity e) {
            double dx = e.getX() - x, dy = e.getY() - y, dz = e.getZ() - z;
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
    }

    /** A recorder of plays (GameTests); close it to stop. */
    public static final class Recorder implements AutoCloseable {
        private final List<Played> plays = Collections.synchronizedList(new ArrayList<>());

        public List<Played> plays() {
            synchronized (plays) {
                return List.copyOf(plays);
            }
        }

        /** The plays of {@code event} (a full id or a cue key, {@code cue:parry}). */
        public List<Played> of(String event) {
            String id = event.startsWith("cue:") ? index().cue(event.substring(4)) : event;
            List<Played> out = new ArrayList<>();
            for (Played p : plays()) {
                if (p.event().equals(id)) {
                    out.add(p);
                }
            }
            return out;
        }

        @Override
        public void close() {
            RECORDERS.remove(this);
        }
    }

    private static final List<Recorder> RECORDERS = new CopyOnWriteArrayList<>();

    public static Recorder record() {
        Recorder r = new Recorder();
        RECORDERS.add(r);
        return r;
    }

    private static void recorded(String id, Level level, double x, double y, double z, ServerPlayer to) {
        if (!RECORDERS.isEmpty()) {
            Played p = new Played(id, level.getGameTime(), x, y, z, to == null ? null : to.getGameProfile().getName());
            for (Recorder r : RECORDERS) {
                r.plays.add(p);
            }
        }
    }

    /** Plays event {@code id} at (x, y, z) for everyone in range, with its companions. */
    public static void play(Level level, double x, double y, double z, String id) {
        if (level instanceof ServerLevel sl) {
            play(sl, x, y, z, id, 0, List.of());
        }
    }

    /** Plays event {@code id} at an entity. */
    public static void playAt(Entity e, String id) {
        if (e != null) {
            play(e.level(), e.getX(), e.getY(), e.getZ(), id);
        }
    }

    private static void play(ServerLevel sl, double x, double y, double z, String id, int depth, Collection<ServerPlayer> except) {
        Holder<SoundEvent> h = holder(id);
        if (h == null) {
            return;
        }
        SoundIndex.Event e = index().event(id);
        RandomSource r = sl.random;
        float vol = (float) ((e == null ? 1.0 : e.volume(r.nextDouble())) * FCConfig.HERO_SOUND_VOLUME.get());
        float pitch = (float) (e == null ? 1.0 : e.pitch(r.nextDouble()));
        if (!(vol > 0)) {
            return;
        }
        SoundSource src = source(e);
        long seed = r.nextLong();
        recorded(id, sl, x, y, z, null);
        if (except.isEmpty()) {
            sl.playSeededSound(null, x, y, z, h, src, vol, pitch, seed);
        } else {
            // a beat its bar's viewers already hear in the head: the others in range hear it at the hero
            double range = h.value().getRange(vol);
            ClientboundSoundPacket packet = new ClientboundSoundPacket(h, src, x, y, z, vol, pitch, seed);
            for (ServerPlayer p : sl.players()) {
                if (!except.contains(p) && p.distanceToSqr(x, y, z) < range * range) {
                    p.connection.send(packet);
                }
            }
        }
        companions(sl, e, depth, x, y, z, null, except);
    }

    /** {@code id} as a notification to each player of {@code to} (heard in the head, not positioned), with its companions. */
    public static void notifyPlayers(Collection<ServerPlayer> to, String id) {
        notifyPlayers(to, id, 0);
    }

    private static void notifyPlayers(Collection<ServerPlayer> to, String id, int depth) {
        Holder<SoundEvent> h = holder(id);
        if (h == null || to.isEmpty()) {
            return;
        }
        SoundIndex.Event e = index().event(id);
        SoundSource src = source(e);
        ServerLevel first = null;
        for (ServerPlayer p : to) {
            RandomSource r = p.getRandom();
            float vol = (float) ((e == null ? 1.0 : e.volume(r.nextDouble())) * FCConfig.HERO_SOUND_VOLUME.get());
            float pitch = (float) (e == null ? 1.0 : e.pitch(r.nextDouble()));
            if (!(vol > 0)) {
                continue;
            }
            recorded(id, p.level(), p.getX(), p.getY(), p.getZ(), p);
            p.playNotifySound(h.value(), src, vol, pitch);
            first = p.serverLevel();
        }
        if (first != null) {
            companions(first, e, depth, 0, 0, 0, List.copyOf(to), List.of());
        }
    }

    private static void companions(ServerLevel sl, SoundIndex.Event e, int depth, double x, double y, double z,
                                   List<ServerPlayer> notifyTo, Collection<ServerPlayer> except) {
        if (e == null || e.with().isEmpty() || depth >= SoundIndex.MAX_COMPANION_DEPTH) {
            return;
        }
        for (SoundIndex.Companion c : e.with()) {
            Pending p = new Pending(sl, x, y, z, c.sound(), depth + 1, notifyTo, except);
            if (c.delay() <= 0) {
                p.run();
            } else {
                later(sl.getServer(), c.delay(), p);
            }
        }
    }

    // ------------------------------------------------------------------ the delayed queue (companions, cue chains)

    private record Pending(ServerLevel level, double x, double y, double z, String id, int depth, List<ServerPlayer> notifyTo,
                           Collection<ServerPlayer> except) implements Runnable {
        @Override
        public void run() {
            if (notifyTo != null) {
                List<ServerPlayer> still = new ArrayList<>(notifyTo.size());
                for (ServerPlayer p : notifyTo) {
                    if (!p.isRemoved() && p.serverLevel() == level) {
                        still.add(p);
                    }
                }
                notifyPlayers(still, id, depth);
            } else {
                play(level, x, y, z, id, depth, except);
            }
        }
    }

    private record Due(long at, Runnable what) {
    }

    private static final List<Due> QUEUE = new ArrayList<>();

    private static void later(MinecraftServer server, int ticks, Runnable what) {
        if (server == null) {
            return;
        }
        synchronized (QUEUE) {
            QUEUE.add(new Due(server.getTickCount() + Math.max(1, ticks), what));
        }
    }

    /** Every server tick (from {@code HeroEvents}): the companions that are due. */
    public static void tick(MinecraftServer server) {
        if (QUEUE.isEmpty()) {
            return;
        }
        List<Runnable> due = null;
        synchronized (QUEUE) {
            long now = server.getTickCount();
            for (Iterator<Due> it = QUEUE.iterator(); it.hasNext(); ) {
                Due d = it.next();
                if (d.at() <= now) {
                    if (due == null) {
                        due = new ArrayList<>(2);
                    }
                    due.add(d.what());
                    it.remove();
                }
            }
        }
        if (due != null) {
            for (Runnable r : due) {
                r.run();
            }
        }
    }

    /** The server stops: nothing left to play. */
    public static void serverStopped() {
        synchronized (QUEUE) {
            QUEUE.clear();
        }
    }

    // ------------------------------------------------------------------ the hero's moments (section 7)

    /** A shared cue at the hero (positional). */
    public static void cue(Entity at, String key) {
        String id = index().cue(key);
        if (id != null) {
            playAt(at, id);
        }
    }

    /** A shared cue at where {@code at} is now, {@code ticks} later (the stagger's stars after the bar's break). */
    static void cueAfter(Entity at, String key, int ticks) {
        String id = index().cue(key);
        if (id != null && at.level() instanceof ServerLevel sl) {
            later(sl.getServer(), ticks, new Pending(sl, at.getX(), at.getY(), at.getZ(), id, 0, null, List.of()));
        }
    }

    /** A sound of the hero's map at the hero, when it names one. */
    private static void own(HeroEntity h, String id) {
        if (id != null) {
            playAt(h, id);
        }
    }

    private static SoundIndex.MoveSounds moveSounds(HeroEntity h, HeroDef.Move m) {
        SoundIndex.HeroMap map = map(h.heroId());
        return map == null || m == null ? null : map.moves().get(m.id());
    }

    /** A move's wind-up starts: its own {@code windup} sound at once (the class cue is {@link #telegraphCue}'s). */
    static void windup(HeroEntity h, HeroDef.Move m) {
        SoundIndex.MoveSounds s = moveSounds(h, m);
        if (s != null) {
            own(h, s.windup());
        }
    }

    /**
     * The class cue a move's wind-up plays ({@code telegraph.<class>}, a grab's {@code .unblockable}, a dive's
     * {@code .dive}), or null: none, or {@code heroes.telegraphCues} off. Every hero has it, with a map or not.
     */
    static String telegraphCue(HeroDef.Move m) {
        if (m == null || !FCConfig.HERO_TELEGRAPH_CUES.get()) {
            return null;
        }
        String key = SoundIndex.telegraphCue(m.cls() == null ? null : m.cls().id(), m.form() == HeroDef.Form.GRAB,
                m.form() == HeroDef.Form.DIVE);
        return key == null ? null : index().cue(key);
    }

    /** Ticks after the wind-up's start its class cue {@code cueId} starts, so the cue's {@code hit_at} lands on the release. */
    static int cueDelay(String cueId, int windupTicks) {
        SoundIndex.Event e = index().event(cueId);
        return SoundIndex.cueDelay(windupTicks, e == null ? Double.NaN : e.hitAt());
    }

    /** The release tick (the wind-up's end): the move's {@code release} when its event is {@code plays: server}. */
    static void release(HeroEntity h, HeroDef.Move m) {
        SoundIndex.MoveSounds s = moveSounds(h, m);
        if (s != null && s.release() != null) {
            SoundIndex.Event e = index().event(s.release());
            if (e != null && e.plays() == SoundIndex.Plays.SERVER) {
                playAt(h, s.release());
            }
        }
    }

    /** Damage or a zone's pulse of the use landed (on an enemy) at {@code at}: the move's {@code impact}, cycling per shot. */
    static void impact(HeroUse use, Entity at) {
        if (use.move == null) {
            return;
        }
        SoundIndex.MoveSounds s = moveSounds(use.hero, use.move);
        String id = s == null ? null : s.impact(use.impacts++);
        if (id != null) {
            playAt(at, id);
        }
    }

    /** The hero's basic attack landed on {@code target} (its {@code n}th): {@code moves.basic.impact}, cycling. */
    static void basicImpact(HeroEntity h, Entity target, int n) {
        SoundIndex.HeroMap map = map(h.heroId());
        SoundIndex.MoveSounds s = map == null ? null : map.moves().get("basic");
        String id = s == null ? null : s.impact(n);
        if (id != null) {
            playAt(target, id);
        }
    }

    /** A channel was broken: {@code cues.channel_break} and the move's {@code broken}. */
    static void channelBroken(HeroEntity h, HeroDef.Move m) {
        cue(h, SoundIndex.CHANNEL_BREAK);
        SoundIndex.MoveSounds s = moveSounds(h, m);
        if (s != null) {
            own(h, s.broken());
        }
    }

    /** The hero entered state {@code name}: {@code states.<name>}. */
    static void stateEntered(HeroEntity h, String name) {
        SoundIndex.HeroMap map = map(h.heroId());
        if (map != null && name != null) {
            own(h, map.states().get(name));
        }
    }

    /** A passive named {@code name} fired: {@code passives.<key>}. */
    static void passiveFired(HeroEntity h, String name) {
        SoundIndex.HeroMap map = map(h.heroId());
        if (map != null) {
            String key = SoundIndex.passiveKey(map.passives().keySet(), name);
            if (key != null) {
                own(h, map.passives().get(key));
            }
        }
    }

    /**
     * A weak point triggered (6.9): {@code cues.weak_point} and the hero's {@code weak_points.<kind>}. Returns whether
     * the hero's map has a sound of its own for it (else the caller may keep its vanilla tell).
     */
    static boolean weakPoint(HeroEntity h, HeroDef.WeakPoint kind) {
        cue(h, SoundIndex.WEAK_POINT);
        SoundIndex.HeroMap map = map(h.heroId());
        String id = map == null ? null : map.weakPoints().get(kind.id());
        own(h, id);
        return id != null;
    }

    /** A shield parried one of the hero's moves (6.9): {@code cues.parry} at the one who parried, a player or an NPC. */
    static void parry(Entity blocker) {
        cue(blocker, SoundIndex.PARRY);
    }

    /**
     * The players a notification of the hero reaches (section 7): those who see its boss bar; with the boss bars off,
     * the players within the bar's 32 blocks (alive, not spectators).
     */
    static List<ServerPlayer> audience(HeroEntity h) {
        if (FCConfig.BOSS_BARS.get()) {
            return HeroBossBars.viewers(h);
        }
        if (!(h.level() instanceof ServerLevel sl)) {
            return List.of();
        }
        List<ServerPlayer> out = new ArrayList<>(2);
        double r = BossRules.BAR_RADIUS;
        for (ServerPlayer p : sl.players()) {
            if (!p.isSpectator() && p.isAlive() && p.distanceToSqr(h) <= r * r) {
                out.add(p);
            }
        }
        return out;
    }

    /** A beat about the fight: a notification to {@code viewers} and, at the hero, for everyone else in range. */
    private static void beat(HeroEntity h, String key, List<ServerPlayer> viewers) {
        String id = index().cue(key);
        if (id == null || !(h.level() instanceof ServerLevel sl)) {
            return;
        }
        notifyPlayers(viewers, id);
        play(sl, h.getX(), h.getY(), h.getZ(), id, 0, viewers);
    }

    /** A phase transition begins: {@code cues.phase} as a notification, the shockwave and the super-armour glint at the hero. */
    static void phase(HeroEntity h) {
        String id = index().cue(SoundIndex.PHASE);
        if (id != null) {
            notifyPlayers(audience(h), id);
        }
        cue(h, SoundIndex.SHOCKWAVE);
        cue(h, SoundIndex.SUPER_ARMOR);
    }

    /** The hero is enraged: {@code cues.enrage}, a notification and at the hero. */
    static void enraged(HeroEntity h) {
        beat(h, SoundIndex.ENRAGE, audience(h));
    }

    /** The hero's intro title reaches a player (once that in-game day): {@code cues.intro} as a notification. */
    static void introTitle(ServerPlayer p) {
        String id = index().cue(SoundIndex.INTRO);
        if (id != null) {
            notifyPlayers(List.of(p), id);
        }
    }

    /**
     * The end of a fight it did not win (6.13): {@code cues.fled} or {@code cues.fallen} as a notification to the
     * players who saw its bar, and at the hero for everyone else in range.
     */
    static void endOfFight(HeroEntity h, boolean fled, List<ServerPlayer> viewers) {
        beat(h, fled ? SoundIndex.FLED : SoundIndex.FALLEN, viewers);
    }

    /** The hero's last stand (once a fight): {@code cues.last_stand} at the hero, under its line. */
    static void lastStand(HeroEntity h) {
        cue(h, SoundIndex.LAST_STAND);
    }

    // ------------------------------------------------------------------ the entity's own sounds (section 7's last rows)

    /** The vanilla step volume times {@code steps.volume} of the hero's map (1 without one) times {@code heroes.soundVolume}. */
    static float stepVolume(HeroEntity h) {
        SoundIndex.HeroMap map = map(h.heroId());
        return (float) ((map == null ? 1.0 : map.stepsVolume()) * FCConfig.HERO_SOUND_VOLUME.get());
    }

    /**
     * A step: {@code steps.with} (the heavy step thud of heavy heroes) at the hero; while it is invisible, its
     * {@code quirks.invisible_step} (Tobimaru's smoke puffs: SOUND.md 1.5, an invisible hero stays audible).
     */
    static void stepWith(HeroEntity h) {
        SoundIndex.HeroMap map = map(h.heroId());
        if (map == null) {
            return;
        }
        if (map.stepsWith() != null) {
            playAt(h, map.stepsWith());
        }
        if (h.isInvisible()) {
            String puff = map.quirks().get(INVISIBLE_STEP);
            if (puff != null) {
                playAt(h, puff);
            }
        }
    }

    /** The quirk an invisible hero's steps play. */
    static final String INVISIBLE_STEP = "invisible_step";

    /** The hero's hurt or death sound and pitch from {@code voice.hurt} / {@code voice.death} (null: none in its map). */
    static SoundIndex.Pitched voiceSound(HeroEntity h, boolean death) {
        SoundIndex.HeroMap map = map(h.heroId());
        if (map == null || map.voice() == null) {
            return null;
        }
        return death ? map.voice().death() : map.voice().hurt();
    }
}
