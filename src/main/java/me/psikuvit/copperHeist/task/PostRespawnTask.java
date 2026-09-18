package me.psikuvit.copperHeist.task;

import me.psikuvit.copperHeist.game.Game;
import me.psikuvit.copperHeist.game.GamePlayer;
import me.psikuvit.copperHeist.CopperHeist;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/** Runs a tick after the respawn event - items given during the event itself can be clobbered by the client's own respawn handling. */
public class PostRespawnTask extends BukkitRunnable {

    private final CopperHeist plugin;
    private final Game game;
    private final Player player;
    private final GamePlayer gamePlayer;

    public PostRespawnTask(CopperHeist plugin, Game game, Player player, GamePlayer gamePlayer) {
        this.plugin = plugin;
        this.game = game;
        this.player = player;
        this.gamePlayer = gamePlayer;
    }

    @Override
    public void run() {
        if (!player.isOnline() || plugin.getGameManager().getGame(player) != game) return;
        if (game.isActive()) game.beginRespawnWait(player, gamePlayer);
        else game.getRoleService().giveLoadout(player, gamePlayer.getRole(), gamePlayer.getTeam());
    }
}
