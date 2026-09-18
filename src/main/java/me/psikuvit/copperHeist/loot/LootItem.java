package me.psikuvit.copperHeist.loot;

import me.psikuvit.copperHeist.game.Team;
import me.psikuvit.copperHeist.util.Pdc;
import me.psikuvit.copperHeist.util.PdcKeys;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;

/**
 * PDC-tagged loot items, read/written through {@link Pdc}. Tags: loot_value
 * (anti-dupe), loot_id, match_id (prevents loot leaking between matches),
 * last_team (steal tracking), loot_tier (id of the loot.yml tier this is).
 */
public final class LootItem {

    private static LootTierRegistry tiers;

    private LootItem() {
    }

    public static void init(LootTierRegistry registry) {
        tiers = registry;
    }

    public static LootTierRegistry tiers() {
        return tiers;
    }

    public static ItemStack create(LootTierDefinition tier, String matchId) {
        ItemStack item = new ItemStack(tier.material());
        Pdc.set(item, PdcKeys.LOOT_VALUE, tier.value());
        Pdc.set(item, PdcKeys.LOOT_ID, UUID.randomUUID().toString());
        Pdc.set(item, PdcKeys.MATCH_ID, matchId);
        Pdc.set(item, PdcKeys.LOOT_TIER, tier.id());

        var meta = item.getItemMeta();
        meta.displayName(tier.displayName(tier.value()));
        if (tier.glint()) meta.setEnchantmentGlintOverride(true);
        if (tier.customModelData() != null) {
            var component = meta.getCustomModelDataComponent();
            component.setFloats(List.of(tier.customModelData().floatValue()));
            meta.setCustomModelDataComponent(component);
        }
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isLoot(ItemStack item) {
        return Pdc.has(item, PdcKeys.LOOT_VALUE);
    }

    public static int getValue(ItemStack item) {
        return Pdc.get(item, PdcKeys.LOOT_VALUE, 0);
    }

    public static LootTierDefinition getTier(ItemStack item) {
        String id = Pdc.get(item, PdcKeys.LOOT_TIER);
        return id == null || tiers == null ? null : tiers.get(id);
    }

    public static void setValue(ItemStack item, int value) {
        Pdc.set(item, PdcKeys.LOOT_VALUE, value);
        LootTierDefinition tier = getTier(item);
        if (tier != null && !tier.relic()) {
            var meta = item.getItemMeta();
            meta.displayName(tier.displayName(value));
            item.setItemMeta(meta);
        }
    }

    public static boolean isRelic(ItemStack item) {
        LootTierDefinition tier = getTier(item);
        return tier != null && tier.relic();
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

    public static UUID getLastCarrier(ItemStack item) {
        String id = Pdc.get(item, PdcKeys.LAST_CARRIER);
        if (id == null) return null;
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public static void setLastCarrier(ItemStack item, UUID player) {
        Pdc.set(item, PdcKeys.LAST_CARRIER, player.toString());
    }

    public static void setLastTeam(ItemStack item, Team team) {
        Pdc.set(item, PdcKeys.LAST_TEAM, team.name());
    }
}
