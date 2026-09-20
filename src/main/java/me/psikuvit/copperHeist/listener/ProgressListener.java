package me.psikuvit.copperHeist.listener;

import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.event.MatchEndEvent;
import me.psikuvit.copperHeist.game.Game;
import me.psikuvit.copperHeist.game.GamePlayer;
import me.psikuvit.copperHeist.game.Team;
import me.psikuvit.copperHeist.progress.ProgressService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/** Pays XP and coins to everyone in a match when it ends (amounts from progress.yml) and shows each online player their summary. */
public class ProgressListener implements Listener {

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
            if (online != null) summary(online, progress, award);
        }
        if (plugin.getStats() != null) plugin.getStats().flushAll();
    }

    private static double per(YamlConfiguration config, String key, int count, boolean xp) {
        return count * config.getDouble("per." + key + (xp ? ".xp" : ".coins"));
    }

    private void summary(Player player, ProgressService progress, ProgressService.Award award) {
        var messages = plugin.getMessageService();
        long xp = progress.xp(player.getUniqueId(), player.getName());
        int level = progress.curve().levelFor(xp);
        double boost = progress.booster();

        player.sendMessage(messages.get(player, "progress.summary-header"));
        player.sendMessage(messages.get(player, "progress.summary-line", "xp", award.xp(), "coins", award.coins(),
                "booster", boost > 1.0 ? messages.rawFor(player, "progress.booster-note", "multiplier", trim(boost)) : ""));
        player.sendMessage(messages.get(player, "progress.level-line", "level", level, "rank", progress.rank(level),
                "bar", progress.bar(xp), "into", progress.curve().xpIntoLevel(xp),
                "need", level >= progress.curve().maxLevel() ? 0 : progress.curve().xpForNext(level)));
    }

    private static String trim(double value) {
        return value == Math.floor(value) ? String.valueOf((long) value) : String.valueOf(value);
    }
}
