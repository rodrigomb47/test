package com.factionclash.info;

import com.factionclash.campaign.BaseState;
import com.factionclash.campaign.CampaignData;
import com.factionclash.campaign.FactionDirector;
import com.factionclash.campaign.FactionState;
import com.factionclash.campaign.NpcRecord;
import com.factionclash.campaign.Project;
import com.factionclash.campaign.Recruitment;
import com.factionclash.campaign.Report;
import com.factionclash.campaign.WorkplaceState;
import com.factionclash.config.FCConfig;
import com.factionclash.faction.FactionType;
import com.factionclash.npc.NpcRole;
import com.factionclash.storage.FactionStorage;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** In-world information panels (spec 19). Only a faction's own members see its internal state. */
public final class BaseInfo {
    private BaseInfo() {
    }

    public static BaseState baseAtPoint(CampaignData data, String key, BlockPos pos) {
        for (BaseState b : data.bases.values()) {
            if (pos.equals(b.point(key))) {
                return b;
            }
        }
        return null;
    }

    private static boolean allowed(ServerPlayer p, FactionType f) {
        return CampaignData.get(p.server).players.get(p.getUUID()) == f || p.hasPermissions(2);
    }

    private static void line(ServerPlayer p, String text, ChatFormatting color) {
        p.sendSystemMessage(Component.literal(text).withStyle(color));
    }

    public static List<String> baseLines(ServerLevel level, CampaignData data, BaseState b) {
        List<String> out = new ArrayList<>();
        FactionState f = data.faction(b.faction);
        out.add("== " + b.name + " (" + b.faction.displayName + ")" + (b.destroyed ? " [DESTROYED]" : "") + " ==");
        Map<NpcRole, Integer> pop = new EnumMap<>(NpcRole.class);
        int total = 0;
        for (NpcRecord r : f.roster.values()) {
            if (r.alive && r.baseId == b.id) {
                pop.merge(r.role, 1, Integer::sum);
                total++;
            }
        }
        StringBuilder sb = new StringBuilder("Population " + total + "/" + b.beds.size() + " beds: ");
        pop.forEach((k, v) -> sb.append(k.displayName).append(" ").append(v).append(", "));
        out.add(sb.toString());
        out.add("Food " + Recruitment.storedFoodValue(level, b) + " hunger pts (recruit costs " + FCConfig.RECRUIT_FOOD_VALUE.get() + ")"
                + "  Storage " + Math.round(FactionStorage.fillRatio(level, b) * 100) + "% full");
        StringBuilder res = new StringBuilder("Stock: ");
        Object[][] items = {{"Logs", null}, {"Planks", null}, {"Cobble", Items.COBBLESTONE}, {"Coal", Items.COAL},
                {"Raw iron", Items.RAW_IRON}, {"Iron", Items.IRON_INGOT}, {"Gold", Items.GOLD_INGOT}, {"Diamond", Items.DIAMOND},
                {"Torches", Items.TORCH}, {"Wheat", Items.WHEAT}};
        for (Object[] it : items) {
            int n;
            if (it[0].equals("Logs")) {
                n = FactionStorage.count(level, b, s -> s.is(ItemTags.LOGS));
            } else if (it[0].equals("Planks")) {
                n = FactionStorage.count(level, b, s -> s.is(ItemTags.PLANKS));
            } else {
                Item item = (Item) it[1];
                n = FactionStorage.count(level, b, s -> s.is(item));
            }
            res.append(it[0]).append(" ").append(n).append(", ");
        }
        out.add(res.toString());
        out.add("Recruitment: " + FactionDirector.LAST_RECRUIT_NOTE.getOrDefault(b.id, "pending evaluation"));
        for (int id : b.workplaces) {
            WorkplaceState w = data.workplace(id);
            if (w == null) {
                continue;
            }
            NpcRecord worker = w.assigned == null ? null : f.roster.get(w.assigned);
            String who = worker == null ? "none" : worker.name + (worker.alive ? "" : " (dead)");
            if (w.type == WorkplaceState.Type.CAVE) {
                out.add("Surveyed cave #" + w.id + " at " + w.origin.toShortString() + ": " + (w.workedOut ? "worked out" : w.marks.size() + " marked ores")
                        + ", miner " + who + ", output " + w.totalOutput + " - " + w.note);
                continue;
            }
            out.add(w.type.label + " #" + w.id + ": tier " + w.tier + "/" + w.type.maxTier + (w.upgrading() ? " (upgrading, step " + w.upgradeStep + ")" : "")
                    + ", worker " + who + ", output " + w.totalOutput);
        }
        if (b.projects.isEmpty()) {
            out.add("Construction: none queued; " + b.expansionLots.size() + " house lots free");
        }
        for (Project p : b.projects) {
            out.add("Construction: " + p.type.label + " #" + p.id + " - " + p.status + " (" + p.placed + " blocks placed)");
        }
        out.add("Repairs pending: " + b.pendingRepairs.size() + "  Player edits preserved: " + b.playerEdits.size());
        out.add("Reports delivered: " + f.reportsDelivered + " (" + f.knowledge.size() + " on the map), sectors explored " + f.explored.size());
        if (f.raidTarget >= 0) {
            BaseState t = data.base(f.raidTarget);
            out.add("Raid in progress on " + (t == null ? "?" : t.faction.displayName) + " with " + f.raiders.size() + " soldiers");
        }
        out.add("Great People: not implemented in this build");
        return out;
    }

    public static void sendBaseInfo(ServerPlayer p, BaseState b) {
        if (!allowed(p, b.faction)) {
            line(p, "This is the reporting station of " + b.faction.displayName + ". Its records are not yours to read.", ChatFormatting.GRAY);
            return;
        }
        List<String> lines = baseLines(p.serverLevel(), CampaignData.get(p.server), b);
        for (int i = 0; i < lines.size(); i++) {
            line(p, lines.get(i), i == 0 ? b.faction.color : ChatFormatting.WHITE);
        }
    }

    public static List<String> mapLines(CampaignData data, FactionType f, long now) {
        List<String> out = new ArrayList<>();
        FactionState fs = data.faction(f);
        out.add("== " + f.displayName + " world map: " + fs.knowledge.size() + " delivered reports ==");
        for (BaseState b : data.livingBasesOf(f)) {
            for (int id : b.workplaces) {
                WorkplaceState w = data.workplace(id);
                if (w != null && w.type == WorkplaceState.Type.CAVE) {
                    out.add("Surveyed cave #" + w.id + " (entrance " + w.origin.toShortString() + "): "
                            + (w.workedOut ? "worked out" : w.marks.size() + " marked ores") + " - " + w.note);
                }
            }
        }
        List<Report> sorted = new ArrayList<>(fs.knowledge);
        sorted.sort(Comparator.comparing((Report r) -> r.kind).thenComparingLong(r -> -r.deliveredAt));
        for (Report r : sorted) {
            long age = (now - r.observedAt) / 20;
            boolean stale = r.kind.canGoStale && now - r.observedAt > FCConfig.REPORT_STALE_TICKS.get();
            out.add(r.kind.label + " at " + r.pos.getX() + ", " + r.pos.getZ() + (r.subject != null ? " [" + r.subject.displayName + "]" : "")
                    + " - " + r.note + " (seen " + age + "s ago by " + r.by + (stale ? ", STALE" : "") + ")");
        }
        return out;
    }

    public static void sendMap(ServerPlayer p, FactionType f) {
        if (!allowed(p, f)) {
            line(p, "This map belongs to " + f.displayName + ".", ChatFormatting.GRAY);
            return;
        }
        List<String> lines = mapLines(CampaignData.get(p.server), f, p.serverLevel().getGameTime());
        int shown = 0;
        for (String l : lines) {
            if (shown++ > 30) {
                line(p, "... " + (lines.size() - 31) + " more", ChatFormatting.GRAY);
                break;
            }
            line(p, l, shown == 1 ? f.color : ChatFormatting.WHITE);
        }
    }
}
