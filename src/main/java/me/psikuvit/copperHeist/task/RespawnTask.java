package me.psikuvit.copperHeist.task;

import me.psikuvit.copperHeist.game.Game;
import me.psikuvit.copperHeist.game.GamePlayer;
import me.psikuvit.copperHeist.CopperHeist;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/** Counts a dead player down on the action bar, then hands them back to the game to respawn. */
public class RespawnTask extends BukkitRunnable {

    private final CopperHeist plugin;
    private final Game game;
    private final Player player;
    private final GamePlayer gamePlayer;
    private int secondsLeft;

    public RespawnTask(CopperHeist plugin, Game game, Player player, GamePlayer gamePlayer, int seconds) {
        this.plugin = plugin;
        this.game = game;
        this.player = player;
        this.gamePlayer = gamePlayer;
        this.secondsLeft = seconds;
    }

    @Override
    public void run() {
        if (!player.isOnline() || !game.isPlaying(gamePlayer) || !game.isActive()) {
            cancel();
            return;
        }
        if (secondsLeft <= 0) {
            cancel();
            game.finishRespawn(player, gamePlayer);
            return;
        }
        player.sendActionBar(plugin.getMessageService().get(player, "actionbar.respawning", "seconds", secondsLeft));
        secondsLeft--;
    }
}
