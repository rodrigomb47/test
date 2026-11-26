package com.factionclash.gametest;

import com.factionclash.FactionClash;
import com.factionclash.campaign.BaseState;
import com.factionclash.campaign.BaseWatch;
import com.factionclash.campaign.FactionDirector;
import com.factionclash.campaign.FactionState;
import com.factionclash.campaign.Report;
import com.factionclash.command.FCCommands;
import com.factionclash.config.FCConfig;
import com.factionclash.faction.FactionType;
import com.factionclash.info.BaseInfo;
import com.factionclash.logic.campaign.Intel;
import com.factionclash.logic.campaign.Sightings;
import com.factionclash.npc.NpcRole;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/**
 * What the operators and a faction's station see of focused reconnaissance ({@code docs/design/recon.md} 10.4, task R6):
 * {@code /fc admin dump} lists each known base's recon, the council's recon orders, the strikes it saw and the plan it last
 * judged a target by; the station tells the faction's own view of the war, never the operators' truth; the truth of a
 * watched base's box ({@link BaseWatch#truth}) counts the soldiers in it whatever is in sight. A test base of
 * {@link KnowledgeGameTests} on the platform, with its own batch.
 */
@GameTestHolder(FactionClash.MODID)
@PrefixGameTestTemplate(false)
public final class ReconOperatorsGameTests {
    private ReconOperatorsGameTests() {
    }

    private static final FactionType SIDE = KnowledgeGameTests.SIDE, ENEMY = KnowledgeGameTests.ENEMY, OTHER = FactionType.STONEGUARD;

    /**
     * Our council knows a Grove settlement by a fresh recon counting 6 and a Stoneguard settlement only by a passing look,
     * saw Stoneguard strike our walls and one Grove soldier at home. It waits on Grove (three to send, six needed against
     * six and a reserve of three) and orders a recon of Stoneguard. The dump lists the recon (6 faces, good, usable, the
     * eyes), the order, the strike, the plan (6 counted, a share of 3, the sighting seen away, the reserve, the verdict)
     * and each member's base; the station says the same in the faction's words with no truth in it; and the truth of
     * Grove's box counts its 3 soldiers there.
     */
    @GameTest(template = "platform", timeoutTicks = 60, batch = "recon_operators")
    public static void theOperatorsSeeTheReconTheOrdersAndThePlan(GameTestHelper h) {
        GameTestBases.Handle t = KnowledgeGameTests.base(h, 60);
        ServerLevel level = h.getLevel();
        FactionState f = t.data.faction(SIDE);
        KnowledgeGameTests.keepRaidState(t, f);
        FactionDirector.CouncilPlan oldPlan = FactionDirector.LAST_PLAN.remove(SIDE);
        t.onCleanup(() -> {
            FactionDirector.LAST_PLAN.remove(SIDE);
            if (oldPlan != null) {
                FactionDirector.LAST_PLAN.put(SIDE, oldPlan);
            }
        });
        try {
            // (a filtered run starts in a young world: the strike and the recon need times after tick 0)
            KnowledgeGameTests.atLeast(level, 100_000);
            long now = level.getGameTime();
            int interval = FCConfig.RAID_INTERVAL_TICKS.get();
            t.data.startedAt = now - interval - 1;
            f.lastRaidAt = now - interval - 1;
            for (int[] p : KnowledgeGameTests.GARRISON) {
                KnowledgeGameTests.member(t, p[0], p[1], NpcRole.SWORDSMAN);
            }
            BaseState grove = KnowledgeGameTests.enemyBase(t, ENEMY, h.absolutePos(new BlockPos(12, 2, 21)));
            for (int[] p : new int[][]{{11, 20}, {13, 20}, {12, 22}}) {
                KnowledgeGameTests.stranger(t, ENEMY, p[0], p[1], NpcRole.SWORDSMAN);
            }
            Intel.Recon seen = KnowledgeGameTests.counted(6, 1.0, now - 100, java.util.Map.of());
            KnowledgeGameTests.deliverRecon(t, f, grove, seen, now - 100);
            BlockPos stoneguardAt = h.absolutePos(new BlockPos(2, 2, 28));
            Report passing = new Report(Report.Kind.ENEMY_BASE, stoneguardAt, now - 300, OTHER, 0, Report.positionNote(OTHER, Report.Kind.ENEMY_BASE));
            KnowledgeGameTests.deliver(t, f, passing, now - 300);
            f.struckBy.put(OTHER, now - 200);
            BlockPos at = grove.center.offset(30, 0, 0);
            f.sightings.file(new Sightings.Sighting(UUID.randomUUID(), ENEMY.id, "", 1.0, now - 10, at.getX(), at.getZ(),
                    Sightings.Source.HOME, "", -1, false));
            KnowledgeGameTests.raids(t, f, now);
            t.assertTrue(f.raidTarget < 0, "six counted and a reserve of three should hold three back: " + FactionDirector.LAST_COUNCIL.get(SIDE));

            JsonObject snap = FCCommands.snapshot(level);
            JsonObject us = null;
            for (JsonElement e : snap.getAsJsonArray("factions")) {
                if (e.getAsJsonObject().get("faction").getAsString().equals(SIDE.id)) {
                    us = e.getAsJsonObject();
                }
            }
            t.assertTrue(us != null, "no dump of our faction");
            JsonObject groveKnown = null, stoneKnown = null;
            for (JsonElement e : us.getAsJsonArray("enemyBasesKnown")) {
                JsonObject r = e.getAsJsonObject();
                if (r.get("subject").getAsString().equals(ENEMY.id)) {
                    groveKnown = r;
                } else if (r.get("subject").getAsString().equals(OTHER.id)) {
                    stoneKnown = r;
                }
            }
            t.assertTrue(groveKnown != null && stoneKnown != null, "both reports in the dump: " + us.get("enemyBasesKnown"));
            t.assertTrue(groveKnown.get("base").getAsInt() == grove.id, "the report's base: " + groveKnown);
            JsonObject rj = groveKnown.getAsJsonObject("recon");
            t.assertTrue(rj != null && rj.get("soldiers").getAsInt() == 6 && rj.get("faces").getAsInt() == 6 && rj.get("good").getAsBoolean()
                    && rj.get("usable").getAsBoolean() && rj.get("optic").getAsString().isEmpty() && rj.get("at").getAsLong() == now - 100,
                    "the dump's recon of Grove: " + rj);
            t.assertTrue(!stoneKnown.has("recon") && stoneKnown.get("reconFailures").getAsInt() == 0, "Stoneguard was never watched: " + stoneKnown);
            JsonArray orders = us.getAsJsonArray("reconOrders");
            boolean ordered = false;
            for (JsonElement e : orders) {
                JsonObject o = e.getAsJsonObject();
                ordered |= o.get("subject").getAsString().equals(OTHER.id) && o.get("target").getAsString().equals(stoneguardAt.toShortString())
                        && o.get("orderedAt").getAsLong() == now && o.get("takenAt").getAsLong() == 0;
            }
            t.assertTrue(ordered, "the recon order on Stoneguard in the dump: " + orders);
            t.assertTrue(us.getAsJsonObject("struckBy").get(OTHER.id).getAsLong() == now - 200, "the strike in the dump: " + us.get("struckBy"));
            JsonObject plan = us.getAsJsonObject("plan");
            t.assertTrue(plan != null, "no plan in the dump");
            t.assertTrue(plan.get("base").getAsInt() == grove.id && plan.get("counted").getAsInt() == 6 && plan.get("shareSoldiers").getAsInt() == 3
                    && plan.get("reserve").getAsInt() == 3 && !plan.get("fromSightings").getAsBoolean() && plan.getAsJsonArray("seenAway").size() == 1
                    && plan.get("verdict").getAsString().equals("WAIT") && plan.get("party").getAsInt() == 3
                    && plan.get("needed").getAsInt() > plan.get("party").getAsInt() && !plan.get("counter").getAsBoolean()
                    && plan.getAsJsonObject("recon").get("soldiers").getAsInt() == 6, "the plan in the dump: " + plan);
            t.assertTrue(plan.get("against").getAsString().contains("about 6 defenders") && plan.get("against").getAsString().contains("a reserve of 3"),
                    "the plan's line: " + plan.get("against"));
            boolean bases = true;
            for (JsonElement e : us.getAsJsonArray("roster")) {
                bases &= e.getAsJsonObject().has("base");
            }
            t.assertTrue(bases, "every roster entry names its base");

            List<String> lines = BaseInfo.warLines(f, now);
            String all = String.join(" | ", lines);
            t.assertTrue(lines.stream().anyMatch(l -> l.startsWith("War council, day") && l.contains("waiting on the Grove Covenant settlement: 3 soldiers to send")
                    && l.contains("about 6 defenders")), "the station's council line: " + all);
            t.assertTrue(lines.stream().anyMatch(l -> l.startsWith("Recon of the Grove Covenant settlement") && l.contains("about 6 soldiers")
                    && l.contains("scout Edrin Cole") && l.contains("pillar 12") && l.endsWith("fresh")), "the station's recon of Grove: " + all);
            t.assertTrue(lines.stream().anyMatch(l -> l.startsWith("Recon of the Stoneguard settlement") && l.endsWith("never watched")),
                    "the station's Stoneguard line: " + all);
            t.assertTrue(lines.stream().anyMatch(l -> l.startsWith("Recon orders: ") && l.contains("of the Stoneguard settlement")), "the station's orders: " + all);
            t.assertTrue(lines.stream().anyMatch(l -> l.startsWith("Struck at our walls") && l.contains("Stoneguard")), "the station's strike: " + all);
            t.assertTrue(lines.stream().anyMatch(l -> l.startsWith("Seen away") && l.contains("1 Grove Covenant soldier")), "the station's sightings: " + all);
            t.assertTrue(!all.contains("truth") && !all.contains("[truth"), "the station shows the operators' truth: " + all);
            t.assertTrue(BaseInfo.baseLines(level, t.data, t.base).containsAll(lines), "the station's panel carries the war lines");

            BaseWatch.Truth truth = BaseWatch.truth(level, grove);
            t.assertTrue(truth.soldiers() == 3 && truth.heroes().isEmpty(), "the truth of Grove's box: " + truth);
            h.succeed();
        } catch (RuntimeException e) {
            t.cleanup();
            throw e;
        }
    }
}
