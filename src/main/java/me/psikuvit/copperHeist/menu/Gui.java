package me.psikuvit.copperHeist.menu;

import me.psikuvit.copperHeist.ui.Theme;
import me.psikuvit.copperHeist.util.Pdc;
import me.psikuvit.copperHeist.util.PdcKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Small toolkit every menu in the plugin builds on, so they all look alike: a frame of dark panes, items whose names and lore
 * are never italic, a glint for "selected" things, and consistent click sounds. Text is MiniMessage with the theme tags.
 */
public final class Gui {

    /** Columns 1-7 of the rows between the top and bottom border. */
    public static final int ITEMS_PER_ROW = 7;

    private Gui() {
    }

    /** MiniMessage -> component with italics switched off (item text is italic by default). */
    public static Component text(String miniMessage) {
        return Theme.mini().deserialize(miniMessage).decoration(TextDecoration.ITALIC, false);
    }

    public static Component plain(Component component) {
        return component.decoration(TextDecoration.ITALIC, false);
    }

    /** An item with a name and lore lines (MiniMessage). {@code menuId} is what click handlers read back; may be null. */
    public static ItemStack item(Material material, String name, List<String> lore, String menuId) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(text(name));
        List<Component> lines = new ArrayList<>();
        for (String line : lore) lines.add(text(line));
        meta.lore(lines);
        stack.setItemMeta(meta);
        if (menuId != null) Pdc.set(stack, PdcKeys.MENU_ID, menuId);
        return stack;
    }

    /** A pane with no name and no tooltip - pure decoration. */
    public static ItemStack pane(Material material) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.setHideTooltip(true);
        stack.setItemMeta(meta);
        return stack;
    }

    /** Fills the outer ring of the inventory (first and last row, first and last column) with the pane. */
    public static void border(Inventory inventory, Material paneMaterial) {
        ItemStack pane = pane(paneMaterial);
        int size = inventory.getSize();
        int rows = size / 9;
        for (int slot = 0; slot < size; slot++) {
            int row = slot / 9;
            int column = slot % 9;
            if (row == 0 || row == rows - 1 || column == 0 || column == 8) inventory.setItem(slot, pane);
        }
    }

    /** The slot for the n-th item (0-based) laid out over columns 1-7, starting at row 1. */
    public static int slotFor(int index) {
        return (1 + index / ITEMS_PER_ROW) * 9 + 1 + index % ITEMS_PER_ROW;
    }

    /** Rows needed for {@code count} items plus the two border rows, between 3 and 6. */
    public static int rowsFor(int count) {
        int inner = Math.max(1, (count + ITEMS_PER_ROW - 1) / ITEMS_PER_ROW);
        return Math.min(6, Math.max(3, inner + 2));
    }

    public static void glow(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        meta.setEnchantmentGlintOverride(true);
        stack.setItemMeta(meta);
    }

    public static String menuId(ItemStack stack) {
        return stack == null ? null : Pdc.get(stack, PdcKeys.MENU_ID);
    }

    // ---- sounds ----

    public static void open(Player player) {
        player.playSound(player.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 0.4f, 1.4f);
    }

    public static void click(Player player) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
    }

    public static void success(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.3f);
    }

    public static void deny(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1.0f);
    }
}
