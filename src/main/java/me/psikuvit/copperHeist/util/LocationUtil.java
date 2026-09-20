package me.psikuvit.copperHeist.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;

public final class LocationUtil {

    private LocationUtil() {
    }

    /** The middle of the block column: x.5 / z.5. Y, yaw and pitch are kept; the input isn't modified. */
    public static Location center(Location loc) {
        if (loc == null) return null;
        Location out = loc.clone();
        out.setX(loc.getBlockX() + 0.5);
        out.setZ(loc.getBlockZ() + 0.5);
        return out;
    }

    public static List<Double> serialize(Location loc) {
        List<Double> list = new ArrayList<>();
        list.add(loc.getX());
        list.add(loc.getY());
        list.add(loc.getZ());
        list.add((double) loc.getYaw());
        list.add((double) loc.getPitch());
        return list;
    }

    public static Location deserialize(String worldName, List<?> list) {
        if (list == null || list.size() < 3 || worldName == null) return null;
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        double x = ((Number) list.get(0)).doubleValue();
        double y = ((Number) list.get(1)).doubleValue();
        double z = ((Number) list.get(2)).doubleValue();
        float yaw = list.size() > 3 ? ((Number) list.get(3)).floatValue() : 0f;
        float pitch = list.size() > 4 ? ((Number) list.get(4)).floatValue() : 0f;
        return new Location(world, x, y, z, yaw, pitch);
    }

    public static List<List<Double>> serializeList(List<Location> locations) {
        List<List<Double>> out = new ArrayList<>();
        for (Location loc : locations) out.add(serialize(loc));
        return out;
    }

    public static List<Location> deserializeList(String worldName, List<?> raw) {
        List<Location> out = new ArrayList<>();
        if (raw == null) return out;
        for (Object o : raw) {
            if (o instanceof List<?> l) {
                Location loc = deserialize(worldName, l);
                if (loc != null) out.add(loc);
            }
        }
        return out;
    }

    /** Like {@link #deserialize} but snapped to the block centre (x.5 / z.5), for points things spawn at. */
    public static Location deserializeCentered(String worldName, List<?> list) {
        return center(deserialize(worldName, list));
    }

    public static List<Location> deserializeCenteredList(String worldName, List<?> raw) {
        List<Location> out = new ArrayList<>();
        for (Location loc : deserializeList(worldName, raw)) out.add(center(loc));
        return out;
    }
}
