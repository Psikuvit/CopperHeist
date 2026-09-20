package me.psikuvit.copperHeist.menu;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

/** A click on a menu button, handed to the button's action. */
public record Click(Player player, Menu menu, int slot, ClickType type) {

    public boolean isRight() {
        return type == ClickType.RIGHT || type == ClickType.SHIFT_RIGHT;
    }

    public boolean isShift() {
        return type.isShiftClick();
    }
}
