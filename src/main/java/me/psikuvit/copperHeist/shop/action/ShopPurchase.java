package me.psikuvit.copperHeist.shop.action;

import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.game.Game;
import me.psikuvit.copperHeist.game.GamePlayer;
import me.psikuvit.copperHeist.shop.ShopEntry;
import org.bukkit.entity.Player;

/** What an action knows about the purchase in progress. */
public record ShopPurchase(CopperHeist plugin, Game game, Player player, GamePlayer gamePlayer, ShopEntry entry) {
}
