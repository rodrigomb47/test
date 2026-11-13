package com.factionclash.hero;

import com.factionclash.FactionClash;
import com.factionclash.campaign.CampaignData;
import com.factionclash.campaign.HeroState;
import com.factionclash.combat.CombatRules;
import com.factionclash.config.FCConfig;
import com.factionclash.logic.hero.HeroActions;
import com.factionclash.logic.hero.HeroDef;
import com.factionclash.logic.hero.HeroDef.LineEvent;
import com.factionclash.logic.hero.HeroLoadout;
import com.factionclash.logic.hero.HeroRules;
import com.factionclash.logic.hero.HeroSpeech;
import com.factionclash.logic.hero.HeroClips;
import com.factionclash.logic.hero.HeroSize;
import com.factionclash.logic.hero.HeroTimings;
import com.factionclash.logic.hero.MoodRules;
import com.factionclash.npc.FactionNpc;
import com.factionclash.npc.NpcRole;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A hero (HEROES_V2 3): a faction NPC with everything a faction NPC has (inventory, hunger, gear and wear, traits,
 * levels, its job, navigation, stuck recovery, roster record, alarms, war council) plus a hero id, the synced fields
 * the animated model is driven by (10.1) and a hitbox sized from its model (3.6). It counts as one soldier of the
 * role that fits its weapon style and fights with that role's basic attacks until the kit engine (A3) exists.
 *
 * <p><b>Its definition</b> (A1b, {@link #applyDefinition}): the stats of its file with growth, boss health at all
 * times (one mode, HEROES_V2 6.1: nothing reads who it fights), the role its style fits, the kit of its file (bound
 * items; a fists-style hero holds no weapon and never fetches one), the name tag {@code ★ Name · title} with
 * {@code [Fighting 80%]} while it fights, and its lines ({@link #say}: the rate limit of v1 23, chat to the event's
 * audience and a speech bubble above the head). A hero whose definition is broken or gone is <b>dormant</b>
 * (HEROES_V2 3.4): it keeps its entity and saved state and fights as a plain soldier with the neutral model and a
 * {@code (dormant)} tag; the synced flag tells clients which look to draw.
 *
 * <p>GeckoLib is on both sides, so implementing {@link GeoEntity} is safe on a dedicated server; nothing here
 * touches a client class. Its three animation controllers are {@link HeroAnimator}'s (A2); they run only in the client
 * renderer. The server keeps the synced flags the model reads: the weapon is drawn while it fights, its mood follows
 * {@link MoodRules}, a played action code is cleared once its clip has played (the timing files). An operator can force
 * a mood, the weapon flag, a base pose and any flag for screenshots ({@code /fc admin hero pose}); a <b>preview</b> is a
 * display copy for screenshots ({@code /fc admin hero preview|lineup}): it stands still, cannot be hurt, has no job,
 * hunger or roster entry, is not the campaign's hero and is never counted by the director.
 */
public class HeroEntity extends FactionNpc implements GeoEntity {
    /** The hero id ({@code ""} until set; set once at spawn). */
    private static final EntityDataAccessor<String> DATA_HERO = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.STRING);
    /** The action the {@code action} controller plays: code, sequence, speed, variant ({@link HeroActions}). */
    private static final EntityDataAccessor<Integer> DATA_ACTION = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.INT);
    /** Phase, enraged, super armour, weapon drawn, talking, wind-up, escaping ({@link HeroActions}). */
    private static final EntityDataAccessor<Byte> DATA_STATE = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.BYTE);
    /** Mood, intensity, blush, glowing eyes ({@link HeroActions}). */
    private static final EntityDataAccessor<Byte> DATA_MOOD = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.BYTE);
    /** Locomotion mode, one-shot and tired (MOBILITY_AND_WORK 4.2); reserved here, driven by the mobility engine (A10). */
    private static final EntityDataAccessor<Byte> DATA_LOCO = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.BYTE);

    /**
     * A base pose an operator forced for screenshots ({@link HeroClips#FORCED}; 0: the hero's own motion). Not in the
     * design's 10.1 list: an operator tool, one byte, only ever non-zero on a hero posed by hand.
     */
    private static final EntityDataAccessor<Byte> DATA_POSE = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.BYTE);

    /** No valid definition: a plain soldier drawn with the neutral model (HEROES_V2 3.4); set by the server. */
    private static final EntityDataAccessor<Boolean> DATA_DORMANT = SynchedEntityData.defineId(HeroEntity.class, EntityDataSerializers.BOOLEAN);

    /** The attribute modifiers a definition sets (fixed ids, so a reload or level-up never stacks them). */
    public static final ResourceLocation BOSS_HEALTH = ResourceLocation.fromNamespaceAndPath(FactionClash.MODID, "boss_health"),
            HERO_ATTACK = ResourceLocation.fromNamespaceAndPath(FactionClash.MODID, "hero_attack"),
            HERO_ARMOR = ResourceLocation.fromNamespaceAndPath(FactionClash.MODID, "hero_armor"),
            HERO_SPEED = ResourceLocation.fromNamespaceAndPath(FactionClash.MODID, "hero_speed"),
            HERO_KNOCKBACK = ResourceLocation.fromNamespaceAndPath(FactionClash.MODID, "hero_knockback");

    /** A fight starts when it takes a target after this long without one (v1 23's {@code fight_start}; 10 s). */
    public static final int FIGHT_GAP = 200;
    /** A fight is won when it has had no target this long and no hostile is within {@link #VICTORY_RADIUS}. */
    public static final int VICTORY_AFTER = 100;
    public static final double VICTORY_RADIUS = 16;
    /** {@code low_health} under 30% of max health, said again only after it has been back above half. */
    public static final float LOW_HEALTH = 0.3F, LOW_HEALTH_REARM = 0.5F;
    /** {@code idle}: at its base, a faction player within 12 blocks, checked every 10 s (the clock allows one per 15 min). */
    public static final double IDLE_RADIUS = 12;
    /** {@code ally_fallen}: a member of its side dying within this many blocks (v1 23). */
    public static final double ALLY_RADIUS = 24;
    /** The tag a speech bubble carries (a bubble saved with a chunk is discarded when it loads again). */
    public static final String BUBBLE_TAG = "fc_hero_speech";

    /** After its last blow or shot it keeps the weapon drawn this long (ticks). */
    public static final int WEAPON_DRAWN_AFTER_FIGHT = 100;

    /** Wings, tails and long weapons reach past the hitbox: the culling box is this much larger (3.1). */
    public static final double CULLING_MARGIN = 1.5;

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    private final HeroAnimator animator = new HeroAnimator(this);

    /** A display copy (screenshots), not the campaign's hero. */
    private boolean preview;
    /** An operator's mood code (-1: the hero's own), weapon flag (-1 own, 0 sheathed, 1 drawn). */
    private int forcedMood = -1;
    private int forcedWeapon = -1;
    /** The tick the played action's clip ends (the code is then cleared), or -1. */
    private int actionEndsAt = -1;
    /** With the marker log on ({@link HeroAnimator#LOG_MARKERS}): the played action's clip, start tick and hit ticks. */
    private String actionLogClip;
    private int actionLogStart;
    private double actionLogSpeed = 1.0;
    private int[] actionLogHits = new int[0];
    /** The client renderer's per-hero state (face clocks, level of detail, sway); null on a server. */
    private Object clientState;

    /** Energy 0-100 (v1 14): basic hits, kills and damage taken fill it; a revival starts at 0. Saved. */
    private int energy;
    /** Its fight runtime: moves, cooldowns, basic hits' effects (task A3); made on first use. */
    private HeroCombat combat;
    /** The kit it was last equipped with ({@link HeroLoadout#signature}); a reload re-equips it only when this changes. */
    private String kitSignature = "";
    /** From its definition at the last {@link #applyDefinition}: a bare-handed kit, a kit with a shield. */
    private boolean bareHandedKit, shieldKit;
    /** The fight: the last tick it had a target, whether a fight is on, whether the low-health line is armed. */
    private int lastTargetTick = -100000;
    private boolean inFight;
    private boolean lowHealthArmed = true;
    /** Its dialogue clock (v1 23), the last line it said and its speech bubble (a text display above the head). */
    private final HeroSpeech.Clock clock = new HeroSpeech.Clock();
    private String lastLine = "";
    private Display.TextDisplay bubble;
    private int bubbleUntil;
    private int talkUntil = -1;

    public HeroEntity(EntityType<? extends HeroEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return FactionNpc.createAttributes();
    }

    /**
     * A faction NPC's goals plus the hero's own (HEROES_V2 3.5): its moves at priority 2 and, for a {@code thrown}
     * basic attack, its throwing goal at priority 3 beside the soldiers' attack goals. (Runs in the constructor:
     * the goals read the hero's fields only later.)
     */
    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(2, new com.factionclash.combat.HeroAbilityGoal(this));
        this.goalSelector.addGoal(3, new com.factionclash.combat.HeroThrowGoal(this));
    }

    /** Its fight runtime (task A3): moves, cooldowns and charges, energy gains, basic hits' effects. */
    public HeroCombat combat() {
        if (combat == null) {
            combat = new HeroCombat(this);
        }
        return combat;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_HERO, "");
        builder.define(DATA_ACTION, HeroActions.CODE_NONE);
        builder.define(DATA_STATE, (byte) 0);
        builder.define(DATA_MOOD, (byte) 0);
        builder.define(DATA_LOCO, (byte) 0);
        builder.define(DATA_POSE, (byte) 0);
        builder.define(DATA_DORMANT, false);
    }

    // ------------------------------------------------------------------ identity

    public String heroId() {
        return entityData.get(DATA_HERO);
    }

    /**
     * The registry entry it is drawn and sized with: its own, or the neutral fallback for a dormant or unknown hero
     * (the synced flag decides on a client, which may not know the server's definitions).
     */
    public HeroEntry entry() {
        return entityData.get(DATA_DORMANT) ? HeroRegistry.fallback() : HeroRegistry.getOrFallback(heroId());
    }

    /**
     * Whether it has no valid definition (HEROES_V2 3.4): a plain soldier with the neutral look and a {@code (dormant)}
     * tag. The server works it out from the registry; a client reads the synced flag.
     */
    public boolean dormant() {
        if (level().isClientSide) {
            return entityData.get(DATA_DORMANT);
        }
        HeroEntry e = HeroRegistry.get(heroId());
        return e == null || e.dormant();
    }

    /** Its checked definition (server), or null when it is dormant. */
    public HeroDef definition() {
        HeroEntry e = HeroRegistry.get(heroId());
        return e == null ? null : e.def();
    }

    /**
     * Sets the hero id: its size and art scale follow ({@code generic.scale} starts at the model's {@code scale}).
     * Called once, before the entity joins the level, and on loading.
     */
    public void setHeroId(String id) {
        entityData.set(DATA_HERO, id == null ? "" : id);
        if (!level().isClientSide) {
            entityData.set(DATA_DORMANT, dormant());
        }
        AttributeInstance scale = getAttribute(Attributes.SCALE);
        if (scale != null) {
            scale.setBaseValue(entry().visual().scale());
        }
        refreshDimensions();
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_HERO.equals(key) || DATA_DORMANT.equals(key)) {
            refreshDimensions();
        }
    }

    // ------------------------------------------------------------------ the definition (A1b)

    /**
     * Takes its definition as the registry has it now (spawn, load, {@code /reload}, {@code /fc hero reload}): the
     * dormant flag, its numbers with the health share kept, its role, name and title, and its kit when the kit changed
     * ({@code equipKit} true: always, as at a spawn). A dormant hero keeps what it holds and its last role, and fights
     * as a plain soldier (20 health, nothing added).
     */
    public void applyDefinition(boolean equipKit) {
        if (level().isClientSide) {
            return;
        }
        HeroEntry e = HeroRegistry.get(heroId());
        boolean dormant = e == null || e.dormant();
        entityData.set(DATA_DORMANT, dormant);
        AttributeInstance scale = getAttribute(Attributes.SCALE);
        if (scale != null) {
            scale.setBaseValue(entry().visual().scale());
        }
        if (!dormant && !preview) {
            NpcRole role = e.role();
            if (role != getRole()) {
                role(role); // (releases a workplace of the old role; also re-applies the numbers)
            }
            if (!e.name().equals(npcName())) {
                setNpcName(e.name());
            }
        }
        HeroDef d = e == null ? null : e.def(); // (null for a dormant hero and for a preview fixture)
        bareHandedKit = d != null && HeroLoadout.bareHanded(d, e.style());
        shieldKit = d != null && HeroLoadout.hasShield(d, e.style());
        applyNumbers();
        String sig = d == null ? "" : HeroLoadout.signature(HeroLoadout.kit(d, e.style()), HeroLoadout.needsArrow(d));
        if (equipKit || d != null && !sig.equals(kitSignature)) {
            equipKit();
        }
        refreshDimensions();
        refreshName();
    }

    /** The stats of its definition at its level (or a plain soldier's when dormant), keeping its share of health. */
    private void applyNumbers() {
        if (level().isClientSide || heroId().isEmpty()) {
            return;
        }
        HeroDef d = dormant() ? null : definition();
        HeroLoadout.Numbers n = d == null ? HeroLoadout.PLAIN
                : HeroLoadout.numbers(d, npcLevel(), false, FCConfig.BOSS_HEALTH_SCALE.get());
        float oldMax = getMaxHealth(), health = getHealth();
        AttributeInstance max = getAttribute(Attributes.MAX_HEALTH);
        if (max != null) {
            max.setBaseValue(n.baseHealth());
        }
        modifier(Attributes.MAX_HEALTH, BOSS_HEALTH, n.bossModifier(), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        modifier(Attributes.ATTACK_DAMAGE, HERO_ATTACK, n.attackBonus(), AttributeModifier.Operation.ADD_VALUE);
        modifier(Attributes.ARMOR, HERO_ARMOR, n.armorBonus(), AttributeModifier.Operation.ADD_VALUE);
        modifier(Attributes.MOVEMENT_SPEED, HERO_SPEED, n.speedModifier(), AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        modifier(Attributes.KNOCKBACK_RESISTANCE, HERO_KNOCKBACK, n.knockbackResistance(), AttributeModifier.Operation.ADD_VALUE);
        float newMax = getMaxHealth();
        if (Math.abs(newMax - oldMax) > 1e-4 && oldMax > 0 && isAlive()) {
            setHealth((float) com.factionclash.logic.hero.BossRules.keepShare(health, oldMax, newMax));
        }
    }

    private void modifier(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr, ResourceLocation id,
                          double amount, AttributeModifier.Operation op) {
        AttributeInstance inst = getAttribute(attr);
        if (inst == null) {
            return;
        }
        inst.removeModifier(id);
        if (amount != 0) {
            inst.addTransientModifier(new AttributeModifier(id, amount, op));
        }
    }

    /** Affixes and the definition's numbers together: a level-up (growth) and a role change re-apply both. */
    @Override
    public void reapplyAffixes() {
        super.reapplyAffixes();
        applyNumbers();
    }

    /**
     * Puts on its kit: every bound item it wears or carries is taken away, then the definition's kit goes into the
     * slots storehouse gear does not fill (a dormant hero: its role's basic weapon, as a plain soldier). Bow and
     * crossbow heroes get the never-used-up arrow.
     */
    public void equipKit() {
        if (level().isClientSide) {
            return;
        }
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() != EquipmentSlot.Type.ANIMAL_ARMOR && HeroKit.isKit(getItemBySlot(slot))) {
                setItemSlot(slot, ItemStack.EMPTY);
            }
        }
        for (int i = 0; i < inventory().getContainerSize(); i++) {
            if (HeroKit.isKit(inventory().getItem(i))) {
                inventory().setItem(i, ItemStack.EMPTY);
            }
        }
        if (HeroKit.isKit(ammo())) {
            setAmmo(ItemStack.EMPTY);
        }
        HeroEntry e = HeroRegistry.get(heroId());
        HeroDef d = e == null ? null : e.def();
        if (d == null) {
            kitSignature = "";
            ItemStack w = HeroKit.weapon(getRole(), heroId());
            if (!w.isEmpty() && getMainHandItem().isEmpty()) {
                setItemSlot(EquipmentSlot.MAINHAND, w);
            }
            if (getRole().ranged && ammo().isEmpty()) {
                setAmmo(HeroKit.mark(new ItemStack(net.minecraft.world.item.Items.ARROW), heroId()));
            }
            return;
        }
        List<HeroDef.GearItem> kit = HeroLoadout.kit(d, e.style());
        for (HeroDef.GearItem g : kit) {
            EquipmentSlot slot = HeroKit.slot(g.slot());
            if (getItemBySlot(slot).isEmpty()) {
                ItemStack stack = HeroKit.item(g, heroId(), registryAccess());
                if (!stack.isEmpty()) {
                    setItemSlot(slot, stack);
                }
            }
        }
        if (HeroLoadout.needsArrow(d) && ammo().isEmpty()) {
            setAmmo(HeroKit.mark(new ItemStack(net.minecraft.world.item.Items.ARROW), heroId()));
        }
        kitSignature = HeroLoadout.signature(kit, HeroLoadout.needsArrow(d));
    }

    /** A fists-style hero (or a thrown one) fights with its bare hands: its kit holds nothing in the main hand. */
    @Override
    public boolean fightsBareHanded() {
        return bareHandedKit && !entityData.get(DATA_DORMANT);
    }

    /**
     * Its weapon is its kit's: a bare-handed hero needs none, and one holding its bound kit weapon (a mace, a sword)
     * needs no trip to the storehouse for its role's tool.
     */
    @Override
    public boolean needsRoleTool() {
        return !fightsBareHanded() && !HeroKit.isKit(getMainHandItem());
    }

    /** A better storehouse weapon of the same kind replaces its kit weapon (v1 7); a bare-handed hero takes none. */
    @Override
    public boolean takesStoreWeapons() {
        return !fightsBareHanded() && (getMainHandItem().isEmpty() || getRole().tool.matches(getMainHandItem()));
    }

    /** Only a hero whose kit has a shield takes one from the storehouse (a katana hero fights without). */
    @Override
    public boolean takesShield() {
        return entityData.get(DATA_DORMANT) ? super.takesShield() : shieldKit;
    }

    // ------------------------------------------------------------------ energy (v1 14)

    public int energy() {
        return energy;
    }

    public void setEnergy(int e) {
        int v = Math.max(0, Math.min(HeroRules.ENERGY_MAX, e));
        if (v != energy) {
            int shownBefore = energy;
            energy = v;
            if (inFight && FCConfig.HERO_SHOW_ENERGY.get() && shownBefore != v) {
                refreshName();
            }
        }
    }

    /** Adds energy for an event worth {@code base} (x the ultimate's {@code charge_rate} and the energy_gain stat). */
    private void gainEnergy(double base) {
        combat().gain(base);
    }

    /** Whether its basic attack is {@code thrown} (it fights with {@code HeroThrowGoal}, not in melee). */
    public boolean throwsBasic() {
        if (level().isClientSide || dormant()) {
            return false;
        }
        HeroDef d = definition();
        return d != null && d.basicAttack().style() == HeroDef.Style.THROWN;
    }

    /** A thrown hero does not fight in melee: its throws are its basic attack. */
    @Override
    public boolean fightsInMelee() {
        return !throwsBasic();
    }

    /** Its arrows are tagged: one that lands is its basic hit (energy and {@code on_hit}, v1 9 and 14). */
    @Override
    protected void onArrowShot(net.minecraft.world.entity.projectile.AbstractArrow arrow) {
        arrow.getPersistentData().putBoolean(BASIC_ARROW_TAG, true);
    }

    /** The tag on a hero's own arrows. */
    public static final String BASIC_ARROW_TAG = "fc_hero_basic";

    // ------------------------------------------------------------------ name tag (v1 25.8)

    /** {@code ★ Name · title} in the hero's colour, {@code [Fighting 80%]} while it fights, {@code (dormant)}. */
    @Override
    public void refreshName() {
        if (heroId().isEmpty()) {
            super.refreshName();
            return;
        }
        HeroEntry e = level().isClientSide ? null : HeroRegistry.get(heroId());
        HeroDef d = e == null ? null : e.def();
        boolean dormant = !level().isClientSide && (e == null || e.dormant());
        MutableComponent tag = Component.literal(HeroSpeech.tagName(npcName(), dormant || e == null ? "" : e.title())).withStyle(colour(d));
        if (preview) {
            tag.append(Component.literal(" (preview)").withStyle(ChatFormatting.GRAY));
        } else if (dormant) {
            tag.append(Component.literal(" " + HeroSpeech.DORMANT).withStyle(ChatFormatting.DARK_GRAY));
        }
        if (inFight && !preview) {
            tag.append(Component.literal(" " + HeroSpeech.fightTag(energy, FCConfig.HERO_SHOW_ENERGY.get())).withStyle(ChatFormatting.GRAY));
        }
        setCustomName(tag);
    }

    /** The hero's colour (its file's {@code color}), or its faction's. */
    private net.minecraft.network.chat.Style colour(HeroDef d) {
        net.minecraft.network.chat.Style s = net.minecraft.network.chat.Style.EMPTY;
        return d != null && d.color() != HeroDef.NO_COLOR ? s.withColor(TextColor.fromRgb(d.color())) : s.withColor(getFaction().color);
    }

    // ------------------------------------------------------------------ lines (v1 23, HEROES_V2 12.15)

    /** The last line it said (with its placeholders filled), for the GameTests and {@code /fc hero info}. */
    public String lastLine() {
        return lastLine;
    }

    public HeroSpeech.Clock clock() {
        return clock;
    }

    /**
     * Says a line of {@code event} if the rate limit and the event's chance allow and a line exists (its own, or a
     * generic one): in chat to the event's audience ({@code heroes.dialogue}: nearby players within
     * {@code dialogueRadius}, its faction, or the one player it answers) as {@code <★ Name> line}, and in a speech bubble
     * above its head for 3 s while its face talks. {@code values} fills the placeholders ({@code name} and
     * {@code faction} are its own). Returns whether it spoke.
     */
    public boolean say(LineEvent event, Map<String, String> values, ServerPlayer listener) {
        if (!(level() instanceof ServerLevel sl) || preview || !isAlive() && event != LineEvent.FALLEN) {
            return false;
        }
        String mode = FCConfig.HERO_DIALOGUE.get();
        if ("off".equals(mode)) {
            return false;
        }
        long now = sl.getGameTime();
        if (!clock.allows(event, now, FCConfig.HERO_DIALOGUE_COOLDOWN_SECONDS.get()) || !HeroRules.lineRoll(event, random.nextDouble() * 100)) {
            return false;
        }
        String line = HeroSpeech.pick(dormant() ? null : definition(), event, FCConfig.HERO_DEFAULT_LINES.get(), random.nextDouble());
        if (line == null) {
            return false;
        }
        Map<String, String> v = new HashMap<>(values);
        v.putIfAbsent("name", npcName());
        v.putIfAbsent("faction", getFaction().displayName);
        String text = HeroSpeech.fill(line, v);
        deliver(sl, event, text, listener, mode, now);
        return true;
    }

    /**
     * Says a move's own {@code line} (v1 10: "said when it is used, rate-limited") under {@code event}'s rate limit
     * and audience. Returns whether it spoke.
     */
    public boolean speak(LineEvent event, String text) {
        if (!(level() instanceof ServerLevel sl) || preview || !isAlive() || text == null || text.isBlank()) {
            return false;
        }
        String mode = FCConfig.HERO_DIALOGUE.get();
        long now = sl.getGameTime();
        if ("off".equals(mode) || !clock.allows(event, now, FCConfig.HERO_DIALOGUE_COOLDOWN_SECONDS.get())) {
            return false;
        }
        Map<String, String> v = new HashMap<>();
        v.put("name", npcName());
        v.put("faction", getFaction().displayName);
        deliver(sl, event, HeroSpeech.fill(text, v), null, mode, now);
        return true;
    }

    private void deliver(ServerLevel sl, LineEvent event, String text, ServerPlayer listener, String mode, long now) {
        clock.said(event, now);
        lastLine = text;
        Component chat = Component.literal(HeroSpeech.chatLine(npcName(), text)).withStyle(colour(dormant() ? null : definition()));
        HeroSpeech.Audience who = HeroSpeech.audience(event);
        CampaignData data = CampaignData.get(sl.getServer());
        double r = FCConfig.HERO_DIALOGUE_RADIUS.get();
        for (ServerPlayer p : sl.getServer().getPlayerList().getPlayers()) {
            boolean member = data.players.get(p.getUUID()) == getFaction();
            boolean hears = switch (who) {
                case PLAYER -> p == listener;
                case FACTION -> member;
                case NEARBY -> "faction".equals(mode) ? member : p.level() == level() && p.distanceToSqr(this) <= r * r;
            };
            if (hears) {
                p.sendSystemMessage(chat);
            }
        }
        FactionClash.LOGGER.info("[Heroes] {} says ({}): {}", heroId(), event.id(), text);
        if (FCConfig.HERO_SPEECH_BUBBLES.get()) {
            showBubble(sl, text);
        }
        talkUntil = tickCount + HeroSpeech.BUBBLE_TICKS;
        setTalking(true);
    }

    public boolean say(LineEvent event) {
        return say(event, Map.of(), null);
    }

    /** The speech bubble: a vanilla text display above the name tag, following the hero, gone after 3 s. */
    private void showBubble(ServerLevel sl, String text) {
        removeBubble();
        net.minecraft.nbt.CompoundTag t = new net.minecraft.nbt.CompoundTag();
        t.putString("id", "minecraft:text_display");
        t.putString("text", Component.Serializer.toJson(Component.literal(text), registryAccess()));
        t.putString("billboard", "center");
        t.putString("alignment", "center");
        t.putBoolean("shadow", true);
        t.putInt("teleport_duration", 1);
        t.putInt("line_width", 160);
        net.minecraft.nbt.ListTag tags = new net.minecraft.nbt.ListTag();
        tags.add(net.minecraft.nbt.StringTag.valueOf(BUBBLE_TAG));
        t.put("Tags", tags);
        Entity made = EntityType.loadEntityRecursive(t, sl, x -> x);
        if (made instanceof Display.TextDisplay d) {
            d.moveTo(getX(), bubbleY(), getZ(), 0F, 0F);
            if (sl.addFreshEntity(d)) {
                bubble = d;
                bubbleUntil = tickCount + HeroSpeech.BUBBLE_TICKS;
            }
        }
    }

    private double bubbleY() {
        return getY() + getBbHeight() + 0.75;
    }

    /** The speech bubble it shows now (null: none). */
    public Display.TextDisplay bubble() {
        return bubble != null && bubble.isAlive() ? bubble : null;
    }

    private void removeBubble() {
        if (bubble != null) {
            bubble.discard();
            bubble = null;
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        removeBubble();
        super.remove(reason);
    }

    /** A faction player's right click: its {@code greeting} (5 s apart), then the NPC sheet as for any NPC. */
    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand == InteractionHand.MAIN_HAND && !level().isClientSide && !preview && player instanceof ServerPlayer sp
                && CombatRules.isMember(sp, getFaction())) {
            say(LineEvent.GREETING, Map.of("player", sp.getName().getString()), sp);
        }
        return super.mobInteract(player, hand);
    }

    /**
     * No soldier's withdrawal (HEROES_V2 3.5 and 6.1; the owner's rule of 2026-11-13: a hero escapes or fights until it
     * dies): a hero never steps back to eat and return at 60%. Its {@code retreat_below} only sets where an escaping
     * hero leaves ({@code EscapeRules.fleeAt}, wired by A6/A12).
     */
    @Override
    protected boolean withdrawsToRecover() {
        return false;
    }

    /** Every hero looks for enemies, whatever its duty (v1 25.6). */
    @Override
    public boolean isGuardDuty() {
        return true;
    }

    // ------------------------------------------------------------------ size

    /**
     * The hitbox of HEROES_V2 3.6 from the model: vanilla multiplies these dimensions by {@code generic.scale}, so they
     * are the clamped size divided by it, and the box never exceeds 1.98 blocks however large the scale grows.
     */
    @Override
    protected EntityDimensions getDefaultDimensions(Pose pose) {
        float s = getScale();
        if (!(s > 0)) {
            s = 1.0F;
        }
        HeroSize.Size size = entry().visual().size(s);
        return EntityDimensions.scalable((float) (size.width() / s), (float) (size.height() / s))
                .withEyeHeight((float) (size.eyeHeight() / s));
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return super.getBoundingBoxForCulling().inflate(CULLING_MARGIN);
    }

    // ------------------------------------------------------------------ synced fields

    public int action() {
        return entityData.get(DATA_ACTION);
    }

    public void setAction(int packed) {
        entityData.set(DATA_ACTION, packed);
    }

    /**
     * Starts an action: a new sequence number, so even the same action again restarts on the client. Its code is
     * cleared again when the clip has played (its timing file's total ticks divided by the speed), so a player who
     * starts tracking the hero later does not see an old action; channels and loops play until the next code.
     */
    public void playAction(int code, double speed, int variant) {
        int packed = HeroActions.nextAction(action(), code, speed, variant);
        setAction(packed);
        actionEndsAt = -1;
        String clip = HeroClips.actionClip(code, entry().visual().look().moves(), entry().visual().look().states());
        HeroTimings.Clip t = clip == null ? null : HeroTimingFiles.clip(entry(), clip);
        if (t != null && !t.loops() && HeroTimingFiles.clip(entry(), clip + ".loop") == null && t.totalTicks() > 0) {
            actionEndsAt = tickCount + HeroTimings.ticksAt(t.totalTicks(), HeroActions.speed(packed));
        }
        actionLogHits = new int[0];
        if (HeroAnimator.LOG_MARKERS && t != null && !level().isClientSide) {
            double played = HeroActions.speed(packed);
            actionLogClip = clip;
            actionLogStart = tickCount;
            actionLogSpeed = played;
            actionLogHits = t.events().stream().filter(e -> "hit".equals(e.type()))
                    .mapToInt(e -> HeroTimings.ticksAt(e.tick(), played)).toArray();
            FactionClash.LOGGER.info("[Heroes] {} server starts {} at speed {} (server tick {}; hit at +{})", heroId(),
                    clip, String.format(Locale.ROOT, "%.2f", played), tickCount, Arrays.toString(actionLogHits));
        }
    }

    /** The marker log's server side: the timing file's hits, on the tick the server counts them (4.8). */
    private void logServerHits() {
        int since = tickCount - actionLogStart;
        for (int hit : actionLogHits) {
            if (since == hit) {
                FactionClash.LOGGER.info("[Heroes] {} server {} hit at +{} ticks (server tick {}, speed {})", heroId(),
                        actionLogClip, since, tickCount, String.format(Locale.ROOT, "%.2f", actionLogSpeed));
            }
        }
    }

    /** Ends the action now (code 0: the client's action controller stops, the base pose takes over). */
    public void stopAction() {
        actionEndsAt = -1;
        if (HeroActions.code(action()) != HeroActions.CODE_NONE) {
            setAction(HeroActions.nextAction(action(), HeroActions.CODE_NONE, 1.0, 0));
        }
    }

    public byte state() {
        return entityData.get(DATA_STATE);
    }

    public void setState(byte state) {
        entityData.set(DATA_STATE, state);
    }

    public boolean stateFlag(int flag) {
        return HeroActions.has(state(), flag);
    }

    public void setStateFlag(int flag, boolean on) {
        setState(HeroActions.with(state(), flag, on));
    }

    /** The talking flag (DATA_STATE bit 5): set while a line of dialogue shows; the face cycles its talking mouth. */
    public boolean isTalking() {
        return stateFlag(HeroActions.STATE_TALKING);
    }

    public void setTalking(boolean talking) {
        setStateFlag(HeroActions.STATE_TALKING, talking);
    }

    public byte mood() {
        return entityData.get(DATA_MOOD);
    }

    public void setMood(byte mood) {
        entityData.set(DATA_MOOD, mood);
    }

    public byte loco() {
        return entityData.get(DATA_LOCO);
    }

    public void setLoco(byte loco) {
        entityData.set(DATA_LOCO, loco);
    }

    /** The base pose an operator forced (an index of {@link HeroClips#FORCED}; 0: none). */
    public int forcedPose() {
        return entityData.get(DATA_POSE);
    }

    public void setForcedPose(int pose) {
        entityData.set(DATA_POSE, (byte) (pose >= 0 && pose < HeroClips.FORCED.size() ? pose : 0));
    }

    /** An operator's mood (-1: the hero's own, by {@link MoodRules}). */
    public void setForcedMood(int mood) {
        forcedMood = mood >= 0 && mood < HeroActions.MOODS.size() ? mood : -1;
    }

    public int forcedMood() {
        return forcedMood;
    }

    /** An operator's weapon flag: -1 the hero's own (drawn while fighting), 0 sheathed, 1 drawn. */
    public void setForcedWeapon(int weapon) {
        forcedWeapon = weapon == 0 || weapon == 1 ? weapon : -1;
    }

    public int forcedWeapon() {
        return forcedWeapon;
    }

    /** Clears every forced value: mood, weapon, base pose, the state flags (the phase stays) and the action. */
    public void clearPose() {
        forcedMood = -1;
        forcedWeapon = -1;
        setForcedPose(0);
        setState(HeroActions.withPhase(0, HeroActions.phase(state())));
        setMood((byte) 0);
        stopAction();
    }

    // ------------------------------------------------------------------ server: flags the model reads

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            return;
        }
        if (bubble != null) {
            if (!isAlive() || tickCount >= bubbleUntil || !bubble.isAlive()) {
                removeBubble();
            } else {
                bubble.moveTo(getX(), bubbleY(), getZ(), 0F, 0F);
            }
        }
        if (talkUntil >= 0 && tickCount >= talkUntil) {
            talkUntil = -1;
            setTalking(false);
        }
        if (isAlive()) {
            updateFlags();
            if (!preview) {
                fightTick();
                combat().tick();
            }
        }
    }

    /**
     * The fight as its lines and counters see it: a fight starts when it takes a target after 10 s without one
     * ({@code fight_start}, counted by who the enemy is), and is won when it has had no target for 5 s and no hostile is
     * within 16 blocks ({@code victory}); the name tag shows the fight. {@code idle} at its base with a faction player
     * near, every 10 s at most (its clock allows one per 15 minutes).
     */
    private void fightTick() {
        LivingEntity t = getTarget();
        boolean fighting = t != null && t.isAlive();
        if (fighting) {
            if (!inFight) {
                boolean fresh = tickCount - lastTargetTick > FIGHT_GAP;
                inFight = true;
                refreshName();
                if (fresh) {
                    countFight(t);
                    say(LineEvent.FIGHT_START, Map.of("enemy", nameOf(t)), null);
                }
            }
            lastTargetTick = tickCount;
        } else if (inFight && tickCount - lastTargetTick >= VICTORY_AFTER) {
            inFight = false;
            refreshName();
            if (level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(VICTORY_RADIUS),
                    e -> e != this && e.isAlive() && isHostileTo(e)).isEmpty()) {
                say(LineEvent.VICTORY);
            }
        }
        if (tickCount % 200 == 37 && !inFight && level() instanceof ServerLevel sl) {
            var b = base();
            if (b != null && b.contains(blockPosition(), 0)) {
                for (ServerPlayer p : sl.players()) {
                    if (p.distanceToSqr(this) <= IDLE_RADIUS * IDLE_RADIUS && CombatRules.isMember(p, getFaction())) {
                        say(LineEvent.IDLE, Map.of("player", p.getName().getString()), null);
                        break;
                    }
                }
            }
        }
    }

    private boolean isHostileTo(LivingEntity e) {
        if (e instanceof FactionNpc n) {
            return com.factionclash.campaign.Diplomacy.hostile(getServer(), getFaction(), n.getFaction());
        }
        if (e instanceof ServerPlayer p) {
            return CombatRules.isEnemyPlayer(this, p);
        }
        return e instanceof Monster m && CombatRules.isHostileMonster(m);
    }

    /**
     * The name a line uses for someone ({@code {enemy}}, {@code {ally}}): a faction NPC's or hero's own name (not its
     * whole name tag), a player's, or a creature's ("Zombie").
     */
    public static String nameOf(Entity e) {
        if (e == null) {
            return "";
        }
        return e instanceof FactionNpc n && n.npcName() != null && !n.npcName().isEmpty() ? n.npcName() : e.getName().getString();
    }

    /** Who a fight is against, for {@code sim.py}: a player, a hero, soldiers (faction NPCs) or monsters. */
    public static String enemyKind(LivingEntity t) {
        return t instanceof Player ? "player" : t instanceof HeroEntity ? "hero" : t instanceof FactionNpc ? "soldiers" : "monsters";
    }

    private void countFight(LivingEntity t) {
        HeroState s = campaignRecord();
        if (s != null) {
            HeroState.count(s.fights, enemyKind(t));
            CampaignData.get(getServer()).setDirty();
        }
    }

    /** The campaign's record of this hero when this entity is the one it records (not a stale copy or a preview). */
    public HeroState campaignRecord() {
        if (!(level() instanceof ServerLevel sl) || preview) {
            return null;
        }
        HeroState s = CampaignData.get(sl.getServer()).heroes.get(heroId());
        return s != null && getUUID().equals(s.uuid) ? s : null;
    }

    /** Whether it is in a fight as its lines and name tag see it. */
    public boolean inFight() {
        return inFight;
    }

    /** The last tick it had a live target (v1 {@code out_of_combat}: 5 s without one). */
    public int lastTargetTick() {
        return lastTargetTick;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        float before = getHealth();
        boolean r = super.hurt(source, amount);
        float lost = before - getHealth();
        if (r && lost > 0 && !level().isClientSide && !preview) {
            gainEnergy(HeroRules.energyFromDamage(lost));
            float share = getHealth() / Math.max(1F, getMaxHealth());
            if (isAlive() && lowHealthArmed && share < LOW_HEALTH) {
                lowHealthArmed = false;
                say(LineEvent.LOW_HEALTH);
            }
        }
        return r;
    }

    @Override
    public void heal(float amount) {
        super.heal(amount);
        if (!lowHealthArmed && getHealth() / Math.max(1F, getMaxHealth()) >= LOW_HEALTH_REARM) {
            lowHealthArmed = true;
        }
    }

    /**
     * A landed basic attack: energy (v1 14: 5, x its ultimate's charge rate) and its {@code on_hit} effects and
     * finisher (v1 9), the same on any target.
     */
    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && !level().isClientSide && !preview) {
            if (target instanceof LivingEntity l) {
                combat().onBasicHit(l, this);
            } else {
                gainEnergy(HeroRules.ENERGY_BASIC_HIT);
            }
        }
        return hit;
    }

    /** Its kill: energy (10), the {@code kill} line (30%), the campaign's count. */
    @Override
    public boolean killedEntity(ServerLevel level, LivingEntity victim) {
        boolean r = super.killedEntity(level, victim);
        if (!preview) {
            gainEnergy(HeroRules.ENERGY_KILL);
            HeroState s = campaignRecord();
            if (s != null) {
                s.kills++;
                CampaignData.get(level.getServer()).setDirty();
            }
            say(LineEvent.KILL, Map.of("enemy", nameOf(victim)), null);
        }
        return r;
    }

    /** A level gained: the numbers grow (re-applied with the affixes) and it says {@code level_up}. */
    @Override
    public void awardXp(int amount) {
        int before = npcLevel();
        super.awardXp(amount);
        if (npcLevel() > before) {
            say(LineEvent.LEVEL_UP, Map.of("level", Integer.toString(npcLevel())), null);
        }
    }

    /**
     * The weapon is drawn while it fights and for 5 s after its last blow; the mood follows {@link MoodRules} (an
     * operator's forced values first; the hurt wince is vanilla's 10-tick hurt time); a played action's code is cleared
     * when its clip has played.
     */
    private void updateFlags() {
        boolean fighting = getTarget() != null && getTarget().isAlive();
        boolean drawn = forcedWeapon >= 0 ? forcedWeapon == 1 : fighting || tickCount - lastAttackTick() < WEAPON_DRAWN_AFTER_FIGHT;
        if (stateFlag(HeroActions.STATE_WEAPON_DRAWN) != drawn) {
            setStateFlag(HeroActions.STATE_WEAPON_DRAWN, drawn);
        }
        int mood = MoodRules.pick(forcedMood, hurtTime > 0, isSleeping(), stateFlag(HeroActions.STATE_WINDUP), fighting,
                getHealth() / Math.max(1F, getMaxHealth()));
        byte moodByte = mood();
        if (HeroActions.mood(moodByte) != mood) {
            setMood((byte) ((moodByte & ~0xF) | mood));
        }
        if (actionLogHits.length > 0) {
            logServerHits();
        }
        if (actionEndsAt >= 0 && tickCount >= actionEndsAt) {
            stopAction();
        }
    }

    // ------------------------------------------------------------------ preview copies

    /** A display copy for screenshots (see the class comment). */
    public boolean isPreview() {
        return preview;
    }

    /** Makes this a display copy: no AI, cannot be hurt or targeted, never despawns. Called before it joins the level. */
    public void makePreview() {
        preview = true;
        setNoAi(true);
        setInvulnerable(true);
        setPersistenceRequired();
        refreshName();
    }

    @Override
    public boolean isDisplayOnly() {
        return preview;
    }

    @Override
    public boolean canBeSeenAsEnemy() {
        return !preview && super.canBeSeenAsEnemy();
    }



    // ------------------------------------------------------------------ client

    /** The controllers' owner (HEROES_V2 4.4). */
    public HeroAnimator animator() {
        return animator;
    }

    /** The client renderer's state for this hero (an opaque object; null until first drawn, always null on a server). */
    public Object clientState() {
        return clientState;
    }

    public void setClientState(Object state) {
        this.clientState = state;
    }

    // ------------------------------------------------------------------ kit

    /** Better gear replacing a kit item: the kit item vanishes instead of going to the backpack (and the storehouse). */
    @Override
    public void equip(ItemStack stack) {
        if (!stack.isEmpty()) {
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                if (slot.getType() != EquipmentSlot.Type.ANIMAL_ARMOR && HeroKit.isKit(getItemBySlot(slot)) && fits(stack, slot)) {
                    setItemSlot(slot, ItemStack.EMPTY);
                }
            }
        }
        super.equip(stack);
    }

    private static boolean fits(ItemStack stack, EquipmentSlot slot) {
        EquipmentSlot target = stack.getItem() instanceof net.minecraft.world.item.ArmorItem armor ? armor.getEquipmentSlot()
                : stack.getItem() instanceof net.minecraft.world.item.ShieldItem ? EquipmentSlot.OFFHAND : EquipmentSlot.MAINHAND;
        return target == slot;
    }

    /** Kit items never drop: they vanish here and the hero gets them back when it returns. */
    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (HeroKit.isKit(getItemBySlot(slot))) {
                setItemSlot(slot, ItemStack.EMPTY);
            }
        }
        for (int i = 0; i < inventory().getContainerSize(); i++) {
            if (HeroKit.isKit(inventory().getItem(i))) {
                inventory().setItem(i, ItemStack.EMPTY);
            }
        }
        if (HeroKit.isKit(ammo())) {
            setAmmo(ItemStack.EMPTY);
        }
        super.dropCustomDeathLoot(level, source, recentlyHit);
    }

    @Override
    public void die(DamageSource source) {
        if (combat != null) {
            combat.reset();
        }
        super.die(source);
        if (level() instanceof ServerLevel sl && isDeadOrDying()) {
            HeroManager.onDeath(sl.getServer(), this, source);
        }
    }

    // ------------------------------------------------------------------ GeckoLib

    /** The base, action and secondary controllers (HEROES_V2 4.4; {@link HeroAnimator}). */
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        animator.register(controllers);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animationCache;
    }

    // ------------------------------------------------------------------ saving

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        CompoundTag h = new CompoundTag();
        h.putString("id", heroId());
        if (preview) {
            h.putBoolean("preview", true);
        }
        if (forcedMood >= 0) {
            h.putInt("mood", forcedMood);
        }
        if (forcedWeapon >= 0) {
            h.putInt("weapon", forcedWeapon);
        }
        if (forcedPose() > 0) {
            h.putInt("pose", forcedPose());
        }
        h.putInt("energy", energy);
        h.putString("kit", kitSignature);
        if (combat != null) {
            combat.save(h);
        }
        tag.put("FcHero", h);
    }

    /**
     * Loads the hero: vanilla reads its health before its boss maximum is back (the modifiers are not saved), so the
     * saved health is read here first and set again once the definition has given the maximum (3.3: "the boss maximum is
     * re-applied before it is read").
     */
    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        float savedHealth = tag.contains("Health", 99) ? tag.getFloat("Health") : -1F;
        super.readAdditionalSaveData(tag);
        CompoundTag h = tag.getCompound("FcHero");
        preview = h.getBoolean("preview");
        energy = Math.max(0, Math.min(HeroRules.ENERGY_MAX, h.getInt("energy")));
        kitSignature = h.getString("kit");
        combat().load(h);
        setHeroId(h.getString("id"));
        forcedMood = h.contains("mood") ? h.getInt("mood") : -1;
        forcedWeapon = h.contains("weapon") ? h.getInt("weapon") : -1;
        setForcedPose(h.getInt("pose"));
        if (!level().isClientSide) {
            applyDefinition(false);
            if (savedHealth >= 0) {
                setHealth(savedHealth);
            }
        }
        refreshName();
    }
}
