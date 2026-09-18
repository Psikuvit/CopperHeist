package me.psikuvit.copperHeist.game;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.configuration.ConfigurationSection;

import java.lang.reflect.Field;

/**
 * The two sides. The enum itself is fixed, but every visible property - name,
 * chat color, tab/scoreboard prefix and leather armor dye - is read from the
 * {@code teams} section of config.yml via {@link #configure}.
 */
public enum Team {

    COPPER("Copper", NamedTextColor.GOLD, "[C] ", Color.ORANGE),
    IRON("Iron", NamedTextColor.GRAY, "[I] ", Color.SILVER);

    private String displayName;
    private NamedTextColor color;
    private String prefix;
    private Color armorColor;

    Team(String displayName, NamedTextColor color, String prefix, Color armorColor) {
        this.displayName = displayName;
        this.color = color;
        this.prefix = prefix;
        this.armorColor = armorColor;
    }

    /** Applies the {@code teams.<id>} config entries; anything missing or invalid keeps its current value. */
    public static void configure(ConfigurationSection teams) {
        if (teams == null) return;
        for (Team team : values()) {
            ConfigurationSection section = teams.getConfigurationSection(team.name().toLowerCase());
            if (section == null) continue;
            team.displayName = section.getString("name", team.displayName);
            team.prefix = section.getString("prefix", team.prefix);
            NamedTextColor parsed = NamedTextColor.NAMES.value(section.getString("color", "").toLowerCase());
            if (parsed != null) team.color = parsed;
            Color dye = parseColor(section.getString("armor-color"));
            if (dye != null) team.armorColor = dye;
        }
    }

    private static Color parseColor(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            if (raw.startsWith("#")) return Color.fromRGB(Integer.parseInt(raw.substring(1), 16));
            Field field = Color.class.getField(raw.toUpperCase());
            return field.get(null) instanceof Color color ? color : null;
        } catch (ReflectiveOperationException | IllegalArgumentException ex) {
            return null;
        }
    }

    public String displayName() {
        return displayName;
    }

    public NamedTextColor color() {
        return color;
    }

    public String prefix() {
        return prefix;
    }

    public Color armorColor() {
        return armorColor;
    }

    public Team opposite() {
        return this == COPPER ? IRON : COPPER;
    }
}
