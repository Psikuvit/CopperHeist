package me.psikuvit.copperHeist.role;

import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.config.ConfigFiles;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Loads roles.yml into {@link RoleDefinition}s and hands them out by id. Other plugins may {@link #register} more. */
public class RoleRegistry {

    private final CopperHeist plugin;
    private final Map<String, RoleDefinition> roles = new LinkedHashMap<>();
    private String defaultRoleId = "runner";
    private int defaultMaxPerTeam = 2;

    public RoleRegistry(CopperHeist plugin) {
        this.plugin = plugin;
    }

    public void load() {
        YamlConfiguration yaml = ConfigFiles.load(plugin, "roles.yml");
        roles.clear();
        defaultRoleId = yaml.getString("default-role", "runner").toLowerCase(Locale.ROOT);
        defaultMaxPerTeam = Math.max(1, yaml.getInt("max-per-team", 2));

        ConfigurationSection section = yaml.getConfigurationSection("roles");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection role = section.getConfigurationSection(id);
                if (role == null) continue;
                try {
                    register(parse(id.toLowerCase(Locale.ROOT), role));
                } catch (RuntimeException ex) {
                    plugin.getLogger().warning("Skipping role '" + id + "' in roles.yml: " + ex.getMessage());
                }
            }
        }
        if (roles.isEmpty()) plugin.getLogger().severe("roles.yml defines no usable roles - players will have no role.");
        if (!roles.containsKey(defaultRoleId) && !roles.isEmpty()) defaultRoleId = roles.keySet().iterator().next();
        plugin.getLogger().info("Loaded " + roles.size() + " role(s).");
    }

    public void register(RoleDefinition role) {
        roles.put(role.id(), role);
    }

    public List<RoleDefinition> all() {
        return new ArrayList<>(roles.values());
    }

    public RoleDefinition get(String id) {
        return id == null ? null : roles.get(id.toLowerCase(Locale.ROOT));
    }

    public RoleDefinition defaultRole() {
        return roles.get(defaultRoleId);
    }

    public int maxPerTeam(RoleDefinition role) {
        return role.maxPerTeam() > 0 ? role.maxPerTeam() : defaultMaxPerTeam;
    }

    // ---- parsing ----

    private RoleDefinition parse(String id, ConfigurationSection s) {
        NamedTextColor color = NamedTextColor.NAMES.value(s.getString("color", "white").toLowerCase(Locale.ROOT));
        Material icon = material(s.getString("icon", "PAPER"), Material.PAPER);

        Map<String, ArmorPiece> armor = new LinkedHashMap<>();
        ConfigurationSection armorSection = s.getConfigurationSection("armor");
        if (armorSection != null) {
            for (String slot : armorSection.getKeys(false)) {
                ConfigurationSection piece = armorSection.getConfigurationSection(slot);
                if (piece == null) continue;
                Material material = Material.matchMaterial(piece.getString("material", ""));
                if (material == null) throw new IllegalArgumentException("unknown armor material in " + slot);
                armor.put(slot.toLowerCase(Locale.ROOT), new ArmorPiece(material, "team".equalsIgnoreCase(piece.getString("dye"))));
            }
        }

        List<LoadoutItem> items = new ArrayList<>();
        for (Map<?, ?> raw : s.getMapList("items")) items.add(parseItem(raw));

        List<PotionEffect> effects = new ArrayList<>();
        for (Map<?, ?> raw : s.getMapList("effects")) {
            String type = String.valueOf(raw.get("type"));
            NamespacedKey key = NamespacedKey.fromString(type.toLowerCase(Locale.ROOT));
            PotionEffectType effectType = key == null ? null : Registry.POTION_EFFECT_TYPE.get(key);
            if (effectType == null) throw new IllegalArgumentException("unknown potion effect '" + type + "'");
            int amplifier = raw.get("amplifier") instanceof Number n ? n.intValue() : 0;
            int duration = raw.get("duration-seconds") instanceof Number n ? n.intValue() * 20 : PotionEffect.INFINITE_DURATION;
            effects.add(new PotionEffect(effectType, duration, amplifier, true, false));
        }

        Map<String, Double> passives = new LinkedHashMap<>();
        ConfigurationSection passiveSection = s.getConfigurationSection("passives");
        if (passiveSection != null) {
            for (String key : passiveSection.getKeys(false)) passives.put(key, passiveSection.getDouble(key));
        }

        AbilitySpec ability = null;
        ConfigurationSection abilitySection = s.getConfigurationSection("ability");
        if (abilitySection != null && abilitySection.getString("id") != null) {
            Map<String, Object> params = new LinkedHashMap<>();
            for (String key : abilitySection.getKeys(false)) params.put(key, abilitySection.get(key));
            ability = new AbilitySpec(abilitySection.getString("id").toLowerCase(Locale.ROOT), params);
        }

        return new RoleDefinition(id, s.getString("name", id), color == null ? NamedTextColor.WHITE : color, icon,
                s.getString("description", ""), s.getInt("max-per-team", 0), armor, items, effects, passives, ability);
    }

    private LoadoutItem parseItem(Map<?, ?> raw) {
        String shopItem = raw.get("shop-item") instanceof String str ? str.toLowerCase(Locale.ROOT) : null;
        Material material = null;
        if (shopItem == null) {
            material = Material.matchMaterial(String.valueOf(raw.get("material")));
            if (material == null) throw new IllegalArgumentException("unknown item material '" + raw.get("material") + "'");
        }
        int amount = raw.get("amount") instanceof Number n ? Math.max(1, n.intValue()) : 1;
        String name = raw.get("name") instanceof String str ? str : null;

        List<String> lore = new ArrayList<>();
        if (raw.get("lore") instanceof List<?> list) for (Object line : list) lore.add(String.valueOf(line));

        Map<String, Integer> enchants = new LinkedHashMap<>();
        if (raw.get("enchants") instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getValue() instanceof Number level) enchants.put(String.valueOf(entry.getKey()), level.intValue());
            }
        }
        boolean offhand = "offhand".equalsIgnoreCase(String.valueOf(raw.get("slot")));
        return new LoadoutItem(material, shopItem, amount, name, lore, enchants, offhand);
    }

    private Material material(String name, Material fallback) {
        Material material = Material.matchMaterial(name);
        return material == null ? fallback : material;
    }
}
