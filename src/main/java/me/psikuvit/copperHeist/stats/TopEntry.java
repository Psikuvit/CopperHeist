package me.psikuvit.copperHeist.stats;

import java.util.UUID;

/** One leaderboard row. */
public record TopEntry(int rank, UUID uuid, String name, long value) {
}
