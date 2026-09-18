package me.psikuvit.copperHeist.shop;

import me.psikuvit.copperHeist.util.Pdc;
import me.psikuvit.copperHeist.util.PdcKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

/**
 * The shop table. key matches shop.yml's items.<key> section. Honeycomb
 * and Oxidizer Splash also get handed out directly by role loadouts
 * (Mechanic, Saboteur), so their item construction lives here as the one
 * shared place rather than being duplicated in both ShopService and
 * RoleService.
 */
public enum ShopItem {

    HONEYCOMB("honeycomb", Material.HONEYCOMB),
    WIND_CHARGES("wind_charges", Material.WIND_CHARGE),
    HEALING_POTION("healing_potion", Material.POTION),
    OXIDIZER_SPLASH("oxidizer_splash", Material.SPLASH_POTION),
    NEW_GOLEM("new_golem", Material.COPPER_INGOT),
    STORM_ROD("storm_rod", Material.BLAZE_ROD),
    ALARM("alarm", Material.TRIPWIRE_HOOK),
    VAULT_DRILL("vault_drill", Material.IRON_PICKAXE);

    public final String key;
    public final Material material;

    ShopItem(String key, Material material) {
        this.key = key;
        this.material = material;
    }

    public static ShopItem fromKey(String key) {
        for (ShopItem item : values()) {
            if (item.key.equals(key)) return item;
        }
        return null;
    }

    public static ItemStack createHoneycomb() {
        ItemStack item = new ItemStack(HONEYCOMB.material);
        Pdc.set(item, PdcKeys.SHOP_ITEM, HONEYCOMB.key);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Honeycomb", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack createOxidizerSplash() {
        ItemStack item = new ItemStack(OXIDIZER_SPLASH.material);
        Pdc.set(item, PdcKeys.SHOP_ITEM, OXIDIZER_SPLASH.key);
        if (item.getItemMeta() instanceof PotionMeta meta) {
            meta.setBasePotionType(PotionType.WATER);
            meta.setColor(Color.fromRGB(0x8B4513));
            meta.displayName(Component.text("Oxidizer Splash", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
            item.setItemMeta(meta);
        }
        return item;
    }
}
