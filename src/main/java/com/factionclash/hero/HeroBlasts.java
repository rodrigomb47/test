package com.factionclash.hero;

import com.factionclash.FactionClash;
import com.factionclash.campaign.BaseState;
import com.factionclash.campaign.BaseZones;
import com.factionclash.campaign.CampaignData;
import com.factionclash.campaign.Diplomacy;
import com.factionclash.config.FCConfig;
import com.factionclash.faction.FactionType;
import com.factionclash.logic.hero.HeroDef;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.level.ExplosionEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Explosive moves in the game (HEROES_V2 6.18, task T2). At each blast point of a move that is an explosion by nature
 * (a shot's, bolt's, volley's or barrage's impact, a nova's burst, a zone's first pulse, each rain impact, a leap's or a
 * dive's landing; at most {@link HeroDef.Explosive#blasts} a use) the hero sets off a vanilla explosion of the move's
 * power ({@code Level.explode} with interaction {@code MOB}): blocks break by vanilla's rules and resistances
 * (obsidian, bedrock and their like never; water and lava soak a blast up; TNT in reach is lit), drops follow
 * {@code mobExplosionDropDecay}, the game rule {@code mobGriefing} (NeoForge's per-entity check) keeps every block
 * whole, and so does {@code heroes.explosiveBlocks} false. With {@code fire} (a fire move only), vanilla's explosion
 * fire, only where blocks may break.
 *
 * <p>The blast is terrain only: its {@link #CALCULATOR} hurts and pushes nobody, and while it runs {@link #onDetonate}
 * empties the explosion's entity list, so no entity is hurt, pushed or told it was hit (dropped items, end crystals,
 * creepers and players alike). The move's own effects do every hurting and pushing, under every cap; nothing here reads
 * whether anyone is a player. The particles and the sound are vanilla's, by id.
 *
 * <p>The own-side check of the pick ({@link #ownSideDistance}): the distance to the nearest block of the hero's own
 * side, its faction's bases (the walls box at any height, and the box round each base's planned buildings).
 */
public final class HeroBlasts {
    private HeroBlasts() {
    }

    /** Hurts and pushes no entity; blocks break by vanilla's resistances (the default rules). */
    public static final ExplosionDamageCalculator CALCULATOR = new ExplosionDamageCalculator() {
        @Override
        public boolean shouldDamageEntity(Explosion explosion, Entity entity) {
            return false;
        }

        @Override
        public float getKnockbackMultiplier(Entity entity) {
            return 0F;
        }
    };

    /** The hero whose blast is going off now (the detonation empties that explosion's entities), or null. */
    private static Entity blasting;

    /** One blast set off: the hero, its move, where, its power, its fire and whether it could break blocks. */
    public record Blast(int hero, String move, double x, double y, double z, int power, boolean fire, boolean blocks,
                        long at) {
    }

    private static final int RECENT_MAX = 64;
    private static final ArrayDeque<Blast> RECENT = new ArrayDeque<>();

    /** The last blasts on this server, oldest first (a copy; tests and the combat log). */
    public static synchronized List<Blast> recent() {
        return new ArrayList<>(RECENT);
    }

    private static synchronized void log(Blast b) {
        if (RECENT.size() >= RECENT_MAX) {
            RECENT.removeFirst();
        }
        RECENT.addLast(b);
    }

    /**
     * One blast of {@code use}'s move at {@code at}, if the move is explosive and the use has blasts left (6.18: at most
     * its {@code blasts}, never more than 6). True when it went off.
     */
    public static boolean blast(HeroUse use, Vec3 at) {
        if (use == null || use.move == null || use.move.explosive() == null || at == null) {
            return false;
        }
        HeroDef.Explosive e = use.move.explosive();
        if (use.blasts >= e.blasts() || !use.hero.isAlive()) {
            return false;
        }
        use.blasts++;
        return blastAt(use.hero, use.move.id(), e.power(), e.fire(), at) != null;
    }

    /**
     * A blast of {@code power} at {@code at}, set off by {@code hero} as its move {@code move}'s (6.18): vanilla's
     * explosion with interaction {@code MOB} (or {@code NONE} with {@code heroes.explosiveBlocks} false), the
     * {@link #CALCULATOR}, fire only with {@code fire} where blocks may break. Null off the server.
     */
    public static Explosion blastAt(HeroEntity hero, String move, int power, boolean fire, Vec3 at) {
        if (!(hero.level() instanceof ServerLevel level) || power <= 0) {
            return null;
        }
        boolean blocks = FCConfig.HERO_EXPLOSIVE_BLOCKS.get();
        Level.ExplosionInteraction how = blocks ? Level.ExplosionInteraction.MOB : Level.ExplosionInteraction.NONE;
        boolean burn = fire && blocks && EventHooks.canEntityGrief(level, hero);
        Explosion x;
        Entity was = blasting;
        blasting = hero;
        try {
            x = level.explode(hero, null, CALCULATOR, at.x, at.y, at.z, power, burn, how);
        } finally {
            blasting = was;
        }
        log(new Blast(hero.getId(), move, at.x, at.y, at.z, power, burn, x.interactsWithBlocks(), level.getGameTime()));
        if (HeroCombatLog.ON) {
            FactionClash.LOGGER.info("[Heroes] combat {} blasts {} at {} {} {}: power {}, blocks {}", hero.heroId(), move,
                    Math.round(at.x), Math.round(at.y), Math.round(at.z), power, x.interactsWithBlocks());
        }
        return x;
    }

    /**
     * The detonation of a hero's blast (registered on the game bus): no entity is in it (no damage, no push, no
     * "hit by an explosion" for anyone), and an explosion that keeps blocks names none (so no repair is queued for
     * blocks that stand).
     */
    public static final class Events {
        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public void onDetonate(ExplosionEvent.Detonate event) {
            Entity h = blasting;
            if (h == null || event.getExplosion().getDirectSourceEntity() != h) {
                return;
            }
            event.getAffectedEntities().clear();
            if (!event.getExplosion().interactsWithBlocks()) {
                event.getAffectedBlocks().clear();
            }
        }
    }

    // ------------------------------------------------------------------ the own-side check (6.18)

    /** Bases farther than this from a point are not looked at (no blast reaches so far). */
    public static final double OWN_SIDE_LOOK = 24;

    /**
     * The horizontal distance from {@code (x, z)} to the nearest block of {@code hero}'s own side: anything inside
     * the walls box of a standing base of its faction, or the box round that base's planned buildings
     * (6.18). Infinite when none is within {@link #OWN_SIDE_LOOK}, the hero has no faction, or it is not in the overworld,
     * where every base stands.
     */
    public static double ownSideDistance(HeroEntity hero, double x, double z) {
        FactionType f = hero.getFaction();
        if (f == null || hero.getServer() == null || hero.level().dimension() != Level.OVERWORLD) {
            return Double.POSITIVE_INFINITY; // every base stands in the overworld (review of T2: not at its coordinates elsewhere)
        }
        CampaignData data = CampaignData.get(hero.getServer());
        double best = Double.POSITIVE_INFINITY;
        for (BaseState b : data.bases.values()) {
            if (b.destroyed || b.center == null || !Diplomacy.allied(f, b.faction)) {
                continue;
            }
            int half = BaseZones.half(b);
            double d = boxDistance(x, z, b.center.getX() - half, b.center.getZ() - half, b.center.getX() + half,
                    b.center.getZ() + half);
            if (d > OWN_SIDE_LOOK + 64) {
                continue; // far away: its planned buildings are near its walls
            }
            best = Math.min(best, d);
            int[] box = b.planBox();
            if (box != null) {
                best = Math.min(best, boxDistance(x, z, box[0], box[1], box[2], box[3]));
            }
        }
        return best > OWN_SIDE_LOOK ? Double.POSITIVE_INFINITY : best;
    }

    /** From a point to a box of whole blocks, min to max inclusive (0 inside). */
    static double boxDistance(double x, double z, int minX, int minZ, int maxX, int maxZ) {
        double dx = x < minX ? minX - x : x > maxX + 1 ? x - (maxX + 1) : 0;
        double dz = z < minZ ? minZ - z : z > maxZ + 1 ? z - (maxZ + 1) : 0;
        return Math.sqrt(dx * dx + dz * dz);
    }
}
