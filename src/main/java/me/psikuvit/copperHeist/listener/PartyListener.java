package me.psikuvit.copperHeist.listener;

import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.command.PartyCommands;
import me.psikuvit.copperHeist.party.PartyService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/** Takes a quitting player out of their built-in party (and tells the rest). Another plugin's parties handle their own quits. */
public class PartyListener implements Listener {

    private final CopperHeist plugin;

    public PartyListener(CopperHeist plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        PartyService service = plugin.getParties().builtIn();
        if (service == null) return;
        Player player = event.getPlayer();
        PartyCommands.announceDeparture(plugin, player.getUniqueId(), player.getName(), service.leave(player.getUniqueId()), "party.member-quit");
    }
}
