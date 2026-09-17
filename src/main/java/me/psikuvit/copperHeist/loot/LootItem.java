package me.psikuvit.copperHeist.loot;

import me.psikuvit.copperHeist.game.Team;
import me.psikuvit.copperHeist.util.Pdc;
import me.psikuvit.copperHeist.util.PdcKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;
import java.util.random.RandomGenerator;

/**
 * PDC-tagged loot items, read/written through {@link Pdc}. Tags: loot_value
 * (anti-dupe), loot_id, match_id (prevents loot leaking between matches),
 * last_team (steal tracking).
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

    private LootItem() {
    }

    public static ItemStack create(Tier tier, String matchId) {
        ItemStack item = new ItemStack(tier.material);
        Pdc.set(item, PdcKeys.LOOT_VALUE, tier.value);
        Pdc.set(item, PdcKeys.LOOT_ID, UUID.randomUUID().toString());
        Pdc.set(item, PdcKeys.MATCH_ID, matchId);

        var meta = item.getItemMeta();
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
        return Pdc.has(item, PdcKeys.LOOT_VALUE);
    }

    public static int getValue(ItemStack item) {
        return Pdc.get(item, PdcKeys.LOOT_VALUE, 0);
    }

    public static String getMatchId(ItemStack item) {
        return Pdc.get(item, PdcKeys.MATCH_ID);
    }

    public static Team getLastTeam(ItemStack item) {
        String name = Pdc.get(item, PdcKeys.LAST_TEAM);
        if (name == null) return null;
        try {
            return Team.valueOf(name);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public static void setLastTeam(ItemStack item, Team team) {
        Pdc.set(item, PdcKeys.LAST_TEAM, team.name());
    }
}
