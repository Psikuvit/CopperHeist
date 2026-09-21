package me.psikuvit.copperHeist.cosmetics;

import org.bukkit.Material;

import java.util.Locale;

/**
 * The kinds of cosmetic, each tied to one moment in the game where an equipped item of that kind plays (or, for titles, is shown).
 * {@code id} is what cosmetics.yml calls it; the lang key {@code cosmetics.category.<id>} is its display name.
 */
public enum CosmeticCategory {
    TRAIL("trail", Material.ARROW),
    KILL_EFFECT("kill-effect", Material.IRON_SWORD),
    VICTORY("victory", Material.FIREWORK_ROCKET),
    GOLEM("golem", Material.COPPER_BLOCK),
    NPC("npc", Material.VILLAGER_SPAWN_EGG),
    TITLE("title", Material.NAME_TAG),
    JOIN("join", Material.LIME_DYE),
    DEATH_MESSAGE("death-message", Material.SKELETON_SKULL);

    private final String id;
    private final Material icon;

    CosmeticCategory(String id, Material icon) {
        this.id = id;
        this.icon = icon;
    }

    public String id() {
        return id;
    }

    public Material icon() {
        return icon;
    }

    public String langKey() {
        return "cosmetics.category." + id;
    }

    public static CosmeticCategory parse(String value) {
        if (value == null) return null;
        String wanted = value.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        for (CosmeticCategory category : values()) {
            if (category.id.equals(wanted)) return category;
        }
        return null;
    }
}
