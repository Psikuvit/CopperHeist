package me.psikuvit.copperHeist.shop;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jspecify.annotations.NonNull;

/** Marker so ShopListener can tell a shop GUI apart from any other open inventory. */
public class ShopHolder implements InventoryHolder {

    @Override
    public @NonNull Inventory getInventory() {
        throw new UnsupportedOperationException("marker holder only");
    }
}
