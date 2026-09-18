package me.psikuvit.copperHeist.shop.action;

import me.psikuvit.copperHeist.shop.ShopEntry;
import me.psikuvit.copperHeist.util.Pdc;
import me.psikuvit.copperHeist.util.PdcKeys;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Color;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import java.util.Locale;

/**
 * Hands the buyer an item. Params: {@code potion} (a potion type such as strong_healing), {@code color}
 * (#RRGGBB potion tint), {@code item-name} (MiniMessage) and {@code tag: true} to mark the item as usable
 * (see ItemUse) - tagged items default their name to the entry's name.
 */
public class GiveItemAction implements ShopAction {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    @Override
    public void perform(ShopPurchase purchase) {
        purchase.player().getInventory().addItem(createStack(purchase.entry()));
    }

    public static ItemStack createStack(ShopEntry entry) {
        ItemStack stack = new ItemStack(entry.material(), Math.max(1, entry.amount()));
        ItemMeta meta = stack.getItemMeta();

        String itemName = entry.text("item-name", entry.flag("tag") ? entry.name() : null);
        if (itemName != null) meta.displayName(MINI.deserialize(itemName).decoration(TextDecoration.ITALIC, false));

        if (meta instanceof PotionMeta potion) {
            String type = entry.text("potion", null);
            if (type != null) {
                NamespacedKey key = NamespacedKey.fromString(type.toLowerCase(Locale.ROOT));
                PotionType potionType = key == null ? null : Registry.POTION.get(key);
                if (potionType != null) potion.setBasePotionType(potionType);
            }
            String color = entry.text("color", null);
            if (color != null && color.startsWith("#")) {
                try {
                    potion.setColor(Color.fromRGB(Integer.parseInt(color.substring(1), 16)));
                } catch (IllegalArgumentException ignored) {
                    // a malformed color just leaves the default tint
                }
            }
        }
        stack.setItemMeta(meta);
        if (entry.flag("tag")) Pdc.set(stack, PdcKeys.SHOP_ITEM, entry.id());
        return stack;
    }
}
