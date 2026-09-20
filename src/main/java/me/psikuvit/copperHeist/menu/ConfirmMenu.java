package me.psikuvit.copperHeist.menu;

import me.psikuvit.copperHeist.CopperHeist;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * A small "are you sure?" screen for anything that costs something: the thing in the middle, a green confirm on one side and a red
 * cancel on the other. Open it from another menu with {@code openChild}; cancel goes back to that menu.
 */
public class ConfirmMenu extends Menu {

    private final String titleMini;
    private final ItemStack subject;
    private final String confirmLine;
    private final Runnable onConfirm;

    /**
     * @param titleMini   MiniMessage title
     * @param subject     the item being bought, shown in the middle
     * @param confirmLine MiniMessage lore for the confirm button, e.g. the price
     * @param onConfirm   runs when the player confirms (the menu then goes back to the one it was opened from)
     */
    public ConfirmMenu(CopperHeist plugin, Player viewer, String titleMini, ItemStack subject, String confirmLine, Runnable onConfirm) {
        super(plugin, viewer);
        this.titleMini = titleMini;
        this.subject = subject;
        this.confirmLine = confirmLine;
        this.onConfirm = onConfirm;
    }

    @Override
    protected Component title() {
        return Gui.text(titleMini);
    }

    @Override
    protected int rows() {
        return 3;
    }

    @Override
    protected void draw() {
        border(Material.GRAY_STAINED_GLASS_PANE);
        var messages = plugin.getMessageService();
        set(13, subject);
        set(11, Gui.item(Material.LIME_CONCRETE, messages.rawFor(viewer, "gui.confirm"), List.of(confirmLine)), click -> {
            Gui.click(click.player());
            onConfirm.run();
            back();
        });
        set(15, Gui.item(Material.RED_CONCRETE, messages.rawFor(viewer, "gui.cancel"), List.of()), click -> {
            Gui.click(click.player());
            back();
        });
    }
}
