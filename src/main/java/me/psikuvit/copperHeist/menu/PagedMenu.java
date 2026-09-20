package me.psikuvit.copperHeist.menu;

import me.psikuvit.copperHeist.CopperHeist;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.function.Consumer;

/**
 * A menu that lists any number of things over several pages: a framed 6-row grid of 28 items with previous / next arrows and a
 * "page x of y" marker on the bottom row, and a back or close button. A subclass supplies the items and says how each one looks and
 * what clicking it does; paging, clamping and the arrows are handled here. It is meant for cosmetics but works for any list
 * (quests, achievements, ...).
 */
public abstract class PagedMenu<T> extends Menu {

    private static final int ROWS = 6;
    /** 4 inner rows of 7 (see {@link Gui#ITEMS_PER_ROW}). */
    public static final int PAGE_SIZE = 4 * Gui.ITEMS_PER_ROW;

    private int page;

    protected PagedMenu(CopperHeist plugin, Player viewer) {
        super(plugin, viewer);
    }

    /** Everything to list, in order. Called on every redraw, so it may change while the menu is open. */
    protected abstract List<T> items();

    /** How one entry looks. */
    protected abstract ItemStack icon(T item);

    /** What happens when an entry is clicked. */
    protected abstract void onSelect(T item, Click click);

    /** Extra buttons on the bottom row (columns 1 and 7 are free); the default draws none. */
    protected void drawFooter(int firstSlot) {
    }

    @Override
    protected final int rows() {
        return ROWS;
    }

    @Override
    protected final void draw() {
        border(Material.GRAY_STAINED_GLASS_PANE);
        List<T> all = items();
        int pages = pageCount(all.size());
        page = clamp(page, pages);

        int from = page * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE && from + i < all.size(); i++) {
            T item = all.get(from + i);
            Consumer<Click> action = click -> onSelect(item, click);
            set(Gui.slotFor(i), icon(item), action);
        }

        int bottom = size() - 9;
        if (page > 0) set(bottom + 3, arrow("gui.page.previous", pages), click -> turn(-1));
        if (page < pages - 1) set(bottom + 5, arrow("gui.page.next", pages), click -> turn(1));
        set(bottom + 4, Gui.item(Material.PAPER, plugin.getMessageService().rawFor(viewer, "gui.page.info",
                "page", page + 1, "pages", pages), List.of()));
        drawFooter(bottom);

        backButton(bottom);
        closeButton();
    }

    private ItemStack arrow(String key, int pages) {
        return Gui.item(Material.ARROW, plugin.getMessageService().rawFor(viewer, key, "page", page + 1, "pages", pages), List.of());
    }

    private void turn(int by) {
        Gui.click(viewer);
        page += by;
        refresh();
    }

    public int page() {
        return page;
    }

    /** At least one page, even when the list is empty. */
    public static int pageCount(int itemCount) {
        return Math.max(1, (itemCount + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    /** Keeps a page number inside 0..pages-1 (the list can shrink while the menu is open). */
    public static int clamp(int page, int pages) {
        return Math.max(0, Math.min(page, pages - 1));
    }
}
