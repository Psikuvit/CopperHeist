package me.psikuvit.copperHeist.achievement;

import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.profile.PlayerProfile;
import net.kyori.adventure.title.Title;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Achievements: permanent goals on lifetime statistics and level. They are checked when a match ends and when a player levels up, and an
 * unlocked achievement is recorded in the player's profile (with the time), so it stays unlocked even if its target is later changed.
 * Main thread only.
 */
public class AchievementService {

    private final CopperHeist plugin;
    private final AchievementRegistry registry;

    public AchievementService(CopperHeist plugin, AchievementRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
    }

    public AchievementRegistry registry() {
        return registry;
    }

    public boolean enabled() {
        return plugin.settings().getBoolean("features.achievements", true) && plugin.getProfiles() != null && plugin.getStats() != null;
    }

    public PlayerProfile profile(Player player) {
        return plugin.getProfiles() == null ? null : plugin.getProfiles().get(player);
    }

    public boolean isUnlocked(PlayerProfile profile, AchievementDefinition achievement) {
        return profile.owns(achievement.profileId());
    }

    /** How far along the player is: their lifetime stat (or level) - not capped at the target. */
    public long value(Player player, AchievementDefinition achievement) {
        if (achievement.isLevel()) return plugin.getProgress() == null ? 1 : plugin.getProgress().level(player.getUniqueId(), player.getName());
        return plugin.getStats().snapshot(player.getUniqueId(), player.getName()).get(achievement.stat());
    }

    /** Unlocks (and pays) every achievement the player has now reached. Returns the ones unlocked by this check. */
    public List<AchievementDefinition> check(Player player) {
        List<AchievementDefinition> unlocked = new ArrayList<>();
        PlayerProfile profile = profile(player);
        if (profile == null || !enabled()) return unlocked;
        for (AchievementDefinition achievement : registry.all()) {
            if (isUnlocked(profile, achievement) || value(player, achievement) < achievement.target()) continue;
            profile.unlock(achievement.profileId());
            reward(player, achievement);
            unlocked.add(achievement);
        }
        if (!unlocked.isEmpty()) plugin.getProfiles().save(profile);
        return unlocked;
    }

    public int unlockedCount(PlayerProfile profile) {
        int count = 0;
        for (AchievementDefinition achievement : registry.all()) {
            if (isUnlocked(profile, achievement)) count++;
        }
        return count;
    }

    private void reward(Player player, AchievementDefinition achievement) {
        var progress = plugin.getProgress();
        if (progress != null && progress.enabled() && (achievement.xp() > 0 || achievement.coins() > 0)) {
            progress.award(player.getUniqueId(), player.getName(), achievement.xp(), achievement.coins());
        }
        if (achievement.cosmetic() != null && plugin.getCosmetics() != null) {
            var cosmetic = plugin.getCosmeticRegistry().get(achievement.cosmetic());
            if (cosmetic != null) plugin.getCosmetics().grant(player, cosmetic, "achievement");
        }
        var messages = plugin.getMessageService();
        player.showTitle(Title.title(messages.get(player, "achievements.title"),
                messages.get(player, "achievements.subtitle", "name", achievement.name())));
        player.sendMessage(messages.get(player, "achievements.unlocked", "name", achievement.name(), "description", achievement.description(),
                "xp", achievement.xp(), "coins", achievement.coins()));
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.2f);
    }

    /** The lang key naming what an achievement counts (for lists to say what the number means). */
    public static String statLangKey(AchievementDefinition achievement) {
        return achievement.isLevel() ? "achievements.level" : achievement.stat().langKey();
    }
}
