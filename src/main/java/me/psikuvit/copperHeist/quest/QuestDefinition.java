package me.psikuvit.copperHeist.quest;

/**
 * One quest from quests.yml.
 *
 * @param name      MiniMessage title
 * @param target    how much of the trigger to count before it is done
 * @param cosmetic  a cosmetic id to give as well (null = none)
 */
public record QuestDefinition(String id, QuestPeriod period, String name, String description, QuestTrigger trigger, int target,
                              long xp, long coins, String cosmetic) {
}
