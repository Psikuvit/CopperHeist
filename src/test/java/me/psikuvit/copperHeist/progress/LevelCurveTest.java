package me.psikuvit.copperHeist.progress;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LevelCurveTest {

    private final LevelCurve curve = new LevelCurve(100, 30, 100);

    @Test
    void levelOneNeedsNoXp() {
        assertEquals(0, curve.totalFor(1));
        assertEquals(1, curve.levelFor(0));
    }

    @Test
    void eachLevelCostsBasePlusStepPerLevel() {
        assertEquals(100, curve.xpForNext(1));
        assertEquals(130, curve.xpForNext(2));
        assertEquals(100, curve.totalFor(2));
        assertEquals(230, curve.totalFor(3));
        assertEquals(390, curve.totalFor(4));
    }

    @Test
    void levelForIsTheInverseOfTotalFor() {
        for (int level = 1; level <= 100; level++) {
            assertEquals(level, curve.levelFor(curve.totalFor(level)), "exactly at level " + level);
            if (level > 1) assertEquals(level - 1, curve.levelFor(curve.totalFor(level) - 1), "one XP short of level " + level);
        }
    }

    @Test
    void levelIsCappedAtMax() {
        assertEquals(100, curve.levelFor(Long.MAX_VALUE / 4));
        assertEquals(0, curve.xpIntoLevel(Long.MAX_VALUE / 4));
        assertEquals(1.0, curve.progress(Long.MAX_VALUE / 4));
    }

    @Test
    void progressRunsFromZeroToJustUnderOne() {
        assertEquals(0.0, curve.progress(0));
        assertEquals(0.5, curve.progress(50));
        assertTrue(curve.progress(99) < 1.0);
        assertEquals(0.0, curve.progress(100));
        assertEquals(50, curve.xpIntoLevel(150));
    }

    @Test
    void nonsenseSettingsAreClamped() {
        LevelCurve odd = new LevelCurve(0, -5, 0);
        assertEquals(1, odd.maxLevel());
        assertEquals(1, odd.levelFor(1000));
    }
}
