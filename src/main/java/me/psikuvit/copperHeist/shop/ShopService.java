package me.psikuvit.copperHeist.shop;

import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.game.Game;
import me.psikuvit.copperHeist.game.GamePlayer;
import me.psikuvit.copperHeist.game.Team;
import me.psikuvit.copperHeist.loot.LootItem;
import me.psikuvit.copperHeist.util.Pdc;
import me.psikuvit.copperHeist.util.PdcKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The doc §9 shop: browsing (a small GUI) and buying (spending carried loot
 * value - the same unit LootItem already tags everything with, so there's no
 * separate currency to track). Applying each item's actual effect lives here
 * too, except the two that need to be triggered later by using a held item
 * (Honeycomb, Storm Rod) - those are handled where that use happens
 * (GolemInteractListener) and just call back into GolemManager.
 */
public class ShopService {

    private final CopperHeist plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private FileConfiguration config;

    public ShopService(CopperHeist plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "shop.yml");
        if (!file.exists()) plugin.saveResource("shop.yml", false);
        config = YamlConfiguration.loadConfiguration(file);
    }

    public int cost(ShopItem item) {
        return config.getInt("items." + item.key + ".cost", 0);
    }

    public Inventory buildMenu() {
        Inventory inventory = Bukkit.createInventory(new ShopHolder(), 9, miniMessage.deserialize("<gold><bold>Copper Heist Shop"));
        for (ShopItem item : ShopItem.values()) {
            inventory.addItem(buildDisplayItem(item));
        }
        return inventory;
    }

    private ItemStack buildDisplayItem(ShopItem item) {
        ItemStack stack = new ItemStack(item.material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(miniMessage.deserialize(config.getString("items." + item.key + ".name", item.key))
                .append(Component.text(" - " + cost(item) + " value")).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        for (String line : config.getStringList("items." + item.key + ".lore")) {
            lore.add(miniMessage.deserialize(line).decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);
        stack.setItemMeta(meta);
        Pdc.set(stack, PdcKeys.SHOP_ITEM, item.key);
        return stack;
    }

    public void purchase(Player player, ShopItem item, Game game, GamePlayer gp) {
        if (item == ShopItem.NEW_GOLEM) {
            int cap = plugin.getConfig().getInt("golems.cap", 4);
            if (game.getTeam(gp.getTeam()).getGolems().size() >= cap) {
                player.sendActionBar(plugin.getMessageService().get("actionbar.golem-cap-reached"));
                return;
            }
        }

        int cost = cost(item);
        if (!charge(player, cost)) {
            player.sendActionBar(plugin.getMessageService().get("actionbar.cant-afford", "cost", cost));
            return;
        }

        switch (item) {
            case HONEYCOMB -> player.getInventory().addItem(ShopItem.createHoneycomb());
            case WIND_CHARGES -> player.getInventory().addItem(new ItemStack(Material.WIND_CHARGE, 3));
            case HEALING_POTION -> player.getInventory().addItem(createHealingPotion());
            case OXIDIZER_SPLASH -> player.getInventory().addItem(ShopItem.createOxidizerSplash());
            case NEW_GOLEM -> game.getGolemManager().spawnOne(gp.getTeam());
            case STORM_ROD -> giveTaggedItem(player, item, "<gold>Storm Rod");
        }
        player.sendActionBar(plugin.getMessageService().get("actionbar.purchased", "item", config.getString("items." + item.key + ".name", item.key)));
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
            player.getInventory().setItem(i, newAmount <= 0 ? null : withAmount(stack, newAmount));
            remaining -= need * value;
        }
        return true;
    }

    private ItemStack withAmount(ItemStack stack, int amount) {
        stack.setAmount(amount);
        return stack;
    }

    private void giveTaggedItem(Player player, ShopItem item, String name) {
        ItemStack stack = new ItemStack(item.material);
        Pdc.set(stack, PdcKeys.SHOP_ITEM, item.key);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(miniMessage.deserialize(name).decoration(TextDecoration.ITALIC, false));
        stack.setItemMeta(meta);
        player.getInventory().addItem(stack);
    }

    private ItemStack createHealingPotion() {
        ItemStack stack = new ItemStack(Material.POTION);
        if (stack.getItemMeta() instanceof PotionMeta meta) {
            meta.setBasePotionType(PotionType.STRONG_HEALING);
            stack.setItemMeta(meta);
        }
        return stack;
    }
}
