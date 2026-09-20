package me.psikuvit.copperHeist.shop;

import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.config.ConfigFiles;
import me.psikuvit.copperHeist.game.Game;
import me.psikuvit.copperHeist.game.GamePlayer;
import me.psikuvit.copperHeist.game.GameState;
import me.psikuvit.copperHeist.loot.LootItem;
import me.psikuvit.copperHeist.menu.Gui;
import me.psikuvit.copperHeist.shop.action.GiveItemAction;
import me.psikuvit.copperHeist.shop.action.ShopAction;
import me.psikuvit.copperHeist.shop.action.ShopPurchase;
import me.psikuvit.copperHeist.util.Pdc;
import me.psikuvit.copperHeist.util.PdcKeys;
import me.psikuvit.copperHeist.ui.Theme;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The shop: entries and menu layout come from shop.yml, what each entry does is a registered
 * {@link ShopAction}. Payment is carried loot value (the same unit LootItem tags everything
 * with, so there is no separate currency), smallest pieces first.
 */
public class ShopService {

    private final CopperHeist plugin;
    private final MiniMessage miniMessage = Theme.mini();
    private final Map<String, ShopEntry> entries = new LinkedHashMap<>();
    private FileConfiguration config;

    public ShopService(CopperHeist plugin) {
        this.plugin = plugin;
    }

    public void load() {
        config = ConfigFiles.load(plugin, "shop.yml");
        entries.clear();
        ConfigurationSection section = config.getConfigurationSection("items");
        if (section == null) return;
        for (String id : section.getKeys(false)) {
            ConfigurationSection item = section.getConfigurationSection(id);
            if (item == null) continue;
            try {
                ShopEntry entry = parse(id.toLowerCase(Locale.ROOT), item);
                entries.put(entry.id(), entry);
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("Skipping shop item '" + id + "' in shop.yml: " + ex.getMessage());
            }
        }
        plugin.getLogger().info("Loaded " + entries.size() + " shop item(s).");
    }

    private ShopEntry parse(String id, ConfigurationSection s) {
        Material material = Material.matchMaterial(s.getString("material", "PAPER"));
        if (material == null) throw new IllegalArgumentException("unknown material '" + s.getString("material") + "'");

        GameState minPhase = null;
        String phase = s.getString("min-phase");
        if (phase != null && !phase.isBlank()) {
            try {
                minPhase = GameState.valueOf(phase.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("unknown min-phase '" + phase + "' (setup, collection, heist or final_rush)");
            }
        }
        Map<String, Object> params = new LinkedHashMap<>();
        for (String key : s.getKeys(false)) params.put(key, s.get(key));
        return new ShopEntry(id, s.getString("action", "give-item"), material, s.getInt("amount", 1), s.getInt("cost", 0),
                s.getString("name", id), s.getStringList("lore"), minPhase, s.getInt("max-per-player", 0),
                s.getInt("cooldown-seconds", 0), s.getInt("slot", -1), params);
    }

    public List<ShopEntry> entries() {
        return new ArrayList<>(entries.values());
    }

    /** Menu title in the viewer's language (lang key shop.title if a translation defines it, else shop.yml). */
    public String menuTitle(Player viewer) {
        String translated = plugin.getMessageService().rawOrNull(viewer, "shop.title");
        return translated != null ? translated : config.getString("menu.title", "<gold><bold>Copper Heist Shop");
    }

    /** Entry name in the viewer's language (lang key shop.items.&lt;id&gt;.name, else shop.yml). */
    public String entryName(ShopEntry entry, Player viewer) {
        String translated = plugin.getMessageService().rawOrNull(viewer, "shop.items." + entry.id() + ".name");
        return translated != null ? translated : entry.name();
    }

    public List<String> entryLore(ShopEntry entry, Player viewer) {
        List<String> translated = plugin.getMessageService().listOrNull(viewer, "shop.items." + entry.id() + ".lore");
        return translated != null ? translated : entry.lore();
    }

    public ShopEntry entry(String id) {
        return id == null ? null : entries.get(id.toLowerCase(Locale.ROOT));
    }

    /** The item a give-item entry hands out - role loadouts use this to start with shop items. */
    public ItemStack createItem(String id) {
        ShopEntry entry = entry(id);
        return entry == null ? null : GiveItemAction.createStack(entry);
    }

    // ---- menu ----

    /** A framed menu: entries laid out over the inside, your carried loot value at the bottom, a close button in the corner. */
    public Inventory buildMenu(Player viewer) {
        int rows = Gui.rowsFor(entries.size());
        Inventory inventory = Bukkit.createInventory(new ShopHolder(), rows * 9, miniMessage.deserialize(menuTitle(viewer)));
        fill(inventory, viewer);
        return inventory;
    }

    /** (Re)draws the whole menu. Also used to refresh an open menu after a purchase so prices and locks stay current. */
    public void fill(Inventory inventory, Player viewer) {
        inventory.clear();
        Material frame = Material.matchMaterial(config.getString("menu.border", "GRAY_STAINED_GLASS_PANE"));
        Gui.border(inventory, frame == null ? Material.GRAY_STAINED_GLASS_PANE : frame);

        Game game = plugin.getGameManager().getGame(viewer);
        GamePlayer gp = game == null ? null : game.getGamePlayer(viewer.getUniqueId());
        int have = plugin.getLootWeightService().getCarriedValue(viewer);
        int footer = inventory.getSize() - 9;

        int index = 0;
        for (ShopEntry entry : entries.values()) {
            int slot = entry.slot() >= 0 && entry.slot() < footer ? entry.slot() : Gui.slotFor(index++);
            if (slot >= footer) continue; // more entries than the menu has room for
            inventory.setItem(slot, buildDisplayItem(entry, viewer, game, gp, have));
        }

        var messages = plugin.getMessageService();
        inventory.setItem(footer + 4, Gui.item(Material.GOLD_INGOT, messages.rawFor(viewer, "gui.shop.balance-name"),
                List.of(messages.rawFor(viewer, "gui.shop.balance-lore", "value", have),
                        messages.rawFor(viewer, "gui.shop.balance-hint")), "info"));
        inventory.setItem(footer + 8, Gui.item(Material.BARRIER, messages.rawFor(viewer, "gui.close"), List.of(), "close"));
    }

    private ItemStack buildDisplayItem(ShopEntry entry, Player viewer, Game game, GamePlayer gp, int have) {
        var messages = plugin.getMessageService();
        ItemStack stack = "give-item".equals(entry.action()) ? GiveItemAction.createStack(entry)
                : new ItemStack(entry.material(), Math.max(1, entry.amount()));
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Gui.text(entryName(entry, viewer)));

        List<Component> lore = new ArrayList<>();
        for (String line : entryLore(entry, viewer)) lore.add(Gui.text(line));
        lore.add(Component.empty());
        boolean affordable = have >= entry.cost();
        lore.add(Gui.text(messages.rawFor(viewer, affordable ? "gui.shop.cost-ok" : "gui.shop.cost-bad", "cost", entry.cost(), "have", have)));

        String blocked = null;
        if (entry.minPhase() != null && (game == null || !(game.isActive() && game.getState().ordinal() >= entry.minPhase().ordinal()))) {
            blocked = messages.rawFor(viewer, "gui.shop.locked", "phase", entry.minPhase().name().replace('_', ' '));
        } else if (gp != null && entry.maxPerPlayer() > 0 && gp.purchaseCount(entry.id()) >= entry.maxPerPlayer()) {
            blocked = messages.rawFor(viewer, "gui.shop.limit", "limit", entry.maxPerPlayer());
        } else if (gp != null && gp.purchaseCooldownRemaining(entry.id()) > 0) {
            blocked = messages.rawFor(viewer, "gui.shop.cooldown", "seconds", gp.purchaseCooldownRemaining(entry.id()));
        }
        lore.add(Gui.text(blocked != null ? blocked : messages.rawFor(viewer, affordable ? "gui.shop.click" : "gui.shop.need-more")));
        meta.lore(lore);
        stack.setItemMeta(meta);
        Pdc.set(stack, PdcKeys.SHOP_ITEM, entry.id());
        return stack;
    }

    // ---- buying ----

    /** Tries to buy {@code entry}; tells the player why not on failure. Returns whether the purchase went through. */
    public boolean purchase(Player player, ShopEntry entry, Game game, GamePlayer gp) {
        var messages = plugin.getMessageService();

        if (entry.minPhase() != null && !(game.isActive() && game.getState().ordinal() >= entry.minPhase().ordinal())) {
            plugin.getActionBar().show(player, messages.get(player, "actionbar.shop-locked", "phase", entry.minPhase().name().replace('_', ' ')));
            return false;
        }
        if (entry.maxPerPlayer() > 0 && gp.purchaseCount(entry.id()) >= entry.maxPerPlayer()) {
            plugin.getActionBar().show(player, messages.get(player, "actionbar.shop-limit", "limit", entry.maxPerPlayer()));
            return false;
        }
        long cooldown = gp.purchaseCooldownRemaining(entry.id());
        if (cooldown > 0) {
            plugin.getActionBar().show(player, messages.get(player, "actionbar.shop-cooldown", "seconds", cooldown));
            return false;
        }

        ShopAction action = plugin.getShopActions().get(entry.action());
        if (action == null) {
            plugin.getLogger().warning("Shop item '" + entry.id() + "' uses unknown action '" + entry.action() + "'");
            return false;
        }
        ShopPurchase purchase = new ShopPurchase(plugin, game, player, gp, entry);
        String refusal = action.check(purchase);
        if (refusal != null) {
            plugin.getActionBar().show(player, messages.get(player, refusal));
            return false;
        }

        if (!charge(player, entry.cost())) {
            plugin.getActionBar().show(player, messages.get(player, "actionbar.cant-afford", "cost", entry.cost()));
            return false;
        }

        action.perform(purchase);
        gp.recordPurchase(entry.id(), entry.cooldownSeconds());
        plugin.getActionBar().show(player, messages.get(player, "actionbar.purchased", "item", entry.name()));
        return true;
    }

    /** Removes tagged loot worth at least cost, smallest-value pieces first, or refuses if there isn't enough. */
    private boolean charge(Player player, int cost) {
        if (cost <= 0) return true;

        ItemStack[] contents = player.getInventory().getContents();
        List<Integer> lootSlots = new ArrayList<>();
        for (int i = 0; i < contents.length; i++) {
            if (LootItem.isLoot(contents[i])) lootSlots.add(i);
        }
        lootSlots.sort(Comparator.comparingInt(i -> LootItem.getValue(contents[i])));

        int total = 0;
        for (int i : lootSlots) total += LootItem.getValue(contents[i]) * contents[i].getAmount();
        if (total < cost) return false;

        int remaining = cost;
        for (int i : lootSlots) {
            if (remaining <= 0) break;
            ItemStack stack = contents[i];
            int value = LootItem.getValue(stack);
            int amount = stack.getAmount();
            int need = Math.min(amount, (int) Math.ceil(remaining / (double) value));
            int newAmount = amount - need;
            stack.setAmount(newAmount);
            player.getInventory().setItem(i, newAmount <= 0 ? null : stack);
            remaining -= need * value;
        }
        return true;
    }
}
