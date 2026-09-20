package me.psikuvit.copperHeist.menu;

import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.cosmetics.CosmeticDefinition;
import me.psikuvit.copperHeist.cosmetics.CosmeticService;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** How a cosmetic looks as a menu item, shared by the category list and the featured row so they always say the same thing. */
final class CosmeticItems {

    private CosmeticItems() {
    }

    /**
     * The item for a cosmetic: its icon, its name in the rarity colour, its description, then one status line that tells the viewer the
     * next thing they can do (unequip, equip, buy) or what is in their way (level, coins, not for sale).
     */
    static ItemStack icon(CopperHeist plugin, Player viewer, CosmeticDefinition cosmetic, boolean featured) {
        CosmeticService service = plugin.getCosmetics();
        var messages = plugin.getMessageService();
        List<String> lore = new ArrayList<>(cosmetic.description());
        lore.add("");
        lore.add(messages.rawFor(viewer, "cosmetics.menu.rarity", "tag", cosmetic.rarity().tag(),
                "rarity", messages.rawFor(viewer, "cosmetics.rarity." + cosmetic.rarity().key())));
        if (featured) lore.add(messages.rawFor(viewer, "cosmetics.menu.featured"));

        boolean equipped = service.equipped(viewer, cosmetic.category()) == cosmetic;
        if (equipped) {
            lore.add(messages.rawFor(viewer, "cosmetics.menu.equipped"));
            lore.add(messages.rawFor(viewer, "cosmetics.menu.click-unequip"));
        } else if (service.hasAccess(viewer, cosmetic)) {
            lore.add(messages.rawFor(viewer, "cosmetics.menu.click-equip"));
        } else {
            lore.addAll(buyLines(plugin, viewer, cosmetic));
        }
        lore.add(messages.rawFor(viewer, "cosmetics.menu.right-click-preview"));

        ItemStack item = Gui.item(cosmetic.icon(), service.displayName(cosmetic), lore);
        if (equipped) Gui.glow(item);
        return item;
    }

    private static List<String> buyLines(CopperHeist plugin, Player viewer, CosmeticDefinition cosmetic) {
        var messages = plugin.getMessageService();
        List<String> lines = new ArrayList<>();
        if (!cosmetic.purchasable()) {
            lines.add(messages.rawFor(viewer, cosmetic.free() && cosmetic.level() > 0 ? "cosmetics.menu.unlocks-at-level" : "cosmetics.menu.not-for-sale",
                    "level", cosmetic.level()));
            return lines;
        }
        long coins = plugin.getProgress().coins(viewer.getUniqueId(), viewer.getName());
        lines.add(messages.rawFor(viewer, coins >= cosmetic.price() ? "cosmetics.menu.price-ok" : "cosmetics.menu.price-bad",
                "price", cosmetic.price(), "coins", coins));
        var outcome = plugin.getCosmetics().preview(viewer, cosmetic);
        lines.add(switch (outcome) {
            case OK -> messages.rawFor(viewer, "cosmetics.menu.click-buy");
            case LEVEL_TOO_LOW -> messages.rawFor(viewer, "cosmetics.menu.needs-level", "level", cosmetic.level());
            case NOT_ENOUGH_COINS -> messages.rawFor(viewer, "cosmetics.menu.need-coins");
            default -> messages.rawFor(viewer, "cosmetics.outcome." + outcome.name().toLowerCase(Locale.ROOT));
        });
        return lines;
    }
}
