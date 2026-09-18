package me.psikuvit.copperHeist.stats;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/** A player's counters at one moment; missing stats read as zero. */
public record PlayerStats(UUID uuid, String name, Map<Stat, Long> values) {

    public static PlayerStats empty(UUID uuid, String name) {
        return new PlayerStats(uuid, name, new EnumMap<>(Stat.class));
    }

    public long get(Stat stat) {
        return values.getOrDefault(stat, 0L);
    }
}
