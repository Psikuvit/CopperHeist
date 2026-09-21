package me.psikuvit.copperHeist.achievement;

import com.google.gson.JsonParser;
import me.psikuvit.copperHeist.stats.Stat;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AchievementTest {

    private static YamlConfiguration resource(String name) throws Exception {
        try (InputStream in = AchievementTest.class.getClassLoader().getResourceAsStream(name)) {
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    private static ConfigurationSection section(String yaml) throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString(yaml);
        return config;
    }

    @Test
    void aStatAchievementParses() throws Exception {
        AchievementDefinition a = AchievementRegistry.parse("Winner", section("""
                name: "<accent>Winner"
                description: "Win 10 matches."
                stat: wins
                target: 10
                secret: true
                reward: { xp: 250, coins: 150, cosmetic: victory_fireworks }
                """));
        assertEquals("winner", a.id());
        assertEquals(Stat.WINS, a.stat());
        assertEquals(10, a.target());
        assertTrue(a.secret());
        assertEquals("victory_fireworks", a.cosmetic());
        assertEquals("ach.winner", a.profileId());
        assertFalse(a.isLevel());
    }

    @Test
    void theToastLooksRightAndCarriesTheDisplay() throws Exception {
        String json = AchievementDisplay.toastJson("minecraft:copper_ingot", "{\"text\":\"First Blood\",\"color\":\"gold\"}",
                "{\"text\":\"Win a match.\"}", "challenge");
        var root = JsonParser.parseString(json).getAsJsonObject();
        var display = root.getAsJsonObject("display");
        assertEquals("minecraft:copper_ingot", display.getAsJsonObject("icon").get("id").getAsString());
        assertEquals("First Blood", display.getAsJsonObject("title").get("text").getAsString());
        assertEquals("challenge", display.get("frame").getAsString());
        assertTrue(display.get("show_toast").getAsBoolean());
        assertFalse(display.get("announce_to_chat").getAsBoolean(), "the toast must not also announce in chat");
        assertTrue(display.get("hidden").getAsBoolean(), "and must not linger in the advancements screen");
        assertEquals("minecraft:impossible", root.getAsJsonObject("criteria").getAsJsonObject("done").get("trigger").getAsString());
    }

    @Test
    void frameAndIconAreValidated() throws Exception {
        AchievementDefinition ok = AchievementRegistry.parse("a", section("stat: wins\ntarget: 1\nicon: gold_ingot\nframe: Challenge"));
        assertEquals("challenge", ok.frame());
        assertEquals(Material.GOLD_INGOT, ok.icon());
        assertEquals(Material.NETHER_STAR, AchievementRegistry.parse("b", section("stat: wins\ntarget: 1")).icon(), "a default icon");
        assertThrows(IllegalArgumentException.class, () -> AchievementRegistry.parse("c", section("stat: wins\ntarget: 1\nicon: NOPE")));
        assertThrows(IllegalArgumentException.class, () -> AchievementRegistry.parse("d", section("stat: wins\ntarget: 1\nframe: huge")));
    }

    @Test
    void aLevelAchievementHasNoStat() throws Exception {
        AchievementDefinition a = AchievementRegistry.parse("lvl", section("stat: level\ntarget: 10"));
        assertNull(a.stat());
        assertTrue(a.isLevel());
        assertEquals("achievements.level", AchievementService.statLangKey(a));
    }

    @Test
    void mistakesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> AchievementRegistry.parse("a", section("stat: nonsense\ntarget: 1")));
        assertThrows(IllegalArgumentException.class, () -> AchievementRegistry.parse("a", section("stat: wins\ntarget: 0")));
        assertThrows(IllegalArgumentException.class, () -> AchievementRegistry.parse("a", section("stat: wins")));
        assertThrows(IllegalArgumentException.class, () -> AchievementRegistry.parse("a", section("stat: coins\ntarget: 5")),
                "coins go down when spent, so they can't be an achievement");
    }

    @Test
    void everyShippedAchievementIsValid() throws Exception {
        ConfigurationSection achievements = resource("achievements.yml").getConfigurationSection("achievements");
        YamlConfiguration lang = resource("lang/en.yml");
        assertTrue(achievements.getKeys(false).size() >= 15);
        for (String id : achievements.getKeys(false)) {
            AchievementDefinition a = AchievementRegistry.parse(id, achievements.getConfigurationSection(id));
            assertTrue(a.xp() > 0 || a.coins() > 0, id + " should pay something");
            assertTrue(lang.isString(AchievementService.statLangKey(a)), id + ": no language key for what it counts");
        }
    }

    @Test
    void everyCosmeticAGoalGivesExists() throws Exception {
        Set<String> cosmetics = resource("cosmetics.yml").getConfigurationSection("cosmetics").getKeys(false);
        Set<String> given = new HashSet<>();

        ConfigurationSection achievements = resource("achievements.yml").getConfigurationSection("achievements");
        for (String id : achievements.getKeys(false)) given.add(achievements.getString(id + ".reward.cosmetic"));
        ConfigurationSection quests = resource("quests.yml").getConfigurationSection("quests");
        for (String id : quests.getKeys(false)) given.add(quests.getString(id + ".reward.cosmetic"));
        ConfigurationSection levels = resource("progress.yml").getConfigurationSection("level-rewards");
        if (levels != null) for (String level : levels.getKeys(false)) given.add(levels.getString(level + ".cosmetic"));

        for (Map<?, ?> reward : resource("daily.yml").getMapList("rewards")) given.add(reward.get("cosmetic") == null ? null : String.valueOf(reward.get("cosmetic")));

        given.remove(null);
        for (String cosmetic : given) assertTrue(cosmetics.contains(cosmetic), "a goal gives the cosmetic '" + cosmetic + "' which isn't in cosmetics.yml");
    }
}
