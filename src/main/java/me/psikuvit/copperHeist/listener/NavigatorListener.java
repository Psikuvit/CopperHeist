package me.psikuvit.copperHeist.listener;

import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.menu.ArenaMenu;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

/** Right-clicking a hub navigator opens the arena picker; nobody can hurt or dress them, creative mode included. */
public class NavigatorListener implements Listener {

    private final CopperHeist plugin;

    public NavigatorListener(CopperHeist plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(PlayerInteractEntityEvent event) {
        open(event, event.getRightClicked(), event.getHand());
    }

    /** Armor stands report clicks as the "at entity" variant. */
    @EventHandler
    public void onClickAt(PlayerInteractAtEntityEvent event) {
        open(event, event.getRightClicked(), event.getHand());
    }

    private void open(PlayerInteractEntityEvent event, Entity clicked, EquipmentSlot hand) {
        if (plugin.getNavigators().idOf(clicked.getUniqueId()) == null) return;
        event.setCancelled(true);
        if (hand != EquipmentSlot.HAND) return;
        plugin.getMenus().open(event.getPlayer(), new ArenaMenu(plugin, event.getPlayer()));
    }

    @EventHandler
    public void onHit(EntityDamageByEntityEvent event) {
        if (plugin.getNavigators().idOf(event.getEntity().getUniqueId()) != null) event.setCancelled(true);
    }
}
