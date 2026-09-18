package me.psikuvit.copperHeist.arena;

import org.bukkit.Location;

/** An axis-aligned box between two corners (inclusive of the whole block at each corner). */
public record Region(Location min, Location max) {

    public static Region of(Location a, Location b) {
        Location low = new Location(a.getWorld(), Math.min(a.getBlockX(), b.getBlockX()),
                Math.min(a.getBlockY(), b.getBlockY()), Math.min(a.getBlockZ(), b.getBlockZ()));
        Location high = new Location(a.getWorld(), Math.max(a.getBlockX(), b.getBlockX()),
                Math.max(a.getBlockY(), b.getBlockY()), Math.max(a.getBlockZ(), b.getBlockZ()));
        return new Region(low, high);
    }

    public boolean contains(Location loc) {
        if (loc.getWorld() == null || !loc.getWorld().equals(min.getWorld())) return false;
        return loc.getBlockX() >= min.getBlockX() && loc.getBlockX() <= max.getBlockX()
                && loc.getBlockY() >= min.getBlockY() && loc.getBlockY() <= max.getBlockY()
                && loc.getBlockZ() >= min.getBlockZ() && loc.getBlockZ() <= max.getBlockZ();
    }
}
