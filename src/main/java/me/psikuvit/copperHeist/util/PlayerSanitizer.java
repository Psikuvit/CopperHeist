package me.psikuvit.copperHeist.util;

import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;

/**
 * Puts a player back into a known-clean state - used when they enter the hub or an arena's waiting room and
 * when they leave a match, so nothing from one context (items, armor, potion effects, glow, speed changes,
 * flight) can leak into the next one.
 */
public final class PlayerSanitizer {

    private PlayerSanitizer() {
    }

    /** Empties every slot: main inventory, hotbar, all four armor slots, the offhand and the cursor. */
    public static void clearInventory(Player player) {
        player.closeInventory();
        player.setItemOnCursor(null);
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        inventory.setArmorContents(new ItemStack[4]);
        inventory.setItemInOffHand(ItemStack.empty());
    }

    /** Removes everything a match can put on a player besides items. Game mode and health are left alone. */
    public static void clearEffects(Player player) {
        for (PotionEffect effect : player.getActivePotionEffects()) player.removePotionEffect(effect.getType());
        player.setGlowing(false);
        player.setInvisible(false);
        player.setFireTicks(0);
        player.setFreezeTicks(0);
        player.setFallDistance(0f);
        player.setWalkSpeed(0.2f);
        player.setFlySpeed(0.1f);
        if (player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR) {
            player.setFlying(false);
            player.setAllowFlight(false);
        }
    }

    /** Full health, food and air. */
    public static void restoreVitals(Player player) {
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        player.setHealth(maxHealth == null ? 20.0 : maxHealth.getValue());
        player.setFoodLevel(20);
        player.setSaturation(5f);
        player.setRemainingAir(player.getMaximumAir());
    }

    /** Items, effects and vitals - a fresh player. */
    public static void reset(Player player) {
        clearInventory(player);
        clearEffects(player);
        restoreVitals(player);
    }
}
