package me.psikuvit.copperHeist.quest;

import java.util.Locale;
import java.util.function.ToIntFunction;

/** What a quest counts: each trigger says how much one finished match adds to it. {@code key} is what quests.yml calls it. */
public enum QuestTrigger {
    MATCHES("matches", stats -> 1),
    WINS("wins", stats -> stats.won() ? 1 : 0),
    MVPS("mvps", stats -> stats.mvp() ? 1 : 0),
    LOOT_DELIVERED("loot-delivered", MatchStats::delivered),
    STEALS("steals", MatchStats::steals),
    KILLS("kills", MatchStats::kills),
    SCRAPES("scrapes", MatchStats::scrapes),
    RELICS("relics", MatchStats::relics),
    DRILLS("drills", MatchStats::drills);

    private final String key;
    private final ToIntFunction<MatchStats> amount;

    QuestTrigger(String key, ToIntFunction<MatchStats> amount) {
        this.key = key;
        this.amount = amount;
    }

    public String key() {
        return key;
    }

    /** How much this trigger goes up by for a match with these numbers. */
    public int amount(MatchStats stats) {
        return Math.max(0, amount.applyAsInt(stats));
    }

    public String langKey() {
        return "quests.trigger." + key;
    }

    public static QuestTrigger parse(String value) {
        if (value == null) return null;
        String wanted = value.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        for (QuestTrigger trigger : values()) {
            if (trigger.key.equals(wanted)) return trigger;
        }
        return null;
    }
}
