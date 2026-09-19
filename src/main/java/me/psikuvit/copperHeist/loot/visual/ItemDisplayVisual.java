package me.psikuvit.copperHeist.loot.visual;

import me.psikuvit.copperHeist.config.Settings;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;

/** A floating item model. loot.bag.item chooses the material (bundle by default). */
public class ItemDisplayVisual implements LootBagVisual {

    private final Settings settings;

    public ItemDisplayVisual(Settings settings) {
        this.settings = settings;
    }

    @Override
    public Entity spawn(Location location) {
        Material material = Material.matchMaterial(settings.getString("loot.bag.item", "BUNDLE"));
        if (material == null || !material.isItem()) material = Material.BUNDLE;
        ItemStack stack = new ItemStack(material);
        return location.getWorld().spawn(location, ItemDisplay.class, entity -> {
            entity.setItemStack(stack);
            });
    }
}
