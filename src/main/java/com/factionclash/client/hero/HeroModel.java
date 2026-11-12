package com.factionclash.client.hero;

import com.factionclash.hero.HeroEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/**
 * The one GeckoLib model every hero is drawn with (HEROES_V2 4.5): the geometry, texture and animation chain come
 * from the hero's registry entry (its own files, or the neutral {@code _fallback} model for a fixture or an unknown
 * hero). A1a draws the hero in its bind pose; the controllers, the face frames, the procedural touches, the layers,
 * the sash tint and the level of detail come with A2.
 */
public class HeroModel extends GeoModel<HeroEntity> {
    /** (GeckoLib 4.9 marks the one-argument forms deprecated but still abstract; the renderer-aware forms call them.) */
    @Override
    @SuppressWarnings("deprecation")
    public ResourceLocation getModelResource(HeroEntity hero) {
        return hero.entry().model();
    }

    @Override
    @SuppressWarnings("deprecation")
    public ResourceLocation getTextureResource(HeroEntity hero) {
        return hero.entry().texture();
    }

    @Override
    public ResourceLocation getAnimationResource(HeroEntity hero) {
        return hero.entry().animation();
    }

    /** The style library, then the core library: a hero overrides any shared animation by defining it. */
    @Override
    public ResourceLocation[] getAnimationResourceFallbacks(HeroEntity hero) {
        return hero.entry().animationFallbacks();
    }
}
