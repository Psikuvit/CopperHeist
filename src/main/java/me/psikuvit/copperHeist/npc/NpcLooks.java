package me.psikuvit.copperHeist.npc;

import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.config.ConfigFiles;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The library of named NPC appearances in npcs.yml. A look with a mistake (an NPC type that doesn't exist) is skipped with a warning; the
 * rest still load. {@code defaults.shop} and {@code defaults.navigator} name the looks used when nothing more specific is chosen.
 */
public class NpcLooks {

    private static final Set<String> RESERVED = Set.of("type", "name");

    private final CopperHeist plugin;
    private final Map<String, NpcLook> looks = new LinkedHashMap<>();
    private final Map<String, String> defaults = new LinkedHashMap<>();

    public NpcLooks(CopperHeist plugin) {
        this.plugin = plugin;
    }

    public void load() {
        YamlConfiguration yaml = ConfigFiles.load(plugin, "npcs.yml");
        looks.clear();
        defaults.clear();
        ConfigurationSection section = yaml.getConfigurationSection("looks");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection entry = section.getConfigurationSection(id);
                if (entry == null) continue;
                try {
                    NpcLook look = parse(id, entry, type -> plugin.providers().npc().has(type));
                    looks.put(look.id(), look);
                } catch (RuntimeException ex) {
                    plugin.getLogger().warning("Skipping NPC look '" + id + "' in npcs.yml: " + ex.getMessage());
                }
            }
        }
        ConfigurationSection defaultSection = yaml.getConfigurationSection("defaults");
        if (defaultSection != null) {
            for (String role : defaultSection.getKeys(false)) {
                String id = defaultSection.getString(role, "").toLowerCase(Locale.ROOT);
                if (id.isBlank()) continue;
                if (looks.containsKey(id)) defaults.put(role.toLowerCase(Locale.ROOT), id);
                else plugin.getLogger().warning("npcs.yml defaults." + role + " names the look '" + id + "' which does not exist.");
            }
        }
        plugin.getLogger().info("Loaded " + looks.size() + " NPC look(s).");
    }

    public NpcLook get(String id) {
        return id == null ? null : looks.get(id.toLowerCase(Locale.ROOT));
    }

    public Set<String> ids() {
        return looks.keySet();
    }

    /** The look used for a role ("shop" or "navigator") when nothing more specific is chosen, or null to use the old npc.* config. */
    public NpcLook defaultFor(String role) {
        return get(defaults.get(role));
    }

    /** Builds one look from its npcs.yml section; throws IllegalArgumentException with a readable reason if it can't be used. */
    public static NpcLook parse(String rawId, ConfigurationSection s, Predicate<String> knownType) {
        String type = s.getString("type");
        if (type != null) {
            type = type.toLowerCase(Locale.ROOT);
            if (!knownType.test(type)) throw new IllegalArgumentException("unknown NPC type '" + type + "'");
        }
        Map<String, Object> options = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : s.getValues(true).entrySet()) {
            if (entry.getValue() instanceof ConfigurationSection || RESERVED.contains(entry.getKey())) continue;
            options.put(entry.getKey().toLowerCase(Locale.ROOT), entry.getValue());
        }
        return new NpcLook(rawId.toLowerCase(Locale.ROOT), type, s.getString("name"), options);
    }
}
