package me.psikuvit.copperHeist.shop;

import org.bukkit.Material;

/** doc §9's shop table. key matches shop.yml's items.<key> section. */
public enum ShopItem {

    HONEYCOMB("honeycomb", Material.HONEYCOMB),
    WIND_CHARGES("wind_charges", Material.WIND_CHARGE),
    HEALING_POTION("healing_potion", Material.POTION),
    OXIDIZER_SPLASH("oxidizer_splash", Material.SPLASH_POTION),
    NEW_GOLEM("new_golem", Material.COPPER_INGOT),
    STORM_ROD("storm_rod", Material.BLAZE_ROD);

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
}
