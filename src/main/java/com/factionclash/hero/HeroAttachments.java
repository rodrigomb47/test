package com.factionclash.hero;

import com.factionclash.FactionClash;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/**
 * The data the heroes keep on any living entity (task A3): {@link HeroStatus}, the marks, damage over time, stat
 * modifiers, shield and freeze a hero's moves put on a player, an NPC, a hero or a monster alike. It is saved with the
 * entity (nothing when it carries none) and not copied when a player respawns.
 */
public final class HeroAttachments {
    private HeroAttachments() {
    }

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, FactionClash.MODID);

    public static final Supplier<AttachmentType<HeroStatus>> STATUS = ATTACHMENTS.register("hero_status",
            () -> AttachmentType.builder(() -> new HeroStatus()).serialize(HeroStatus.SERIALIZER).build());
}
