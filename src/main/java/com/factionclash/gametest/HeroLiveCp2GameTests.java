package com.factionclash.gametest;

import com.factionclash.FactionClash;
import com.factionclash.faction.FactionType;
import com.factionclash.hero.HeroEntity;
import com.factionclash.hero.HeroEntry;
import com.factionclash.hero.HeroRegistry;
import com.factionclash.npc.FactionNpc;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.factionclash.gametest.HeroAbilityGameTests.foe;
import static com.factionclash.gametest.HeroAbilityGameTests.slice;

/**
 * Checkpoint 2 in game (SLICE_PLAN 7, HEROES_V2 14.4 gates 3 and 5; 2026-11-19): what the live check on a dedicated
 * server found, each held here by a test.
 * <ul>
 * <li>{@code cp2HinaePurifiesHurtAlliesInAFight}: no test had Hinae pick Purify by herself (the ability tests force it),
 * and the live check could not show it at first (her allies there walked off to a raid, or healed above 60% from their
 * own food before her next pick). Held here so the support pick stays covered.</li>
 * <li>{@code cp2DormantIsReportedOnlyOnceDefinitionsAreRead}: the viewer's client log said at every resource load that
 * every hero was "dormant (a plain soldier with the neutral look)", Natsuho and Hinae included, while it drew them in
 * their own models: a client of a dedicated server never reads definitions. Now a load reports dormant heroes only once
 * definitions have been read; failed before the fix.</li>
 * </ul>
 */
@GameTestHolder(FactionClash.MODID)
@PrefixGameTestTemplate(false)
public final class HeroLiveCp2GameTests {
    private HeroLiveCp2GameTests() {
    }

    private static final FactionType SIDE = FactionType.EMBER_LEGION;

    /**
     * Hinae, in a fight against a soldier that stands its ground 9 blocks away, with two hurt allies at her side (50 of
     * 100 health), heals them with Purify by her own pick (no forced move), as her kit says ("she uses it when an ally is
     * under 60% health").
     */
    @GameTest(template = "platform", timeoutTicks = 400, batch = "hero_cp2_support")
    public static void cp2HinaePurifiesHurtAlliesInAFight(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 400);
        try {
            String hi = "hinae_shirasuzu";
            HeroEntity priest = HeroMoveGameTests.hero(t, hi, HeroMoveGameTests.visual(hi), slice(hi), SIDE, 4, 6);
            FactionNpc a = HeroReviewGameTests.friend(t, 5, 4);
            FactionNpc b = HeroReviewGameTests.friend(t, 5, 8);
            FactionNpc enemy = foe(t, 13, 6, 400);
            priest.setTarget(enemy);
            List<String> started = new ArrayList<>();
            long[] seen = {-1};
            t.onEachTick(() -> {
                long at = priest.combat().lastMoveStart();
                if (at >= 0 && at != seen[0]) {
                    seen[0] = at;
                    started.add(priest.combat().lastMove() + "@" + h.getTick());
                }
                if (a.getHealth() > 50.5F && b.getHealth() > 50.5F) {
                    t.succeed();
                    return;
                }
                if (h.getTick() >= 380) {
                    t.fail("no Purify in 19 s with two allies at half health: moves " + started + ", fighting "
                            + priest.combat().boss().fighting() + ", target " + priest.getTarget() + ", allies "
                            + a.getHealth() + " / " + b.getHealth());
                }
            });
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }

    @GameTest(template = "platform", timeoutTicks = 20, batch = "workers_static")
    public static void cp2DormantIsReportedOnlyOnceDefinitionsAreRead(GameTestHelper h) {
        GameTestBases.Handle t = GameTestBases.register(h, SIDE, 2, 11, 20);
        try {
            Map<String, HeroEntry> all = new LinkedHashMap<>();
            for (HeroEntry e : HeroRegistry.heroes()) {
                all.put(e.id(), e);
            }
            t.assertTrue(HeroRegistry.definitionsRead(), "the GameTest server read no definitions");
            t.assertTrue(all.containsKey("natsuho_komezaki") && !all.get("natsuho_komezaki").dormant(), "Natsuho is dormant");
            List<String> server = HeroRegistry.dormantToReport(all, true);
            t.assertTrue(!server.contains("natsuho_komezaki") && !server.contains("hinae_shirasuzu"),
                    "a loaded hero reported dormant: " + server);
            boolean anyDormant = all.values().stream().anyMatch(HeroEntry::dormant);
            t.assertTrue(server.isEmpty() == !anyDormant, "the dormant heroes were not reported: " + server);
            // a client of a dedicated server: no definitions read, so its entries are all without one, and none is reported
            t.assertTrue(HeroRegistry.dormantToReport(all, false).isEmpty(),
                    "a load with no definitions read reports " + HeroRegistry.dormantToReport(all, false));
            t.succeed();
        } catch (RuntimeException | Error e) {
            t.cleanup();
            throw e;
        }
    }
}
