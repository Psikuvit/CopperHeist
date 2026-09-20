package me.psikuvit.copperHeist.menu;

import org.bukkit.inventory.ItemStack;

import java.util.function.Consumer;

/** One slot of a {@link Menu}: what it shows and what a click on it does (null action = decoration only). */
public record Button(ItemStack item, Consumer<Click> action) {

    public static Button of(ItemStack item, Consumer<Click> action) {
        return new Button(item, action);
    }

    public static Button decoration(ItemStack item) {
        return new Button(item, null);
    }
}
