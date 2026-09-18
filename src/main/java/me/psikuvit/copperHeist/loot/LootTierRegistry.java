package me.psikuvit.copperHeist.loot;

import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.arena.Arena;
import me.psikuvit.copperHeist.config.ConfigFiles;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.random.RandomGenerator;

/** Loads loot.yml and answers "which tier do I roll here?". Other plugins may {@link #register} extra tiers. */
public class LootTierRegistry {

    private final CopperHeist plugin;
    private final Map<String, LootTierDefinition> tiers = new LinkedHashMap<>();

    public LootTierRegistry(CopperHeist plugin) {
        this.plugin = plugin;
    }

    public void load() {
        YamlConfiguration yaml = ConfigFiles.load(plugin, "loot.yml");
        tiers.clear();
        ConfigurationSection section = yaml.getConfigurationSection("tiers");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection tier = section.getConfigurationSection(id);
                if (tier == null) continue;
                try {
                    register(parse(id.toLowerCase(Locale.ROOT), tier));
                } catch (RuntimeException ex) {
                    plugin.getLogger().warning("Skipping loot tier '" + id + "' in loot.yml: " + ex.getMessage());
                }
            }
        }
        if (tiers.values().stream().noneMatch(t -> t.weight() > 0 && !t.relic())) {
            plugin.getLogger().severe("loot.yml has no rollable loot tiers - no loot will spawn.");
        }
        plugin.getLogger().info("Loaded " + tiers.size() + " loot tier(s).");
    }

    public void register(LootTierDefinition tier) {
        tiers.put(tier.id(), tier);
    }

    public List<LootTierDefinition> all() {
        return new ArrayList<>(tiers.values());
    }

    public LootTierDefinition get(String id) {
        return id == null ? null : tiers.get(id.toLowerCase(Locale.ROOT));
    }

    /** The tier the timed relic spawns as: the first one flagged {@code relic: true}. */
    public LootTierDefinition relicTier() {
        for (LootTierDefinition tier : tiers.values()) {
            if (tier.relic()) return tier;
        }
        return null;
    }

    /** Weighted roll among the non-relic tiers allowed in {@code zone}. */
    public LootTierDefinition roll(RandomGenerator random, Arena.LootZone zone) {
        return weighted(random, tier -> tier.zones().contains(zone));
    }

    /** Weighted roll among every non-relic tier, ignoring zones - used by the debug giveloot command. */
    public LootTierDefinition rollAny(RandomGenerator random) {
        return weighted(random, tier -> true);
    }

    private LootTierDefinition weighted(RandomGenerator random, java.util.function.Predicate<LootTierDefinition> allowed) {
        List<LootTierDefinition> pool = new ArrayList<>();
        int total = 0;
        for (LootTierDefinition tier : tiers.values()) {
            if (tier.relic() || tier.weight() <= 0 || !allowed.test(tier)) continue;
            pool.add(tier);
            total += tier.weight();
        }
        if (pool.isEmpty()) return null;
        int roll = random.nextInt(total);
        int cumulative = 0;
        for (LootTierDefinition tier : pool) {
            cumulative += tier.weight();
            if (roll < cumulative) return tier;
        }
        return pool.get(0);
    }

    private LootTierDefinition parse(String id, ConfigurationSection s) {
        Material material = Material.matchMaterial(s.getString("material", ""));
        if (material == null) throw new IllegalArgumentException("unknown material '" + s.getString("material") + "'");

        Set<Arena.LootZone> zones = EnumSet.noneOf(Arena.LootZone.class);
        List<String> zoneNames = s.getStringList("zones");
        if (zoneNames.isEmpty()) zones.addAll(EnumSet.allOf(Arena.LootZone.class));
        for (String name : zoneNames) {
            try {
                zones.add(Arena.LootZone.valueOf(name.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("unknown zone '" + name + "' (use common, rare or cache)");
            }
        }
        Integer modelData = s.isSet("custom-model-data") ? s.getInt("custom-model-data") : null;
        return new LootTierDefinition(id, material, Math.max(0, s.getInt("value", 1)), Math.max(0, s.getInt("weight", 0)),
                zones, s.getString("name", "<yellow>" + id + " ({value})"), s.getBoolean("relic", false), modelData,
                s.getBoolean("glint", false));
    }
}
