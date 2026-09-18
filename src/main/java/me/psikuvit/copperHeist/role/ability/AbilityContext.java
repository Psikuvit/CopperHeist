package me.psikuvit.copperHeist.role.ability;

import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.game.Game;
import me.psikuvit.copperHeist.game.GamePlayer;
import me.psikuvit.copperHeist.role.AbilitySpec;
import org.bukkit.entity.Player;

/** Everything an ability needs when its owner presses the ability key. */
public record AbilityContext(CopperHeist plugin, Game game, Player player, GamePlayer gamePlayer, AbilitySpec spec) {
}
