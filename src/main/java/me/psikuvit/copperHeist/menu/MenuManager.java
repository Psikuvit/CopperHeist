package me.psikuvit.copperHeist.menu;

import me.psikuvit.copperHeist.CopperHeist;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * The one place that manages every {@link Menu}: it opens them, tracks who has which open, cancels every click and drag inside
 * them (so no item can ever be taken out), dispatches clicks to the button under the cursor, rate-limits click spam, redraws
 * menus that ask for it, and closes everything on shutdown. Menus themselves contain no event code.
 */
public class MenuManager implements Listener {

    /** Ignore clicks arriving faster than this - stops a double-click buying twice. */
    private static final long CLICK_COOLDOWN_MS = 120;

    private final CopperHeist plugin;
    private final Map<UUID, Menu> open = new HashMap<>();
    private final Map<UUID, Long> lastClick = new HashMap<>();
    private BukkitTask task;
    private long tick;

    public MenuManager(CopperHeist plugin) {
        this.plugin = plugin;
    }

    public void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 10L, 10L);
    }

    /** Closes every open menu and stops refreshing - called from onDisable. */
    public void stop() {
        if (task != null) task.cancel();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof Menu) player.closeInventory();
        }
        open.clear();
        lastClick.clear();
    }

    /** Builds and shows the menu. Replaces whatever menu the player had open. */
    public void open(Player player, Menu menu) {
        menu.build();
        player.openInventory(menu.getInventory());
        open.put(player.getUniqueId(), menu);
        Gui.open(player);
    }

    /** The menu the player currently has open, or null. */
    public Menu current(Player player) {
        return open.get(player.getUniqueId());
    }

    /** Redraws the player's open menu if it is of the given type (e.g. after their balance changed). */
    public <T extends Menu> void refresh(Player player, Class<T> type) {
        Menu menu = open.get(player.getUniqueId());
        if (type.isInstance(menu)) menu.refresh();
    }

    /** Redraws every open menu of the given type (e.g. all arena pickers when a match changes state). */
    public <T extends Menu> void refreshAll(Class<T> type) {
        for (Menu menu : new ArrayList<>(open.values())) {
            if (type.isInstance(menu)) menu.refresh();
        }
    }

    private void tick() {
        tick += 10;
        for (Map.Entry<UUID, Menu> entry : new ArrayList<>(open.entrySet())) {
            Menu menu = entry.getValue();
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null) continue;
            if (!menu.valid()) {
                player.closeInventory();
                continue;
            }
            int interval = menu.refreshInterval();
            if (interval > 0 && tick % interval < 10) menu.refresh();
        }
    }

    // ---- events ----

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Menu menu)) return;
        event.setCancelled(true); // nothing in a menu can be taken, moved or shift-clicked in
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        if (event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT
                && event.getClick() != ClickType.SHIFT_LEFT && event.getClick() != ClickType.SHIFT_RIGHT) return;

        long now = System.currentTimeMillis();
        Long last = lastClick.put(player.getUniqueId(), now);
        if (last != null && now - last < CLICK_COOLDOWN_MS) return;

        try {
            menu.handleClick(new Click(player, menu, event.getSlot(), event.getClick()));
        } catch (RuntimeException ex) {
            plugin.getLogger().log(Level.WARNING, "A click in " + menu.getClass().getSimpleName() + " failed", ex);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Menu) event.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof Menu menu)) return;
        UUID id = event.getPlayer().getUniqueId();
        if (open.get(id) == menu) open.remove(id);
        try {
            menu.closed();
        } catch (RuntimeException ex) {
            plugin.getLogger().log(Level.WARNING, "Closing " + menu.getClass().getSimpleName() + " failed", ex);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        open.remove(event.getPlayer().getUniqueId());
        lastClick.remove(event.getPlayer().getUniqueId());
    }
}
