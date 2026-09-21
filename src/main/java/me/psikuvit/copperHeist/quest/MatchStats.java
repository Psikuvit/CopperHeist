package me.psikuvit.copperHeist.quest;

/** What one player did in one match - the numbers quests and achievements count. */
public record MatchStats(boolean won, boolean mvp, int delivered, int steals, int kills, int scrapes, int relics, int drills) {
}
