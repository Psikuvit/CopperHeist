package me.psikuvit.copperHeist.cosmetics;

import me.psikuvit.copperHeist.cosmetics.effect.CosmeticEffects;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The cosmetics that ship in cosmetics.yml must all load with the effects that ship with the plugin. */
class BundledCosmeticsTest {

    private static final Pattern FIXED_COLOR = Pattern.compile("</?(gold|yellow|gray|grey|white|dark_gray|green|red|light_purple|blue|aqua)>");

    private static ConfigurationSection cosmetics() throws Exception {
        try (InputStream in = BundledCosmeticsTest.class.getClassLoader().getResourceAsStream("cosmetics.yml")) {
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)).getConfigurationSection("cosmetics");
        }
    }

    private static EffectRegistry effects() {
        EffectRegistry registry = new EffectRegistry();
        CosmeticEffects.registerAll(null, registry); // the effects only need the plugin once they play, not to check their params
        return registry;
    }

    @Test
    void everyShippedCosmeticParses() throws Exception {
        ConfigurationSection section = cosmetics();
        EffectRegistry effects = effects();
        for (String id : section.getKeys(false)) {
            CosmeticDefinition cosmetic = CosmeticRegistry.parse(id, section.getConfigurationSection(id), effects);
            assertEquals(id, cosmetic.id());
            assertFalse(FIXED_COLOR.matcher(cosmetic.name()).find(), id + " should use theme tags, not fixed colours");
        }
    }

    @Test
    void everyCategoryHasSomethingToUnlock() throws Exception {
        ConfigurationSection section = cosmetics();
        Set<CosmeticCategory> found = EnumSet.noneOf(CosmeticCategory.class);
        for (String id : section.getKeys(false)) found.add(CosmeticRegistry.parse(id, section.getConfigurationSection(id), effects()).category());
        assertEquals(EnumSet.allOf(CosmeticCategory.class), found);
    }

    @Test
    void newPlayersStartWithSomethingAndThereIsAWayUpTheLadder() throws Exception {
        ConfigurationSection section = cosmetics();
        boolean freeStarter = false;
        boolean cheap = false;
        boolean legendary = false;
        for (String id : section.getKeys(false)) {
            CosmeticDefinition cosmetic = CosmeticRegistry.parse(id, section.getConfigurationSection(id), effects());
            freeStarter |= cosmetic.free() && cosmetic.level() == 0;
            cheap |= cosmetic.purchasable() && cosmetic.price() <= 200 && cosmetic.level() <= 2;
            legendary |= cosmetic.rarity() == Rarity.LEGENDARY;
            assertTrue(cosmetic.purchasable() || cosmetic.free(), id + " must be buyable or free, or nobody can get it");
        }
        assertTrue(freeStarter, "a free cosmetic to start with");
        assertTrue(cheap, "something a new player can afford after one match");
        assertTrue(legendary, "a long-term goal");
    }
}
