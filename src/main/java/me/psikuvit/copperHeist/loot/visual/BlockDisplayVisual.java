package me.psikuvit.copperHeist.loot.visual;

import me.psikuvit.copperHeist.config.Settings;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** A small block model. loot.bag.block chooses the block (chest by default). */
public class BlockDisplayVisual implements LootBagVisual {

    private final Settings settings;

    public BlockDisplayVisual(Settings settings) {
        this.settings = settings;
    }

    @Override
    public Entity spawn(Location location) {
        Material material = Material.matchMaterial(settings.getString("loot.bag.block", "CHEST"));
        if (material == null || !material.isBlock()) material = Material.CHEST;
        Material block = material;
        return location.getWorld().spawn(location.clone().subtract(0.25, 0.25, 0.25), BlockDisplay.class, entity -> {
            entity.setBlock(block.createBlockData());
            entity.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.5f, 0.5f, 0.5f), new Quaternionf()));
            entity.setPersistent(true);
        });
    }
}
