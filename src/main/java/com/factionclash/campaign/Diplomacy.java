package com.factionclash.campaign;

import com.factionclash.FactionClash;
import com.factionclash.faction.FactionType;
import com.factionclash.util.Messages;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The balance of power. When a human faction conquers another, the surviving human factions (two or more besides
 * the conqueror) swear a pact against it: they stop fighting each other, the survivors of the fallen faction tell
 * them where the conqueror's bases are, they only raid the conqueror, and a member joins an offensive another member
 * has launched. The pact ends when the conqueror falls (the next conquest can raise a new one against the new
 * conqueror) or fewer than two members are left. Off with config {@code war.pacts}.
 */
public final class Diplomacy {
    private Diplomacy() {
    }

    /** Same faction, or two members of the pact. */
    public static boolean allied(CampaignData data, FactionType a, FactionType b) {
        if (a == null || b == null) {
            return false;
        }
        return a == b || data.pactAgainst != null && data.pactMembers.contains(a) && data.pactMembers.contains(b);
    }

    public static boolean allied(MinecraftServer server, FactionType a, FactionType b) {
        if (a == null || b == null) {
            return false;
        }
        return a == b || server != null && allied(CampaignData.get(server), a, b);
    }

    /** Two different factions that are not allied: their NPCs fight, and they may raid each other. */
    public static boolean hostile(MinecraftServer server, FactionType a, FactionType b) {
        return a != null && b != null && !allied(server, a, b);
    }

    /** The faction a pact member is sworn against (null when it is in no pact). */
    public static FactionType swornEnemy(CampaignData data, FactionType f) {
        return data.pactAgainst != null && data.pactMembers.contains(f) ? data.pactAgainst : null;
    }

    /** A faction was eliminated, by {@code by} (null for monsters or an admin). */
    public static void onElimination(ServerLevel level, CampaignData data, FactionType fallen, FactionType by) {
        if (com.factionclash.config.FCConfig.PACTS.get() && by != null && by.human && fallen.human && by != fallen) {
            List<FactionType> survivors = new ArrayList<>();
            for (FactionType t : FactionType.humans()) {
                if (t != by && t != fallen && data.faction(t).alive && !data.livingBasesOf(t).isEmpty()) {
                    survivors.add(t);
                }
            }
            if (survivors.size() >= 2) {
                swear(level, data, survivors, by, fallen);
                return;
            }
        }
        recheck(level, data);
    }

    private static void swear(ServerLevel level, CampaignData data, List<FactionType> members, FactionType against, FactionType fallen) {
        boolean renewed = data.pactAgainst == against && data.pactMembers.containsAll(members);
        data.pactAgainst = against;
        data.pactMembers.clear();
        data.pactMembers.addAll(members);
        data.pactSince = level.getGameTime();
        data.setDirty();
        String names = members.stream().map(t -> t.displayName).collect(Collectors.joining(" and "));
        if (!renewed) {
            Messages.toAll(level.getServer(), Component.literal(names + " swear a pact against " + against.displayName
                    + ", conqueror of " + fallen.displayName).withStyle(ChatFormatting.GOLD));
            FactionClash.LOGGER.info("[Faction Clash] {} swear a pact against {}", names, against.displayName);
        }
        // raids between the new allies are called off
        for (FactionType m : members) {
            FactionState f = data.faction(m);
            BaseState target = data.base(f.raidTarget);
            if (target != null && members.contains(target.faction)) {
                f.raidTarget = -1;
                f.raiders.clear();
                Messages.toFaction(level.getServer(), m, Component.literal("The raid on " + target.faction.displayName
                        + " is called off: we are allies now"));
            }
        }
        // the fallen faction's survivors tell the allies where the conqueror lives
        long now = level.getGameTime();
        for (FactionType m : members) {
            FactionState f = data.faction(m);
            for (BaseState b : data.livingBasesOf(against)) {
                Report r = new Report(Report.Kind.ENEMY_BASE, b.center, now, against, 0,
                        against.displayName + " settlement (told by " + fallen.displayName + " survivors)");
                r.by = fallen.displayName + " survivors";
                r.deliveredAt = now;
                f.addReport(r);
            }
        }
    }

    /** Ends the pact when its target has fallen or fewer than two members are left. */
    public static void recheck(ServerLevel level, CampaignData data) {
        if (data.pactAgainst == null) {
            return;
        }
        data.pactMembers.removeIf(t -> !data.faction(t).alive);
        if (!data.faction(data.pactAgainst).alive || data.pactMembers.size() < 2) {
            Messages.toAll(level.getServer(), Component.literal("The pact against " + data.pactAgainst.displayName + " is over")
                    .withStyle(ChatFormatting.GOLD));
            data.pactAgainst = null;
            data.pactMembers.clear();
            data.setDirty();
        }
    }

    /** One line for status screens, or null. */
    public static String describe(CampaignData data) {
        if (data.pactAgainst == null) {
            return null;
        }
        return "Pact: " + data.pactMembers.stream().map(t -> t.displayName).collect(Collectors.joining(" and "))
                + " against " + data.pactAgainst.displayName;
    }
}
