package me.psikuvit.copperHeist.config;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsTest {

    private static YamlConfiguration yaml(String text) {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(text);
        } catch (InvalidConfigurationException ex) {
            throw new IllegalArgumentException(ex);
        }
        return yaml;
    }

    @Test
    void mostSpecificLayerWins() throws Exception {
        YamlConfiguration global = yaml("match:\n  duration-seconds: 900\n  min-players: 6\n");
        YamlConfiguration preset = yaml("match:\n  duration-seconds: 480\n");
        YamlConfiguration arena = yaml("match:\n  min-players: 2\n");
        Settings settings = Settings.of(() -> global).withOverride(() -> preset).withOverride(() -> arena);

        assertEquals(2, settings.getInt("match.min-players", 6));
        assertEquals(480, settings.getInt("match.duration-seconds", 900));
    }

    @Test
    void presetOverridesGlobalButOnlyForTheKeysItSets() throws Exception {
        YamlConfiguration global = yaml("match:\n  duration-seconds: 900\n  respawn-delay-seconds: 6\n");
        YamlConfiguration preset = yaml("match:\n  respawn-delay-seconds: 0\n");
        Settings settings = Settings.of(() -> global).withOverride(() -> preset);

        assertEquals(0, settings.getInt("match.respawn-delay-seconds", 6));
        assertEquals(900, settings.getInt("match.duration-seconds", 1));
    }

    @Test
    void missingKeysFallBackToTheCallersDefault() throws Exception {
        Settings settings = Settings.of(() -> yaml("a: 1\n"));

        assertEquals(42, settings.getInt("nothing.here", 42));
        assertEquals("fallback", settings.getString("nothing.here", "fallback"));
        assertFalse(settings.getBoolean("nothing.here", false));
        assertFalse(settings.has("nothing.here"));
    }

    @Test
    void aMissingOrNullLayerIsSkipped() throws Exception {
        YamlConfiguration global = yaml("features:\n  alarms: true\n");
        Settings settings = Settings.of(() -> global).withOverride(() -> null);

        assertTrue(settings.getBoolean("features.alarms", false));
    }

    @Test
    void layersAreReadLazilySoReloadsShowUp() throws Exception {
        YamlConfiguration[] current = {yaml("value: 1\n")};
        Settings settings = Settings.of(() -> current[0]);

        assertEquals(1, settings.getInt("value", 0));
        current[0] = yaml("value: 2\n");
        assertEquals(2, settings.getInt("value", 0));
    }
}
