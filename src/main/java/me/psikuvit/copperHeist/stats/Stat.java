package me.psikuvit.copperHeist.stats;

import java.util.Locale;

/**
 * Every counter tracked per player. The lowercase name is the row key in ch_stats, and the lang key
 * {@code stat.<key>} is its display name, so a new stat is one line here plus one line in the language file.
 */
public enum Stat {
    GAMES_PLAYED,
    WINS,
    LOSSES,
    LOOT_DELIVERED,
    LOOT_STOLEN,
    STEALS,
    RELICS_DELIVERED,
    KILLS,
    DEATHS,
    GOLEMS_SCRAPED,
    DRILLS_COMPLETED,
    DRILLS_DESTROYED,
    MVPS,
    XP,
    COINS,
    COINS_EARNED;

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String langKey() {
        return "stat." + key();
    }

    /** The stat for a key; "level" is accepted for XP (leaderboards of XP are shown as levels). */
    public static Stat fromKey(String key) {
        if (key == null) return null;
        if (key.equalsIgnoreCase("level")) return XP;
        try {
            return valueOf(key.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
