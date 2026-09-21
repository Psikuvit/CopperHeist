package me.psikuvit.copperHeist.daily;

import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.config.ConfigFiles;
import me.psikuvit.copperHeist.profile.PlayerProfile;
import me.psikuvit.copperHeist.quest.QuestPeriod;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The daily login reward and the streak behind it. Each day a player logs in counts towards their streak (a missed day resets it), and once a
 * day they can claim a reward that grows with the streak (daily.yml: a list that repeats). The streak and the day of the last claim live in
 * the player's profile. Days change at midnight UTC. Main thread only.
 */
public class DailyRewardService {

    /** One day of the reward list. */
    public record Reward(long xp, long coins, String cosmetic) {
    }

    private static final String LAST_LOGIN = "daily.last";
    private static final String STREAK = "daily.streak";
    private static final String CLAIMED = "daily.claimed";

    private final CopperHeist plugin;
    private final List<Reward> rewards = new ArrayList<>();
    private boolean fileEnabled = true;

    public DailyRewardService(CopperHeist plugin) {
        this.plugin = plugin;
    }

    public void load() {
        YamlConfiguration yaml = ConfigFiles.load(plugin, "daily.yml");
        fileEnabled = yaml.getBoolean("enabled", true);
        rewards.clear();
        for (Map<?, ?> raw : yaml.getMapList("rewards")) {
            rewards.add(new Reward(number(raw.get("xp")), number(raw.get("coins")), raw.get("cosmetic") == null ? null : String.valueOf(raw.get("cosmetic"))));
        }
        if (rewards.isEmpty()) rewards.add(new Reward(20, 20, null));
    }

    private static long number(Object value) {
        return value instanceof Number number ? Math.max(0, number.longValue()) : 0;
    }

    public boolean enabled() {
        return fileEnabled && plugin.settings().getBoolean("features.daily-reward", true) && plugin.getProfiles() != null;
    }

    private static long today() {
        return QuestPeriod.DAILY.idAt(System.currentTimeMillis());
    }

    public int streak(Player player) {
        PlayerProfile profile = plugin.getProfiles() == null ? null : plugin.getProfiles().get(player);
        return profile == null ? 0 : (int) profile.longField(STREAK, 0);
    }

    /** The reward the player's current streak earns. */
    public Reward rewardFor(int streak) {
        return rewards.get(DailyStreak.rewardIndex(streak, rewards.size()));
    }

    public List<Reward> rewards() {
        return List.copyOf(rewards);
    }

    public boolean canClaim(Player player) {
        PlayerProfile profile = plugin.getProfiles() == null ? null : plugin.getProfiles().get(player);
        return enabled() && profile != null && DailyStreak.canClaim(profile.longField(CLAIMED, -1), today());
    }

    /** Called when the profile has loaded: counts today's login towards the streak and tells the player if a reward is waiting. */
    public void onLogin(Player player) {
        if (!enabled()) return;
        PlayerProfile profile = plugin.getProfiles().get(player);
        if (profile == null) return;
        long today = today();
        long last = profile.longField(LAST_LOGIN, -1);
        int streak = DailyStreak.next(last, today, (int) profile.longField(STREAK, 0));
        profile.setField(LAST_LOGIN, today);
        profile.setField(STREAK, streak);
        plugin.getProfiles().save(profile);

        // A moment after joining, so the message isn't lost in the join spam.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && canClaim(player)) announce(player, streak);
        }, 60L);
    }

    private void announce(Player player, int streak) {
        var messages = plugin.getMessageService();
        Reward reward = rewardFor(streak);
        Component line = messages.get(player, "daily.available", "streak", streak, "coins", reward.coins(), "xp", reward.xp())
                .clickEvent(ClickEvent.runCommand("/ch daily"))
                .hoverEvent(HoverEvent.showText(messages.get(player, "daily.hover")));
        player.sendMessage(line);
    }

    /** Claims today's reward. Returns false (and says why) if there is nothing to claim. */
    public boolean claim(Player player) {
        var messages = plugin.getMessageService();
        if (!enabled()) {
            player.sendMessage(messages.err(player, "daily.disabled"));
            return false;
        }
        PlayerProfile profile = plugin.getProfiles().get(player);
        if (profile == null) {
            player.sendMessage(messages.err(player, "stats.loading"));
            return false;
        }
        long today = today();
        if (!DailyStreak.canClaim(profile.longField(CLAIMED, -1), today)) {
            long minutes = QuestPeriod.DAILY.millisLeft(System.currentTimeMillis()) / 60_000;
            player.sendMessage(messages.err(player, "daily.already", "hours", minutes / 60, "minutes", minutes % 60));
            return false;
        }
        int streak = Math.max(1, (int) profile.longField(STREAK, 1));
        Reward reward = rewardFor(streak);
        profile.setField(CLAIMED, today);
        plugin.getProfiles().save(profile);

        var progress = plugin.getProgress();
        if (progress != null && progress.enabled() && (reward.xp() > 0 || reward.coins() > 0)) {
            progress.award(player.getUniqueId(), player.getName(), reward.xp(), reward.coins());
        }
        if (reward.cosmetic() != null && plugin.getCosmetics() != null) {
            var cosmetic = plugin.getCosmeticRegistry().get(reward.cosmetic());
            if (cosmetic != null) plugin.getCosmetics().grant(player, cosmetic, "daily");
        }
        player.sendMessage(messages.get(player, "daily.claimed", "streak", streak, "coins", reward.coins(), "xp", reward.xp()));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.4f);
        return true;
    }
}
