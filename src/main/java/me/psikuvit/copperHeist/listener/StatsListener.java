package me.psikuvit.copperHeist.listener;

import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.event.MatchEndEvent;
import me.psikuvit.copperHeist.game.Game;
import me.psikuvit.copperHeist.game.GamePlayer;
import me.psikuvit.copperHeist.game.Team;
import me.psikuvit.copperHeist.stats.MatchRecord;
import me.psikuvit.copperHeist.stats.Stat;
import me.psikuvit.copperHeist.stats.StatsService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Feeds the stats service: loads a player's stored numbers when they join, saves them when they quit, and
 * settles every player's match counters (and the match history row) when a match ends.
 */
public class StatsListener implements Listener {

    private final CopperHeist plugin;

    public StatsListener(CopperHeist plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        StatsService stats = plugin.getStats();
        if (stats != null) stats.onJoin(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        StatsService stats = plugin.getStats();
        if (stats != null) stats.onQuit(event.getPlayer());
    }

    @EventHandler
    public void onMatchEnd(MatchEndEvent event) {
        StatsService stats = plugin.getStats();
        if (stats == null || plugin.getGameManager().isShuttingDown()) return;
        Game game = event.getGame();
        Team winner = event.getWinner();

        GamePlayer mvp = game.mvp();

        for (GamePlayer gp : game.gamePlayers()) {
            OfflinePlayer player = Bukkit.getOfflinePlayer(gp.getUuid());
            String name = player.getName() != null ? player.getName() : gp.getUuid().toString();
            var id = gp.getUuid();

            stats.add(id, name, Stat.GAMES_PLAYED, 1);
            if (winner != null) stats.add(id, name, gp.getTeam() == winner ? Stat.WINS : Stat.LOSSES, 1);
            stats.add(id, name, Stat.LOOT_DELIVERED, gp.getDelivered());
            stats.add(id, name, Stat.LOOT_STOLEN, gp.getStolenValue());
            stats.add(id, name, Stat.STEALS, gp.getSteals());
            stats.add(id, name, Stat.RELICS_DELIVERED, gp.getRelicsDelivered());
            stats.add(id, name, Stat.KILLS, gp.getKills());
            stats.add(id, name, Stat.DEATHS, gp.getDeaths());
            stats.add(id, name, Stat.GOLEMS_SCRAPED, gp.getScrapes());
            stats.add(id, name, Stat.DRILLS_COMPLETED, gp.getDrillsCompleted());
            stats.add(id, name, Stat.DRILLS_DESTROYED, gp.getDrillsDestroyed());
            if (gp == mvp) stats.add(id, name, Stat.MVPS, 1);
        }
        stats.flushAll();

        stats.repository().recordMatch(new MatchRecord(game.getMatchId(), game.getArena().getName(),
                winner == null ? null : winner.name().toLowerCase(), event.getCopperScore(), event.getIronScore(),
                game.elapsedSeconds(), System.currentTimeMillis()));
    }
}
