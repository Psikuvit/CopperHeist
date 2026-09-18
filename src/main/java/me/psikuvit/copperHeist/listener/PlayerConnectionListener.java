package me.psikuvit.copperHeist.listener;

import me.psikuvit.copperHeist.CopperHeist;
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
        plugin.getSidebarService().showHub(event.getPlayer());
        plugin.getLobbyKitService().giveHubKit(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getGameManager().onQuit(event.getPlayer());
        plugin.getSidebarService().clear(event.getPlayer());
    }
}
