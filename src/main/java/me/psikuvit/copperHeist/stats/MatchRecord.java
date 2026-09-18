package me.psikuvit.copperHeist.stats;

/** A finished match for the ch_matches history table; {@code winner} is null for a draw. */
public record MatchRecord(String matchId, String arena, String winner, int copperScore, int ironScore,
                          int durationSeconds, long endedAtMillis) {
}
