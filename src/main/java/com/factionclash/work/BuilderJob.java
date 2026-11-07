package com.factionclash.work;

import com.factionclash.campaign.BaseState;
import com.factionclash.campaign.Project;
import com.factionclash.campaign.StructureRecord;
import com.factionclash.logic.Affix;
import com.factionclash.npc.FactionNpc;
import com.factionclash.registry.ModBlocks;
import com.factionclash.storage.FactionStorage;
import com.factionclash.storage.StorageCategory;
import com.factionclash.structure.Blueprint;
import com.factionclash.structure.ProjectLayouts;
import com.factionclash.structure.Terrain;
import com.factionclash.util.Messages;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builder (spec 11). Repairs damaged plan blocks first, then works queued projects: real materials are carried
 * from storage to the physical staging chest (its sign lists what is still needed), then from the staging chest
 * to the site, where blocks are placed bottom-up from the saved plan. Doorways stay open until the end.
 */
public class BuilderJob extends Job {
    enum State { CHECK, REPAIR_FETCH, REPAIR, GATHER, CRAFT, DELIVER, FETCH, BUILD }

    private State state = State.CHECK;
    private final List<Long> repairBatch = new ArrayList<>();
    private int timer;
    private int idleRounds;
    private String blocker = "";
    private Blueprint planCache;
    private int planCacheId = -1;

    public BuilderJob(FactionNpc npc) {
        super(npc);
    }

    private Project activeProject() {
        BaseState b = base();
        if (b == null) {
            return null;
        }
        for (Project p : b.projects) {
            if (p.status != Project.Status.DONE) {
                return p;
            }
        }
        return null;
    }

    private Blueprint plan(Project p) {
        if (planCache == null || planCacheId != p.id) {
            planCache = ProjectLayouts.plan(p, base());
            planCacheId = p.id;
        }
        return planCache;
    }

    @Override
    public String workplaceInfo() {
        Project p = activeProject();
        BaseState b = base();
        String rep = b == null ? "" : b.pendingRepairs.size() + " repairs pending";
        return p == null ? "No active project; " + rep : p.type.label + " #" + p.id + " (" + p.status + ", " + p.placed + "/"
                + plan(p).blocks.size() + "); " + rep + (blocker.isEmpty() ? "" : "; blocked by: " + blocker);
    }

    @Override
    protected int keep(ItemStack s) {
        return 0;
    }

    @Override
    protected void run() {
        BaseState b = base();
        if (b == null) {
            status("No home base", "Idle");
            waitFor(400);
            return;
        }
        switch (state) {
            case CHECK -> check(b);
            case REPAIR_FETCH -> repairFetch(b);
            case REPAIR -> repair(b);
            case GATHER -> gather(b);
            case CRAFT -> craft(b);
            case DELIVER -> deliver(b);
            case FETCH -> fetch(b);
            case BUILD -> build(b);
        }
    }

    private void check(BaseState b) {
        if (!b.pendingRepairs.isEmpty()) {
            state = State.REPAIR_FETCH;
            return;
        }
        Project p = activeProject();
        if (p == null) {
            if (Inv.totalItems(npc.inventory()) > 0) {
                if (atStorage()) {
                    unloadNow();
                    upgradeEquipment();
                }
                return;
            }
            blocker = "";
            status("No construction needed", "Idle");
            BlockPos staging = b.point(BaseState.STAGING);
            if (staging != null && !com.factionclash.npc.NpcNavigator.within(npc, staging, 4)) {
                goTo(staging, 3.0, "the construction yard");
                return;
            }
            waitFor(200);
            return;
        }
        if (p.status == Project.Status.GATHERING) {
            Map<String, Integer> missing = missing(p, true);
            updateSign(b, p, missing);
            if (missing.isEmpty()) {
                p.status = Project.Status.BUILDING;
                data().setDirty();
                state = State.FETCH;
            } else {
                state = State.GATHER;
            }
        } else {
            state = State.FETCH;
        }
    }

    // ---------------------------------------------------------------- repairs

    private void repairFetch(BaseState b) {
        if (!atStorage()) {
            return;
        }
        unloadNow();
        repairBatch.clear();
        Map<String, Integer> wanted = new LinkedHashMap<>();
        Map<String, MaterialRules.Need> needs = new LinkedHashMap<>();
        for (long l : List.copyOf(b.pendingRepairs)) {
            if (repairBatch.size() >= 24) {
                break;
            }
            BlockState planned = plannedAt(b, BlockPos.of(l));
            if (planned == null || b.playerEdits.contains(l)) {
                b.pendingRepairs.remove(l);
                continue;
            }
            MaterialRules.Need n = MaterialRules.need(planned);
            repairBatch.add(l);
            if (n.count() > 0) {
                wanted.merge(n.key(), n.count(), Integer::sum);
                needs.put(n.key(), n);
            }
        }
        boolean gotAny = wanted.isEmpty();
        for (Map.Entry<String, Integer> e : wanted.entrySet()) {
            MaterialRules.Need n = needs.get(e.getKey());
            int got = takeFromStorage(s -> s.is(n.exact()), e.getValue());
            if (got < e.getValue()) {
                got += takeFromStorage(MaterialRules.accepts(n), e.getValue() - got);
            }
            if (got < e.getValue() && n.group() == MaterialRules.Group.PLANKS) {
                takeFromStorage(s -> s.is(ItemTags.LOGS), (e.getValue() - got + 3) / 4);
                Crafting.ensurePlanks(npc.inventory(), e.getValue());
                got = Inv.count(npc.inventory(), MaterialRules.accepts(n));
            }
            gotAny |= got > 0;
        }
        if (!gotAny) {
            blocker = "materials for repairs (" + String.join(", ", needs.values().stream().map(MaterialRules.Need::label).toList()) + ")";
            status("Repairs waiting for " + blocker, "Waiting");
            // repairs that cannot be funded do not block funded projects forever
            if (activeProject() != null) {
                state = State.GATHER;
            } else {
                waitFor(600);
            }
            return;
        }
        state = State.REPAIR;
    }

    private BlockState plannedAt(BaseState b, BlockPos pos) {
        long key = pos.asLong();
        for (StructureRecord r : b.structures) {
            for (int i = 0; i < r.size(); i++) {
                if (r.positions[i] == key) {
                    return r.state(i);
                }
            }
        }
        return null;
    }

    private void repair(BaseState b) {
        if (repairBatch.isEmpty()) {
            state = State.CHECK;
            return;
        }
        BlockPos pos = BlockPos.of(repairBatch.get(0));
        BlockState planned = plannedAt(b, pos);
        BlockState cur = level().getBlockState(pos);
        if (planned == null || cur.equals(planned) || b.playerEdits.contains(pos.asLong())
                || !(cur.isAir() || cur.canBeReplaced() || !cur.getFluidState().isEmpty() || cur.is(Blocks.FIRE))) {
            b.pendingRepairs.remove(pos.asLong());
            repairBatch.remove(0);
            return;
        }
        if (!goTo(pos, 4.5, "damaged block")) {
            return;
        }
        lookAt(pos);
        status("Repairing damage (" + repairBatch.size() + " left in batch, " + b.pendingRepairs.size() + " total)", "Repairing");
        if (++timer < speed()) {
            return;
        }
        timer = 0;
        MaterialRules.Need n = MaterialRules.need(planned);
        ItemStack used = ItemStack.EMPTY;
        if (n.count() > 0) {
            ItemStack exact = Inv.first(npc.inventory(), s -> s.is(n.exact()));
            used = exact.isEmpty() ? Inv.first(npc.inventory(), MaterialRules.accepts(n)) : exact;
            if (used.isEmpty() || used.getCount() < n.count()) {
                repairBatch.remove(0); // stays in pendingRepairs for a later, funded attempt
                return;
            }
        }
        BlockState place = MaterialRules.placedState(planned, used);
        if (!used.isEmpty()) {
            used.shrink(n.count());
            npc.inventory().setChanged();
        }
        level().setBlock(pos, place, Block.UPDATE_ALL);
        if (place.getBlock() instanceof DoorBlock && place.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER) {
            level().setBlock(pos.above(), place.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
            b.pendingRepairs.remove(pos.above().asLong());
        }
        level().playSound(null, pos, place.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0F, 1.0F);
        npc.swing(InteractionHand.MAIN_HAND);
        npc.awardXp(1);
        b.pendingRepairs.remove(pos.asLong());
        repairBatch.remove(0);
        data().setDirty();
    }

    private int speed() {
        return (int) Math.max(3, 10 / (1.0 + npc.affix(Affix.MASON) + npc.affix(Affix.DILIGENCE)));
    }

    // ---------------------------------------------------------------- projects

    /** Remaining material needs of a project (optionally minus what is already staged and carried). */
    private Map<String, Integer> missing(Project p, boolean subtractStaged) {
        Blueprint plan = plan(p);
        Map<String, Integer> need = new LinkedHashMap<>();
        Map<String, MaterialRules.Need> needs = new LinkedHashMap<>();
        int i = 0;
        for (Map.Entry<BlockPos, BlockState> e : plan.blocks.entrySet()) {
            if (i++ < p.placed) {
                continue;
            }
            if (satisfied(e.getKey(), e.getValue())) {
                continue;
            }
            MaterialRules.Need n = MaterialRules.need(e.getValue());
            if (n.count() > 0) {
                need.merge(n.key(), n.count(), Integer::sum);
                needs.put(n.key(), n);
            }
        }
        if (subtractStaged) {
            Container staging = staging();
            for (Map.Entry<String, Integer> e : List.copyOf(need.entrySet())) {
                MaterialRules.Need n = needs.get(e.getKey());
                int have = (staging == null ? 0 : Inv.count(staging, MaterialRules.accepts(n)))
                        + Inv.count(npc.inventory(), MaterialRules.accepts(n));
                int rest = e.getValue() - have;
                if (rest <= 0) {
                    need.remove(e.getKey());
                } else {
                    need.put(e.getKey(), rest);
                }
            }
        }
        lastNeeds.clear();
        lastNeeds.putAll(needs);
        return need;
    }

    private final Map<String, MaterialRules.Need> lastNeeds = new LinkedHashMap<>();

    private boolean satisfied(BlockPos pos, BlockState planned) {
        BlockState cur = level().getBlockState(pos);
        if (cur.equals(planned)) {
            return true;
        }
        if (planned.is(Blocks.DIRT) && Terrain.isSolidGround(cur)) {
            return true; // gap filling only where there is a gap
        }
        MaterialRules.Need n = MaterialRules.need(planned);
        return n.count() > 0 && !cur.isAir() && cur.getBlock() != Blocks.GRASS_BLOCK && cur.getBlock() != Blocks.DIRT
                && MaterialRules.accepts(n).test(new ItemStack(cur.getBlock().asItem()));
    }

    private Container staging() {
        BaseState b = base();
        return FactionStorage.container(level(), b.point(BaseState.STAGING));
    }

    private void gather(BaseState b) {
        Project p = activeProject();
        if (p == null) {
            state = State.CHECK;
            return;
        }
        if (!atStorage()) {
            return;
        }
        // drop anything unrelated to the project
        unloadUnrelated(p);
        Map<String, Integer> missing = missing(p, true);
        if (missing.isEmpty()) {
            state = State.DELIVER;
            return;
        }
        boolean craftNeeded = false;
        List<String> short_ = new ArrayList<>();
        for (Map.Entry<String, Integer> e : missing.entrySet()) {
            MaterialRules.Need n = lastNeeds.get(e.getKey());
            int want = Math.min(e.getValue(), 64 * 3);
            int got = takeFromStorage(s -> s.is(n.exact()), want);
            if (got < want) {
                got += takeFromStorage(MaterialRules.accepts(n), want - got);
            }
            if (got < want) {
                Crafting.Recipe r = MaterialRules.craftFor(n.group());
                if (r != null) {
                    int batches = (want - got + r.outputCount() - 1) / r.outputCount();
                    int taken = 0;
                    for (Crafting.Input in : r.inputs()) {
                        int amount = in.count() * batches;
                        int t = takeFromStorage(in.filter(), amount);
                        if (t < amount && (in.label().equals("Planks") || in.label().equals(Items.STICK.getDescription().getString()))) {
                            takeFromStorage(s -> s.is(ItemTags.LOGS), (amount - t + 3) / 4 + 1);
                        }
                        taken += t;
                    }
                    if (taken > 0 || Inv.count(npc.inventory(), s -> s.is(ItemTags.LOGS)) > 0) {
                        craftNeeded = true;
                    }
                }
            }
            if (got < want) {
                short_.add(n.label() + " " + (want - got));
            }
            if (Inv.freeSlots(npc.inventory()) <= 1) {
                break;
            }
        }
        boolean carrying = Inv.totalItems(npc.inventory()) > 0;
        if (craftNeeded) {
            state = State.CRAFT;
        } else if (carrying) {
            state = State.DELIVER;
        } else {
            blocker = "materials: " + String.join(", ", short_);
            status("Project waiting for " + blocker, "Waiting");
            updateSign(b, p, missing);
            waitFor(600);
            state = State.CHECK;
        }
    }

    private void unloadUnrelated(Project p) {
        Map<String, Integer> all = missing(p, false);
        for (int i = 0; i < npc.inventory().getContainerSize(); i++) {
            ItemStack s = npc.inventory().getItem(i);
            if (s.isEmpty()) {
                continue;
            }
            boolean useful = s.is(ItemTags.LOGS) || s.is(ItemTags.PLANKS) || s.is(Items.STICK) || s.is(Items.WHEAT)
                    || s.is(Items.COBBLESTONE) || s.is(Items.COAL) || s.is(Items.CHARCOAL);
            for (String k : all.keySet()) {
                MaterialRules.Need n = lastNeeds.get(k);
                if (n != null && MaterialRules.accepts(n).test(s)) {
                    useful = true;
                }
            }
            if (!useful) {
                ItemStack rest = FactionStorage.insert(level(), base(), s.copy());
                npc.inventory().setItem(i, rest);
            }
        }
    }

    /** Crafts project parts (planks, doors, chests, stairs, bedrolls) from carried raw materials. */
    private void craft(BaseState b) {
        Project p = activeProject();
        if (p == null) {
            state = State.CHECK;
            return;
        }
        if (!goTo(b.point(BaseState.WORKSHOP_STAND), 2.0, "the crafting table")) {
            return;
        }
        lookAt(b.point(BaseState.CRAFTING));
        if (++timer < 20) {
            status("Crafting building parts for " + p.type.label, "Crafting");
            return;
        }
        timer = 0;
        Map<String, Integer> missing = missing(p, true);
        for (Map.Entry<String, Integer> e : missing.entrySet()) {
            MaterialRules.Need n = lastNeeds.get(e.getKey());
            Crafting.Recipe r = MaterialRules.craftFor(n.group());
            if (r != null && Crafting.craftWithIntermediates(npc.inventory(), r, 1) > 0) {
                npc.swing(InteractionHand.MAIN_HAND);
                return;
            }
        }
        state = Inv.totalItems(npc.inventory()) > 0 ? State.DELIVER : State.CHECK;
    }

    private void deliver(BaseState b) {
        Project p = activeProject();
        BlockPos chest = b.point(BaseState.STAGING);
        if (p == null || chest == null) {
            state = State.CHECK;
            return;
        }
        if (!goTo(chest, 2.5, "the staging chest")) {
            return;
        }
        Container staging = staging();
        if (staging == null) {
            blocker = "staging chest missing";
            status("The staging chest is missing", "Blocked");
            waitFor(400);
            state = State.CHECK;
            return;
        }
        Map<String, Integer> all = missing(p, false);
        for (int i = 0; i < npc.inventory().getContainerSize(); i++) {
            ItemStack s = npc.inventory().getItem(i);
            if (s.isEmpty()) {
                continue;
            }
            boolean needed = false;
            for (String k : all.keySet()) {
                if (MaterialRules.accepts(lastNeeds.get(k)).test(s)) {
                    needed = true;
                }
            }
            if (needed) {
                ItemStack rest = FactionStorage.insertInto(staging, s.copy());
                npc.inventory().setItem(i, rest);
            }
        }
        npc.swing(InteractionHand.MAIN_HAND);
        Map<String, Integer> missing = missing(p, true);
        updateSign(b, p, missing);
        state = State.CHECK;
    }

    private void fetch(BaseState b) {
        Project p = activeProject();
        if (p == null) {
            state = State.CHECK;
            return;
        }
        BlockPos chest = b.point(BaseState.STAGING);
        if (!goTo(chest, 2.5, "the staging chest")) {
            return;
        }
        Container staging = staging();
        if (staging == null) {
            state = State.CHECK;
            return;
        }
        Blueprint plan = plan(p);
        Map<String, Integer> want = new LinkedHashMap<>();
        Map<String, MaterialRules.Need> needs = new LinkedHashMap<>();
        int i = 0;
        for (Map.Entry<BlockPos, BlockState> e : plan.blocks.entrySet()) {
            if (i++ < p.placed || satisfied(e.getKey(), e.getValue())) {
                continue;
            }
            MaterialRules.Need n = MaterialRules.need(e.getValue());
            if (n.count() > 0) {
                want.merge(n.key(), n.count(), Integer::sum);
                needs.put(n.key(), n);
            }
        }
        for (Map.Entry<String, Integer> e : want.entrySet()) {
            MaterialRules.Need n = needs.get(e.getKey());
            int have = Inv.count(npc.inventory(), MaterialRules.accepts(n));
            int take = Math.min(e.getValue() - have, 64 * 2);
            if (take > 0) {
                for (ItemStack s : Inv.remove(staging, MaterialRules.accepts(n), take)) {
                    ItemStack rest = Crafting.addAll(npc.inventory(), s);
                    if (!rest.isEmpty()) {
                        FactionStorage.insertInto(staging, rest);
                    }
                }
            }
        }
        state = State.BUILD;
    }

    private void build(BaseState b) {
        Project p = activeProject();
        if (p == null) {
            state = State.CHECK;
            return;
        }
        Blueprint plan = plan(p);
        List<Map.Entry<BlockPos, BlockState>> entries = new ArrayList<>(plan.blocks.entrySet());
        while (p.placed < entries.size() && satisfied(entries.get(p.placed).getKey(), entries.get(p.placed).getValue())) {
            p.placed++;
        }
        if (p.placed >= entries.size()) {
            finish(b, p, plan);
            return;
        }
        Map.Entry<BlockPos, BlockState> e = entries.get(p.placed);
        MaterialRules.Need n = MaterialRules.need(e.getValue());
        ItemStack used = ItemStack.EMPTY;
        if (n.count() > 0) {
            ItemStack exact = Inv.first(npc.inventory(), s -> s.is(n.exact()));
            used = exact.isEmpty() ? Inv.first(npc.inventory(), MaterialRules.accepts(n)) : exact;
            if (used.isEmpty()) {
                Container staging = staging();
                if (staging != null && Inv.count(staging, MaterialRules.accepts(n)) > 0) {
                    state = State.FETCH;
                } else {
                    p.status = Project.Status.GATHERING;
                    state = State.CHECK;
                }
                return;
            }
        }
        BlockPos pos = e.getKey();
        if (!goTo(pos, 4.5, p.type.label + " site")) {
            return;
        }
        lookAt(pos);
        status("Building " + p.type.label + " #" + p.id + " (" + p.placed + "/" + entries.size() + ")", "Building");
        if (++timer < speed()) {
            return;
        }
        timer = 0;
        BlockState cur = level().getBlockState(pos);
        if (!cur.isAir() && !cur.canBeReplaced()) {
            // ground preparation: clear the old block (natural terrain only; its drop is kept)
            if (cur.hasBlockEntity()) {
                blocker = "site blocked by a container at " + pos.toShortString();
                status("Site blocked - leaving player blocks alone", "Blocked");
                p.placed++;
                return;
            }
            Crafting.addAll(npc.inventory(), new ItemStack(cur.getBlock().asItem()));
            level().setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        BlockState place = MaterialRules.placedState(e.getValue(), used);
        if (!used.isEmpty()) {
            used.shrink(n.count());
            npc.inventory().setChanged();
        }
        level().setBlock(pos, place, Block.UPDATE_ALL);
        Blueprint.fixShapes(level(), List.of(pos));
        level().playSound(null, pos, place.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0F, 1.0F);
        npc.swing(InteractionHand.MAIN_HAND);
        p.placed++;
        data().setDirty();
        if (p.placed % 16 == 0) {
            updateSign(b, p, missing(p, true));
        }
    }

    private void finish(BaseState b, Project p, Blueprint plan) {
        p.status = Project.Status.DONE;
        Map<BlockPos, BlockState> record = new LinkedHashMap<>();
        for (Map.Entry<BlockPos, BlockState> e : plan.blocks.entrySet()) {
            BlockState cur = level().getBlockState(e.getKey());
            if (!cur.isAir()) {
                record.put(e.getKey(), cur);
            }
            if (cur.is(ModBlocks.BEDROLL.get())) {
                b.beds.add(e.getKey());
            }
            if (cur.is(Blocks.CHEST) && p.type == Project.Type.STORAGE_EXPANSION) {
                b.storage(StorageCategory.GENERAL).add(e.getKey());
                b.storageExpansionSlots.remove(e.getKey());
            }
        }
        b.structures.add(StructureRecord.of(p.type.label + " #" + p.id, record));
        b.expansionLots.remove(p.origin);
        b.projects.removeIf(x -> x.status == Project.Status.DONE);
        data().setDirty();
        npc.awardXp(25 + plan.blocks.size() / 20);
        Messages.toFaction(level().getServer(), npc.getFaction(), Component.literal(npc.npcName() + " finished " + p.type.label + " #" + p.id));
        Blueprint.writeSign(level(), b.point(BaseState.STAGING_SIGN), "Construction", "Done: " + p.type.label, "#" + p.id, "");
        blocker = "";
        state = State.CHECK;
    }

    private void updateSign(BaseState b, Project p, Map<String, Integer> missing) {
        BlockPos sign = b.point(BaseState.STAGING_SIGN);
        if (sign == null) {
            return;
        }
        List<String> lines = new ArrayList<>();
        lines.add(p.type.label + " #" + p.id);
        if (missing.isEmpty()) {
            lines.add(p.status == Project.Status.BUILDING ? "Building " + p.placed + "/" + plan(p).blocks.size() : "Materials ready");
        } else {
            for (Map.Entry<String, Integer> e : missing.entrySet()) {
                if (lines.size() >= 4) {
                    break;
                }
                MaterialRules.Need n = lastNeeds.get(e.getKey());
                lines.add("Need " + (n == null ? e.getKey() : n.label()) + " " + e.getValue());
            }
        }
        Blueprint.writeSign(level(), sign, lines.toArray(new String[0]));
    }

    @Override
    public void save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putString("state", state.name());
        tag.putString("blocker", blocker);
    }

    @Override
    public void load(CompoundTag tag, HolderLookup.Provider provider) {
        state = State.CHECK;
        blocker = tag.getString("blocker");
    }
}
