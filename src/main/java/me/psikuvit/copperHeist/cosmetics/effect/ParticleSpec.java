package me.psikuvit.copperHeist.cosmetics.effect;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;

/**
 * The particle settings shared by the particle effects, read from a cosmetic's {@code params}:
 * <pre>
 *   particle: FLAME        any particle that needs no extra data, plus DUST (with color), ITEM and BLOCK (with material)
 *   count: 1               particles per spawn (1-50)
 *   spread: 0.0            random offset in blocks (0-2)
 *   speed: 0.0             particle speed (0-1)
 *   color: "#FF8800"       DUST only; "rainbow" cycles through the colours
 *   size: 1.0              DUST only (0.3-4)
 *   material: GOLD_NUGGET  ITEM and BLOCK only
 * </pre>
 * Purely visual: sending is skipped for viewers in another world or more than {@link #RANGE} blocks away.
 */
public record ParticleSpec(Particle particle, Object data, int count, double spread, double speed, boolean rainbow, float size) {

    public static final double RANGE = 48;

    /** Builds the spec or throws IllegalArgumentException with a message a server owner can act on. */
    public static ParticleSpec parse(Map<String, Object> params) {
        String name = String.valueOf(params.getOrDefault("particle", "")).trim();
        if (name.isEmpty()) throw new IllegalArgumentException("params.particle is required");
        Particle particle;
        try {
            particle = Particle.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("unknown particle '" + name + "'");
        }
        int count = (int) clamp(number(params, "count", 1), 1, 50);
        double spread = clamp(number(params, "spread", 0), 0, 2);
        double speed = clamp(number(params, "speed", 0), 0, 1);
        float size = (float) clamp(number(params, "size", 1), 0.3, 4);

        Class<?> type = particle.getDataType();
        boolean rainbow = false;
        Object data = null;
        if (type == Void.class) {
            data = null;
        } else if (type == Particle.DustOptions.class) {
            String color = String.valueOf(params.getOrDefault("color", "#FFFFFF")).trim();
            if (color.equalsIgnoreCase("rainbow")) {
                rainbow = true;
                data = new Particle.DustOptions(Color.WHITE, size);
            } else {
                data = new Particle.DustOptions(parseColor(color), size);
            }
        } else if (type == ItemStack.class || type == BlockData.class) {
            Material material = Material.matchMaterial(String.valueOf(params.getOrDefault("material", "")));
            if (material == null) throw new IllegalArgumentException("particle " + particle.name() + " needs params.material");
            data = material; // turned into the item or block data only when sent, so parsing needs no running server
        } else {
            throw new IllegalArgumentException("particle " + particle.name() + " isn't supported (it needs data this plugin can't supply)");
        }
        return new ParticleSpec(particle, data, count, spread, speed, rainbow, size);
    }

    /** Sends the particle at {@code at} to every viewer close enough to see it; {@code tick} moves a rainbow along. */
    public void send(Collection<Player> viewers, Location at, int tick) {
        Object dataNow = rainbow ? new Particle.DustOptions(rainbowColor(tick), size) : data;
        if (dataNow instanceof Material material) {
            dataNow = particle.getDataType() == ItemStack.class ? new ItemStack(material) : material.createBlockData();
        }
        double rangeSquared = RANGE * RANGE;
        for (Player viewer : viewers) {
            if (!viewer.isOnline() || viewer.getWorld() != at.getWorld() || viewer.getLocation().distanceSquared(at) > rangeSquared) continue;
            viewer.spawnParticle(particle, at, count, spread, spread, spread, speed, dataNow);
        }
    }

    /** A colour walking round the hue wheel, one full turn every 60 ticks. */
    static Color rainbowColor(int tick) {
        double hue = (tick % 60) / 60.0;
        double h = hue * 6;
        int sector = (int) Math.floor(h);
        double f = h - sector;
        int rise = (int) Math.round(255 * f);
        int fall = 255 - rise;
        return switch (sector % 6) {
            case 0 -> Color.fromRGB(255, rise, 0);
            case 1 -> Color.fromRGB(fall, 255, 0);
            case 2 -> Color.fromRGB(0, 255, rise);
            case 3 -> Color.fromRGB(0, fall, 255);
            case 4 -> Color.fromRGB(rise, 0, 255);
            default -> Color.fromRGB(255, 0, fall);
        };
    }

    static Color parseColor(String value) {
        String hex = value.startsWith("#") ? value.substring(1) : value;
        try {
            return Color.fromRGB(Integer.parseInt(hex, 16));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("color '" + value + "' isn't a #RRGGBB colour");
        }
    }

    static double number(Map<String, Object> params, String key, double fallback) {
        Object value = params.get(key);
        if (value instanceof Number number) return number.doubleValue();
        if (value == null) return fallback;
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("params." + key + " must be a number");
        }
    }

    static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
