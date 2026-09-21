package me.psikuvit.copperHeist.daily;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DailyStreakTest {

    @Test
    void aFirstLoginStartsAStreakOfOne() {
        assertEquals(1, DailyStreak.next(-1, 20_000, 0));
    }

    @Test
    void loggingInTheNextDayExtendsTheStreak() {
        assertEquals(2, DailyStreak.next(20_000, 20_001, 1));
        assertEquals(8, DailyStreak.next(20_000, 20_001, 7));
    }

    @Test
    void loggingInAgainTheSameDayChangesNothing() {
        assertEquals(4, DailyStreak.next(20_000, 20_000, 4));
    }

    @Test
    void missingADayStartsOver() {
        assertEquals(1, DailyStreak.next(20_000, 20_002, 9));
        assertEquals(1, DailyStreak.next(20_000, 20_100, 9));
    }

    @Test
    void aBrokenStoredStreakStillCountsAsOne() {
        assertEquals(1, DailyStreak.next(20_000, 20_000, 0), "never below 1");
        assertEquals(2, DailyStreak.next(20_000, 20_001, 0));
    }

    @Test
    void oneClaimPerDay() {
        assertTrue(DailyStreak.canClaim(-1, 20_000), "never claimed");
        assertTrue(DailyStreak.canClaim(19_999, 20_000));
        assertFalse(DailyStreak.canClaim(20_000, 20_000));
    }

    @Test
    void theRewardListRepeats() {
        assertEquals(0, DailyStreak.rewardIndex(1, 7));
        assertEquals(6, DailyStreak.rewardIndex(7, 7));
        assertEquals(0, DailyStreak.rewardIndex(8, 7));
        assertEquals(3, DailyStreak.rewardIndex(25, 7));
        assertEquals(0, DailyStreak.rewardIndex(0, 7), "a streak below 1 pays day 1");
        assertEquals(0, DailyStreak.rewardIndex(5, 0), "an empty list can't crash");
    }
}
