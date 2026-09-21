package me.psikuvit.copperHeist.npc;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcLooksTest {

    private static final Set<String> TYPES = Set.of("villager", "mannequin", "armor-stand", "interaction", "none");
    private static final Pattern FIXED_COLOR = Pattern.compile("</?(gold|yellow|gray|grey|white|dark_gray|green|red|light_purple|blue|aqua)>");

    private static YamlConfiguration bundled() throws Exception {
        try (InputStream in = NpcLooksTest.class.getClassLoader().getResourceAsStream("npcs.yml")) {
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    private static ConfigurationSection section(String yaml) throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString(yaml);
        return config;
    }

    @Test
    void optionsAreFlatAndDotted() throws Exception {
        NpcLook look = NpcLooks.parse("Knight", section("""
                type: armor-stand
                name: "{team} Knight"
                head: IRON_HELMET
                armor: { chest: IRON_CHESTPLATE, color: "#B87333" }
                small: true
                level: 3
                """), TYPES::contains);
        assertEquals("knight", look.id());
        assertEquals("armor-stand", look.type());
        assertEquals("{team} Knight", look.name());
        assertEquals("IRON_HELMET", look.string("head"));
        assertEquals("IRON_CHESTPLATE", look.string("armor.chest"));
        assertEquals("#B87333", look.string("armor.color"));
        assertTrue(look.bool("small", false));
        assertEquals(3, look.integer("level", 1));
        assertFalse(look.has("type"), "type and name are not options");
        assertNull(look.string("missing"));
    }

    @Test
    void anUnknownNpcTypeIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> NpcLooks.parse("x", section("type: dragon"), TYPES::contains));
    }

    @Test
    void aLookWithoutATypeUsesTheConfiguredOne() throws Exception {
        assertNull(NpcLooks.parse("plain", section("profession: farmer"), TYPES::contains).type());
    }

    @Test
    void defaultsAndFallbacksInASpec() throws Exception {
        NpcLook look = NpcLooks.parse("v", section("type: villager\nprofession: armorer\nbaby: true"), TYPES::contains);
        assertEquals("armorer", look.string("profession"));
        assertTrue(look.bool("baby", false));
        assertEquals(5, look.integer("missing", 5));
        assertEquals(2, new NpcLook("x", null, null, Map.of("level", "2")).integer("level", 9), "numbers written as text still work");
        assertEquals(9, new NpcLook("x", null, null, Map.of("level", "many")).integer("level", 9));
    }

    @Test
    void everyShippedLookParsesAndItsGearExists() throws Exception {
        YamlConfiguration yaml = bundled();
        ConfigurationSection looks = yaml.getConfigurationSection("looks");
        assertNotNull(looks);
        assertTrue(looks.getKeys(false).size() >= 15);
        for (String id : looks.getKeys(false)) {
            NpcLook look = NpcLooks.parse(id, looks.getConfigurationSection(id), TYPES::contains);
            if (look.name() != null) assertFalse(FIXED_COLOR.matcher(look.name()).find(), id + " should use theme tags");
            for (String key : List.of("head", "armor.chest", "armor.legs", "armor.feet", "main-hand", "off-hand")) {
                if (look.has(key)) assertNotNull(Material.matchMaterial(look.string(key)), id + ": unknown material " + look.string(key));
            }
        }
    }

    @Test
    void theDefaultsNameLooksThatExistAndCoverEveryNpcKind() throws Exception {
        YamlConfiguration yaml = bundled();
        ConfigurationSection looks = yaml.getConfigurationSection("looks");
        for (String role : List.of("shop", "navigator")) {
            assertTrue(looks.contains(yaml.getString("defaults." + role)), "defaults." + role + " must name a look");
        }
        Set<String> types = new HashSet<>();
        for (String id : looks.getKeys(false)) types.add(looks.getString(id + ".type"));
        assertTrue(types.containsAll(Set.of("villager", "mannequin", "armor-stand")), "a look for each kind of keeper");
    }
}
