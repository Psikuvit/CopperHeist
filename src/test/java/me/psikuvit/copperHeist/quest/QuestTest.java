package me.psikuvit.copperHeist.quest;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestTest {

    private static final long DAY = 86_400_000L;

    private static ConfigurationSection section(String yaml) throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString(yaml);
        return config;
    }

    private static QuestDefinition quest(String id, QuestPeriod period) {
        return new QuestDefinition(id, period, id, "", QuestTrigger.MATCHES, 1, 10, 5, null);
    }

    @Test
    void aDayChangesAtMidnightUtc() {
        assertEquals(20_000, QuestPeriod.DAILY.idAt(20_000 * DAY));
        assertEquals(19_999, QuestPeriod.DAILY.idAt(20_000 * DAY - 1));
        assertEquals(DAY, QuestPeriod.DAILY.millisLeft(20_000 * DAY));
        assertEquals(1, QuestPeriod.DAILY.millisLeft(20_001 * DAY - 1));
    }

    @Test
    void aWeekChangesAtMidnightUtcOnMonday() {
        long monday = 20_003 * DAY; // day 0 (1 Jan 1970) was a Thursday, so a Monday is a day where day % 7 == 4
        assertEquals(4, 20_003 % 7, "sanity: the chosen day is a Monday");
        long week = QuestPeriod.WEEKLY.idAt(monday);
        assertEquals(week, QuestPeriod.WEEKLY.idAt(monday + 6 * DAY + DAY - 1), "still the same week on Sunday night");
        assertEquals(week + 1, QuestPeriod.WEEKLY.idAt(monday + 7 * DAY));
        assertEquals(week - 1, QuestPeriod.WEEKLY.idAt(monday - 1));
        assertEquals(7 * DAY, QuestPeriod.WEEKLY.millisLeft(monday));
        assertEquals(1, QuestPeriod.WEEKLY.millisLeft(monday + 7 * DAY - 1));
    }

    @Test
    void triggersCountWhatAMatchDid() {
        MatchStats stats = new MatchStats(true, false, 40, 2, 3, 4, 1, 0);
        assertEquals(1, QuestTrigger.MATCHES.amount(stats));
        assertEquals(1, QuestTrigger.WINS.amount(stats));
        assertEquals(0, QuestTrigger.MVPS.amount(stats));
        assertEquals(40, QuestTrigger.LOOT_DELIVERED.amount(stats));
        assertEquals(2, QuestTrigger.STEALS.amount(stats));
        assertEquals(3, QuestTrigger.KILLS.amount(stats));
        assertEquals(4, QuestTrigger.SCRAPES.amount(stats));
        assertEquals(1, QuestTrigger.RELICS.amount(stats));
        assertEquals(0, QuestTrigger.DRILLS.amount(stats));
        assertEquals(0, QuestTrigger.WINS.amount(new MatchStats(false, false, 0, 0, 0, 0, 0, 0)));
    }

    @Test
    void triggersParseByTheirFileKey() {
        assertEquals(QuestTrigger.LOOT_DELIVERED, QuestTrigger.parse("loot-delivered"));
        assertEquals(QuestTrigger.LOOT_DELIVERED, QuestTrigger.parse("LOOT_DELIVERED"));
        assertNull(QuestTrigger.parse("nonsense"));
        assertEquals(QuestPeriod.WEEKLY, QuestPeriod.parse("Weekly"));
        assertNull(QuestPeriod.parse("monthly"));
    }

    @Test
    void aQuestParsesOrExplainsWhyNot() throws Exception {
        QuestDefinition q = QuestRegistry.parse("Loot_Runner", section("""
                period: daily
                name: "<accent>Runner"
                description: "Deliver loot."
                trigger: loot-delivered
                target: 60
                reward: { xp: 60, coins: 30, cosmetic: flame_trail }
                """));
        assertEquals("loot_runner", q.id());
        assertEquals(QuestPeriod.DAILY, q.period());
        assertEquals(QuestTrigger.LOOT_DELIVERED, q.trigger());
        assertEquals(60, q.target());
        assertEquals(30, q.coins());
        assertEquals("flame_trail", q.cosmetic());

        assertThrows(IllegalArgumentException.class, () -> QuestRegistry.parse("a", section("period: hourly\ntrigger: wins\ntarget: 1")));
        assertThrows(IllegalArgumentException.class, () -> QuestRegistry.parse("a", section("period: daily\ntrigger: flying\ntarget: 1")));
        assertThrows(IllegalArgumentException.class, () -> QuestRegistry.parse("a", section("period: daily\ntrigger: wins\ntarget: 0")));
    }

    @Test
    void todaysPickIsTheSameForEveryoneWhateverTheOrder() {
        List<QuestDefinition> pool = new ArrayList<>();
        for (int i = 0; i < 8; i++) pool.add(quest("q" + i, QuestPeriod.DAILY));

        List<QuestDefinition> pick = QuestRegistry.pick(pool, 3, 20_000, QuestPeriod.DAILY);
        assertEquals(3, pick.size());
        assertEquals(pick, QuestRegistry.pick(pool, 3, 20_000, QuestPeriod.DAILY));

        List<QuestDefinition> reversed = new ArrayList<>(pool);
        Collections.reverse(reversed);
        assertEquals(pick, QuestRegistry.pick(reversed, 3, 20_000, QuestPeriod.DAILY));

        boolean changes = false;
        for (long day = 20_001; day < 20_010; day++) changes |= !pick.equals(QuestRegistry.pick(pool, 3, day, QuestPeriod.DAILY));
        assertTrue(changes, "different days give different quests");
    }

    @Test
    void pickHandlesSmallPools() {
        assertTrue(QuestRegistry.pick(List.of(), 3, 1, QuestPeriod.DAILY).isEmpty());
        assertEquals(1, QuestRegistry.pick(List.of(quest("only", QuestPeriod.DAILY)), 3, 1, QuestPeriod.DAILY).size());
        assertTrue(QuestRegistry.pick(List.of(quest("a", QuestPeriod.DAILY)), 0, 1, QuestPeriod.DAILY).isEmpty());
        assertNotNull(QuestRegistry.pick(List.of(quest("a", QuestPeriod.DAILY)), -1, 1, QuestPeriod.DAILY));
    }

    @Test
    void theShippedQuestsAreValidAndEnoughToRotate() throws Exception {
        YamlConfiguration yaml;
        try (InputStream in = QuestTest.class.getClassLoader().getResourceAsStream("quests.yml")) {
            yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        ConfigurationSection quests = yaml.getConfigurationSection("quests");
        int daily = 0;
        int weekly = 0;
        for (String id : quests.getKeys(false)) {
            QuestDefinition quest = QuestRegistry.parse(id, quests.getConfigurationSection(id));
            assertTrue(quest.xp() > 0 || quest.coins() > 0, id + " should pay something");
            if (quest.period() == QuestPeriod.DAILY) daily++;
            else weekly++;
        }
        assertTrue(daily > yaml.getInt("settings.daily-count"), "more daily quests than run at once, so they rotate");
        assertTrue(weekly > yaml.getInt("settings.weekly-count"), "more weekly quests than run at once, so they rotate");
        assertNotEquals(0, daily);
    }
}
