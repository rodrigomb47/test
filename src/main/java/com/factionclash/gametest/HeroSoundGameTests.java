package com.factionclash.gametest;

import com.factionclash.FactionClash;
import com.factionclash.config.FCConfig;
import com.factionclash.faction.FactionType;
import com.factionclash.hero.HeroBossBars;
import com.factionclash.hero.HeroEntity;
import com.factionclash.hero.HeroSounds;
import com.factionclash.logic.hero.SoundIndex;
import com.factionclash.npc.FactionNpc;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import static com.factionclash.gametest.HeroAbilityGameTests.force;
import static com.factionclash.gametest.HeroAbilityGameTests.move;
import static com.factionclash.gametest.HeroBossGameTests.blow;
import static com.factionclash.gametest.HeroBossGameTests.bossDef;
import static com.factionclash.gametest.HeroBossGameTests.bossHero;
import static com.factionclash.gametest.HeroBossGameTests.shieldPlayer;
import static com.factionclash.gametest.HeroBossGameTests.soldier;
import static com.factionclash.gametest.HeroBossGameTests.swordPlayer;

/**
 * Heroes, task S2: the sound engine (SOUND.md sections 7 and 15). A recorder ({@link HeroSounds#record}) logs what the
 * server plays (the event, the game time, where, and to whom as a notification). A fixture hero gets a sound map of its
 * own ({@link HeroSounds#registerTransient}) naming real events of the slice. <b>Equal treatment</b> (HEROES_V2 6.15):
 * every cue plays whoever causes it; the parry, the stagger and the end of a fight are each checked with an NPC soldier
 * and a mock player. Each test that runs a hero has a batch of its own.
 */
@GameTestHolder(FactionClash.MODID)
@PrefixGameTestTemplate(false)
public final class HeroSoundGameTests {
    private HeroSoundGameTests() {
    }

    private static final String NATSUHO = "factionclash:hero.natsuho_komezaki.";

    private static HeroSounds.Recorder recorder(GameTestBases.Handle t) {
        HeroSounds.Recorder r = HeroSounds.record();
        t.onCleanup(r::close);
        return r;
    }

    private static void map(GameTestBases.Handle t, String heroId, String json) {
        AutoCloseable c = HeroSounds.registerTransient(heroId, json);
        t.onCleanup(() -> {
            try {
                c.close();
            } catch (Exception ignored) {
            }
        });
    }

    /** The plays of {@code event} within 1.5 blocks of {@code at}. */
    private static List<HeroSounds.Played> at(HeroSounds.Recorder r, String event, Entity at) {
        List<HeroSounds.Played> out = new ArrayList<>();
        for (HeroSounds.Played p : r.of(event)) {
            if (p.notified() == null && p.distanceTo(at) < 1.5) {
                out.add(p);
            }
        }
        return out;
    }

    private static String cue(String key) {
        return HeroSounds.index().cue(key);
    }

    // ------------------------------------------------------------------ the wind-up's sounds (SOUND.md 7, 15.7)

    /**
     * {@code heavyWindupPlaysItsSoundThenTheClassCue}: a heavy move with a 1.3 s wind-up (26 ticks) plays its own
     * {@code windup} sound at the wind-up's tick 0 and {@code telegraph.heavy} at tick 10 (26 - round(0.78 x 20)), so the
     * cue's swell ends on the hit; its {@code plays: server} release sounds on the release tick and its impact at the
     * one it struck. The same against an NPC soldier and a mock player. A light move's cue (no {@code hit_at}) plays at
     * its start. With {@code heroes.telegraphCues} off the hero's own wind-up sound stays and the class cue goes.
     */
    @GameTest(template = "platform", timeoutTicks = 200, batch = "hero_sound_windup")
    public static void heavyWindupPlaysItsSoundThenTheClassCue(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, FactionType.EMBER_LEGION, 2, 11, 200);
        boolean cuesWere = FCConfig.HERO_TELEGRAPH_CUES.get();
        t.onCleanup(() -> FCConfig.HERO_TELEGRAPH_CUES.set(cuesWere));
        try {
            HeroSounds.Recorder rec = recorder(t);
            String pounce = move("pounce", "melee", "\"class\": \"heavy\", \"weight\": 0, \"cooldown\": 1,"
                    + " \"frames\": {\"windup\": 1.3, \"active\": 0.2, \"recovery\": 0.6}, \"effects\": [{\"type\": \"damage\", \"amount\": 1}]");
            String flick = move("flick", "melee", "\"class\": \"light\", \"weight\": 0, \"cooldown\": 1,"
                    + " \"frames\": {\"windup\": 0.4, \"active\": 0.1, \"recovery\": 0.4}, \"effects\": [{\"type\": \"damage\", \"amount\": 1}]");
            String sounds = "{\"moves\": {\"pounce\": {\"class\": \"heavy\", \"windup\": \"" + NATSUHO + "pounce_wiggle\","
                    + " \"release\": \"" + NATSUHO + "frenzy_sting\", \"impact\": \"" + NATSUHO + "pounce_land\"},"
                    + " \"flick\": {\"class\": \"light\", \"impact\": \"" + NATSUHO + "claw_hit\"}}}";
            String boss = ", \"poise\": 0, \"phases\": [{\"at\": 100}]";
            String a = "fx_sound_windup_a", b = "fx_sound_windup_b", c = "fx_sound_windup_c";
            for (String id : List.of(a, b, c)) {
                map(t, id, sounds);
            }
            HeroEntity vsNpc = bossHero(t, a, bossDef(a, "striker", pounce + ", " + flick, boss, ""), 4, 4);
            HeroEntity vsPlayer = bossHero(t, b, bossDef(b, "striker", pounce + ", " + flick, boss, ""), 4, 12);
            HeroEntity quiet = bossHero(t, c, bossDef(c, "striker", pounce + ", " + flick, boss, ""), 4, 20);
            for (HeroEntity x : List.of(vsNpc, vsPlayer, quiet)) {
                x.setNoAi(true);
                x.setOnGround(true);
            }
            FactionNpc npc = soldier(t, 5, 4), npc2 = soldier(t, 5, 20);
            ServerPlayer pl = swordPlayer(t, 5, 12);
            h.runAtTickTime(5, t.guard(() -> {
                force(t, vsNpc, npc, "pounce");
                force(t, vsPlayer, pl, "pounce");
            }));
            long[] flickAt = {-1};
            t.onEachTick(() -> {
                long now = h.getLevel().getGameTime();
                if (flickAt[0] < 0 && vsNpc.combat().lastRelease() > 0 && vsPlayer.combat().lastRelease() > 0
                        && now == Math.max(vsNpc.combat().lastRelease(), vsPlayer.combat().lastRelease()) + 30) {
                    for (HeroEntity x : List.of(vsNpc, vsPlayer)) {
                        Entity target = x == vsNpc ? npc : pl;
                        String who = x == vsNpc ? "against the soldier" : "against the mock player";
                        List<HeroSounds.Played> w = at(rec, NATSUHO + "pounce_wiggle", x);
                        List<HeroSounds.Played> q = at(rec, cue("telegraph.heavy"), x);
                        t.assertTrue(w.size() == 1, who + ": the wind-up sound played " + w.size() + " times");
                        t.assertTrue(q.size() == 1, who + ": the heavy cue played " + q.size() + " times");
                        long start = w.get(0).time();
                        t.assertTrue(q.get(0).time() - start == 10, who + ": the heavy cue at tick " + (q.get(0).time() - start)
                                + " of the wind-up, not 10");
                        t.assertTrue(x.combat().lastRelease() - start == 26, who + ": the hit at tick "
                                + (x.combat().lastRelease() - start) + ", not 26");
                        List<HeroSounds.Played> rel = at(rec, NATSUHO + "frenzy_sting", x);
                        t.assertTrue(rel.size() == 1 && rel.get(0).time() == x.combat().lastRelease(), who
                                + ": the server release " + rel);
                        List<HeroSounds.Played> imp = at(rec, NATSUHO + "pounce_land", target);
                        t.assertTrue(imp.size() == 1 && imp.get(0).time() == x.combat().lastRelease(), who + ": the impact at the one struck "
                                + imp);
                    }
                    // a light move: its cue (no hit_at) at the wind-up's start
                    force(t, vsNpc, npc, "flick");
                    // with the class cues off: the hero's own wind-up sound only
                    FCConfig.HERO_TELEGRAPH_CUES.set(false);
                    force(t, quiet, npc2, "pounce");
                    flickAt[0] = now;
                }
                if (flickAt[0] > 0 && quiet.combat().lastRelease() > flickAt[0] && now == quiet.combat().lastRelease() + 2) {
                    List<HeroSounds.Played> light = at(rec, cue("telegraph.light"), vsNpc);
                    t.assertTrue(light.size() == 1, "the light cue played " + light.size() + " times");
                    long lightRelease = vsNpc.combat().lastRelease();
                    t.assertTrue(lightRelease - light.get(0).time() == 8, "the light cue " + (lightRelease - light.get(0).time())
                            + " ticks before the hit, not at the 8-tick wind-up's start");
                    t.assertTrue(at(rec, NATSUHO + "claw_hit", npc).size() == 1, "the light move's impact");
                    t.assertTrue(at(rec, NATSUHO + "pounce_wiggle", quiet).size() == 1, "the wind-up sound with the cues off");
                    t.assertTrue(at(rec, cue("telegraph.heavy"), quiet).isEmpty(), "a class cue with telegraphCues off");
                    t.succeed();
                }
            });
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    // ------------------------------------------------------------------ registration (SOUND.md 15.1, 15.4)

    /**
     * {@code everyHeroMapSlotResolvesToARegisteredEvent}: every event of the index is registered with its fixed range,
     * and every string of every hero map and of the cue map is a sound event the game has (ours or vanilla).
     */
    @GameTest(template = "platform", batch = "workers_static")
    public static void everyHeroMapSlotResolvesToARegisteredEvent(GameTestHelper h) {
        SoundIndex index = HeroSounds.index();
        TreeSet<String> bad = new TreeSet<>();
        for (SoundIndex.Event e : index.events().values()) {
            SoundEvent s = BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse(e.id()));
            if (s == null) {
                bad.add("not registered: " + e.id());
            } else if (Math.abs(s.getRange(1F) - e.range()) > 1e-3 || Math.abs(s.getRange(4F) - e.range()) > 1e-3) {
                bad.add(e.id() + ": range " + s.getRange(1F) + " (fixed " + e.range() + " expected)");
            }
        }
        for (Map.Entry<String, String> c : index.cues().entrySet()) {
            if (HeroSounds.holder(c.getValue()) == null) {
                bad.add("cue " + c.getKey() + ": " + c.getValue());
            }
        }
        for (SoundIndex.HeroMap m : index.heroes().values()) {
            for (String id : m.ids()) {
                Holder<SoundEvent> hd = HeroSounds.holder(id);
                if (hd == null) {
                    bad.add(m.id() + ": " + id);
                }
            }
        }
        for (String key : SoundIndex.CUES) {
            if (index.cue(key) == null) {
                bad.add("the cue map has no " + key);
            }
        }
        if (index.events().size() < 240 || index.heroes().size() != 8) {
            bad.add("index: " + index.events().size() + " events, " + index.heroes().size() + " heroes");
        }
        if (!bad.isEmpty()) {
            h.fail("unresolved sounds: " + bad);
            return;
        }
        h.succeed();
    }

    // ------------------------------------------------------------------ equal treatment (SOUND.md 7, HEROES_V2 6.15)

    /**
     * {@code npcParryPlaysTheSameCueAsAPlayers}: a heavy move parried by an NPC soldier's shield and by a mock player's
     * shield plays {@code cues.parry} at each who parried, the same event on the same tick; a shield up too long blocks
     * with no parry and no parry cue.
     */
    @GameTest(template = "platform", timeoutTicks = 100, batch = "hero_sound_parry")
    public static void npcParryPlaysTheSameCueAsAPlayers(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, FactionType.EMBER_LEGION, 2, 11, 100);
        try {
            HeroSounds.Recorder rec = recorder(t);
            String cleave = move("cleave", "melee", "\"class\": \"heavy\", \"weight\": 0, \"cooldown\": 20,"
                    + " \"frames\": {\"windup\": 0.8, \"active\": 0.2, \"recovery\": 0.8}, \"effects\": [{\"type\": \"damage\", \"amount\": 3}]");
            String a = "fx_sound_parry_a", b = "fx_sound_parry_b", c = "fx_sound_parry_c";
            String boss = ", \"poise\": 100, \"phases\": [{\"at\": 100}]";
            HeroEntity vsNpc = bossHero(t, a, bossDef(a, "striker", cleave, boss, ""), 4, 4);
            HeroEntity vsPlayer = bossHero(t, b, bossDef(b, "striker", cleave, boss, ""), 4, 12);
            HeroEntity late = bossHero(t, c, bossDef(c, "striker", cleave, boss, ""), 4, 20);
            for (HeroEntity x : List.of(vsNpc, vsPlayer, late)) {
                x.setNoAi(true);
            }
            FactionNpc npc = soldier(t, 6, 4), npc2 = soldier(t, 6, 20);
            for (FactionNpc n : List.of(npc, npc2)) {
                n.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
                n.setYRot(90F);
                n.setYBodyRot(90F);
                n.setYHeadRot(90F);
            }
            ServerPlayer pl = shieldPlayer(t, 6, 12);
            FactionNpc archer = HeroMoveGameTests.archer(t, 16, 12);
            h.runAtTickTime(4, t.guard(() -> npc2.setTarget(archer)));
            h.runAtTickTime(7, t.guard(() -> {
                force(t, vsNpc, npc, "cleave");
                force(t, vsPlayer, pl, "cleave");
                force(t, late, npc2, "cleave");
            }));
            h.runAtTickTime(17, t.guard(() -> {
                npc.setTarget(archer);
                pl.startUsingItem(InteractionHand.OFF_HAND);
            }));
            t.onEachTick(() -> {
                if (vsNpc.combat().lastRelease() > 0 && vsPlayer.combat().lastRelease() > 0 && late.combat().lastRelease() > 0
                        && h.getLevel().getGameTime() == vsNpc.combat().lastRelease() + 2) {
                    t.assertTrue(npc.isBlocking() && pl.isBlocking() && npc2.isBlocking(), "a shield was not up");
                    List<HeroSounds.Played> bySoldier = at(rec, cue(SoundIndex.PARRY), npc);
                    List<HeroSounds.Played> byPlayer = at(rec, cue(SoundIndex.PARRY), pl);
                    t.assertTrue(bySoldier.size() == 1, "the soldier's parry played its cue " + bySoldier.size() + " times");
                    t.assertTrue(byPlayer.size() == 1, "the mock player's parry played its cue " + byPlayer.size() + " times");
                    t.assertTrue(bySoldier.get(0).event().equals(byPlayer.get(0).event())
                            && bySoldier.get(0).time() == byPlayer.get(0).time(), "the same cue on the same tick: " + bySoldier
                            + " " + byPlayer);
                    t.assertTrue(at(rec, cue(SoundIndex.PARRY), npc2).isEmpty(), "a shield up for 20 ticks played the parry cue");
                    t.succeed();
                }
            });
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    /**
     * {@code staggerAndFallenSoundAlikeForNpcAndPlayer}: one hero's poise filled by two NPC soldiers' blows and another's
     * by two mock players' blows: each plays {@code cues.poise.crack} once as the bar passes 75%, then
     * {@code cues.poise.break} and the dizzy {@code cues.stagger} 6 ticks later. Killed, the one by a soldier and the
     * other by a player, each plays {@code cues.fallen} at the hero and in the head of every player who saw its bar.
     */
    @GameTest(template = "platform", timeoutTicks = 160, batch = "hero_sound_fallen")
    public static void staggerAndFallenSoundAlikeForNpcAndPlayer(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, FactionType.EMBER_LEGION, 2, 11, 160);
        try {
            HeroSounds.Recorder rec = recorder(t);
            String a = "fx_sound_fallen_a", b = "fx_sound_fallen_b";
            String boss = ", \"poise\": 50, \"phases\": [{\"at\": 100}]";
            HeroEntity byNpc = bossHero(t, a, bossDef(a, "striker", HeroBossGameTests.IDLE_MOVE, boss, ""), 4, 6);
            HeroEntity byPlayer = bossHero(t, b, bossDef(b, "striker", HeroBossGameTests.IDLE_MOVE, boss, ""), 4, 14);
            for (HeroEntity x : List.of(byNpc, byPlayer)) {
                x.setNoAi(true);
            }
            FactionNpc s1 = soldier(t, 6, 5), s2 = soldier(t, 6, 7);
            ServerPlayer p1 = swordPlayer(t, 6, 13), p2 = swordPlayer(t, 6, 15);
            // the fight starts first (a new fight empties the bar), then two oversized blows each fill it
            h.runAtTickTime(2, t.guard(() -> {
                byNpc.setTarget(s1);
                byPlayer.setTarget(p1);
            }));
            long[] cracked = {-1}, broke = {-1};
            h.runAtTickTime(8, t.guard(() -> {
                blow(byNpc, s1, 40);
                blow(byPlayer, p1, 40);
                cracked[0] = h.getLevel().getGameTime();
            }));
            h.runAtTickTime(10, t.guard(() -> {
                blow(byNpc, s2, 40);
                blow(byPlayer, p2, 40);
                broke[0] = h.getLevel().getGameTime();
            }));
            long[] killed = {-1};
            int[] viewers = {0};
            t.onEachTick(() -> {
                long now = h.getLevel().getGameTime();
                if (broke[0] > 0 && now == broke[0] + 12) {
                    for (HeroEntity x : List.of(byNpc, byPlayer)) {
                        String who = x == byNpc ? "the soldiers' blows" : "the mock players' blows";
                        t.assertTrue(x.combat().staggered(), who + " did not stagger it (poise " + x.combat().poise().value()
                                + " of " + x.combat().poise().max() + ")");
                        List<HeroSounds.Played> cr = at(rec, cue(SoundIndex.POISE_CRACK), x);
                        List<HeroSounds.Played> br = at(rec, cue(SoundIndex.POISE_BREAK), x);
                        List<HeroSounds.Played> st = at(rec, cue(SoundIndex.STAGGER), x);
                        t.assertTrue(cr.size() == 1 && cr.get(0).time() == cracked[0], who + ": the crack " + cr);
                        t.assertTrue(br.size() == 1 && br.get(0).time() == broke[0], who + ": the break " + br);
                        t.assertTrue(st.size() == 1 && st.get(0).time() - br.get(0).time() == SoundIndex.STAGGER_AFTER_BREAK,
                                who + ": the stars " + st + " after the break " + br);
                    }
                    viewers[0] = HeroBossBars.viewers(byNpc).size() + HeroBossBars.viewers(byPlayer).size();
                    t.assertTrue(HeroBossBars.viewers(byNpc).contains(p1) && HeroBossBars.viewers(byPlayer).contains(p1),
                            "the mock player does not see both bars");
                    byNpc.setHealth(1F);
                    byPlayer.setHealth(1F);
                    blow(byNpc, s1, 5);
                    blow(byPlayer, p1, 5);
                    killed[0] = now;
                }
                if (killed[0] > 0 && now == killed[0] + 2) {
                    String fallen = cue(SoundIndex.FALLEN);
                    for (HeroEntity x : List.of(byNpc, byPlayer)) {
                        String who = x == byNpc ? "killed by a soldier" : "killed by a mock player";
                        t.assertTrue(x.isDeadOrDying(), who + ": still alive at " + x.getHealth());
                        t.assertTrue(at(rec, fallen, x).size() == 1, who + ": no fallen sting at the hero");
                    }
                    long heard = rec.of(fallen).stream().filter(p -> p.notified() != null).count();
                    t.assertTrue(heard == viewers[0], "the players who saw the bars heard fallen " + heard + " times in the head, not "
                            + viewers[0]);
                    t.assertTrue(rec.of(cue(SoundIndex.FLED)).isEmpty(), "fled for a death");
                    t.succeed();
                }
            });
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }
}
