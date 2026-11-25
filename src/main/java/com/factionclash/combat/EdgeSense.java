package com.factionclash.combat;

import com.factionclash.hero.HeroEntity;
import com.factionclash.logic.combat.EdgeSafety;
import com.factionclash.npc.FactionNpc;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.monster.Zoglin;
import net.minecraft.world.entity.monster.breeze.Breeze;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * The edge sense of one faction NPC or hero, in the game ({@code docs/design/breaching.md} 8.1, task T3; pure rules in
 * {@link EdgeSafety}): while it fights, the enemies within 8 blocks that can push are read twice a second, and a cell
 * with a lethal edge within the reach behind it (6 blocks with a hero among them, else 4) costs its paths
 * {@link EdgeSafety#PATH_MALUS} more ({@code npc/NpcPathNavigation}) and is skipped as a waiting or back-off spot. A
 * pusher is a pusher whoever it is: a player with a Knockback sword is one exactly as an NPC with the same sword is. The
 * columns read are cached until the next reading, so a path search reads each one once.
 */
public final class EdgeSense {
    /** The pushers are read again this often while fighting (ticks). */
    public static final int READ_TICKS = 10;

    private final FactionNpc npc;
    private long readAt = Long.MIN_VALUE / 4;
    private final double[] px = new double[EdgeSafety.MAX_PUSHERS], pz = new double[EdgeSafety.MAX_PUSHERS];
    private final double[] ranks = new double[EdgeSafety.MAX_PUSHERS];
    private int n;
    private boolean hero, fireproof;
    private final Long2ByteOpenHashMap columns = new Long2ByteOpenHashMap();
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    private final int[] drop = new int[1];
    private final EdgeSafety.Columns reader = this::column;

    public EdgeSense(FactionNpc npc) {
        this.npc = npc;
    }

    /** Whether a pusher is near now (it reads them again when its last reading is half a second old). */
    public boolean active() {
        long now = npc.level().getGameTime();
        if (now - readAt >= READ_TICKS || now < readAt) {
            read(now);
        }
        return n > 0;
    }

    /** Whether a hero is among the pushers near (the reach is then 6 blocks). */
    public boolean heroNear() {
        return n > 0 && hero;
    }

    /** The pushers near at the last reading whose lines are read (at most {@link EdgeSafety#MAX_PUSHERS}). */
    public int pushers() {
        return n;
    }

    /** The horizontal position of the {@code i}th pusher read (tests). */
    public double pusherX(int i) {
        return px[i];
    }

    public double pusherZ(int i) {
        return pz[i];
    }

    private void read(long now) {
        readAt = now;
        n = 0;
        hero = false;
        columns.clear();
        fireproof = TerrainEdges.fireproof(npc);
        LivingEntity t = npc.getTarget();
        if (t == null || !t.isAlive() || npc.level().isClientSide) {
            return;
        }
        double r = EdgeSafety.PUSHER_RANGE;
        // every pusher counts for the reach (a hero among them: 6 blocks); the lines are read from the best four, heroes
        // first, then the nearest (the review of T3: the first four the level listed were kept before)
        for (LivingEntity e : npc.level().getEntitiesOfClass(LivingEntity.class, npc.getBoundingBox().inflate(r),
                e -> e != npc && e.isAlive() && e.distanceToSqr(npc) <= r * r && enemy(e) && pusher(e))) {
            boolean h = e instanceof HeroEntity;
            hero |= h;
            n = EdgeSafety.keep(px, pz, ranks, n, e.getX(), e.getZ(), EdgeSafety.rank(e.distanceToSqr(npc), h));
        }
    }

    /** An enemy of this NPC: its target, one that targets it, a hostile faction's NPC or player, a hostile monster. */
    private boolean enemy(LivingEntity e) {
        if (CombatRules.sameSide(npc, e) || e instanceof FactionNpc o && o.isDisplayOnly()) {
            return false;
        }
        if (e == npc.getTarget() || e instanceof Mob m && m.getTarget() == npc) {
            return true;
        }
        if (e instanceof FactionNpc o) {
            return com.factionclash.campaign.Diplomacy.hostile(npc.getFaction(), o.getFaction());
        }
        if (e instanceof ServerPlayer p) {
            return CombatRules.isEnemyPlayer(npc, p);
        }
        return CombatRules.isHostileMonster(e);
    }

    /**
     * {@link EdgeSafety#pusher} read from an entity: any hero; a weapon with Knockback or a bow with Punch in either
     * hand; a ravager, hoglin, zoglin, iron golem or breeze; wind charges in either hand.
     */
    public static boolean pusher(LivingEntity e) {
        ItemStack main = e.getMainHandItem(), off = e.getOffhandItem();
        boolean pushingMob = e instanceof Ravager || e instanceof Hoglin || e instanceof Zoglin || e instanceof IronGolem
                || e instanceof Breeze;
        boolean wind = main.is(Items.WIND_CHARGE) || off.is(Items.WIND_CHARGE);
        int kb = 0, punch = 0;
        if (!(e instanceof HeroEntity) && !pushingMob && !wind) {
            var reg = e.level().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
            Holder<Enchantment> knock = reg.getHolderOrThrow(Enchantments.KNOCKBACK);
            Holder<Enchantment> pun = reg.getHolderOrThrow(Enchantments.PUNCH);
            kb = Math.max(main.getEnchantmentLevel(knock), off.getEnchantmentLevel(knock));
            punch = main.getItem() instanceof BowItem ? main.getEnchantmentLevel(pun)
                    : off.getItem() instanceof BowItem ? off.getEnchantmentLevel(pun) : 0;
        }
        return EdgeSafety.pusher(e instanceof HeroEntity, kb, punch, pushingMob, wind);
    }

    /**
     * The extra path cost of the cell whose feet are at (x, y, z): {@link EdgeSafety#PATH_MALUS} when a lethal edge lies
     * within the reach behind it from a pusher near, else 0 (and 0 with no pusher near).
     */
    public float malus(int x, int y, int z) {
        if (n == 0) {
            return 0;
        }
        return EdgeSafety.malus(EdgeSafety.unsafe(x + 0.5, y, z + 0.5, px, pz, n, hero, reader));
    }

    /** Whether standing at {@code p} (feet) would put a lethal edge at its back with a pusher near (a spot to skip). */
    public boolean unsafeAt(BlockPos p) {
        return active() && malus(p.getX(), p.getY(), p.getZ()) > 0;
    }

    private int column(int x, int y, int z) {
        long k = BlockPos.asLong(x, y, z);
        byte b = columns.get(k);
        if (b != 0) {
            return b - 1;
        }
        var g = TerrainEdges.ground(npc.level(), x, y, z, cursor, drop);
        int c = EdgeSafety.column(g, drop[0], fireproof);
        columns.put(k, (byte) (c + 1));
        return c;
    }
}
