package me.psikuvit.copperHeist.listener;

import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.game.Game;
import me.psikuvit.copperHeist.game.GamePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

/** Abilities are triggered with the swap-hand key (F). */
public class RoleListener implements Listener {

    private final CopperHeist plugin;

    public RoleListener(CopperHeist plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        Game game = plugin.getGameManager().getGame(player);
        if (game == null || !game.isActive()) return;

        GamePlayer gp = game.getGamePlayer(player.getUniqueId());
        if (gp == null) return;

        event.setCancelled(true);
        game.getRoleService().activate(player, gp);
    }
}
