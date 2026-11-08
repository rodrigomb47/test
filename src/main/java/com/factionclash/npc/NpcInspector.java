package com.factionclash.npc;

import com.factionclash.campaign.CampaignData;
import com.factionclash.logic.Affix;
import com.factionclash.logic.AffixRoller;
import com.factionclash.work.Inv;
import com.factionclash.work.Job;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** NPC inspection and player-supplied equipment (spec 19, spec 9). */
public final class NpcInspector {
    private NpcInspector() {
    }

    public static List<String> lines(FactionNpc npc, boolean full) {
        List<String> out = new ArrayList<>();
        out.add(npc.npcName() + " - " + npc.getRole().displayName + " of " + npc.getFaction().displayName
                + " (Lv " + npc.npcLevel() + ", " + Math.round(npc.getHealth()) + "/" + Math.round(npc.getMaxHealth()) + " HP)");
        if (!full) {
            return out;
        }
        out.add("Status: " + npc.status());
        Job job = npc.job();
        if (job != null) {
            out.add("Workplace: " + job.workplaceInfo());
            BlockPos d = job.destination();
            out.add("Destination: " + (d == null ? "none" : d.toShortString()));
        }
        if (com.factionclash.config.FCConfig.HUNGER.get()) {
            var h = npc.hunger();
            out.add(String.format("Food %d/20, saturation %.1f%s", h.foodLevel(), h.saturation(),
                    h.foodLevel() <= 6 ? " - hungry" : h.foodLevel() < 18 ? " - not healing" : ""));
        }
        StringBuilder eq = new StringBuilder("Equipment: ");
        eq.append(npc.getMainHandItem().isEmpty() ? "bare hands" : npc.getMainHandItem().getHoverName().getString() + durability(npc.getMainHandItem()));
        for (EquipmentSlot s : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack a = npc.getItemBySlot(s);
            if (!a.isEmpty()) {
                eq.append(", ").append(a.getHoverName().getString()).append(durability(a));
            }
        }
        if (!npc.ammo().isEmpty()) {
            eq.append(", arrow: ").append(npc.ammo().getHoverName().getString());
        }
        out.add(eq.toString());
        Map<String, Integer> inv = new LinkedHashMap<>();
        for (int i = 0; i < npc.inventory().getContainerSize(); i++) {
            ItemStack s = npc.inventory().getItem(i);
            if (!s.isEmpty()) {
                inv.merge(s.getHoverName().getString(), s.getCount(), Integer::sum);
            }
        }
        out.add("Backpack (" + Inv.freeSlots(npc.inventory()) + " free slots): " + (inv.isEmpty() ? "empty" : inv.toString()));
        out.add("Experience: " + npc.xp() + "/" + AffixRoller.xpToNext(npc.npcLevel()) + " to next level");
        if (!npc.affixes().isEmpty()) {
            StringBuilder af = new StringBuilder("Affixes: ");
            npc.affixes().forEach((a, n) -> af.append(a.displayName).append(" x").append(n).append("/").append(a.maxStacks).append(", "));
            out.add(af.toString());
        }
        return out;
    }

    public static void inspect(FactionNpc npc, ServerPlayer p) {
        boolean member = CampaignData.get(p.server).players.get(p.getUUID()) == npc.getFaction() || p.hasPermissions(2);
        List<String> lines = lines(npc, member);
        if (!member) {
            p.sendSystemMessage(Component.literal(lines.get(0)).withStyle(npc.getFaction().color));
            return;
        }
        List<Integer> colors = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            String l = lines.get(i);
            colors.add(l.startsWith("Status") ? 0xFFFFFF : l.startsWith("Affixes") ? 0xFFD54F : l.startsWith("Workplace") ? 0x80DEEA : 0xC8CCD0);
        }
        Integer c = npc.getFaction().color.getColor();
        com.factionclash.net.FCNetwork.sendInfo(p, lines.get(0), c == null ? 0xFFFFFF : c, new ArrayList<>(lines.subList(1, lines.size())), colors);
    }

    /** A member can hand an NPC better gear; the replaced item disappears (no return-and-resell loop). */
    public static boolean tryGiveEquipment(FactionNpc npc, ServerPlayer p, ItemStack held) {
        NpcRole role = npc.getRole();
        if (role.ranged && ToolKind.isArrow(held)) {
            npc.setAmmo(held);
            held.shrink(1);
            p.sendSystemMessage(Component.literal(npc.npcName() + " now shoots " + npc.ammo().getHoverName().getString()));
            return true;
        }
        if (role.tool != ToolKind.NONE && role.tool.matches(held)) {
            int cur = role.tool.matches(npc.tool()) ? ToolKind.tierOf(npc.tool()) : -1;
            if (ToolKind.tierOf(held) > cur) {
                npc.equip(held);
                held.shrink(1);
                p.sendSystemMessage(Component.literal(npc.npcName() + " equipped " + npc.tool().getHoverName().getString()));
                return true;
            }
        }
        if (held.getItem() instanceof ArmorItem armor) {
            ItemStack cur = npc.getItemBySlot(armor.getEquipmentSlot());
            int curDef = cur.getItem() instanceof ArmorItem a ? a.getDefense() : -1;
            if (armor.getDefense() > curDef) {
                npc.equip(held);
                held.shrink(1);
                p.sendSystemMessage(Component.literal(npc.npcName() + " put on " + npc.getItemBySlot(armor.getEquipmentSlot()).getHoverName().getString()));
                return true;
            }
        }
        return false;
    }

    private static String durability(ItemStack s) {
        return s.isDamageableItem() ? " (" + (s.getMaxDamage() - s.getDamageValue()) + "/" + s.getMaxDamage() + ")" : "";
    }
}
