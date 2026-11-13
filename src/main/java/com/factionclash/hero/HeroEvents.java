package com.factionclash.hero;

import com.factionclash.combat.CombatRules;
import com.factionclash.logic.hero.HeroDef.LineEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Map;

/**
 * Game-bus handlers for heroes: the definitions' reload listener ({@code /reload}), the registry at server start, one
 * entity per hero id, the revival timer, the {@code ally_fallen} line, and no stale speech bubble.
 */
public final class HeroEvents {
    /** The server definitions ({@code data/factionclash/heroes}) are read with the server's data, {@code /reload} too. */
    @SubscribeEvent
    public void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new HeroDefinitions());
    }

    /** The visual files and the config overrides are read again whenever a server starts (a singleplayer world, a dedicated server). */
    @SubscribeEvent
    public void onServerAboutToStart(ServerAboutToStartEvent event) {
        HeroRegistry.reload();
    }

    /**
     * A stale copy of a hero (another UUID than the campaign records) never joins the level; a speech bubble saved with
     * a chunk (the server stopped while a hero spoke) is dropped.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (event.getEntity() instanceof HeroEntity h && event.getLevel().getServer() != null
                && !HeroManager.mayJoin(event.getLevel().getServer(), h)) {
            event.setCanceled(true);
        } else if (event.loadedFromDisk() && event.getEntity() instanceof Display.TextDisplay d && d.getTags().contains(HeroEntity.BUBBLE_TAG)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % HeroManager.TICK_INTERVAL == 0) {
            HeroManager.tick(event.getServer());
        }
        HeroBossBars.tick(event.getServer()); // the boss bars and intro titles of fighting heroes (A6b)
        HeroSounds.tick(event.getServer()); // the companions that are due (S2)
    }

    /** No boss bar outlives its server (a singleplayer world closed and another opened). */
    @SubscribeEvent
    public void onServerStopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
        HeroBossBars.serverStopped();
        HeroSounds.serverStopped();
    }

    /** {@code ally_fallen}: heroes within 24 blocks of a member of their side who died (an NPC, a hero or a player). */
    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (!(dead.level() instanceof ServerLevel level) || dead instanceof HeroEntity h && h.isPreview()) {
            return;
        }
        if (dead instanceof com.factionclash.npc.FactionNpc n && n.isDisplayOnly()) {
            return;
        }
        double r = HeroEntity.ALLY_RADIUS;
        for (HeroEntity hero : level.getEntitiesOfClass(HeroEntity.class, dead.getBoundingBox().inflate(r),
                x -> x != dead && x.isAlive() && !x.isPreview() && x.distanceToSqr(dead) <= r * r)) {
            if (CombatRules.sameSide(hero, dead)) {
                hero.say(LineEvent.ALLY_FALLEN, Map.of("ally", HeroEntity.nameOf(dead)), null);
            }
        }
        if (dead instanceof HeroEntity) {
            // 6.10's hard trigger ally_hero_died: a fighting hero of its side within 32 blocks, killed by anyone
            double far = 32;
            for (HeroEntity hero : level.getEntitiesOfClass(HeroEntity.class, dead.getBoundingBox().inflate(far),
                    x -> x != dead && x.isAlive() && !x.isPreview() && x.distanceToSqr(dead) <= far * far)) {
                if (CombatRules.sameSide(hero, dead)) {
                    hero.combat().boss().hardTrigger(com.factionclash.logic.hero.HeroDef.EnrageTrigger.ALLY_HERO_DIED);
                }
            }
        }
    }
}
