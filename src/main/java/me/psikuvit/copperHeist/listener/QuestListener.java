package me.psikuvit.copperHeist.listener;

import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.event.MatchEndEvent;
import me.psikuvit.copperHeist.game.Game;
import me.psikuvit.copperHeist.game.GamePlayer;
import me.psikuvit.copperHeist.quest.MatchStats;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/** Counts a finished match towards each online player's quests. */
public class QuestListener implements Listener {

    private final CopperHeist plugin;

    public QuestListener(CopperHeist plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onMatchEnd(MatchEndEvent event) {
        if (plugin.getQuests() == null || !plugin.getQuests().enabled() || plugin.getGameManager().isShuttingDown()) return;
        Game game = event.getGame();
        GamePlayer mvp = game.mvp();
        for (GamePlayer gp : game.gamePlayers()) {
            Player player = Bukkit.getPlayer(gp.getUuid());
            if (player == null) continue;
            plugin.getQuests().onMatchFinished(player, statsOf(gp, event, mvp));
        }
    }

    private static MatchStats statsOf(GamePlayer gp, MatchEndEvent event, GamePlayer mvp) {
        boolean won = event.getWinner() != null && gp.getTeam() == event.getWinner();
        return new MatchStats(won, gp == mvp, gp.getDelivered(), gp.getSteals(), gp.getKills(), gp.getScrapes(), gp.getRelicsDelivered(),
                gp.getDrillsCompleted());
    }
}
