package me.psikuvit.copperHeist.cosmetics;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.Collection;

/**
 * What an effect is played with. Which fields are set depends on the moment: a projectile trail has the projectile as {@code entity}
 * and the shooter as {@code owner}; a kill effect has the victim's spot as {@code location}; a victory effect has the winners as
 * {@code audience}. {@code viewers} are the players who should see it (everyone nearby, minus anyone who turned cosmetics off).
 */
public record EffectContext(CosmeticDefinition cosmetic, Player owner, Location location, Entity entity, Collection<Player> viewers) {

    public static EffectContext at(CosmeticDefinition cosmetic, Player owner, Location location, Collection<Player> viewers) {
        return new EffectContext(cosmetic, owner, location, null, viewers);
    }

    public static EffectContext on(CosmeticDefinition cosmetic, Player owner, Entity entity, Collection<Player> viewers) {
        return new EffectContext(cosmetic, owner, entity.getLocation(), entity, viewers);
    }
}
