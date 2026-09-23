package me.psikuvit.copperHeist.menu.shop;

import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.game.Game;
import me.psikuvit.copperHeist.game.GamePlayer;
import me.psikuvit.copperHeist.menu.Click;
import me.psikuvit.copperHeist.menu.Gui;
import me.psikuvit.copperHeist.menu.Menu;
import me.psikuvit.copperHeist.menu.cosmetics.CosmeticsMenu;
import me.psikuvit.copperHeist.shop.ShopEntry;
import me.psikuvit.copperHeist.shop.ShopService;
import me.psikuvit.copperHeist.shop.action.GiveItemAction;
import me.psikuvit.copperHeist.ui.Theme;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * The shop: entries laid out over a frame, your carried loot value at the bottom. Each icon shows its price, whether you can
 * afford it and any lock / limit / cooldown; it redraws after every purchase and once a second so cooldowns tick down.
 */
public class ShopMenu extends Menu {

    private final ShopService shop;

    public ShopMenu(CopperHeist plugin, Player viewer) {
        super(plugin, viewer);
        this.shop = plugin.getShopService();
    }

    @Override
    protected Component title() {
        return Theme.mini().deserialize(shop.menuTitle(viewer));
    }

    @Override
    protected int rows() {
        return Gui.rowsFor(shop.entries().size());
    }

    @Override
    protected boolean isValid() {
        return plugin.getGameManager().getGame(viewer) != null;
    }

    @Override
    protected int refreshTicks() {
        return 20;
    }

    @Override
    protected void draw() {
        Material frame = Material.matchMaterial(shop.menuBorder());
        border(frame == null ? Material.GRAY_STAINED_GLASS_PANE : frame);

        Game game = plugin.getGameManager().getGame(viewer);
        GamePlayer gp = game == null ? null : game.getGamePlayer(viewer.getUniqueId());
        int have = plugin.getLootWeightService().getCarriedValue(viewer);
        int footer = size() - 9;

        int index = 0;
        for (ShopEntry entry : shop.entries()) {
            int slot = entry.slot() >= 0 && entry.slot() < footer ? entry.slot() : Gui.slotFor(index++);
            if (slot >= footer) continue; // more entries than the menu has room for
            set(slot, displayItem(entry, game, gp, have), click -> buy(click, entry));
        }

        var messages = plugin.getMessageService();
        set(footer + 4, Gui.item(Material.GOLD_INGOT, messages.rawFor(viewer, "gui.shop.balance-name"),
                List.of(messages.rawFor(viewer, "gui.shop.balance-lore", "value", have), messages.rawFor(viewer, "gui.shop.balance-hint"))));
        if (plugin.getCosmetics().enabled()) {
            set(footer + 2, Gui.item(Material.ENDER_CHEST, messages.rawFor(viewer, "cosmetics.menu.shop-button-name"),
                    List.of(messages.rawFor(viewer, "cosmetics.menu.shop-button-lore"))), click -> {
                Gui.click(click.player());
                openChild(new CosmeticsMenu(plugin, viewer));
            });
        }
        closeButton();
    }

    private void buy(Click click, ShopEntry entry) {
        Game game = plugin.getGameManager().getGame(viewer);
        GamePlayer gp = game == null ? null : game.getGamePlayer(viewer.getUniqueId());
        if (gp == null) return;
        if (shop.purchase(viewer, entry, game, gp)) Gui.success(viewer);
        else Gui.deny(viewer);
        refresh(); // prices, locks and the balance changed
    }

    private ItemStack displayItem(ShopEntry entry, Game game, GamePlayer gp, int have) {
        var messages = plugin.getMessageService();
        ItemStack stack = "give-item".equals(entry.action()) ? GiveItemAction.createStack(entry)
                : new ItemStack(entry.material(), Math.max(1, entry.amount()));
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Gui.text(shop.entryName(entry, viewer)));

        List<Component> lore = new ArrayList<>();
        for (String line : shop.entryLore(entry, viewer)) lore.add(Gui.text(line));
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
        return stack;
    }
}
