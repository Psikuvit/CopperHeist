package me.psikuvit.copperHeist.menu;

import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.cosmetics.CosmeticCategory;
import me.psikuvit.copperHeist.cosmetics.CosmeticDefinition;
import me.psikuvit.copperHeist.cosmetics.CosmeticService;
import me.psikuvit.copperHeist.ui.MessageService;
import me.psikuvit.copperHeist.ui.Theme;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The cosmetics home screen: one tile per category (with how many you own and what you have equipped), today's featured items, your
 * coins and the switch that turns cosmetic effects off for you. Opened from the hub chest item, {@code /ch cosmetics} and the shop.
 */
public class CosmeticsMenu extends Menu {

    private static final int FIRST_CATEGORY_SLOT = 10;
    private static final int FEATURED_LABEL_SLOT = 28;
    private static final int[] FEATURED_SLOTS = {30, 31, 32};

    public CosmeticsMenu(CopperHeist plugin, Player viewer) {
        super(plugin, viewer);
    }

    @Override
    protected Component title() {
        return Theme.mini().deserialize(plugin.getMessageService().rawFor(viewer, "cosmetics.menu.title"));
    }

    @Override
    protected int rows() {
        return 5;
    }

    @Override
    protected boolean isValid() {
        return plugin.getCosmetics().enabled();
    }

    @Override
    protected void draw() {
        border(Material.GRAY_STAINED_GLASS_PANE);
        CosmeticService service = plugin.getCosmetics();
        var messages = plugin.getMessageService();

        int slot = FIRST_CATEGORY_SLOT;
        for (CosmeticCategory category : CosmeticCategory.values()) {
            if (service.registry().inCategory(category).isEmpty()) continue;
            set(slot++, categoryTile(category), click -> {
                Gui.click(click.player());
                openChild(new CosmeticCategoryMenu(plugin, viewer, category));
            });
        }
        if (slot == FIRST_CATEGORY_SLOT) {
            set(13, Gui.item(Material.BARRIER, messages.rawFor(viewer, "cosmetics.menu.none-name"),
                    List.of(messages.rawFor(viewer, "cosmetics.menu.none-lore"))));
        }

        drawFeatured(service);
        drawFooter(messages);
        backButton(size() - 9);
        closeButton();
    }

    private ItemStack categoryTile(CosmeticCategory category) {
        CosmeticService service = plugin.getCosmetics();
        var messages = plugin.getMessageService();
        int total = 0;
        int owned = 0;
        for (CosmeticDefinition cosmetic : service.registry().inCategory(category)) {
            if (cosmetic.hidden() && !service.hasAccess(viewer, cosmetic)) continue;
            total++;
            if (service.hasAccess(viewer, cosmetic)) owned++;
        }
        CosmeticDefinition equipped = service.equipped(viewer, category);

        List<String> lore = new ArrayList<>();
        lore.add(messages.rawFor(viewer, "cosmetics.menu.owned", "owned", owned, "total", total));
        lore.add(equipped == null ? messages.rawFor(viewer, "cosmetics.menu.nothing-equipped")
                : messages.rawFor(viewer, "cosmetics.menu.equipped-name", "name", service.displayName(equipped)));
        lore.add("");
        lore.add(messages.rawFor(viewer, "cosmetics.menu.click-open"));
        return Gui.item(category.icon(), "<primary>" + messages.rawFor(viewer, category.langKey()), lore);
    }

    /** A row of today's picks: things you could buy and don't have. Clicking one does the same as in its category. */
    private void drawFeatured(CosmeticService service) {
        int count = Math.min(FEATURED_SLOTS.length, plugin.settings().getInt("cosmetics.featured-count", 3));
        List<CosmeticDefinition> picks = service.featured(viewer, count);
        if (picks.isEmpty()) return;
        set(FEATURED_LABEL_SLOT, Gui.item(Material.NETHER_STAR, plugin.getMessageService().rawFor(viewer, "cosmetics.menu.featured-title"),
                List.of(plugin.getMessageService().rawFor(viewer, "cosmetics.menu.featured-lore"))));
        for (int i = 0; i < picks.size(); i++) {
            CosmeticDefinition cosmetic = picks.get(i);
            set(FEATURED_SLOTS[i], CosmeticItems.icon(plugin, viewer, cosmetic, true), click -> {
                if (click.isRight()) CosmeticCategoryMenu.preview(viewer, service, cosmetic, plugin);
                else CosmeticCategoryMenu.handle(plugin, this, viewer, cosmetic);
            });
        }
    }

    private void drawFooter(MessageService messages) {
        int bottom = size() - 9;
        long coins = plugin.getProgress().coins(viewer.getUniqueId(), viewer.getName());
        set(bottom + 4, Gui.item(Material.GOLD_NUGGET, messages.rawFor(viewer, "cosmetics.menu.balance-name"),
                List.of(messages.rawFor(viewer, "cosmetics.menu.balance-lore", "coins", coins))));

        boolean on = plugin.getCosmetics().effectsEnabled(viewer);
        set(bottom + 2, Gui.item(on ? Material.LIME_DYE : Material.GRAY_DYE,
                messages.rawFor(viewer, on ? "cosmetics.menu.effects-on" : "cosmetics.menu.effects-off"),
                List.of(messages.rawFor(viewer, "cosmetics.menu.effects-hint"))), click -> {
            plugin.getCosmetics().setEffectsEnabled(viewer, !on);
            Gui.click(viewer);
            refresh();
        });
    }
}
