package me.psikuvit.copperHeist.config;

import me.psikuvit.copperHeist.CopperHeist;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Presets are partial copies of config.yml (presets/&lt;name&gt;.yml) that an arena can switch on: only the keys
 * a preset sets change, everything else still comes from config.yml. The three bundled presets are copied out
 * on first run; add your own by dropping another file in the folder and using /ch reload.
 */
public final class PresetRegistry {

    private static final List<String> BUNDLED = List.of("classic", "quick", "hardcore");

    private final CopperHeist plugin;
    private final Map<String, YamlConfiguration> presets = new LinkedHashMap<>();

    public PresetRegistry(CopperHeist plugin) {
        this.plugin = plugin;
    }

    public void load() {
        presets.clear();
        File folder = new File(plugin.getDataFolder(), "presets");
        for (String name : BUNDLED) {
            if (!new File(folder, name + ".yml").exists()) plugin.saveResource("presets/" + name + ".yml", false);
        }
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return;
        Arrays.sort(files);
        for (File file : files) {
            String name = file.getName().substring(0, file.getName().length() - 4).toLowerCase(Locale.ROOT);
            presets.put(name, ConfigFiles.loadFile(plugin, file, null));
        }
    }

    /** The preset's settings, or null for no/unknown preset. */
    public YamlConfiguration get(String name) {
        return name == null ? null : presets.get(name.toLowerCase(Locale.ROOT));
    }

    public List<String> names() {
        return new ArrayList<>(presets.keySet());
    }
}
