package me.psikuvit.copperHeist.daily;

import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.config.ConfigFiles;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Loads daily.yml: the list of streak rewards (it repeats once the streak runs past the end) and the file's own on/off switch. */
public class DailyRewardRegistry {

    /** One day of the reward list. */
    public record Reward(long xp, long coins, String cosmetic) {
    }

    private final CopperHeist plugin;
    private final List<Reward> rewards = new ArrayList<>();
    private boolean fileEnabled = true;

    public DailyRewardRegistry(CopperHeist plugin) {
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

    /** daily.yml's {@code enabled}; the feature also needs features.daily-reward and profiles (see {@link DailyRewardService#enabled}). */
    public boolean fileEnabled() {
        return fileEnabled;
    }

    /** The reward a streak of this many days earns. */
    public Reward rewardFor(int streak) {
        return rewards.get(DailyStreak.rewardIndex(streak, rewards.size()));
    }

    public List<Reward> all() {
        return List.copyOf(rewards);
    }
}
