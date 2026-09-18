package me.psikuvit.copperHeist.listener;

import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.game.Game;
import me.psikuvit.copperHeist.game.GamePlayer;
import me.psikuvit.copperHeist.role.RoleDefinition;
import me.psikuvit.copperHeist.role.RoleHolder;
import me.psikuvit.copperHeist.shop.ShopHolder;
import me.psikuvit.copperHeist.shop.ShopEntry;
import me.psikuvit.copperHeist.util.Pdc;
import me.psikuvit.copperHeist.util.PdcKeys;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

public class ShopListener implements Listener {

    private final CopperHeist plugin;

    public ShopListener(CopperHeist plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onRoleClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof RoleHolder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() != event.getInventory()) return;

        int slot = event.getSlot();
        var roleList = plugin.getRoleRegistry().all();
        if (slot < 0 || slot >= roleList.size()) return;
        Game game = plugin.getGameManager().getGame(player);
        if (game == null) return;
        GamePlayer gp = game.getGamePlayer(player.getUniqueId());
        if (gp == null) return;

        RoleDefinition role = roleList.get(slot);
        String error = game.getRoleService().trySetRole(gp, role);
        player.closeInventory();
        if (error != null) {
            player.sendMessage(net.kyori.adventure.text.Component.text(error, net.kyori.adventure.text.format.NamedTextColor.RED));
            return;
        }
        player.sendMessage(net.kyori.adventure.text.Component.text("Role set to " + role.displayName()
                + (game.isActive() ? " - applies next respawn." : "."), net.kyori.adventure.text.format.NamedTextColor.GREEN));
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ShopHolder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack clicked = event.getCurrentItem();
        String key = Pdc.get(clicked, PdcKeys.SHOP_ITEM);
        ShopEntry item = plugin.getShopService().entry(key);
        if (item == null) return;

        Game game = plugin.getGameManager().getGame(player);
        if (game == null) return;
        GamePlayer gp = game.getGamePlayer(player.getUniqueId());
        if (gp == null) return;

        plugin.getShopService().purchase(player, item, game, gp);
    }
}
