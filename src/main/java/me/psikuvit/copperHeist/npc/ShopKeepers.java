package me.psikuvit.copperHeist.npc;

import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.game.Team;
import me.psikuvit.copperHeist.ui.Theme;
import org.bukkit.Location;

import java.util.Map;

/**
 * Builds a team's shop keeper. A shop point can name a look from shop-looks.yml (a villager profession, a Mannequin skin, an armor stand
 * with a custom head and armor ...); without one the default shop look is used, and without that the plain npc.type from config.yml. The
 * team's equipped shop-skin cosmetic (if any) is worn instead of the look's own Mannequin skin. Keepers belong to a match, so the game
 * tracks and removes them; hub navigators are a separate thing (see NavigatorService).
 */
public class ShopKeepers {

    private final CopperHeist plugin;

    public ShopKeepers(CopperHeist plugin) {
        this.plugin = plugin;
    }

    /** Spawns one keeper for {@code team} at {@code location}; null when the NPC type places nothing. */
    public NpcHandle spawn(Location location, Team team, String lookId, Map<String, Object> skin) {
        var settings = plugin.settings();
        NpcLook look = plugin.getShopLooks().choose(lookId);
        String type = look != null && look.type() != null ? look.type() : settings.getString("npc.type", "villager");
        String format = look != null && look.name() != null ? look.name() : settings.getString("npc.name-format", "{team} Shop");
        var name = Theme.mini().deserialize(format.replace("{team}", team.displayName())).colorIfAbsent(team.color());
        return NpcSpawner.spawn(plugin, type, new NpcSpec(location, name, team, settings, skin, look), "a shop keeper");
    }
}
