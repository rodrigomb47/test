package com.factionclash.hero;

import com.factionclash.FactionClash;
import com.factionclash.campaign.CampaignData;
import com.factionclash.campaign.HeroState;
import com.factionclash.logic.hero.BossRules;
import com.factionclash.logic.hero.HeroRules;
import com.factionclash.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.List;

/**
 * What a hero leaves where it died (HEROES_V2 6.13; task A6b): only a death rewards anyone, and by one rule for a player
 * and an NPC side. The winning side is the side of the last person (a player, a faction NPC or a hero) that hurt it in
 * its last 15 s ({@link HeroRules#winningSide}); a monster's blow or a fall after it counts for that side. That side
 * gets XP orbs ({@code 7 x level}, at most 100) and the hero's crest (once per hero per winning side per in-game day),
 * dropped where it died: a player picks them up, the NPCs of a winning faction pick the orbs up as they pick up any
 * (into their faction's experience) and its guards carry the crest home with their loot. Its non-kit gear drops as any
 * NPC's does; its kit never drops. Killed with no person involved, it drops only that gear. An escape is no death and
 * never comes here: nothing drops for an escaped hero.
 */
public final class HeroRewards {
    private HeroRewards() {
    }

    /** At the hero's death (after vanilla's drops): the orbs and the crest for the winning side, if any. */
    static void onDeath(ServerLevel level, HeroEntity hero) {
        if (hero.isPreview() || hero.dormant()) {
            return;
        }
        long now = level.getGameTime();
        HeroBoss boss = hero.combat().boss();
        HeroRules.Side side = boss.winningSide(now);
        if (side == null) {
            return; // no person hurt it in its last 15 s: its gear only, as any NPC
        }
        int xp = HeroRules.rewardXp(hero.npcLevel());
        ExperienceOrb.award(level, hero.position(), xp);
        long day = BossRules.day(level.getDayTime());
        HeroState rec = hero.campaignRecord();
        boolean crest = rec == null || rec.crests.get(side.key()) == null || rec.crests.get(side.key()) != day;
        if (crest) {
            if (rec != null) {
                rec.crests.put(side.key(), day);
                CampaignData.get(level.getServer()).setDirty();
            }
            ItemEntity item = new ItemEntity(level, hero.getX(), hero.getY() + 0.5, hero.getZ(), crest(hero, boss.winnerName(), day));
            item.setDefaultPickUpDelay();
            level.addFreshEntity(item);
        }
        FactionClash.LOGGER.info("[Heroes] {} fell to {} ({}): {} XP{}", hero.heroId(), boss.winnerName(), side.key(), xp,
                crest ? " and its crest" : "");
    }

    /** The crest: "Crest of Name", its title, and who won it on which day. */
    public static ItemStack crest(HeroEntity hero, String winner, long day) {
        ItemStack s = new ItemStack(ModItems.HERO_CREST.get());
        s.set(DataComponents.CUSTOM_NAME, Component.literal("Crest of " + hero.npcName())
                .withStyle(st -> st.withItalic(false).withColor(ChatFormatting.GOLD)));
        String title = hero.entry() == null ? null : hero.entry().title();
        java.util.List<Component> lore = new java.util.ArrayList<>();
        if (title != null && !title.isBlank()) {
            lore.add(Component.literal(title).withStyle(st -> st.withItalic(true).withColor(ChatFormatting.GRAY)));
        }
        lore.add(Component.literal("Won by " + winner + " on day " + (day + 1)).withStyle(st -> st.withItalic(false)
                .withColor(ChatFormatting.DARK_GRAY)));
        s.set(DataComponents.LORE, new ItemLore(List.copyOf(lore)));
        return s;
    }
}
