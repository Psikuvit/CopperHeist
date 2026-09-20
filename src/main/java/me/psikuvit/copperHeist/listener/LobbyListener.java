package me.psikuvit.copperHeist.listener;

import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.menu.ArenaMenu;
import me.psikuvit.copperHeist.menu.Gui;
import me.psikuvit.copperHeist.util.Pdc;
import me.psikuvit.copperHeist.util.PdcKeys;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/** Handles the hub's join compass (opens the arena picker), the guide book and the arena waiting room's leave item. */
public class LobbyListener implements Listener {

    private final CopperHeist plugin;

    public LobbyListener(CopperHeist plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !event.getAction().isRightClick()) return;

        ItemStack item = event.getItem();
        String action = Pdc.get(item, PdcKeys.LOBBY_ITEM);
        if (action == null) return;

        Player player = event.getPlayer();
        event.setCancelled(true);

        switch (action) {
            case "join_compass" -> new ArenaMenu(plugin).open(player);
            // The event is cancelled above so the item can't be placed or used; a book has to be opened by hand.
            case "guide_book" -> player.openBook(item);
            case "leave_arena" -> {
                if (plugin.getGameManager().getGame(player) != null) {
                    plugin.getGameManager().leave(player);
                    player.sendMessage(plugin.getMessageService().ok(player, "command.left-arena"));
                }
            }
            default -> {
            }
        }
    }

    /** A click on an arena tile joins that arena; unavailable tiles just say no. */
    @EventHandler
    public void onArenaMenuClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ArenaMenu.Holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() != event.getInventory()) return;

        String id = Gui.menuId(event.getCurrentItem());
        if (id == null) return;
        if (id.equals("unavailable")) {
            Gui.deny(player);
            return;
        }
        Gui.click(player);
        player.closeInventory();
        player.performCommand("ch join " + id);
    }
}
