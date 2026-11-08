package com.factionclash.combat;

import com.factionclash.campaign.CampaignData;
import com.factionclash.faction.FactionType;
import com.factionclash.npc.FactionNpc;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.AbstractIllager;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;

/** Shared combat predicates. */
public final class CombatRules {
    private CombatRules() {
    }

    /** Hostile monsters guards should engage. Neutral mobs only count while they are actually aggressive. */
    public static boolean isHostileMonster(LivingEntity e) {
        if (!(e instanceof Enemy) || !e.isAlive()) {
            return false;
        }
        if (e instanceof EnderMan || e instanceof ZombifiedPiglin || e instanceof AbstractPiglin) {
            return e instanceof net.minecraft.world.entity.Mob m && m.getTarget() instanceof FactionNpc;
        }
        if (e instanceof Spider s) {
            return s.getTarget() != null || s.getLightLevelDependentMagicValue() < 0.5F;
        }
        return true;
    }

    /** Vanilla hostiles that should hunt faction NPCs as they hunt players. */
    public static boolean huntsNpcs(Entity e) {
        return (e instanceof Zombie && !(e instanceof ZombifiedPiglin)) || e instanceof AbstractSkeleton || e instanceof Creeper
                || e instanceof Spider || e instanceof Witch || e instanceof AbstractIllager;
    }

    /** Zombies, skeletons and their variants deal half damage to NPCs (spec 14). */
    public static boolean isUndeadHalfDamageSource(Entity attacker) {
        return attacker != null && (attacker.getType().is(EntityTypeTags.ZOMBIES) || attacker.getType().is(EntityTypeTags.SKELETONS));
    }

    public static FactionType factionOf(Entity e) {
        if (e instanceof FactionNpc n) {
            return n.getFaction();
        }
        if (e instanceof ServerPlayer p) {
            return CampaignData.get(p.server).players.get(p.getUUID());
        }
        return null;
    }

    public static boolean isMember(ServerPlayer p, FactionType f) {
        return CampaignData.get(p.server).players.get(p.getUUID()) == f;
    }

    /** Same faction, or allied by a pact (no friendly fire, no retaliation, no alarms between them). */
    public static boolean sameSide(Entity a, Entity b) {
        FactionType fa = factionOf(a), fb = factionOf(b);
        return fa != null && fb != null && (fa == fb || com.factionclash.campaign.Diplomacy.allied(a.getServer(), fa, fb));
    }

    public static boolean isEnemyPlayer(FactionNpc npc, ServerPlayer p) {
        if (p.isCreative() || p.isSpectator()) {
            return false;
        }
        FactionType f = CampaignData.get(p.server).players.get(p.getUUID());
        return com.factionclash.campaign.Diplomacy.hostile(p.server, f, npc.getFaction());
    }
}
