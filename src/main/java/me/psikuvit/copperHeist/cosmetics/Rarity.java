package me.psikuvit.copperHeist.cosmetics;

import java.util.Locale;

/** How special a cosmetic is; it only changes the colour of its name in menus (a theme tag, so a re-theme restyles it). */
public enum Rarity {
    COMMON("muted"),
    RARE("info"),
    EPIC("special"),
    LEGENDARY("accent");

    private final String tag;

    Rarity(String tag) {
        this.tag = tag;
    }

    /** The theme tag name to wrap the cosmetic's name in, e.g. {@code info}. */
    public String tag() {
        return tag;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Rarity parse(String value) {
        if (value != null) {
            for (Rarity rarity : values()) {
                if (rarity.key().equalsIgnoreCase(value.trim())) return rarity;
            }
        }
        return COMMON;
    }
}
