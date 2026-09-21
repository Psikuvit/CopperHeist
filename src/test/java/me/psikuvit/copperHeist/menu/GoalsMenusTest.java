package me.psikuvit.copperHeist.menu;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GoalsMenusTest {

    @Test
    void theProgressBarFillsInProportionAndNeverOverflows() {
        assertEquals("<ok></ok><dim>░░░░</dim>", Gui.bar(0, 4));
        assertEquals("<ok>██</ok><dim>░░</dim>", Gui.bar(0.5, 4));
        assertEquals("<ok>████</ok><dim></dim>", Gui.bar(1, 4));
        assertEquals("<ok>████</ok><dim></dim>", Gui.bar(7.5, 4), "more than done is still a full bar");
        assertEquals("<ok></ok><dim>░░░░</dim>", Gui.bar(-3, 4), "and less than nothing an empty one");
    }

    @Test
    void everyMessageTheGoalsMenusUseExists() throws Exception {
        YamlConfiguration lang;
        try (InputStream in = GoalsMenusTest.class.getClassLoader().getResourceAsStream("lang/en.yml")) {
            lang = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        List<String> keys = List.of(
                "quests.menu.title", "quests.menu.none", "quests.menu.done", "quests.menu.reward", "quests.menu.resets",
                "quests.period.daily", "quests.period.weekly", "quests.completed",
                "achievements.menu.title", "achievements.menu.button-name", "achievements.menu.button-lore", "achievements.menu.unlocked",
                "achievements.menu.reward", "achievements.menu.secret-name", "achievements.menu.secret-lore", "achievements.unlocked",
                "achievements.title", "achievements.subtitle",
                "daily.menu.ready", "daily.menu.claimed", "daily.menu.streak", "daily.menu.reward", "daily.menu.click", "daily.menu.next",
                "daily.available", "daily.hover", "daily.claimed", "daily.already",
                "lobby.goals-name", "lobby.goals-lore", "gui.back", "gui.page.info");
        for (String key : keys) assertTrue(lang.isString(key), "lang/en.yml has no " + key);
    }
}
