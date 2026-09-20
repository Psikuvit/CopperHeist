package me.psikuvit.copperHeist.menu;

import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.game.Game;
import me.psikuvit.copperHeist.game.GamePlayer;
import org.bukkit.entity.Player;

/** The classic chest-inventory GUI, built on {@link Menu} and opened through the {@link MenuManager}. */
public class ChestMenuProvider implements MenuProvider {

    private final CopperHeist plugin;

    public ChestMenuProvider(CopperHeist plugin) {
        this.plugin = plugin;
    }

    @Override
    public void openShop(Player player, Game game, GamePlayer gamePlayer) {
        plugin.getMenus().open(player, new ShopMenu(plugin, player));
    }

    @Override
    public void openRoles(Player player, Game game, GamePlayer gamePlayer) {
        plugin.getMenus().open(player, new RoleMenu(plugin, player));
    }
}
