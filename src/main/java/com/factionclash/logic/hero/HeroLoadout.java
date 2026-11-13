package com.factionclash.logic.hero;

import com.factionclash.logic.hero.HeroCatalogue.GearKind;
import com.factionclash.logic.hero.HeroDef.GearItem;
import com.factionclash.logic.hero.HeroDef.GearSlot;
import com.factionclash.logic.hero.HeroDef.Style;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * What a hero's definition makes of its entity (HEROES_V2 3.2, 6.2; v1 5, 7 and 9; task A1b): the soldier role its
 * style fits, the kit it wears (a fists-style hero holds no weapon), and its numbers at a level: the v1 health,
 * attack, armour, speed and knock-back resistance with growth, and the boss health that is its health at all times
 * (one mode: nothing here reads who it fights or how many). A dormant hero, whose definition is gone, has the plain
 * soldier's numbers ({@link #PLAIN}). Pure, unit-tested ({@code HeroLoadoutTest}).
 */
public final class HeroLoadout {
    /** Weapon styles whose weapon is the body: the style library has no held item, so the hero fights bare-handed. */
    public static final Set<String> FIST_STYLES = Set.of("fists");
    /** Weapon styles that fight like an axe fighter (heavy chopping and crushing). */
    public static final Set<String> AXE_STYLES = Set.of("axe", "hammer", "scythe");

    private HeroLoadout() {
    }

    // ------------------------------------------------------------------ role and kit

    /**
     * The {@code NpcRole} id a hero counts as (3.2): {@code bow} an archer, {@code crossbow} a crossbowman, trident
     * and thrown styles a swordsman (with their own goal), and a melee hero an axe fighter when its weapon is an axe or
     * a mace or its weapon style is an axe, hammer or scythe, else a swordsman. {@code style} is the visual file's
     * weapon style (null when the hero has no model of its own).
     */
    public static String roleId(HeroDef d, String style) {
        String duty = dutyRoleId(d);
        return duty != null ? duty : combatRoleId(d, style);
    }

    /**
     * The worker role a hero's {@code duty} makes it (v1 8, HEROES_V2 7.1; task A4): the duty itself for every duty but
     * {@code guard} (and a leader's {@code court}), or null. Such a hero counts as that worker for its base's needs,
     * does that job between fights and still fights like a soldier.
     */
    public static String dutyRoleId(HeroDef d) {
        String duty = d.behaviour().duty();
        if (duty == null || duty.isBlank() || "guard".equals(duty) || "court".equals(duty)) {
            return null;
        }
        return duty;
    }

    /** The soldier role its weapon style fits (3.2), whatever its duty: how it fights. */
    public static String combatRoleId(HeroDef d, String style) {
        return switch (d.basicAttack().style()) {
            case BOW -> "archer";
            case CROSSBOW -> "crossbowman";
            case TRIDENT, THROWN -> "swordsman";
            case MELEE -> {
                GearItem w = mainHand(kit(d, style));
                GearKind k = w == null ? null : HeroCatalogue.GEAR.get(w.item());
                boolean heavy = k == GearKind.AXE || k == GearKind.MACE
                        || style != null && AXE_STYLES.contains(style.toLowerCase(Locale.ROOT));
                yield heavy ? "axe_fighter" : "swordsman";
            }
        };
    }

    /**
     * The items the hero wears: the definition's gear, without the stone sword the reader adds to a melee kit with no
     * weapon when the hero's style is a fists style (v1 7's placeholder: such a hero's weapon is its fists). An
     * authored weapon always stays (Natsuho's claw gauntlets are an iron sword drawn as claws).
     */
    public static List<GearItem> kit(HeroDef d, String style) {
        boolean fists = style != null && FIST_STYLES.contains(style.toLowerCase(Locale.ROOT));
        List<GearItem> out = new ArrayList<>();
        for (GearItem g : d.gear()) {
            if (fists && g.added() && g.slot() == GearSlot.MAINHAND) {
                continue;
            }
            out.add(g);
        }
        return List.copyOf(out);
    }

    /** Whether the hero fights with its bare hands: its kit holds nothing in the main hand (fists, thrown styles). */
    public static boolean bareHanded(HeroDef d, String style) {
        return mainHand(kit(d, style)) == null;
    }

    /** Bow and crossbow heroes carry the one never-used-up arrow, as archers do (v1 7). */
    public static boolean needsArrow(HeroDef d) {
        Style s = d.basicAttack().style();
        return s == Style.BOW || s == Style.CROSSBOW;
    }

    /** Whether the kit has a shield in the off hand (only then does a hero take a storehouse shield). */
    public static boolean hasShield(HeroDef d, String style) {
        for (GearItem g : kit(d, style)) {
            if (g.slot() == GearSlot.OFFHAND && HeroCatalogue.GEAR.get(g.item()) == GearKind.SHIELD) {
                return true;
            }
        }
        return false;
    }

    public static GearItem mainHand(List<GearItem> kit) {
        for (GearItem g : kit) {
            if (g.slot() == GearSlot.MAINHAND) {
                return g;
            }
        }
        return null;
    }

    /** A short text that changes when the kit does (the game re-equips a living hero after a reload only then). */
    public static String signature(List<GearItem> kit, boolean arrow) {
        StringBuilder sb = new StringBuilder();
        for (GearItem g : kit) {
            sb.append(g.slot().id()).append('=').append(g.item());
            if (g.name() != null) {
                sb.append('"').append(g.name()).append('"');
            }
            if (!g.enchantments().isEmpty()) {
                sb.append(g.enchantments());
            }
            sb.append(';');
        }
        return sb.append(arrow ? "arrow" : "").toString();
    }

    // ------------------------------------------------------------------ numbers

    /**
     * A hero's numbers at a level (before gear, affixes and buffs): {@code baseHealth} is the v1 health (role preset
     * and growth) and the base of the {@code MAX_HEALTH} attribute; {@code attackBonus} and {@code armorBonus} are
     * added like the Might and Toughness affixes; {@code speedModifier} multiplies the base speed
     * ({@code ADD_MULTIPLIED_BASE}); {@code bossModifier} is the one {@code factionclash:boss_health} modifier
     * ({@code ADD_MULTIPLIED_TOTAL}), so {@code maxHealth = baseHealth x healthScale / 100 x roleFactor} (6.2).
     */
    public record Numbers(double baseHealth, double attackBonus, double armorBonus, double speedModifier,
                          double knockbackResistance, double healthScale, double roleFactor, double bossModifier,
                          double maxHealth, int maxHitShare, int poise) {
    }

    /** A dormant hero: a plain soldier (20 health, nothing added, no boss health). */
    public static final Numbers PLAIN = new Numbers(20, 0, 0, 0, 0, 100, 1, 0, 20, 100, 0);

    /**
     * The numbers at {@code level} (an NPC level; below 1 reads as 1), leading its faction or not (+50 health scale,
     * 6.2) and with {@code boss.healthScale} ({@code bossScalePercent}, 25-300). Who the hero fights is not an input.
     */
    public static Numbers numbers(HeroDef d, int level, boolean leading, int bossScalePercent) {
        HeroRules.LevelStats s = HeroRules.statsAt(d.stats(), level);
        HeroDef.Boss b = d.v2().boss();
        double scale = BossRules.healthScale(b.healthScale(), leading, bossScalePercent);
        double factor = BossRules.roleFactor(d.behaviour().role());
        return new Numbers(s.health(), s.attack(), s.armor(), s.speedPercent() / 100.0 - 1,
                d.stats().knockbackResistance() / 100.0, scale, factor, BossRules.healthModifier(scale, factor),
                BossRules.maxHealth(s.health(), scale, factor), b.maxHitShare(), b.poise());
    }
}
