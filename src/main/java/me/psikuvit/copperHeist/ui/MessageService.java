package me.psikuvit.copperHeist.ui;

import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.config.ConfigFiles;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * All player-facing text. Every language is a file in plugins/CopperHeist/lang/ (en.yml ships; drop in es.yml,
 * de.yml... to add more). A viewer sees their own client language when language.per-player is on and a file
 * for it exists, else language.default; any key a translation lacks falls back to the bundled English.
 * Placeholders are {name} tokens filled from {name, value, ...} pairs.
 */
public class MessageService {

    private final CopperHeist plugin;
    private final MiniMessage miniMessage = Theme.mini();
    private final Map<String, YamlConfiguration> languages = new HashMap<>();
    private String defaultLanguage = "en";
    private boolean perPlayer = true;

    public MessageService(CopperHeist plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File dir = new File(plugin.getDataFolder(), "lang");
        dir.mkdirs();
        migrateLegacyMessages(dir);
        File english = new File(dir, "en.yml");
        if (!english.exists()) plugin.saveResource("lang/en.yml", false);

        YamlConfiguration bundled = ConfigFiles.bundled(plugin, "lang/en.yml");
        if (bundled != null) {
            // Only en.yml is topped up: other languages are translations and fall back to English per key.
            YamlConfiguration current = ConfigFiles.loadFile(plugin, english, null);
            if (!current.getKeys(false).isEmpty()) ConfigFiles.addMissing(plugin, english, current, bundled);
        }
        languages.clear();
        File[] files = dir.listFiles((d, name) -> name.endsWith(".yml"));
        if (files != null) {
            for (File file : files) {
                String code = file.getName().substring(0, file.getName().length() - 4).toLowerCase(Locale.ROOT);
                languages.put(code, ConfigFiles.loadFile(plugin, file, bundled));
            }
        }
        defaultLanguage = plugin.settings().getString("language.default", "en").toLowerCase(Locale.ROOT);
        if (!languages.containsKey(defaultLanguage)) {
            plugin.getLogger().warning("language.default '" + defaultLanguage + "' has no file in lang/ - using en.");
            defaultLanguage = "en";
        }
        perPlayer = plugin.settings().getBoolean("language.per-player", true);
        plugin.getLogger().info("Loaded languages: " + String.join(", ", languages.keySet()) + " (default " + defaultLanguage + ").");
    }

    /** Older versions kept everything in plugins/CopperHeist/messages.yml - carry any edits over to lang/en.yml once. */
    private void migrateLegacyMessages(File dir) {
        File legacy = new File(plugin.getDataFolder(), "messages.yml");
        File english = new File(dir, "en.yml");
        if (!legacy.exists() || english.exists()) return;
        try {
            Files.move(legacy.toPath(), english.toPath(), StandardCopyOption.REPLACE_EXISTING);
            plugin.getLogger().info("Moved messages.yml to lang/en.yml.");
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not move messages.yml to lang/en.yml: " + ex.getMessage());
        }
    }

    public String getPrefix() {
        return rawFor(null, "prefix");
    }

    // ---- lookup ----

    private String languageOf(CommandSender viewer) {
        if (perPlayer && viewer instanceof Player player) {
            String code = player.locale().getLanguage().toLowerCase(Locale.ROOT);
            if (languages.containsKey(code)) return code;
        }
        return defaultLanguage;
    }

    /** The template for {@code key} in the viewer's language with placeholders filled in (no MiniMessage parsing yet). */
    public String rawFor(CommandSender viewer, String key, Object... placeholders) {
        YamlConfiguration yaml = languages.get(languageOf(viewer));
        String template = yaml == null ? null : yaml.getString(key);
        if (template == null && yaml != languages.get(defaultLanguage) && languages.get(defaultLanguage) != null) {
            template = languages.get(defaultLanguage).getString(key);
        }
        if (template == null) template = key;
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            template = template.replace("{" + placeholders[i] + "}", String.valueOf(placeholders[i + 1]));
        }
        return template;
    }

    /** Like {@link #rawFor} but null when no language defines the key - for optional per-language overrides of YAML-configured text. */
    public String rawOrNull(CommandSender viewer, String key) {
        YamlConfiguration yaml = languages.get(languageOf(viewer));
        String value = yaml == null ? null : yaml.getString(key);
        if (value == null && languages.get(defaultLanguage) != null) value = languages.get(defaultLanguage).getString(key);
        return value;
    }

    public List<String> listOrNull(CommandSender viewer, String key) {
        YamlConfiguration yaml = languages.get(languageOf(viewer));
        if (yaml != null && yaml.isList(key)) return yaml.getStringList(key);
        YamlConfiguration fallback = languages.get(defaultLanguage);
        return fallback != null && fallback.isList(key) ? fallback.getStringList(key) : null;
    }

    // ---- components ----

    /** In the server's default language - use the viewer overloads whenever a specific player will read it. */
    public Component get(String key, Object... placeholders) {
        return miniMessage.deserialize(rawFor(null, key, placeholders));
    }

    public Component get(CommandSender viewer, String key, Object... placeholders) {
        return miniMessage.deserialize(rawFor(viewer, key, placeholders));
    }

    public Component get(CommandSender viewer, Text text) {
        return get(viewer, text.key(), text.args());
    }

    public Component getWithPrefix(CommandSender viewer, String key, Object... placeholders) {
        return miniMessage.deserialize(rawFor(viewer, "prefix")).append(get(viewer, key, placeholders));
    }

    /** Success feedback: a green check mark, then the message. */
    public Component ok(CommandSender viewer, String key, Object... placeholders) {
        return miniMessage.deserialize("<ok><check></ok> <text>" + rawFor(viewer, key, placeholders));
    }

    /** Failure feedback: a red cross, then the message in red. */
    public Component err(CommandSender viewer, String key, Object... placeholders) {
        return miniMessage.deserialize("<bad><cross> " + rawFor(viewer, key, placeholders));
    }

    public Component err(CommandSender viewer, Text text) {
        return err(viewer, text.key(), text.args());
    }
}
