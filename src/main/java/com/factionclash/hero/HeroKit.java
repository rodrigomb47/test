package com.factionclash.hero;

import com.factionclash.npc.NpcRole;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * A hero's own kit items are bound to it (v1 7, HEROES_V2 3.4): they never drop, are never deposited in the
 * storehouse and vanish when better storehouse gear replaces them; a revived hero gets them again. A1a has no kit
 * format yet, so the kit is the role's basic weapon (a stone sword for a sword or fist style, a stone axe, a bow or a
 * crossbow), as v1 adds for a kit that lacks one. The binding is a marker in the item's custom data.
 */
public final class HeroKit {
    private static final String TAG = "FcHeroKit";

    private HeroKit() {
    }

    /** The kit weapon of a hero of this role, bound to {@code heroId}. */
    public static ItemStack weapon(NpcRole role, String heroId) {
        ItemStack s = role.tool.basicItem();
        if (!s.isEmpty()) {
            mark(s, heroId);
        }
        return s;
    }

    public static ItemStack mark(ItemStack s, String heroId) {
        CustomData.update(DataComponents.CUSTOM_DATA, s, t -> t.putString(TAG, heroId));
        return s;
    }

    /** Whether this item is some hero's kit item. */
    public static boolean isKit(ItemStack s) {
        if (s.isEmpty()) {
            return false;
        }
        CustomData d = s.get(DataComponents.CUSTOM_DATA);
        return d != null && d.contains(TAG);
    }
}
