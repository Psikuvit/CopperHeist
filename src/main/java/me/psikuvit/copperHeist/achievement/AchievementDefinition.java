package me.psikuvit.copperHeist.achievement;

import me.psikuvit.copperHeist.stats.Stat;
import org.bukkit.Material;

/**
 * One achievement from achievements.yml: reach {@code target} in a lifetime statistic (or in level) once, and it is yours for good.
 *
 * @param stat      the stat that is counted, or null when the achievement is about the player's level
 * @param secret    shown as "???" in lists until it is unlocked
 * @param icon      the item shown on its unlock toast
 * @param frame     the toast's border: task, goal or challenge
 * @param cosmetic  a cosmetic id to give as well (null = none)
 */
public record AchievementDefinition(String id, String name, String description, Stat stat, long target, boolean secret, Material icon,
                                    String frame, long xp, long coins, String cosmetic) {

    /** The profile item id that records this achievement as unlocked (and when). */
    public String profileId() {
        return "ach." + id;
    }

    public boolean isLevel() {
        return stat == null;
    }
}
