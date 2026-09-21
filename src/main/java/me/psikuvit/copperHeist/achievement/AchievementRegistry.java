package me.psikuvit.copperHeist.achievement;

import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.config.ConfigFiles;
import me.psikuvit.copperHeist.stats.Stat;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Loads achievements.yml. An achievement with a mistake (unknown stat, no target) is skipped with a warning; the rest still load. */
public class AchievementRegistry {

    private static final Set<String> FRAMES = Set.of("task", "goal", "challenge");

    private final CopperHeist plugin;
    private final Map<String, AchievementDefinition> achievements = new LinkedHashMap<>();

    public AchievementRegistry(CopperHeist plugin) {
        this.plugin = plugin;
    }

    public void load() {
        YamlConfiguration yaml = ConfigFiles.load(plugin, "achievements.yml");
        achievements.clear();
        ConfigurationSection section = yaml.getConfigurationSection("achievements");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection entry = section.getConfigurationSection(id);
                if (entry == null) continue;
                try {
                    AchievementDefinition achievement = parse(id, entry);
                    achievements.put(achievement.id(), achievement);
                } catch (RuntimeException ex) {
                    plugin.getLogger().warning("Skipping achievement '" + id + "' in achievements.yml: " + ex.getMessage());
                }
            }
        }
        plugin.getLogger().info("Loaded " + achievements.size() + " achievement(s).");
    }

    public AchievementDefinition get(String id) {
        return id == null ? null : achievements.get(id.toLowerCase(Locale.ROOT));
    }

    public List<AchievementDefinition> all() {
        return new ArrayList<>(achievements.values());
    }

    /** Builds one achievement from its section; throws IllegalArgumentException with a readable reason if it is unusable. */
    public static AchievementDefinition parse(String rawId, ConfigurationSection s) {
        String metric = s.getString("stat", "").trim();
        Stat stat = null;
        if (!metric.equalsIgnoreCase("level")) {
            stat = Stat.fromKey(metric);
            if (stat == null) throw new IllegalArgumentException("unknown stat '" + metric + "' (use a stat name from /ch top, or level)");
            if (stat == Stat.COINS) throw new IllegalArgumentException("coins can go down when spent - use coins_earned");
        }
        long target = s.getLong("target", 0);
        if (target < 1) throw new IllegalArgumentException("target must be at least 1");
        Material icon = Material.matchMaterial(s.getString("icon", "NETHER_STAR"));
        if (icon == null) throw new IllegalArgumentException("unknown icon material '" + s.getString("icon") + "'");
        String frame = s.getString("frame", "goal").toLowerCase(Locale.ROOT);
        if (!FRAMES.contains(frame)) throw new IllegalArgumentException("frame must be task, goal or challenge");
        String cosmetic = s.getString("reward.cosmetic");
        return new AchievementDefinition(rawId.toLowerCase(Locale.ROOT), s.getString("name", rawId), s.getString("description", ""), stat, target,
                s.getBoolean("secret", false), icon, frame, Math.max(0, s.getLong("reward.xp", 0)), Math.max(0, s.getLong("reward.coins", 0)),
                cosmetic == null || cosmetic.isBlank() ? null : cosmetic);
    }
}
