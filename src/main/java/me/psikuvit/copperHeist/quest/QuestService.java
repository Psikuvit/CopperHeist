package me.psikuvit.copperHeist.quest;

import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.profile.PlayerProfile;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Daily and weekly quests. Progress lives in the player's profile as a small group of fields per period ({@code q.daily.period},
 * {@code q.daily.<id>}, {@code q.daily.<id>.done}); when a period rolls over its group is deleted and the new period starts at zero. Quests
 * count what happened in a finished match ({@link MatchStats}) and pay out the moment they are completed. Main thread only.
 */
public class QuestService {

    private final CopperHeist plugin;
    private final QuestRegistry registry;

    public QuestService(CopperHeist plugin, QuestRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
    }

    public QuestRegistry registry() {
        return registry;
    }

    public boolean enabled() {
        return plugin.settings().getBoolean("features.quests", true) && plugin.getProfiles() != null;
    }

    private static String prefix(QuestPeriod period) {
        return "q." + period.key() + ".";
    }

    /** Starts a fresh group of fields if the stored period is not the current one. */
    private void rollover(PlayerProfile profile, QuestPeriod period, long now) {
        String periodField = prefix(period) + "period";
        long current = period.idAt(now);
        if (profile.longField(periodField, Long.MIN_VALUE) == current) return;
        profile.removeFields(prefix(period));
        profile.setField(periodField, current);
    }

    public int progress(PlayerProfile profile, QuestDefinition quest) {
        return (int) profile.longField(prefix(quest.period()) + quest.id(), 0);
    }

    public boolean isDone(PlayerProfile profile, QuestDefinition quest) {
        return profile.field(prefix(quest.period()) + quest.id() + ".done") != null;
    }

    /** The quests running now for a period, after making sure the player's stored progress belongs to it. */
    public List<QuestDefinition> current(Player player, QuestPeriod period) {
        PlayerProfile profile = profile(player);
        long now = System.currentTimeMillis();
        if (profile != null) rollover(profile, period, now);
        return registry.active(period, now);
    }

    public PlayerProfile profile(Player player) {
        return plugin.getProfiles() == null ? null : plugin.getProfiles().get(player);
    }

    /**
     * Adds a finished match to every active quest the player has not completed. Quests that reach their target are completed and paid
     * right away. Returns the quests completed by this match (for the caller to announce).
     */
    public List<QuestDefinition> onMatchFinished(Player player, MatchStats stats) {
        List<QuestDefinition> completed = new ArrayList<>();
        PlayerProfile profile = profile(player);
        if (profile == null || !enabled()) return completed;
        long now = System.currentTimeMillis();
        for (QuestPeriod period : QuestPeriod.values()) {
            rollover(profile, period, now);
            for (QuestDefinition quest : registry.active(period, now)) {
                if (isDone(profile, quest)) continue;
                int gained = quest.trigger().amount(stats);
                if (gained <= 0) continue;
                int total = Math.min(quest.target(), progress(profile, quest) + gained);
                profile.setField(prefix(period) + quest.id(), total);
                if (total >= quest.target()) {
                    profile.setField(prefix(period) + quest.id() + ".done", "1"); // inside the period's group, so a rollover clears it
                    complete(player, quest);
                    completed.add(quest);
                }
            }
        }
        plugin.getProfiles().save(profile);
        return completed;
    }

    private void complete(Player player, QuestDefinition quest) {
        var progress = plugin.getProgress();
        if (progress != null && progress.enabled() && (quest.xp() > 0 || quest.coins() > 0)) {
            progress.award(player.getUniqueId(), player.getName(), quest.xp(), quest.coins());
        }
        var cosmetics = plugin.getCosmetics();
        if (quest.cosmetic() != null && cosmetics != null) {
            var cosmetic = plugin.getCosmeticRegistry().get(quest.cosmetic());
            if (cosmetic != null) cosmetics.grant(player, cosmetic, "quest");
        }
        var messages = plugin.getMessageService();
        player.sendMessage(messages.get(player, "quests.completed", "name", quest.name(), "xp", quest.xp(), "coins", quest.coins()));
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.0f);
    }
}
