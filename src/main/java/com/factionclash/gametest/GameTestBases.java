package com.factionclash.gametest;

import com.factionclash.campaign.BaseState;
import com.factionclash.campaign.CampaignData;
import com.factionclash.campaign.Recruitment;
import com.factionclash.campaign.WorkplaceState;
import com.factionclash.faction.FactionType;
import com.factionclash.npc.FactionNpc;
import com.factionclash.npc.NpcRole;
import com.factionclash.registry.ModEntities;
import com.factionclash.storage.StorageCategory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Small registered bases for GameTests whose workers must find their home base. A test base sits on the platform
 * (centre at relative (12, 2, 12)) with a tiny walls box and work radius, so the jobs' searches never leave the
 * platform, and it is put into the campaign data like a real one. Every test that registers one must clean it up:
 * {@link Handle#cleanup} removes the base, its workplaces and the roster entries of the NPCs registered through it.
 * It runs on {@link Handle#succeed}, {@link Handle#fail}, when a {@link Handle#guard guarded} check throws, and,
 * scheduled two ticks before the timeout, when the test runs out of time.
 */
public final class GameTestBases {
    /** Test base ids start here, clear of real bases and of the older fakeBase ids (900000-909999). */
    private static final int FIRST_ID = 990100;
    private static final AtomicInteger NEXT = new AtomicInteger();

    private GameTestBases() {
    }

    /** A test base with the storehouse stand at relative (4, 2, 4). */
    public static Handle register(GameTestHelper h, FactionType faction, int half, int radius, int timeoutTicks) {
        return register(h, faction, half, radius, timeoutTicks, new BlockPos(4, 2, 4));
    }

    /**
     * Registers a settlement for {@code faction} centred at relative (12, 2, 12): walls half-size {@code half}
     * ({@link BaseState#testHalf}), work radius {@code radius} ({@link BaseState#testRadius}), MIN/MAX one block
     * outside the walls (y -1 to +8), the storehouse stand at {@code storageStand} (relative) and the four storage
     * chests of the older fakeBase at relative (2..5, 2, 2).
     */
    public static Handle register(GameTestHelper h, FactionType faction, int half, int radius, int timeoutTicks, BlockPos storageStand) {
        CampaignData data = CampaignData.get(h.getLevel().getServer());
        BaseState b = new BaseState();
        b.id = FIRST_ID + NEXT.getAndIncrement();
        b.faction = faction;
        b.name = "Test base " + b.id;
        b.center = h.absolutePos(new BlockPos(12, 2, 12));
        b.points.put(BaseState.SPAWN, b.center);
        b.points.put(BaseState.STORAGE_STAND, h.absolutePos(storageStand));
        b.points.put(BaseState.MIN, b.center.offset(-(half + 1), -1, -(half + 1)));
        b.points.put(BaseState.MAX, b.center.offset(half + 1, 8, half + 1));
        b.testHalf = half;
        b.testRadius = radius;
        for (int i = 0; i < 4; i++) {
            BlockPos p = new BlockPos(2 + i, 2, 2);
            h.setBlock(p, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH));
            b.storage(i == 0 ? StorageCategory.FOOD : StorageCategory.GENERAL).add(h.absolutePos(p));
        }
        data.bases.put(b.id, b);
        Handle handle = new Handle(h, data, b);
        if (timeoutTicks > 2) {
            h.runAfterDelay(timeoutTicks - 2, handle::cleanup); // a timeout must not leave a phantom base behind
        }
        return handle;
    }

    /** A registered test base and what was added for it. */
    public static final class Handle {
        public final GameTestHelper helper;
        public final CampaignData data;
        public final BaseState base;
        private final List<FactionNpc> npcs = new ArrayList<>();
        private boolean cleaned;

        private Handle(GameTestHelper helper, CampaignData data, BaseState base) {
            this.helper = helper;
            this.data = data;
            this.base = base;
        }

        /** Spawns an NPC of the base's faction at relative (x, 2, z) and registers it as a member of the base. */
        public FactionNpc npc(int x, int z, NpcRole role) {
            FactionNpc n = helper.spawn(ModEntities.FACTION_NPC.get(), new BlockPos(x, 2, z));
            n.faction(base.faction);
            n.role(role);
            register(n);
            return n;
        }

        /** Makes an NPC a member of the base, with a roster entry that cleanup removes again. */
        public FactionNpc register(FactionNpc n) {
            n.setBaseId(base.id);
            Recruitment.register(data, n, base);
            npcs.add(n);
            return n;
        }

        /** Adds a workplace to the base with a fresh id (removed again by cleanup). */
        public WorkplaceState addWorkplace(WorkplaceState w) {
            if (w.origin == null) {
                w.origin = base.center;
            }
            return data.addWorkplace(base, w);
        }

        /** A plain workplace of this type at {@code origin} (absolute), facing east, added to the base. */
        public WorkplaceState addWorkplace(WorkplaceState.Type type, BlockPos origin) {
            WorkplaceState w = new WorkplaceState();
            w.type = type;
            w.origin = origin;
            w.extent = origin;
            return addWorkplace(w);
        }

        /** Wraps a check so that a failing assertion also cleans up before the test fails. */
        public Runnable guard(Runnable check) {
            return () -> {
                try {
                    check.run();
                } catch (RuntimeException | Error e) {
                    cleanup();
                    throw e;
                }
            };
        }

        /** Runs a guarded check every tick until the test ends. */
        public void onEachTick(Runnable check) {
            helper.onEachTick(guard(check));
        }

        /** Asserts like {@link GameTestHelper#assertTrue}, cleaning up first when it fails. */
        public void assertTrue(boolean condition, String message) {
            if (!condition) {
                fail(message);
            }
        }

        public void succeed() {
            cleanup();
            helper.succeed();
        }

        public void fail(String message) {
            cleanup();
            helper.fail(message);
        }

        /**
         * Removes the base from the campaign, every workplace it owns, and the roster entries and entities of the NPCs
         * registered through this handle. Safe to call more than once.
         */
        public void cleanup() {
            if (cleaned) {
                return;
            }
            cleaned = true;
            for (int id : List.copyOf(base.workplaces)) {
                data.workplaces.remove(id);
            }
            base.workplaces.clear();
            data.bases.remove(base.id);
            var roster = data.faction(base.faction).roster;
            for (FactionNpc n : npcs) {
                UUID u = n.getUUID();
                roster.remove(u);
                n.discard();
            }
            npcs.clear();
            data.setDirty();
        }
    }
}
