package me.psikuvit.copperHeist.daily;

/** The rules of the login streak, with no server types so they can be tested. Days are numbered like {@code QuestPeriod.DAILY} (UTC). */
public final class DailyStreak {

    private DailyStreak() {
    }

    /**
     * The streak after logging in on {@code today}. Logging in again the same day changes nothing, logging in the day after the last login
     * extends it, and missing a day (or more) starts again from 1.
     *
     * @param lastDay the day of the last counted login, or a negative number for a first-ever login
     */
    public static int next(long lastDay, long today, int streak) {
        if (lastDay == today) return Math.max(1, streak);
        if (lastDay >= 0 && lastDay == today - 1) return Math.max(1, streak) + 1;
        return 1;
    }

    /** True if the day's reward has not been taken yet. */
    public static boolean canClaim(long claimedDay, long today) {
        return claimedDay != today;
    }

    /** Which reward of a list of {@code size} a streak earns: the list repeats, so day 8 of a 7-day list pays like day 1. */
    public static int rewardIndex(int streak, int size) {
        return size <= 0 ? 0 : (Math.max(1, streak) - 1) % size;
    }
}
