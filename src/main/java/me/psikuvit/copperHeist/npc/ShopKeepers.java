package me.psikuvit.copperHeist.npc;

import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.cosmetics.CosmeticDefinition;
import me.psikuvit.copperHeist.cosmetics.effect.ShopKeeperEffect;
import me.psikuvit.copperHeist.game.Team;
import me.psikuvit.copperHeist.ui.Theme;
import org.bukkit.Location;

/**
 * Builds a team's shop keeper. How it looks is entirely the cosmetic system's business: the team shows the rarest npc cosmetic (a
 * shop-keeper effect) equipped by any teammate, and without one the keeper is built from the plain npc.type and npc.* settings in
 * config.yml. Keepers belong to a match, so the game tracks and removes them; hub navigators are a separate thing (see NavigatorService).
 */
public class ShopKeepers {

    private final CopperHeist plugin;

    public ShopKeepers(CopperHeist plugin) {
        this.plugin = plugin;
    }

    /** Spawns one keeper for {@code team} at {@code location}; {@code equipped} may be null. Null result when the NPC type places nothing. */
    public NpcHandle spawn(Location location, Team team, CosmeticDefinition equipped) {
        var settings = plugin.settings();
        NpcLook look = equipped != null && ShopKeeperEffect.ID.equals(equipped.effect()) ? ShopKeeperEffect.lookOf(equipped) : null;
        String type = look != null && look.type() != null ? look.type() : settings.getString("npc.type", "villager");
        String format = look != null && look.name() != null ? look.name() : settings.getString("npc.name-format", "{team} Shop");
        var name = Theme.mini().deserialize(format.replace("{team}", team.displayName())).colorIfAbsent(team.color());
        return NpcSpawner.spawn(plugin, type, new NpcSpec(location, name, team, settings, look), "a shop keeper");
    }
}
