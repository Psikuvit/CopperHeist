package me.psikuvit.copperHeist.listener;

import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.game.Game;
import me.psikuvit.copperHeist.game.GamePlayer;
import me.psikuvit.copperHeist.role.Role;
import me.psikuvit.copperHeist.role.RoleHolder;
import me.psikuvit.copperHeist.shop.ShopHolder;
import me.psikuvit.copperHeist.shop.ShopItem;
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
        if (slot < 0 || slot >= Role.values().length) return;
        Game game = plugin.getGameManager().getGame(player);
        if (game == null) return;
        GamePlayer gp = game.getGamePlayer(player.getUniqueId());
        if (gp == null) return;

        Role role = Role.values()[slot];
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
        ShopItem item = key == null ? null : ShopItem.fromKey(key);
        if (item == null) return;

        Game game = plugin.getGameManager().getGame(player);
        if (game == null) return;
        GamePlayer gp = game.getGamePlayer(player.getUniqueId());
        if (gp == null) return;

        plugin.getShopService().purchase(player, item, game, gp);
    }
}
