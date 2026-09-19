package me.psikuvit.copperHeist.respawn;

import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.game.Game;
import me.psikuvit.copperHeist.game.GamePlayer;
import me.psikuvit.copperHeist.task.RespawnTask;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

/** Wait out match.respawn-delay-seconds in spectator mode with an action-bar countdown. */
public class SpectatorWaitRespawnProvider implements RespawnProvider {

    private final CopperHeist plugin;

    public SpectatorWaitRespawnProvider(CopperHeist plugin) {
        this.plugin = plugin;
    }

    @Override
    public void begin(Game game, Player player, GamePlayer gamePlayer) {
        int delay = game.settings().getInt("match.respawn-delay-seconds", 6);
        if (delay <= 0) {
            game.finishRespawn(player, gamePlayer);
            return;
        }
        player.setGameMode(GameMode.SPECTATOR);
        new RespawnTask(plugin, game, player, gamePlayer, delay).runTaskTimer(plugin, 0L, 20L);
    }
}
