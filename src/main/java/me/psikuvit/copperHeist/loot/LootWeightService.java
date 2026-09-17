package me.psikuvit.copperHeist.loot;

import me.psikuvit.copperHeist.CopperHeist;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Carried-loot-value -> movement speed penalty (doc §4.4): every 10 value
 * carried costs 5% speed, capped at -35%.
 */
public class LootWeightService {

    private static NamespacedKey key;

    private final CopperHeist plugin;

    public LootWeightService(CopperHeist plugin) {
        this.plugin = plugin;
        if (key == null) key = new NamespacedKey(plugin, "loot_weight");
    }

    public int getCarriedValue(Player player) {
        int total = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null) continue;
            total += LootItem.getValue(item) * item.getAmount();
        }
        return total;
    }

    public double getSpeedPenalty(int carriedValue) {
        double perTen = plugin.getConfig().getDouble("loot.weight-per-10-value", 0.05);
        double cap = plugin.getConfig().getDouble("loot.max-slowdown", 0.35);
        double penalty = (carriedValue / 10.0) * perTen;
        return Math.min(cap, penalty);
    }

    public void recalc(Player player) {
        AttributeInstance attribute = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (attribute == null) return;
        attribute.removeModifier(key);
        int carried = getCarriedValue(player);
        double penalty = getSpeedPenalty(carried);
        if (penalty > 0) {
            AttributeModifier modifier = new AttributeModifier(key, -penalty, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
            attribute.addModifier(modifier);
        }
        int carryLimit = plugin.getConfig().getInt("loot.carry-limit", 80);
        player.sendActionBar(Component.text("Carrying: " + carried + "/" + carryLimit + " value  |  Speed -"
                + Math.round(penalty * 100) + "%", NamedTextColor.GOLD));
    }

    public void clearModifier(Player player) {
        AttributeInstance attribute = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (attribute != null) attribute.removeModifier(key);
    }
}
