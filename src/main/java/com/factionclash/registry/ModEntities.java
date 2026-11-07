package com.factionclash.registry;

import com.factionclash.FactionClash;
import com.factionclash.npc.FactionNpc;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, FactionClash.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<FactionNpc>> FACTION_NPC = ENTITIES.register("faction_npc",
            () -> EntityType.Builder.<FactionNpc>of(FactionNpc::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F)
                    .eyeHeight(1.62F)
                    .clientTrackingRange(10)
                    .build("faction_npc"));

    private ModEntities() {
    }
}
