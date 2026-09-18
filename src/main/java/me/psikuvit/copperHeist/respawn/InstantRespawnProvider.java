package me.psikuvit.copperHeist.respawn;

import me.psikuvit.copperHeist.game.Game;
import me.psikuvit.copperHeist.game.GamePlayer;
import org.bukkit.entity.Player;

/** Back at spawn immediately (spawn protection still applies). */
public class InstantRespawnProvider implements RespawnProvider {

    @Override
    public void begin(Game game, Player player, GamePlayer gamePlayer) {
        game.finishRespawn(player, gamePlayer);
    }
}
