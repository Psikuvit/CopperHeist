package me.psikuvit.copperHeist.progress;

import me.psikuvit.copperHeist.listener.progress.ProgressListener;
import me.psikuvit.copperHeist.stats.Stat;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgressConfigTest {

    private YamlConfiguration bundled() throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("progress.yml")) {
            assertNotNull(in);
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    @Test
    void everyRewardTheListenerReadsExistsInTheBundledFile() throws Exception {
        YamlConfiguration config = bundled();
        for (String reward : new String[]{"participation", "win", "loss", "mvp", "first-win-of-day"}) {
            assertTrue(config.contains("rewards." + reward + ".xp"), "rewards." + reward + ".xp");
            assertTrue(config.contains("rewards." + reward + ".coins"), "rewards." + reward + ".coins");
        }
        for (String per : new String[]{"loot-value", "steal", "relic", "scrape", "kill", "drill"}) {
            assertTrue(config.contains("per." + per + ".xp"), "per." + per + ".xp");
            assertTrue(config.contains("per." + per + ".coins"), "per." + per + ".coins");
        }
    }

    @Test
    void theBundledLevelSettingsBuildAWorkingCurve() throws Exception {
        YamlConfiguration config = bundled();
        LevelCurve curve = new LevelCurve(config.getLong("levels.base-xp"), config.getLong("levels.step-xp"), config.getInt("levels.max-level"));
        assertEquals(1, curve.levelFor(0));
        assertTrue(curve.levelFor(curve.totalFor(10)) == 10);
        assertTrue(config.getMapList("brackets").size() >= 1, "at least one rank bracket");
    }

    @Test
    void theDailyBonusDayChangesAtMidnightUtc() {
        long midnight = 20_000L * 86_400_000L;
        assertEquals(20_000, ProgressListener.dayOf(midnight));
        assertEquals(19_999, ProgressListener.dayOf(midnight - 1));
        assertEquals(20_000, ProgressListener.dayOf(midnight + 86_399_999L));
        assertEquals(20_001, ProgressListener.dayOf(midnight + 86_400_000L));
    }

    @Test
    void levelIsAnAliasForXp() {
        assertEquals(Stat.XP, Stat.fromKey("level"));
        assertEquals(Stat.XP, Stat.fromKey("XP"));
        assertEquals(Stat.WINS, Stat.fromKey("wins"));
        assertNull(Stat.fromKey("nonsense"));
    }
}
