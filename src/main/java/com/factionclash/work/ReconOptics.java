package com.factionclash.work;

import com.factionclash.logic.campaign.ReconOptic;
import com.factionclash.npc.FactionNpc;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.HashSet;
import java.util.Set;

/**
 * The game side of the recon scout's instruments ({@code docs/design/recon.md} 5.6, task R12): which row of
 * {@link ReconOptic} an item is (by its registry id, so a later instrument of the mod's own is a row there and nothing
 * here), and the best one an NPC carries, in its hands or its backpack. No faction has a tech level yet, so only rows that
 * need none count ({@link ReconOptic#NO_TECH}): the vanilla spyglass, never behind one.
 */
public final class ReconOptics {
    private ReconOptics() {
    }

    /** The instrument {@code s} is, or null (not one: dirt, a sword, an empty slot). */
    public static ReconOptic of(ItemStack s) {
        if (s == null || s.isEmpty()) {
            return null;
        }
        return ReconOptic.byItem(BuiltInRegistries.ITEM.getKey(s.getItem()).toString());
    }

    /** Whether {@code s} is an instrument a recon scout watches through (one a faction with no tech level can use). */
    public static boolean isOptic(ItemStack s) {
        ReconOptic o = of(s);
        return o != null && o.usable(ReconOptic.NO_TECH);
    }

    /** The best instrument {@code n} carries (its hands, then its backpack): the eyes when it carries none. */
    public static ReconOptic carried(FactionNpc n) {
        Set<String> ids = new HashSet<>();
        add(ids, n.getMainHandItem());
        add(ids, n.getOffhandItem());
        for (int i = 0; i < n.inventory().getContainerSize(); i++) {
            add(ids, n.inventory().getItem(i));
        }
        return ReconOptic.best(ids, ReconOptic.NO_TECH);
    }

    private static void add(Set<String> ids, ItemStack s) {
        if (!s.isEmpty()) {
            ids.add(BuiltInRegistries.ITEM.getKey(s.getItem()).toString());
        }
    }
}
