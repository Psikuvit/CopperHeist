package me.psikuvit.copperHeist.menu;

import me.psikuvit.copperHeist.game.Game;
import me.psikuvit.copperHeist.game.GamePlayer;
import org.bukkit.entity.Player;

/** How the shop and role picker are presented to a player. */
public interface MenuProvider {

    void openShop(Player player, Game game, GamePlayer gamePlayer);

    void openRoles(Player player, Game game, GamePlayer gamePlayer);
}
