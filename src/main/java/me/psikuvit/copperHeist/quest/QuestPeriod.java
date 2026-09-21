package me.psikuvit.copperHeist.quest;

import java.util.Locale;

/** How long a quest lasts. Days change at midnight UTC and weeks at midnight UTC on Monday. */
public enum QuestPeriod {
    DAILY,
    WEEKLY;

    private static final long DAY_MILLIS = 86_400_000L;

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The number of the period a moment falls in - it changes exactly when the period rolls over. */
    public long idAt(long epochMillis) {
        long day = Math.floorDiv(epochMillis, DAY_MILLIS);
        // Day 0 (1 January 1970) was a Thursday, so shifting by 3 makes week boundaries fall on Mondays.
        return this == DAILY ? day : Math.floorDiv(day + 3, 7);
    }

    /** Milliseconds until this period ends, counted from {@code epochMillis}. */
    public long millisLeft(long epochMillis) {
        long day = Math.floorDiv(epochMillis, DAY_MILLIS);
        long endDay = this == DAILY ? day + 1 : (Math.floorDiv(day + 3, 7) + 1) * 7 - 3;
        return endDay * DAY_MILLIS - epochMillis;
    }

    public static QuestPeriod parse(String value) {
        if (value != null) {
            for (QuestPeriod period : values()) {
                if (period.key().equalsIgnoreCase(value.trim())) return period;
            }
        }
        return null;
    }
}
