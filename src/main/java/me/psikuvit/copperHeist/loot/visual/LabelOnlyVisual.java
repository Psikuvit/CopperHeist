package me.psikuvit.copperHeist.loot.visual;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

/** No model - just the click hitbox and floating value text. */
public class LabelOnlyVisual implements LootBagVisual {

    @Override
    public Entity spawn(Location location) {
        return null;
    }
}
