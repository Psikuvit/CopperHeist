package me.psikuvit.copperHeist.config;

import me.psikuvit.copperHeist.stats.Stat;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every YAML file the plugin ships must parse, and the presets/lang files must line up with config.yml and the stats. */
class BundledResourcesTest {

    private static final List<String> FILES = List.of("config.yml", "roles.yml", "loot.yml", "shop.yml",
            "scoreboard.yml", "guide.yml", "progress.yml", "cosmetics.yml", "navigator-looks.yml", "quests.yml", "achievements.yml", "daily.yml", "lang/en.yml", "presets/classic.yml", "presets/quick.yml", "presets/hardcore.yml");

    private static YamlConfiguration load(String name) throws Exception {
        try (InputStream in = BundledResourcesTest.class.getClassLoader().getResourceAsStream(name)) {
            assertNotNull(in, name + " is missing from the jar");
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            return yaml;
        }
    }

    @Test
    void everyBundledYamlFileParses() throws Exception {
        for (String name : FILES) load(name);
    }

    @Test
    void everyPresetKeyExistsInConfig() throws Exception {
        YamlConfiguration config = load("config.yml");
        for (String preset : List.of("classic", "quick", "hardcore")) {
            YamlConfiguration yaml = load("presets/" + preset + ".yml");
            for (String path : yaml.getKeys(true)) {
                if (yaml.get(path) instanceof ConfigurationSection) continue;
                assertTrue(config.contains(path), "preset " + preset + " sets " + path + " which config.yml does not have");
            }
        }
    }

    @Test
    void everyStatHasADisplayName() throws Exception {
        YamlConfiguration lang = load("lang/en.yml");
        for (Stat stat : Stat.values()) {
            assertTrue(lang.isString(stat.langKey()), "lang/en.yml has no " + stat.langKey());
        }
    }
}
