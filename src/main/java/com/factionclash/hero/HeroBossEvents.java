package com.factionclash.hero;

import com.factionclash.campaign.BaseState;
import com.factionclash.campaign.CampaignData;
import com.factionclash.logic.hero.BossRules;
import com.factionclash.logic.hero.HeroDef;
import com.factionclash.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.VanillaGameEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;
import net.neoforged.neoforge.event.entity.player.CriticalHitEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.List;

/**
 * The boss controller's hooks on the game bus (task A6a; HEROES_V2 6.2, 6.9), each the same for a player and an NPC:
 * <ul>
 * <li>a hit on a hero, after armour, resistances and a counter's absorb (the lowest priority of the damage event):
 * the damage-taken window, the per-hit cap for a hit with any attacking entity and its poise
 * ({@link HeroBoss#incoming});</li>
 * <li>a shield that blocks a hero's heavy move 5-11 ticks after it went up parries it: 25 poise on the hero
 * ({@link HeroBoss#blocked}), whoever held the shield.</li>
 * </ul>
 * Task A6b adds the event weak points and the hard enrage triggers (6.9, 6.10), each caused alike by a player and an
 * NPC: a snowball hit and a thrown water bottle's splash (douse), a bell rung within 8 blocks by anyone (a player's
 * hand, a projectile, redstone, an NPC through the bell's own ring), a light of 12 or more placed within 4 blocks by an
 * enemy (a player's placement, an NPC's through {@link #npcPlaced}), a critical hit from above its eyes (a player's by
 * vanilla's critical-hit event, an NPC's or a hero's jump critical through {@link #npcCritical}), its home's heart
 * attacked (a player starting to break it, an NPC's breaker, {@link #heartAttacked}), a favoured hero of its side hurt,
 * and a base's heart lost ({@link #baseLost}: an escaping hero there goes at once).
 */
public final class HeroBossEvents {
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onDamage(LivingDamageEvent.Pre event) {
        if (event.getEntity() instanceof HeroEntity h && !h.level().isClientSide() && !h.isPreview() && h.isAlive()
                && event.getNewDamage() > 0) {
            float d = h.combat().boss().incoming(event.getSource(), event.getNewDamage());
            if (d != event.getNewDamage()) {
                event.setNewDamage(d);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onShieldBlock(LivingShieldBlockEvent event) {
        if (event.getBlocked() && !event.getEntity().level().isClientSide()
                && event.getDamageSource().is(HeroDamage.HERO)
                && event.getDamageSource().getEntity() instanceof HeroEntity h && h.isAlive()) {
            h.combat().boss().blocked(event.getEntity());
        }
    }

    // ------------------------------------------------------------------ the event weak points (6.9; task A6b)

    /** Heroes within {@code r} blocks of {@code at} that have a definition (a short list: the live heroes are few). */
    private static List<HeroEntity> heroesNear(Level level, Vec3 at, double r) {
        List<HeroEntity> live = HeroEntity.live();
        if (live.isEmpty()) {
            return List.of();
        }
        List<HeroEntity> out = new java.util.ArrayList<>(2);
        for (HeroEntity h : live) {
            if (h.level() == level && h.isAlive() && !h.isPreview() && h.position().distanceToSqr(at) <= r * r) {
                out.add(h);
            }
        }
        return out;
    }

    /** A snowball hit a hero, or a thrown water bottle broke near one: a douse, whoever threw it. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onProjectileImpact(ProjectileImpactEvent event) {
        if (event.isCanceled() || event.getProjectile().level().isClientSide() || HeroEntity.live().isEmpty()) {
            return;
        }
        Entity p = event.getProjectile();
        if (p instanceof Snowball && event.getRayTraceResult() instanceof EntityHitResult hit && hit.getEntity() instanceof HeroEntity h) {
            h.combat().boss().douse(BossRules.Douse.SNOWBALL);
        } else if (p instanceof ThrownPotion potion) {
            PotionContents c = potion.getItem().get(DataComponents.POTION_CONTENTS);
            if (c != null && c.is(Potions.WATER)) {
                for (HeroEntity h : heroesNear(p.level(), p.position(), BossRules.SPLASH_RADIUS)) {
                    h.combat().boss().douse(BossRules.Douse.WATER);
                }
            }
        }
    }

    /** A bell rang (its own ring, whoever or whatever rang it: a hand, a projectile, redstone, an NPC). */
    @SubscribeEvent
    public void onGameEvent(VanillaGameEvent event) {
        if (event.getVanillaEvent() != GameEvent.BLOCK_CHANGE || event.getLevel().isClientSide() || HeroEntity.live().isEmpty()) {
            return;
        }
        Vec3 at = event.getEventPosition();
        BlockPos pos = BlockPos.containing(at);
        if (!(event.getLevel().getBlockState(pos).getBlock() instanceof BellBlock)) {
            return;
        }
        for (HeroEntity h : heroesNear(event.getLevel(), Vec3.atCenterOf(pos), BossRules.BELL_RADIUS)) {
            h.combat().boss().bellRung(Math.sqrt(h.position().distanceToSqr(Vec3.atCenterOf(pos))));
        }
    }

    /** A player (or anything the game reports) placed a block: a light near a hero weakens it. */
    @SubscribeEvent
    public void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof Level level && !level.isClientSide()) {
            placed(level, event.getEntity(), event.getPos(), event.getPlacedBlock());
        }
    }

    /** The mod's own block placing by an NPC (torches): the same check as a player's placement. */
    public static void npcPlaced(Entity npc, BlockPos pos, BlockState state) {
        if (npc != null && !npc.level().isClientSide()) {
            placed(npc.level(), npc, pos, state);
        }
    }

    private static void placed(Level level, Entity placer, BlockPos pos, BlockState state) {
        if (placer == null || HeroEntity.live().isEmpty()) {
            return;
        }
        int light = state.getLightEmission(level, pos);
        if (light < BossRules.LIGHT_EMISSION_MIN) {
            return;
        }
        Vec3 c = Vec3.atCenterOf(pos);
        for (HeroEntity h : heroesNear(level, c, BossRules.LIGHT_RADIUS + 1)) {
            h.combat().boss().lightPlaced(placer, light, Math.sqrt(h.position().add(0, h.getBbHeight() / 2, 0).distanceToSqr(c)));
        }
    }

    /** A player's critical hit on a hero (vanilla's critical-hit event, before the blow lands). */
    @SubscribeEvent
    public void onCriticalHit(CriticalHitEvent event) {
        if (event.isCriticalHit() && event.getTarget() instanceof HeroEntity h && !h.level().isClientSide()) {
            h.combat().boss().criticalHit(event.getEntity());
        }
    }

    /** An NPC's or a hero's jump critical that landed on a hero (from {@code FactionNpc.doHurtTarget}). */
    public static void npcCritical(LivingEntity attacker, Entity target) {
        if (target instanceof HeroEntity h && !h.level().isClientSide()) {
            h.combat().boss().criticalHit(attacker);
        }
    }

    // ------------------------------------------------------------------ hard enrage triggers (6.10), a lost base (6.13)

    /** A player starts breaking a block: a faction heart is attacked. */
    @SubscribeEvent
    public void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getLevel() instanceof ServerLevel sl && event.getAction() == PlayerInteractEvent.LeftClickBlock.Action.START
                && sl.getBlockState(event.getPos()).is(ModBlocks.FACTION_HEART.get())) {
            heartAttacked(sl, event.getPos(), event.getEntity());
        }
    }

    /**
     * Someone (a player or an NPC raider's breaker) is breaking the heart at {@code pos}: the heroes of that base's
     * faction defending it (inside its walls and 16 blocks) with {@code heart_attacked} enrage at once.
     */
    public static void heartAttacked(ServerLevel level, BlockPos pos, LivingEntity attacker) {
        if (HeroEntity.live().isEmpty()) {
            return;
        }
        BaseState base = null;
        for (BaseState b : CampaignData.get(level.getServer()).bases.values()) {
            if (!b.destroyed && pos.equals(b.point(BaseState.HEART))) {
                base = b;
                break;
            }
        }
        if (base == null) {
            return;
        }
        for (HeroEntity h : HeroEntity.live()) {
            if (h.level() == level && h.isAlive() && h.getFaction() == base.faction && base.contains(h.blockPosition(), 16)
                    && (attacker == null || HeroForms.enemy(h, attacker))) {
                h.combat().boss().hardTrigger(HeroDef.EnrageTrigger.HEART_ATTACKED);
            }
        }
    }

    /** A base's heart fell: an escaping hero whose home it was goes at once (6.13, MOBILITY_AND_WORK 7.7). */
    public static void baseLost(ServerLevel level, BaseState base) {
        for (HeroEntity h : HeroEntity.live()) {
            if (h.isAlive() && !h.isPreview() && h.base() == base) {
                h.combat().boss().escape().lostBase(h.combat().boss().fighting());
            }
        }
    }

    /** A hero was hurt by an enemy: heroes of its side within 32 blocks that name it as a friend may enrage. */
    @SubscribeEvent
    public void onHeroHurt(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof HeroEntity victim) || victim.level().isClientSide() || event.getNewDamage() <= 0
                || !(event.getSource().getEntity() instanceof LivingEntity attacker) || HeroEntity.live().size() < 2) {
            return;
        }
        for (HeroEntity h : HeroEntity.live()) {
            if (h != victim && h.isAlive() && h.level() == victim.level() && h.distanceToSqr(victim) <= 32 * 32
                    && HeroForms.ally(h, victim) && HeroForms.enemy(h, attacker) && favours(h, victim)) {
                h.combat().boss().hardTrigger(HeroDef.EnrageTrigger.FAVOURED_HURT);
            }
        }
    }

    /** Whether {@code h} names {@code other} with a friendly bond (its favoured hero; its leader comes with L4). */
    private static boolean favours(HeroEntity h, HeroEntity other) {
        HeroDef d = h.dormant() ? null : h.definition();
        if (d == null || d.v2() == null) {
            return false;
        }
        for (HeroDef.Relationship r : d.v2().relationships()) {
            if (r.hero().equals(other.heroId()) && r.kind() != HeroDef.Bond.RIVAL && r.kind() != HeroDef.Bond.SWORN_ENEMY) {
                return true;
            }
        }
        return false;
    }
}
