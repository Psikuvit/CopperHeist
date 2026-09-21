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
 * The library of named hub navigator appearances in navigator-looks.yml (shop keepers are not here: they are dressed by cosmetics).
 * A look with a mistake (an NPC type that doesn't exist) is skipped with a warning; the rest still load. {@code default} names the look
 * used when nothing more specific is chosen.
 */
public class NpcLooks {

    private static final Set<String> RESERVED = Set.of("type", "name");

    private final CopperHeist plugin;
    private final String file;
    private final Map<String, NpcLook> looks = new LinkedHashMap<>();
    private String defaultId;

    public NpcLooks(CopperHeist plugin, String file) {
        this.plugin = plugin;
        this.file = file;
    }

    public void load() {
        YamlConfiguration yaml = ConfigFiles.load(plugin, file);
        looks.clear();
        defaultId = null;
        ConfigurationSection section = yaml.getConfigurationSection("looks");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection entry = section.getConfigurationSection(id);
                if (entry == null) continue;
                try {
                    NpcLook look = parse(id, entry, type -> plugin.providers().npc().has(type));
                    looks.put(look.id(), look);
                } catch (RuntimeException ex) {
                    plugin.getLogger().warning("Skipping NPC look '" + id + "' in " + file + ": " + ex.getMessage());
                }
            }
        }
        String wanted = yaml.getString("default", "").toLowerCase(Locale.ROOT);
        if (!wanted.isBlank()) {
            if (looks.containsKey(wanted)) defaultId = wanted;
            else plugin.getLogger().warning(file + " default names the look '" + wanted + "' which does not exist.");
        }
        plugin.getLogger().info("Loaded " + looks.size() + " NPC look(s) from " + file + ".");
    }

    public NpcLook get(String id) {
        return id == null ? null : looks.get(id.toLowerCase(Locale.ROOT));
    }

    public Set<String> ids() {
        return looks.keySet();
    }

    /** The look used when none is chosen, or null to use the plain NPC settings from config.yml. */
    public NpcLook defaultLook() {
        return get(defaultId);
    }

    /** The named look, else the default one, else null. */
    public NpcLook choose(String id) {
        NpcLook look = get(id);
        return look != null ? look : defaultLook();
    }

    /** Builds one look from its section; throws IllegalArgumentException with a readable reason if it can't be used. */
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
