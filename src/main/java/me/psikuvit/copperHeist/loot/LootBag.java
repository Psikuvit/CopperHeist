package me.psikuvit.copperHeist.loot;

import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** Everything a player was carrying when they died or left: one right-clickable bag that expires if nobody takes it. */
public class LootBag {

    private final List<ItemStack> items;
    private final ItemDisplay display;
    private final Interaction hitbox;
    private final TextDisplay label;
    private final long expiresAtMillis;

    public LootBag(List<ItemStack> items, ItemDisplay display, Interaction hitbox, TextDisplay label, long expiresAtMillis) {
        this.items = items;
        this.display = display;
        this.hitbox = hitbox;
        this.label = label;
        this.expiresAtMillis = expiresAtMillis;
    }

    public List<ItemStack> getItems() {
        return items;
    }

    public ItemDisplay getDisplay() {
        return display;
    }

    public Interaction getHitbox() {
        return hitbox;
    }

    public TextDisplay getLabel() {
        return label;
    }

    public long getExpiresAtMillis() {
        return expiresAtMillis;
    }

    public int totalValue() {
        int total = 0;
        for (ItemStack item : items) total += LootItem.getValue(item) * item.getAmount();
        return total;
    }
}
