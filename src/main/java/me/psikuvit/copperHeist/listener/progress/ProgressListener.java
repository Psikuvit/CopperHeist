package me.psikuvit.copperHeist.listener.progress;

import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.event.MatchEndEvent;
import me.psikuvit.copperHeist.game.Game;
import me.psikuvit.copperHeist.game.GamePlayer;
import me.psikuvit.copperHeist.game.Team;
import me.psikuvit.copperHeist.profile.PlayerProfile;
import me.psikuvit.copperHeist.profile.ProfileService;
import me.psikuvit.copperHeist.progress.ProgressService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/** Pays XP and coins to everyone in a match when it ends (amounts from progress.yml) and shows each online player their summary. */
public class ProgressListener implements Listener {

    private static final String LAST_WIN_DAY = "last_win_day";

    private final CopperHeist plugin;

    public ProgressListener(CopperHeist plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onMatchEnd(MatchEndEvent event) {
        ProgressService progress = plugin.getProgress();
        if (progress == null || !progress.enabled() || plugin.getGameManager().isShuttingDown()) return;
        Game game = event.getGame();
        Team winner = event.getWinner();
        GamePlayer mvp = game.mvp();
        YamlConfiguration config = progress.config();

        for (GamePlayer gp : game.gamePlayers()) {
            double xp = config.getDouble("rewards.participation.xp");
            double coins = config.getDouble("rewards.participation.coins");
            if (winner != null) {
                String result = gp.getTeam() == winner ? "win" : "loss";
                xp += config.getDouble("rewards." + result + ".xp");
                coins += config.getDouble("rewards." + result + ".coins");
            }
            if (gp == mvp) {
                xp += config.getDouble("rewards.mvp.xp");
                coins += config.getDouble("rewards.mvp.coins");
            }
            boolean firstWin = winner != null && gp.getTeam() == winner && claimFirstWin(gp);
            if (firstWin) {
                xp += config.getDouble("rewards.first-win-of-day.xp");
                coins += config.getDouble("rewards.first-win-of-day.coins");
            }
            xp += per(config, "loot-value", gp.getDelivered(), true) + per(config, "steal", gp.getSteals(), true)
                    + per(config, "relic", gp.getRelicsDelivered(), true) + per(config, "scrape", gp.getScrapes(), true)
                    + per(config, "kill", gp.getKills(), true) + per(config, "drill", gp.getDrillsCompleted(), true);
            coins += per(config, "loot-value", gp.getDelivered(), false) + per(config, "steal", gp.getSteals(), false)
                    + per(config, "relic", gp.getRelicsDelivered(), false) + per(config, "scrape", gp.getScrapes(), false)
                    + per(config, "kill", gp.getKills(), false) + per(config, "drill", gp.getDrillsCompleted(), false);

            OfflinePlayer offline = Bukkit.getOfflinePlayer(gp.getUuid());
            String name = offline.getName() != null ? offline.getName() : gp.getUuid().toString();
            ProgressService.Award award = progress.award(gp.getUuid(), name, xp, coins);
            Player online = Bukkit.getPlayer(gp.getUuid());
            if (online != null) summary(online, progress, award, firstWin);
        }
        if (plugin.getStats() != null) plugin.getStats().flushAll();
    }

    /** The day number the daily bonus is tracked by; it changes at midnight UTC. */
    public static long dayOf(long epochMillis) {
        return Math.floorDiv(epochMillis, 86_400_000L);
    }

    /**
     * True (and remembered in the profile) the first time this player is on a winning team on a given day. False for players whose
     * profile isn't loaded (offline at match end), who simply don't get the bonus.
     */
    private boolean claimFirstWin(GamePlayer gp) {
        ProfileService profiles = plugin.getProfiles();
        PlayerProfile profile = profiles == null ? null : profiles.get(gp.getUuid());
        if (profile == null) return false;
        long today = dayOf(System.currentTimeMillis());
        if (profile.longField(LAST_WIN_DAY, -1) == today) return false;
        profile.setField(LAST_WIN_DAY, today);
        profiles.save(profile);
        return true;
    }

    private static double per(YamlConfiguration config, String key, int count, boolean xp) {
        return count * config.getDouble("per." + key + (xp ? ".xp" : ".coins"));
    }

    private void summary(Player player, ProgressService progress, ProgressService.Award award, boolean firstWin) {
        var messages = plugin.getMessageService();
        long xp = progress.xp(player.getUniqueId(), player.getName());
        int level = progress.curve().levelFor(xp);
        double boost = progress.booster();

        player.sendMessage(messages.get(player, "progress.summary-header"));
        player.sendMessage(messages.get(player, "progress.summary-line", "xp", award.xp(), "coins", award.coins(),
                "booster", boost > 1.0 ? messages.rawFor(player, "progress.booster-note", "multiplier", trim(boost)) : ""));
        if (firstWin) {
            var config = progress.config();
            player.sendMessage(messages.get(player, "progress.first-win", "xp", Math.round(config.getDouble("rewards.first-win-of-day.xp")),
                    "coins", Math.round(config.getDouble("rewards.first-win-of-day.coins"))));
        }
        player.sendMessage(messages.get(player, "progress.level-line", "level", level, "rank", progress.rank(level),
                "bar", progress.bar(xp), "into", progress.curve().xpIntoLevel(xp),
                "need", level >= progress.curve().maxLevel() ? 0 : progress.curve().xpForNext(level)));
    }

    private static String trim(double value) {
        return value == Math.floor(value) ? String.valueOf((long) value) : String.valueOf(value);
    }
}
