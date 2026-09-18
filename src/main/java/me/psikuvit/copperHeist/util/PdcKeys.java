package me.psikuvit.copperHeist.util;

import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/** Every PDC key the plugin tags onto items and entities, initialized once in onEnable. */
public final class PdcKeys {

    public static PdcKey<Integer, Integer> LOOT_VALUE;
    public static PdcKey<String, String> LOOT_ID;
    public static PdcKey<String, String> MATCH_ID;
    public static PdcKey<String, String> LAST_TEAM;
    public static PdcKey<String, String> GOLEM_TEAM;
    public static PdcKey<String, String> LOBBY_ITEM;
    public static PdcKey<String, String> SHOP_ITEM;

    private PdcKeys() {
    }

    public static void init(Plugin plugin) {
        LOOT_VALUE = PdcKey.of(plugin, "loot_value", PersistentDataType.INTEGER);
        LOOT_ID = PdcKey.of(plugin, "loot_id", PersistentDataType.STRING);
        MATCH_ID = PdcKey.of(plugin, "match_id", PersistentDataType.STRING);
        LAST_TEAM = PdcKey.of(plugin, "last_team", PersistentDataType.STRING);
        GOLEM_TEAM = PdcKey.of(plugin, "golem_team", PersistentDataType.STRING);
        LOBBY_ITEM = PdcKey.of(plugin, "lobby_item", PersistentDataType.STRING);
        SHOP_ITEM = PdcKey.of(plugin, "shop_item", PersistentDataType.STRING);
    }
}
