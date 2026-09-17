package me.psikuvit.copperHeist.util;

import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/** A namespaced PDC key paired with its type, so call sites never repeat the type argument. */
public record PdcKey<T, Z>(NamespacedKey namespacedKey, PersistentDataType<T, Z> type) {

    public static <T, Z> PdcKey<T, Z> of(Plugin plugin, String key, PersistentDataType<T, Z> type) {
        return new PdcKey<>(new NamespacedKey(plugin, key), type);
    }
}
