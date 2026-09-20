package me.psikuvit.copperHeist.cosmetics;

import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.config.ConfigFiles;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Loads cosmetics.yml into {@link CosmeticDefinition}s. A cosmetic with a mistake (unknown category, effect that doesn't exist or
 * doesn't fit its category, bad params) is skipped with one readable warning instead of breaking the rest. Loaded after every plugin
 * has enabled, so effects registered by other plugins are known by then.
 */
public class CosmeticRegistry {

    private final CopperHeist plugin;
    private final EffectRegistry effects;
    private final Map<String, CosmeticDefinition> cosmetics = new LinkedHashMap<>();

    public CosmeticRegistry(CopperHeist plugin, EffectRegistry effects) {
        this.plugin = plugin;
        this.effects = effects;
    }

    public void load() {
        YamlConfiguration yaml = ConfigFiles.load(plugin, "cosmetics.yml");
        cosmetics.clear();
        ConfigurationSection section = yaml.getConfigurationSection("cosmetics");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection entry = section.getConfigurationSection(id);
                if (entry == null) continue;
                try {
                    register(parse(id, entry, effects));
                } catch (RuntimeException ex) {
                    plugin.getLogger().warning("Skipping cosmetic '" + id + "' in cosmetics.yml: " + ex.getMessage());
                }
            }
        }
        plugin.getLogger().info("Loaded " + cosmetics.size() + " cosmetic(s).");
    }

    public void register(CosmeticDefinition cosmetic) {
        cosmetics.put(cosmetic.id(), cosmetic);
    }

    public CosmeticDefinition get(String id) {
        return id == null ? null : cosmetics.get(id.toLowerCase(Locale.ROOT));
    }

    public List<CosmeticDefinition> all() {
        return new ArrayList<>(cosmetics.values());
    }

    public List<CosmeticDefinition> inCategory(CosmeticCategory category) {
        List<CosmeticDefinition> found = new ArrayList<>();
        for (CosmeticDefinition cosmetic : cosmetics.values()) {
            if (cosmetic.category() == category) found.add(cosmetic);
        }
        return found;
    }

    /** Builds one cosmetic from its cosmetics.yml section; throws IllegalArgumentException with a readable reason if it is unusable. */
    public static CosmeticDefinition parse(String rawId, ConfigurationSection s, EffectRegistry effects) {
        String id = rawId.toLowerCase(Locale.ROOT);
        CosmeticCategory category = CosmeticCategory.parse(s.getString("category"));
        if (category == null) throw new IllegalArgumentException("unknown category '" + s.getString("category") + "'");

        String effect = s.getString("effect");
        if (effect != null) effect = effect.toLowerCase(Locale.ROOT);
        if (effect == null && category != CosmeticCategory.TITLE) throw new IllegalArgumentException("no effect set");

        Material icon = Material.matchMaterial(s.getString("icon", "PAPER"));
        if (icon == null) throw new IllegalArgumentException("unknown icon material '" + s.getString("icon") + "'");

        Map<String, Object> params = new LinkedHashMap<>();
        ConfigurationSection paramSection = s.getConfigurationSection("params");
        if (paramSection != null) params.putAll(paramSection.getValues(false));

        List<String> description = s.getStringList("description");
        String permission = s.getString("permission");
        CosmeticDefinition cosmetic = new CosmeticDefinition(id, category, effect, s.getString("name", id), description, icon,
                Rarity.parse(s.getString("rarity")), Math.max(0, s.getLong("price", 0)), Math.max(0, s.getInt("level", 0)),
                permission == null || permission.isBlank() ? null : permission, s.getBoolean("free", false),
                s.getBoolean("hidden", false), params);

        if (effect != null) {
            EffectProvider provider = effects.get(effect);
            if (provider == null) throw new IllegalArgumentException("unknown effect '" + effect + "' (available: " + String.join(", ", effects.ids()) + ")");
            if (!provider.categories().contains(category)) {
                throw new IllegalArgumentException("effect '" + effect + "' can't be used for category '" + category.id() + "'");
            }
            provider.validate(cosmetic);
        }
        return cosmetic;
    }
}
