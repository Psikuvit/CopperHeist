package me.psikuvit.copperHeist.respawn;

import me.psikuvit.copperHeist.game.Game;
import me.psikuvit.copperHeist.game.GamePlayer;
import org.bukkit.entity.Player;

/** What happens between a player dying and being back at their spawn with a fresh loadout. */
public interface RespawnProvider {

    void begin(Game game, Player player, GamePlayer gamePlayer);
}
