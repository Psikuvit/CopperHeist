package me.psikuvit.copperHeist.listener;

import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.util.PlayerSanitizer;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerConnectionListener implements Listener {

    private final CopperHeist plugin;

    public PlayerConnectionListener(CopperHeist plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // A cross-server join waiting for this player is applied a moment later, once the normal join handling is done.
        Player joined = event.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> plugin.getNetwork().onJoin(joined), 10L);
        if (plugin.getGameManager().onJoin(event.getPlayer())) return;
        // Anyone entering the hub starts from a clean slate: no leftover items, armor, effects or vitals.
        if (plugin.settings().getBoolean("lobby.clear-on-join", true) && !joined.hasPermission("copperheist.admin.bypass")) {
            PlayerSanitizer.reset(joined);
        }
        Location hub = plugin.getHubSpawn().get();
        if (hub != null && plugin.settings().getBoolean("lobby.teleport-on-join", true)) joined.teleport(hub);
        plugin.getSidebarService().showHub(event.getPlayer());
        plugin.getLobbyKitService().giveHubKit(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getGameManager().onQuit(event.getPlayer());
        plugin.getSidebarService().clear(event.getPlayer());
    }
}
