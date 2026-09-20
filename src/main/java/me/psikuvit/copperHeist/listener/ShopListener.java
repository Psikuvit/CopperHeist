package me.psikuvit.copperHeist.listener;

import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.game.Game;
import me.psikuvit.copperHeist.game.GamePlayer;
import me.psikuvit.copperHeist.menu.Gui;
import me.psikuvit.copperHeist.role.RoleDefinition;
import me.psikuvit.copperHeist.role.RoleHolder;
import me.psikuvit.copperHeist.shop.ShopEntry;
import me.psikuvit.copperHeist.shop.ShopHolder;
import me.psikuvit.copperHeist.ui.Text;
import me.psikuvit.copperHeist.util.Pdc;
import me.psikuvit.copperHeist.util.PdcKeys;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.ItemStack;

/** Clicks in the shop and the role picker: sounds for every outcome, and the shop redraws itself after a purchase. */
public class ShopListener implements Listener {

    private final CopperHeist plugin;

    public ShopListener(CopperHeist plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onOpen(InventoryOpenEvent event) {
        boolean ours = event.getInventory().getHolder() instanceof ShopHolder || event.getInventory().getHolder() instanceof RoleHolder;
        if (ours && event.getPlayer() instanceof Player player) Gui.open(player);
    }

    @EventHandler
    public void onRoleClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof RoleHolder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() != event.getInventory()) return;

        String roleId = Gui.menuId(event.getCurrentItem());
        if (roleId == null) return;
        RoleDefinition role = plugin.getRoleRegistry().get(roleId);
        Game game = plugin.getGameManager().getGame(player);
        if (role == null || game == null) return;
        GamePlayer gp = game.getGamePlayer(player.getUniqueId());
        if (gp == null) return;

        Text error = game.getRoleService().trySetRole(gp, role);
        var messages = plugin.getMessageService();
        if (error != null) {
            Gui.deny(player);
            player.closeInventory();
            player.sendMessage(messages.err(player, error));
            return;
        }
        Gui.success(player);
        player.closeInventory();
        player.sendMessage(messages.ok(player, game.isActive() ? "command.role-set-next" : "command.role-set", "role", role.displayName()));
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ShopHolder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() != event.getInventory()) return;

        ItemStack clicked = event.getCurrentItem();
        if ("close".equals(Gui.menuId(clicked))) {
            Gui.click(player);
            player.closeInventory();
            return;
        }
        ShopEntry item = plugin.getShopService().entry(Pdc.get(clicked, PdcKeys.SHOP_ITEM));
        if (item == null) return;

        Game game = plugin.getGameManager().getGame(player);
        if (game == null) return;
        GamePlayer gp = game.getGamePlayer(player.getUniqueId());
        if (gp == null) return;

        boolean bought = plugin.getShopService().purchase(player, item, game, gp);
        if (bought) Gui.success(player);
        else Gui.deny(player);
        // Prices, locks and your balance changed - redraw the open menu.
        plugin.getShopService().fill(event.getInventory(), player);
    }
}
