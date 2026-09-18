package me.psikuvit.copperHeist.menu;

import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.game.Game;
import me.psikuvit.copperHeist.game.GamePlayer;
import org.bukkit.entity.Player;

/** The classic chest-inventory GUI. */
public class ChestMenuProvider implements MenuProvider {

    private final CopperHeist plugin;

    public ChestMenuProvider(CopperHeist plugin) {
        this.plugin = plugin;
    }

    @Override
    public void openShop(Player player, Game game, GamePlayer gamePlayer) {
        player.openInventory(plugin.getShopService().buildMenu());
    }

    @Override
    public void openRoles(Player player, Game game, GamePlayer gamePlayer) {
        player.openInventory(game.getRoleService().buildRoleMenu());
    }
}
