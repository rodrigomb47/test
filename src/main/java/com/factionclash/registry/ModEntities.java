package com.factionclash.registry;

import com.factionclash.FactionClash;
import com.factionclash.hero.HeroEntity;
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

    /** A hero (HEROES_V2 3.1): its hitbox comes from its model once its id is set (HeroEntity.getDefaultDimensions). */
    public static final DeferredHolder<EntityType<?>, EntityType<HeroEntity>> HERO = ENTITIES.register("hero",
            () -> EntityType.Builder.<HeroEntity>of(HeroEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F)
                    .eyeHeight(1.62F)
                    .clientTrackingRange(10)
                    .build("hero"));

    /** A hero's shot, volley or thrown basic attack (task A3): a vanilla item look, not saved. */
    public static final DeferredHolder<EntityType<?>, EntityType<com.factionclash.hero.entity.HeroProjectile>> HERO_PROJECTILE =
            ENTITIES.register("hero_projectile", () -> EntityType.Builder.<com.factionclash.hero.entity.HeroProjectile>of(
                            com.factionclash.hero.entity.HeroProjectile::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F)
                    .clientTrackingRange(4)
                    .updateInterval(10)
                    .noSave()
                    .build("hero_projectile"));

    /** A hero's zone (task A3): invisible, its rings and pulses drawn by server particles, not saved. */
    public static final DeferredHolder<EntityType<?>, EntityType<com.factionclash.hero.entity.HeroZone>> HERO_ZONE =
            ENTITIES.register("hero_zone", () -> EntityType.Builder.<com.factionclash.hero.entity.HeroZone>of(
                            com.factionclash.hero.entity.HeroZone::new, MobCategory.MISC)
                    .sized(0.5F, 0.1F)
                    .clientTrackingRange(8)
                    .updateInterval(20)
                    .noSave()
                    .fireImmune()
                    .build("hero_zone"));

    private ModEntities() {
    }
}
