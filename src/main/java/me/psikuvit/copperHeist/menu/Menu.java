package me.psikuvit.copperHeist.menu;

import me.psikuvit.copperHeist.CopperHeist;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.NonNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Base class of every chest GUI in the plugin. A menu is one viewer's inventory plus a map of slot -> {@link Button}; it draws
 * itself in {@link #draw()} and the {@link MenuManager} routes every click, drag, close and refresh, so a subclass only says what
 * it looks like and what its buttons do - it never listens for events or checks holders itself. Open one with
 * {@link MenuManager#open}.
 */
public abstract class Menu implements InventoryHolder {

    protected final CopperHeist plugin;
    protected final Player viewer;
    private final Map<Integer, Button> buttons = new HashMap<>();
    private Inventory inventory;
    private Menu parent;

    protected Menu(CopperHeist plugin, Player viewer) {
        this.plugin = plugin;
        this.viewer = viewer;
    }

    /** The inventory title, already in the viewer's language. */
    protected abstract Component title();

    /** Number of rows (1-6), read once when the menu opens. */
    protected abstract int rows();

    /** Places this menu's buttons. Called on open and on every {@link #refresh()}; the inventory is empty when it runs. */
    protected abstract void draw();

    /** Redraw every this many ticks while open (prices, cooldowns, arena states); 0 = only when {@link #refresh()} is called. */
    protected int refreshTicks() {
        return 0;
    }

    /** Called when the viewer closes the menu or it is replaced by another. */
    protected void onClose() {
    }

    /** False to have the manager close the menu on its next refresh pass (e.g. the game it belongs to ended). */
    protected boolean isValid() {
        return true;
    }

    // ---- drawing helpers ----

    protected final void set(int slot, ItemStack item, Consumer<Click> action) {
        set(slot, Button.of(item, action));
    }

    protected final void set(int slot, ItemStack item) {
        set(slot, Button.decoration(item));
    }

    protected final void set(int slot, Button button) {
        if (inventory == null || slot < 0 || slot >= inventory.getSize()) return;
        buttons.put(slot, button);
        inventory.setItem(slot, button.item());
    }

    /** Frames the menu with dark panes. */
    protected final void border(Material pane) {
        ItemStack item = Gui.pane(pane);
        int rows = inventory.getSize() / 9;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            int row = slot / 9;
            int column = slot % 9;
            if (row == 0 || row == rows - 1 || column == 0 || column == 8) set(slot, item);
        }
    }

    /** Bottom-right close button, the standard way out of a menu. */
    protected final void closeButton() {
        String name = plugin.getMessageService().rawFor(viewer, "gui.close");
        set(inventory.getSize() - 1, Gui.item(Material.BARRIER, name, List.of()), click -> {
            Gui.click(click.player());
            close();
        });
    }

    protected final int size() {
        return inventory.getSize();
    }

    // ---- lifecycle (used by MenuManager) ----

    final void build() {
        inventory = Bukkit.createInventory(this, Math.clamp(rows(), 1, 6) * 9, title());
        render();
    }

    /** Clears and redraws the whole menu; safe to call any time while it exists. */
    public final void refresh() {
        if (inventory != null) render();
    }

    private void render() {
        buttons.clear();
        inventory.clear();
        draw();
    }

    final void handleClick(Click click) {
        Button button = buttons.get(click.slot());
        if (button != null && button.action() != null) button.action().accept(click);
    }

    final void closed() {
        onClose();
    }

    final boolean valid() {
        return isValid();
    }

    final int refreshInterval() {
        return refreshTicks();
    }

    // ---- navigation ----

    /** Opens {@code child} on top of this menu; its back button (or {@link #back()}) returns here. */
    public final void openChild(Menu child) {
        child.parent = this;
        plugin.getMenus().open(viewer, child);
    }

    /** Back to the menu this one was opened from, or closes when there is none. */
    public final void back() {
        if (parent != null) plugin.getMenus().open(viewer, parent);
        else close();
    }

    public final void close() {
        viewer.closeInventory();
    }

    public final Player viewer() {
        return viewer;
    }

    @Override
    public @NonNull Inventory getInventory() {
        return inventory;
    }
}
