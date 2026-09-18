package me.psikuvit.copperHeist.role;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Marker so the click listener can tell the role picker apart from any other open inventory. */
public class RoleHolder implements InventoryHolder {

    @Override
    public Inventory getInventory() {
        throw new UnsupportedOperationException("marker holder only");
    }
}
