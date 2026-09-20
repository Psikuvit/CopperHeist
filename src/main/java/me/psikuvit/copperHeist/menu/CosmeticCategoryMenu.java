package me.psikuvit.copperHeist.menu;

import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.cosmetics.CosmeticCategory;
import me.psikuvit.copperHeist.cosmetics.CosmeticDefinition;
import me.psikuvit.copperHeist.cosmetics.CosmeticService;
import me.psikuvit.copperHeist.cosmetics.CosmeticService.Outcome;
import me.psikuvit.copperHeist.ui.Theme;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;

/**
 * One category of cosmetics as pages of items. Left-click equips (or unequips the one that is equipped) or, for something you can
 * afford, asks to confirm buying it; right-click previews it just for you.
 */
public class CosmeticCategoryMenu extends PagedMenu<CosmeticDefinition> {

    private final CosmeticCategory category;

    public CosmeticCategoryMenu(CopperHeist plugin, Player viewer, CosmeticCategory category) {
        super(plugin, viewer);
        this.category = category;
    }

    @Override
    protected Component title() {
        return Theme.mini().deserialize(plugin.getMessageService().rawFor(viewer, "cosmetics.menu.category-title",
                "category", plugin.getMessageService().rawFor(viewer, category.langKey())));
    }

    @Override
    protected boolean isValid() {
        return plugin.getCosmetics().enabled();
    }

    @Override
    protected List<CosmeticDefinition> items() {
        return plugin.getCosmetics().visibleIn(viewer, category);
    }

    @Override
    protected ItemStack icon(CosmeticDefinition cosmetic) {
        return CosmeticItems.icon(plugin, viewer, cosmetic, false);
    }

    @Override
    protected void drawFooter(int bottom) {
        long coins = plugin.getProgress().coins(viewer.getUniqueId(), viewer.getName());
        var messages = plugin.getMessageService();
        set(bottom + 7, Gui.item(Material.GOLD_NUGGET, messages.rawFor(viewer, "cosmetics.menu.balance-name"),
                List.of(messages.rawFor(viewer, "cosmetics.menu.balance-lore", "coins", coins))));
    }

    @Override
    protected void onSelect(CosmeticDefinition cosmetic, Click click) {
        if (click.isRight()) {
            preview(viewer, plugin.getCosmetics(), cosmetic, plugin);
            return;
        }
        handle(plugin, this, viewer, cosmetic);
    }

    /** Right-click: shows the cosmetic to the viewer only, or says there is nothing to show. */
    static void preview(Player viewer, CosmeticService service, CosmeticDefinition cosmetic, CopperHeist plugin) {
        if (service.showPreview(viewer, cosmetic)) {
            Gui.click(viewer);
        } else {
            Gui.deny(viewer);
            plugin.getActionBar().show(viewer, plugin.getMessageService().get(viewer, "cosmetics.menu.no-preview"));
        }
    }

    /** Left-click on a cosmetic from any menu: unequip, equip, or (after confirming) buy - and say what happened. */
    static void handle(CopperHeist plugin, Menu from, Player viewer, CosmeticDefinition cosmetic) {
        CosmeticService service = plugin.getCosmetics();
        var messages = plugin.getMessageService();

        if (service.equipped(viewer, cosmetic.category()) == cosmetic) {
            service.unequip(viewer, cosmetic.category());
            Gui.click(viewer);
            plugin.getActionBar().show(viewer, messages.get(viewer, "cosmetics.unequipped", "name", service.displayName(cosmetic)));
            from.refresh();
            return;
        }
        if (service.hasAccess(viewer, cosmetic)) {
            Outcome outcome = service.equip(viewer, cosmetic);
            if (outcome == Outcome.OK) {
                Gui.success(viewer);
                plugin.getActionBar().show(viewer, messages.get(viewer, "cosmetics.equipped", "name", service.displayName(cosmetic)));
            } else {
                refuse(plugin, viewer, outcome);
            }
            from.refresh();
            return;
        }

        Outcome outcome = service.preview(viewer, cosmetic);
        if (outcome != Outcome.OK) {
            refuse(plugin, viewer, outcome);
            return;
        }
        ItemStack subject = CosmeticItems.icon(plugin, viewer, cosmetic, false);
        String price = messages.rawFor(viewer, "cosmetics.menu.confirm-price", "price", cosmetic.price());
        from.openChild(new ConfirmMenu(plugin, viewer, messages.rawFor(viewer, "cosmetics.menu.confirm-title"), subject, price, () -> {
            Outcome result = service.purchase(viewer, cosmetic);
            if (result == Outcome.OK) {
                Gui.success(viewer);
                plugin.getActionBar().show(viewer, messages.get(viewer, "cosmetics.bought", "name", service.displayName(cosmetic)));
            } else {
                refuse(plugin, viewer, result);
            }
        }));
    }

    private static void refuse(CopperHeist plugin, Player viewer, Outcome outcome) {
        Gui.deny(viewer);
        plugin.getActionBar().show(viewer, plugin.getMessageService().get(viewer, "cosmetics.outcome." + outcome.name().toLowerCase(Locale.ROOT)));
    }
}
