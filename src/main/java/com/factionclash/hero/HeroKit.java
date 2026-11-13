package com.factionclash.hero;

import com.factionclash.FactionClash;
import com.factionclash.logic.hero.HeroDef;
import com.factionclash.npc.NpcRole;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.Enchantment;

import java.util.Map;

/**
 * A hero's own kit items are bound to it (v1 7, HEROES_V2 3.4): they never drop, are never deposited in the
 * storehouse and vanish when better storehouse gear replaces them; a revived hero gets them again. The kit is the
 * definition's {@code gear} ({@link #item}: the vanilla item with its custom name and enchantments); a dormant hero,
 * which has no definition, gets its role's basic weapon as a plain soldier ({@link #weapon}). The binding is a marker
 * in the item's custom data.
 */
public final class HeroKit {
    private static final String TAG = "FcHeroKit";

    private HeroKit() {
    }

    /** A plain soldier's weapon for a hero of this role (a dormant hero), bound to {@code heroId}. */
    public static ItemStack weapon(NpcRole role, String heroId) {
        ItemStack s = role.tool.basicItem();
        if (!s.isEmpty()) {
            mark(s, heroId);
        }
        return s;
    }

    /**
     * One kit item of a definition, bound to {@code heroId}: the vanilla item (the format allows only vanilla ids),
     * its custom name and its enchantments at their levels (an enchantment the registry does not know is skipped and
     * logged: the format's allowlist holds only vanilla ones). Empty for an unknown item id.
     */
    public static ItemStack item(HeroDef.GearItem g, String heroId, HolderLookup.Provider registries) {
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.withDefaultNamespace(g.item()));
        if (item == Items.AIR) {
            FactionClash.LOGGER.warn("[Heroes] {}: the kit item \"{}\" does not exist; left out", heroId, g.item());
            return ItemStack.EMPTY;
        }
        ItemStack s = new ItemStack(item);
        if (g.name() != null && !g.name().isBlank()) {
            s.set(DataComponents.CUSTOM_NAME, Component.literal(g.name()));
        }
        if (!g.enchantments().isEmpty() && registries != null) {
            HolderLookup.RegistryLookup<Enchantment> lookup = registries.lookupOrThrow(Registries.ENCHANTMENT);
            for (Map.Entry<String, Integer> e : g.enchantments().entrySet()) {
                var holder = lookup.get(ResourceKey.create(Registries.ENCHANTMENT, ResourceLocation.withDefaultNamespace(e.getKey())));
                if (holder.isPresent()) {
                    s.enchant(holder.get(), e.getValue());
                } else {
                    FactionClash.LOGGER.warn("[Heroes] {}: the enchantment \"{}\" does not exist; left out", heroId, e.getKey());
                }
            }
        }
        return mark(s, heroId);
    }

    /** The equipment slot of a kit item. */
    public static EquipmentSlot slot(HeroDef.GearSlot s) {
        return switch (s) {
            case MAINHAND -> EquipmentSlot.MAINHAND;
            case OFFHAND -> EquipmentSlot.OFFHAND;
            case HEAD -> EquipmentSlot.HEAD;
            case CHEST -> EquipmentSlot.CHEST;
            case LEGS -> EquipmentSlot.LEGS;
            case FEET -> EquipmentSlot.FEET;
        };
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
