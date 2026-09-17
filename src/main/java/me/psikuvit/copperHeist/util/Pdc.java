package me.psikuvit.copperHeist.util;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataHolder;

/**
 * Read/write helpers for {@link PdcKey}s on both entities (or any other
 * PersistentDataHolder - blocks, chunks) and ItemStacks. Entities expose
 * their container directly; items need the meta get/set round-trip since
 * ItemStack itself isn't a PersistentDataHolder.
 */
public final class Pdc {

    private Pdc() {
    }

    // ---- entities / any PersistentDataHolder ----

    public static <T, Z> void set(PersistentDataHolder holder, PdcKey<T, Z> key, Z value) {
        holder.getPersistentDataContainer().set(key.namespacedKey(), key.type(), value);
    }

    public static <T, Z> Z get(PersistentDataHolder holder, PdcKey<T, Z> key) {
        return holder.getPersistentDataContainer().get(key.namespacedKey(), key.type());
    }

    public static <T, Z> Z get(PersistentDataHolder holder, PdcKey<T, Z> key, Z defaultValue) {
        Z value = get(holder, key);
        return value != null ? value : defaultValue;
    }

    public static boolean has(PersistentDataHolder holder, PdcKey<?, ?> key) {
        return holder.getPersistentDataContainer().has(key.namespacedKey(), key.type());
    }

    public static void remove(PersistentDataHolder holder, PdcKey<?, ?> key) {
        holder.getPersistentDataContainer().remove(key.namespacedKey());
    }

    // ---- ItemStacks ----

    public static <T, Z> void set(ItemStack item, PdcKey<T, Z> key, Z value) {
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(key.namespacedKey(), key.type(), value);
        item.setItemMeta(meta);
    }

    public static <T, Z> Z get(ItemStack item, PdcKey<T, Z> key) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(key.namespacedKey(), key.type());
    }

    public static <T, Z> Z get(ItemStack item, PdcKey<T, Z> key, Z defaultValue) {
        Z value = get(item, key);
        return value != null ? value : defaultValue;
    }

    public static boolean has(ItemStack item, PdcKey<?, ?> key) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(key.namespacedKey(), key.type());
    }

    public static void remove(ItemStack item, PdcKey<?, ?> key) {
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().remove(key.namespacedKey());
        item.setItemMeta(meta);
    }
}
