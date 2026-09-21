package me.psikuvit.copperHeist.quest;

import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.config.ConfigFiles;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Loads quests.yml. A quest with a mistake (unknown trigger or period, no target) is skipped with a warning; the rest still load.
 * Which quests are active today or this week is worked out from the day or week number, so it is the same for every player and on every
 * server of a network, and nothing needs to be stored.
 */
public class QuestRegistry {

    private final CopperHeist plugin;
    private final Map<String, QuestDefinition> quests = new LinkedHashMap<>();
    private int dailyCount = 3;
    private int weeklyCount = 1;

    public QuestRegistry(CopperHeist plugin) {
        this.plugin = plugin;
    }

    public void load() {
        YamlConfiguration yaml = ConfigFiles.load(plugin, "quests.yml");
        quests.clear();
        dailyCount = Math.max(0, yaml.getInt("settings.daily-count", 3));
        weeklyCount = Math.max(0, yaml.getInt("settings.weekly-count", 1));
        ConfigurationSection section = yaml.getConfigurationSection("quests");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection entry = section.getConfigurationSection(id);
                if (entry == null) continue;
                try {
                    QuestDefinition quest = parse(id, entry);
                    quests.put(quest.id(), quest);
                } catch (RuntimeException ex) {
                    plugin.getLogger().warning("Skipping quest '" + id + "' in quests.yml: " + ex.getMessage());
                }
            }
        }
        plugin.getLogger().info("Loaded " + quests.size() + " quest(s).");
    }

    public QuestDefinition get(String id) {
        return id == null ? null : quests.get(id.toLowerCase(Locale.ROOT));
    }

    public List<QuestDefinition> pool(QuestPeriod period) {
        List<QuestDefinition> found = new ArrayList<>();
        for (QuestDefinition quest : quests.values()) {
            if (quest.period() == period) found.add(quest);
        }
        return found;
    }

    /** The quests running in the period that contains {@code epochMillis}. */
    public List<QuestDefinition> active(QuestPeriod period, long epochMillis) {
        int count = period == QuestPeriod.DAILY ? dailyCount : weeklyCount;
        return pick(pool(period), count, period.idAt(epochMillis), period);
    }

    /** Picks {@code count} quests: the same pool, period number and kind of period always give the same result, whatever order the pool is in. */
    public static List<QuestDefinition> pick(List<QuestDefinition> pool, int count, long periodId, QuestPeriod period) {
        List<QuestDefinition> shuffled = new ArrayList<>(pool);
        shuffled.sort(Comparator.comparing(QuestDefinition::id));
        Collections.shuffle(shuffled, new Random(periodId * 31L + period.ordinal()));
        return new ArrayList<>(shuffled.subList(0, Math.clamp(count, 0, shuffled.size())));
    }

    /** Builds one quest from its quests.yml section; throws IllegalArgumentException with a readable reason if it is unusable. */
    public static QuestDefinition parse(String rawId, ConfigurationSection s) {
        QuestPeriod period = QuestPeriod.parse(s.getString("period"));
        if (period == null) throw new IllegalArgumentException("period must be daily or weekly");
        QuestTrigger trigger = QuestTrigger.parse(s.getString("trigger"));
        if (trigger == null) throw new IllegalArgumentException("unknown trigger '" + s.getString("trigger") + "'");
        int target = s.getInt("target", 0);
        if (target < 1) throw new IllegalArgumentException("target must be at least 1");
        String cosmetic = s.getString("reward.cosmetic");
        return new QuestDefinition(rawId.toLowerCase(Locale.ROOT), period, s.getString("name", rawId), s.getString("description", ""),
                trigger, target, Math.max(0, s.getLong("reward.xp", 0)), Math.max(0, s.getLong("reward.coins", 0)),
                cosmetic == null || cosmetic.isBlank() ? null : cosmetic);
    }
}
