package me.psikuvit.copperHeist.loot.visual;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

/** The look of a loot bag on the ground. Returns the visual entity, or null for "hitbox and label only". */
public interface LootBagVisual {

    Entity spawn(Location location);
}
