package me.psikuvit.copperHeist.shop;

import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.config.ConfigFiles;
import me.psikuvit.copperHeist.game.GameState;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Loads shop.yml: the entries on sale and the menu's title and frame. An entry with a mistake (unknown material or min-phase) is skipped
 * with a warning; the rest still load.
 */
public class ShopRegistry {

    private final CopperHeist plugin;
    private final Map<String, ShopEntry> entries = new LinkedHashMap<>();
    private FileConfiguration config;

    public ShopRegistry(CopperHeist plugin) {
        this.plugin = plugin;
    }

    public void load() {
        config = ConfigFiles.load(plugin, "shop.yml");
        entries.clear();
        ConfigurationSection section = config.getConfigurationSection("items");
        if (section == null) return;
        for (String id : section.getKeys(false)) {
            ConfigurationSection item = section.getConfigurationSection(id);
            if (item == null) continue;
            try {
                ShopEntry entry = parse(id.toLowerCase(Locale.ROOT), item);
                entries.put(entry.id(), entry);
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("Skipping shop item '" + id + "' in shop.yml: " + ex.getMessage());
            }
        }
        plugin.getLogger().info("Loaded " + entries.size() + " shop item(s).");
    }

    public List<ShopEntry> all() {
        return new ArrayList<>(entries.values());
    }

    public ShopEntry get(String id) {
        return id == null ? null : entries.get(id.toLowerCase(Locale.ROOT));
    }

    /** The menu title from shop.yml (a translation's shop.title wins over it; see {@link ShopService#menuTitle}). */
    public String menuTitle() {
        return config.getString("menu.title", "<gold><bold>Copper Heist Shop");
    }

    /** The material name of the shop menu's frame (shop.yml menu.border); the menu falls back to grey panes if it isn't valid. */
    public String menuBorder() {
        return config.getString("menu.border", "GRAY_STAINED_GLASS_PANE");
    }

    private ShopEntry parse(String id, ConfigurationSection s) {
        Material material = Material.matchMaterial(s.getString("material", "PAPER"));
        if (material == null) throw new IllegalArgumentException("unknown material '" + s.getString("material") + "'");

        GameState minPhase = null;
        String phase = s.getString("min-phase");
        if (phase != null && !phase.isBlank()) {
            try {
                minPhase = GameState.valueOf(phase.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("unknown min-phase '" + phase + "' (setup, collection, heist or final_rush)");
            }
        }
        Map<String, Object> params = new LinkedHashMap<>();
        for (String key : s.getKeys(false)) params.put(key, s.get(key));
        return new ShopEntry(id, s.getString("action", "give-item"), material, s.getInt("amount", 1), s.getInt("cost", 0),
                s.getString("name", id), s.getStringList("lore"), minPhase, s.getInt("max-per-player", 0),
                s.getInt("cooldown-seconds", 0), s.getInt("slot", -1), params);
    }
}
