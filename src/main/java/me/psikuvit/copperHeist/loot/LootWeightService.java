package me.psikuvit.copperHeist.loot;

import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.game.Game;
import me.psikuvit.copperHeist.game.GamePlayer;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Carried-loot-value -> movement speed penalty: every 10 value
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

    /** The relic ignores the carry limit, but its holder can only carry a small amount of other loot on top. */
    public boolean canCarry(Player player, ItemStack item) {
        if (LootItem.isRelic(item)) return true;
        int limit = plugin.settings().getInt("loot.carry-limit", 80);
        int other = 0;
        boolean holdingRelic = false;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (!LootItem.isLoot(stack)) continue;
            if (LootItem.isRelic(stack)) holdingRelic = true;
            else other += LootItem.getValue(stack) * stack.getAmount();
        }
        if (holdingRelic) limit = Math.min(limit, plugin.settings().getInt("relic.holder-extra-carry", 20));
        return other + LootItem.getValue(item) * item.getAmount() <= limit;
    }

    public double getSpeedPenalty(int carriedValue) {
        return getSpeedPenalty(carriedValue, 1.0);
    }

    public double getSpeedPenalty(int carriedValue, double roleMultiplier) {
        double perTen = plugin.settings().getDouble("loot.weight-per-10-value", 0.05);
        double cap = plugin.settings().getDouble("loot.max-slowdown", 0.35);
        double penalty = (carriedValue / 10.0) * perTen * roleMultiplier;
        return Math.min(cap, penalty);
    }

    public void recalc(Player player) {
        AttributeInstance attribute = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (attribute == null) return;
        attribute.removeModifier(key);
        int carried = getCarriedValue(player);

        double roleMultiplier = 1.0;
        String ability = "<gray>-";
        Game game = plugin.getGameManager().getGame(player);
        if (game != null) {
            GamePlayer gp = game.getGamePlayer(player.getUniqueId());
            if (gp != null) {
                roleMultiplier = game.getRoleService().speedPenaltyMultiplier(gp.getRole());
                ability = game.getRoleService().abilityStatus(player, gp);
            }
        }
        double penalty = getSpeedPenalty(carried, roleMultiplier);
        if (penalty > 0) {
            AttributeModifier modifier = new AttributeModifier(key, -penalty, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
            attribute.addModifier(modifier);
        }
        int carryLimit = plugin.settings().getInt("loot.carry-limit", 80);
        if (player.getGameMode() == GameMode.SPECTATOR) return; // the respawn countdown owns the bar while dead
        plugin.getActionBar().ambient(player, plugin.getMessageService().get(player, "actionbar.carrying",
                "value", carried, "limit", carryLimit, "speed", Math.round(penalty * 100), "ability", ability));
    }

    public void clearModifier(Player player) {
        AttributeInstance attribute = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (attribute != null) attribute.removeModifier(key);
    }
}
