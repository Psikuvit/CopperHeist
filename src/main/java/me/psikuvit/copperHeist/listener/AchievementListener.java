package me.psikuvit.copperHeist.listener;

import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.event.LevelUpEvent;
import me.psikuvit.copperHeist.event.MatchEndEvent;
import me.psikuvit.copperHeist.game.GamePlayer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * Checks achievements when a match ends and when a player levels up. It must be registered after the stats listener, so the match's numbers
 * are already counted when it looks.
 */
public class AchievementListener implements Listener {

    private final CopperHeist plugin;

    public AchievementListener(CopperHeist plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onMatchEnd(MatchEndEvent event) {
        var achievements = plugin.getAchievements();
        if (achievements == null || !achievements.enabled() || plugin.getGameManager().isShuttingDown()) return;
        for (GamePlayer gp : event.getGame().gamePlayers()) {
            Player player = Bukkit.getPlayer(gp.getUuid());
            if (player != null) achievements.check(player);
        }
    }

    @EventHandler
    public void onLevelUp(LevelUpEvent event) {
        var achievements = plugin.getAchievements();
        if (achievements != null && achievements.enabled()) achievements.check(event.getPlayer());
    }
}
