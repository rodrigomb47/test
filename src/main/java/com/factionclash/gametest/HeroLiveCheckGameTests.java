package com.factionclash.gametest;

import com.factionclash.FactionClash;
import com.factionclash.config.FCConfig;
import com.factionclash.faction.FactionType;
import com.factionclash.hero.HeroEntity;
import com.factionclash.hero.HeroManager;
import com.factionclash.hero.HeroStatus;
import com.factionclash.logic.hero.HeroDef;
import com.factionclash.logic.hero.HeroDef.LineEvent;
import com.factionclash.npc.FactionNpc;
import com.factionclash.registry.ModEntities;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

import static com.factionclash.gametest.HeroAbilityGameTests.foe;
import static com.factionclash.gametest.HeroAbilityGameTests.player;
import static com.factionclash.gametest.HeroBossGameTests.IDLE_MOVE;
import static com.factionclash.gametest.HeroBossGameTests.blow;
import static com.factionclash.gametest.HeroMoveGameTests.def;
import static com.factionclash.gametest.HeroMoveGameTests.hero;

/**
 * The live check of the combat engine on a dedicated server with the real client (HEROES_V2 15.2 "Combat engine in
 * game"): one GameTest for each fault it found, each with an NPC soldier and a mock player alike (6.15), each in a batch
 * of its own.
 * <ul>
 * <li>{@code heroFightsAfieldBeyondItsLeash}: a hero far from its base (at a far work site, on the road, in the wild)
 * fights the enemy in front of it; the melee leash measured from its base's centre made it drop every target out
 * there, so it never fought back afield.</li>
 * <li>{@code phaseLineIsSaidRightAfterAnotherLine}: a phase transition's line is said even when the hero spoke a moment
 * before (a skill's line); the dialogue cooldown swallowed it.</li>
 * <li>{@code enrageDoesNotOutliveALoad}: a hero saved while enraged loads without the enrage's damage bonus (the fight
 * does not survive a load); it kept +15% for good.</li>
 * <li>{@code introOncePerDayAcrossARevival}: a hero whose intro played today, killed and back from its revival the same
 * day, opens its next fight without the intro; the new entity played it again.</li>
 * </ul>
 */
@GameTestHolder(FactionClash.MODID)
@PrefixGameTestTemplate(false)
public final class HeroLiveCheckGameTests {
    private HeroLiveCheckGameTests() {
    }

    private static final FactionType SIDE = FactionType.EMBER_LEGION, FOE = FactionType.GROVE_COVENANT;

    // ------------------------------------------------------------------ the leash afield

    @GameTest(template = "platform", timeoutTicks = 260, batch = "hero_live_afield")
    public static void heroFightsAfieldBeyondItsLeash(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 260);
        try {
            // its home lies 200 blocks off: everything here is beyond a steady hero's 56-block leash from home
            t.base.center = t.base.center.offset(200, 0, 0);
            HeroEntity byNpc = hero(t, "fx_live_afield_n", def("fx_live_afield_n", IDLE_MOVE), 4, 4);
            HeroEntity byPlayer = hero(t, "fx_live_afield_p", def("fx_live_afield_p", IDLE_MOVE), 4, 20);
            FactionNpc soldier = foe(t, 6, 4, 100);
            ServerPlayer p = player(t, 6, 20, FOE, 100);
            List<HeroEntity> heroes = List.of(byNpc, byPlayer);
            List<LivingEntity> foes = List.of(soldier, p);
            h.runAtTickTime(3, t.guard(() -> {
                // each foe strikes first, as the player did in the live check
                blow(byNpc, soldier, 1);
                blow(byPlayer, p, 1);
            }));
            boolean[] held = new boolean[2];
            t.onEachTick(() -> {
                if (h.getTick() < 200) {
                    return;
                }
                for (int i = 0; i < 2; i++) {
                    held[i] |= heroes.get(i).getTarget() == foes.get(i);
                }
                if (h.getTick() >= 240) {
                    for (int i = 0; i < 2; i++) {
                        LivingEntity f = foes.get(i);
                        t.assertTrue(held[i], heroes.get(i).heroId() + " dropped its target afield ("
                                + (i == 0 ? "an NPC soldier" : "a player") + ")");
                        t.assertTrue(f.getHealth() < f.getMaxHealth(), heroes.get(i).heroId() + " never struck back afield ("
                                + (i == 0 ? "an NPC soldier" : "a player") + ": " + f.getHealth() + ")");
                    }
                    t.succeed();
                }
            });
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    // ------------------------------------------------------------------ the phase line

    static final String PHASES = ", \"phases\": [{\"at\": 100}, {\"at\": 60, \"transition\": {\"seconds\": 1.0,"
            + " \"line\": \"Second wind!\"}}]";

    /** A hero with two phases (the second at 60%, its own line) and one never-picked move. */
    static String phased(String id) {
        // the boss block's own phases replace def()'s single phase: written whole here
        return def(id, IDLE_MOVE).replace("\"phases\": [{\"at\": 100}]", PHASES.substring(2));
    }

    @GameTest(template = "platform", timeoutTicks = 120, batch = "hero_live_phase")
    public static void phaseLineIsSaidRightAfterAnotherLine(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 120);
        try {
            HeroEntity byNpc = hero(t, "fx_live_phase_n", phased("fx_live_phase_n"), 4, 4);
            HeroEntity byPlayer = hero(t, "fx_live_phase_p", phased("fx_live_phase_p"), 4, 20);
            FactionNpc soldier = foe(t, 6, 4, 100);
            ServerPlayer p = player(t, 6, 20, FOE, 100);
            List<HeroEntity> heroes = List.of(byNpc, byPlayer);
            List<LivingEntity> foes = List.of(soldier, p);
            for (HeroEntity x : heroes) {
                x.setNoAi(true);
            }
            h.runAtTickTime(3, t.guard(() -> {
                for (int i = 0; i < 2; i++) {
                    HeroEntity x = heroes.get(i);
                    x.setTarget(foes.get(i));
                    blow(x, foes.get(i), 1); // the fight begins
                    // a skill's line a moment before (as "Take this!" was in the live check): the cooldown now holds
                    long now = h.getLevel().getGameTime();
                    x.clock().said(LineEvent.SKILL, now);
                    t.assertTrue(!x.clock().allows(LineEvent.SKILL, now, FCConfig.HERO_DIALOGUE_COOLDOWN_SECONDS.get()),
                            "the dialogue cooldown is not running");
                    x.setHealth(x.getMaxHealth() * 0.61F);
                    blow(x, foes.get(i), 3); // under 60%: the second phase is due
                }
            }));
            h.runAtTickTime(12, t.guard(() -> {
                for (int i = 0; i < 2; i++) {
                    HeroEntity x = heroes.get(i);
                    String who = i == 0 ? "an NPC soldier" : "a player";
                    t.assertTrue(x.combat().boss().phase() == 2 && x.combat().boss().transitionCount() == 1,
                            x.heroId() + " did not enter its second phase (" + who + ")");
                    t.assertTrue(x.spokenLine().endsWith("Second wind!"), x.heroId() + "'s phase line was swallowed by the"
                            + " line before it (" + who + "); it last said: " + x.spokenLine().replace('\n', ' '));
                }
                t.succeed();
            }));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    // ------------------------------------------------------------------ the intro, once a day per hero

    @GameTest(template = "platform", timeoutTicks = 140, batch = "hero_live_intro")
    public static void introOncePerDayAcrossARevival(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 140);
        try {
            String[] ids = {"fx_live_intro_n", "fx_live_intro_p"};
            for (int i = 0; i < 2; i++) {
                // the helper takes today's intro as played, as a hero that already opened a fight today
                hero(t, ids[i], def(ids[i], IDLE_MOVE), 4, 4 + 16 * i).kill();
            }
            FactionNpc soldier = foe(t, 6, 4, 100);
            ServerPlayer p = player(t, 6, 20, FOE, 100);
            List<LivingEntity> foes = List.of(soldier, p);
            HeroEntity[] back = new HeroEntity[2];
            h.runAtTickTime(30, t.guard(() -> {
                for (int i = 0; i < 2; i++) {
                    HeroManager.Result r = HeroManager.revive(h.getLevel().getServer(), ids[i]);
                    t.assertTrue(r.ok() && r.hero() != null, ids[i] + " did not return: " + r.message());
                    back[i] = r.hero();
                    back[i].moveTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(h.absolutePos(new net.minecraft.core.BlockPos(4, 2, 4 + 16 * i))));
                    back[i].setNoAi(true);
                }
            }));
            h.runAtTickTime(34, t.guard(() -> {
                for (int i = 0; i < 2; i++) {
                    back[i].setTarget(foes.get(i));
                    blow(back[i], foes.get(i), 1); // its first fight since it came back, against a person
                }
            }));
            h.runAtTickTime(120, t.guard(() -> {
                for (int i = 0; i < 2; i++) {
                    t.assertTrue(back[i].combat().boss().fighting(), ids[i] + " is not fighting");
                    t.assertTrue(back[i].combat().boss().intros() == 0, ids[i] + " played its intro twice in a day, back from"
                            + " its revival (" + (i == 0 ? "an NPC soldier" : "a player") + ")");
                }
                t.succeed();
            }));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    // ------------------------------------------------------------------ the enrage and a load

    @GameTest(template = "platform", timeoutTicks = 80, batch = "hero_live_enrage")
    public static void enrageDoesNotOutliveALoad(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 80);
        try {
            HeroEntity byNpc = hero(t, "fx_live_rage_n", def("fx_live_rage_n", IDLE_MOVE), 4, 4);
            HeroEntity byPlayer = hero(t, "fx_live_rage_p", def("fx_live_rage_p", IDLE_MOVE), 4, 20);
            FactionNpc soldier = foe(t, 6, 4, 100);
            ServerPlayer p = player(t, 6, 20, FOE, 100);
            List<HeroEntity> heroes = List.of(byNpc, byPlayer);
            List<LivingEntity> foes = List.of(soldier, p);
            for (HeroEntity x : heroes) {
                x.setNoAi(true);
            }
            h.runAtTickTime(3, t.guard(() -> {
                for (int i = 0; i < 2; i++) {
                    heroes.get(i).setTarget(foes.get(i));
                    blow(heroes.get(i), foes.get(i), 1);
                }
            }));
            h.runAtTickTime(5, t.guard(() -> {
                for (HeroEntity x : heroes) {
                    x.combat().boss().backdateFight(181 * 20); // the soft enrage is due
                }
            }));
            h.runAtTickTime(10, t.guard(() -> {
                for (int i = 0; i < 2; i++) {
                    HeroEntity x = heroes.get(i);
                    String who = i == 0 ? "an NPC soldier" : "a player";
                    double before = HeroStatus.percent(x, HeroDef.Stat.DAMAGE_DEALT);
                    t.assertTrue(x.combat().boss().enraged() && before >= 15 - 1e-6,
                            x.heroId() + " did not enrage (" + who + "): +" + before + "%");
                    CompoundTag tag = x.saveWithoutId(new CompoundTag());
                    HeroEntity copy = ModEntities.HERO.get().create(h.getLevel());
                    copy.load(tag);
                    double after = HeroStatus.percent(copy, HeroDef.Stat.DAMAGE_DEALT);
                    copy.discard();
                    t.assertTrue(after == 0, x.heroId() + " loaded with the enrage's +" + after + "% damage and no fight ("
                            + who + ")");
                }
                t.succeed();
            }));
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }
}
