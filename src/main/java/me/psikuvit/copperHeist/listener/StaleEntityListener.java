package me.psikuvit.copperHeist.listener;

import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.util.HeistEntities;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.EntitiesLoadEvent;

/** Cleans up match entities (golems, labels, NPCs, loot) left in a chunk by an earlier session as the chunk loads. */
public class StaleEntityListener implements Listener {

    private final CopperHeist plugin;

    public StaleEntityListener(CopperHeist plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        HeistEntities.sweepStale(event.getEntities(), plugin.getGameManager()::isMatchRunning);
    }
}
