package me.psikuvit.copperHeist.listener;

import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.util.Pdc;
import me.psikuvit.copperHeist.util.PdcKeys;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Keeps the hub and the waiting room tidy:
 * <ul>
 * <li>lobby items (join compass, guide book, leave bed, cosmetics chest, goals book, profile head) are frozen in their slot: they
 *     can't be dropped, picked up, dragged, shift-clicked, hotbar-swapped, or moved anywhere at all, in any inventory screen
 *     (lobby.lock-items). The only way to use one is the interaction it's meant for (usually a right-click);</li>
 * <li>players who are not in a running match (hub, waiting room, results screen) take no damage of any kind and don't get hungry
 *     (lobby.protect-players).</li>
 * </ul>
 * Nobody is exempt from the damage protection, admins included.
 */
public class LobbySafetyListener implements Listener {

    private final CopperHeist plugin;

    public LobbySafetyListener(CopperHeist plugin) {
        this.plugin = plugin;
    }

    private static boolean isLobbyItem(ItemStack item) {
        return item != null && Pdc.get(item, PdcKeys.LOBBY_ITEM) != null;
    }

    private boolean locked() {
        return plugin.settings().getBoolean("lobby.lock-items", true);
    }

    /** True in the hub and in an arena's waiting room / results screen - anywhere outside a running match. Nobody is exempt. */
    private boolean protectedFromHarm(Player player) {
        if (!plugin.settings().getBoolean("lobby.protect-players", true)) return false;
        var game = plugin.getGameManager().getGame(player);
        return game == null || !game.isActive();
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (locked() && isLobbyItem(event.getItemDrop().getItemStack())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (locked() && (isLobbyItem(event.getMainHandItem()) || isLobbyItem(event.getOffHandItem()))) event.setCancelled(true);
    }

    /** A lobby item is frozen: it can't be picked up, moved, shift-clicked or swapped out of its slot, in any inventory screen. */
    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!locked()) return;
        if (isLobbyItem(event.getCursor()) || isLobbyItem(event.getCurrentItem()) || isHotbarSwapOfLobbyItem(event)) {
            event.setCancelled(true);
        }
    }

    /** Pressing a number key swaps the hovered slot with that hotbar slot - cancel it if either side is a lobby item. */
    private boolean isHotbarSwapOfLobbyItem(InventoryClickEvent event) {
        if (event.getHotbarButton() < 0 || !(event.getWhoClicked() instanceof Player player)) return false;
        return isLobbyItem(player.getInventory().getItem(event.getHotbarButton()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (locked() && isLobbyItem(event.getOldCursor())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || !protectedFromHarm(player)) return;
        event.setCancelled(true);
        // Cancelled void damage would leave the player falling forever, so bring them back to a safe spot.
        if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
            var game = plugin.getGameManager().getGame(player);
            Location safe = plugin.getHubSpawn().get();
            if (game != null && game.getArena().getLobby() != null) safe = game.getArena().getLobby();
            if (safe != null) {
                player.setFallDistance(0f);
                player.teleport(safe);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player && protectedFromHarm(player)) event.setCancelled(true);
    }
}
