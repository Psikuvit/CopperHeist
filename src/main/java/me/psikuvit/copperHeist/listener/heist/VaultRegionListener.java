package me.psikuvit.copperHeist.listener.heist;

import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.arena.Region;
import me.psikuvit.copperHeist.game.Game;
import me.psikuvit.copperHeist.game.GamePlayer;
import me.psikuvit.copperHeist.game.Team;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * Optional (vault.block-entry): the vault rooms are sealed by region rather than by blocks, so golems can
 * path in while players can't. Attackers may walk in only during a vault drill's breach window.
 */
public class VaultRegionListener implements Listener {

    private final CopperHeist plugin;

    public VaultRegionListener(CopperHeist plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || (from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ())) {
            return;
        }
        if (!plugin.settings().getBoolean("vault.block-entry", false)) return;

        Player player = event.getPlayer();
        Game game = plugin.getGameManager().getGame(player);
        GamePlayer gp = game == null ? null : game.getGamePlayer(player.getUniqueId());
        if (gp == null || !game.isActive()) return;

        for (Team vaultTeam : Team.values()) {
            Region vault = game.getArena().site(vaultTeam).vaultRegion();
            if (vault == null || !vault.contains(to) || vault.contains(from)) continue;
            boolean breachingAttacker = gp.getTeam() != vaultTeam && game.getVaultDrillManager().isBreached(vaultTeam);
            if (breachingAttacker) return;
            event.setTo(from);
            plugin.getActionBar().show(player, plugin.getMessageService().get(player, "vault.no-entry"));
            return;
        }
    }
}
