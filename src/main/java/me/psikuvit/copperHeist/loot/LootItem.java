package me.psikuvit.copperHeist.loot;

import me.psikuvit.copperHeist.game.Team;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.UUID;
import java.util.random.RandomGenerator;

/**
 * PDC-tagged loot items. Tags: loot_value (INTEGER), loot_id (STRING, anti-dupe),
 * match_id (STRING, prevents loot leaking between matches), last_team (STRING, steal tracking).
 */
public final class LootItem {

    public enum Tier {
        COPPER(Material.COPPER_INGOT, 1, 50),
        GOLD(Material.GOLD_INGOT, 3, 25),
        EMERALD(Material.EMERALD, 5, 15),
        DIAMOND(Material.DIAMOND, 10, 8);

        public final Material material;
        public final int value;
        public final int weight;

        Tier(Material material, int value, int weight) {
            this.material = material;
            this.value = value;
            this.weight = weight;
        }
    }

    private static NamespacedKey valueKey;
    private static NamespacedKey idKey;
    private static NamespacedKey matchKey;
    private static NamespacedKey teamKey;

    private LootItem() {
    }

    public static void init(Plugin plugin) {
        valueKey = new NamespacedKey(plugin, "loot_value");
        idKey = new NamespacedKey(plugin, "loot_id");
        matchKey = new NamespacedKey(plugin, "match_id");
        teamKey = new NamespacedKey(plugin, "last_team");
    }

    public static ItemStack create(Tier tier, String matchId) {
        ItemStack item = new ItemStack(tier.material);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(valueKey, PersistentDataType.INTEGER, tier.value);
        meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, UUID.randomUUID().toString());
        meta.getPersistentDataContainer().set(matchKey, PersistentDataType.STRING, matchId);
        meta.displayName(Component.text(tier.name() + " (" + tier.value + ")", NamedTextColor.YELLOW));
        item.setItemMeta(meta);
        return item;
    }

    public static Tier randomTier(RandomGenerator random) {
        int totalWeight = 0;
        for (Tier tier : Tier.values()) totalWeight += tier.weight;
        int roll = random.nextInt(totalWeight);
        int cumulative = 0;
        for (Tier tier : Tier.values()) {
            cumulative += tier.weight;
            if (roll < cumulative) return tier;
        }
        return Tier.COPPER;
    }

    public static boolean isLoot(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(valueKey, PersistentDataType.INTEGER);
    }

    public static int getValue(ItemStack item) {
        if (!isLoot(item)) return 0;
        Integer v = item.getItemMeta().getPersistentDataContainer().get(valueKey, PersistentDataType.INTEGER);
        return v == null ? 0 : v;
    }

    public static String getMatchId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(matchKey, PersistentDataType.STRING);
    }

    public static Team getLastTeam(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        String name = item.getItemMeta().getPersistentDataContainer().get(teamKey, PersistentDataType.STRING);
        if (name == null) return null;
        try {
            return Team.valueOf(name);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public static void setLastTeam(ItemStack item, Team team) {
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(teamKey, PersistentDataType.STRING, team.name());
        item.setItemMeta(meta);
    }
}
