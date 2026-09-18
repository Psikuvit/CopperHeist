package me.psikuvit.copperHeist.ui;

import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.config.ConfigFiles;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

public class MessageService {

    private final CopperHeist plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private FileConfiguration messages;

    public MessageService(CopperHeist plugin) {
        this.plugin = plugin;
    }

    public void load() {
        messages = ConfigFiles.load(plugin, "messages.yml");
    }

    /** placeholders come in {key, value, key, value, ...} pairs, substituted as {key} in the template. */
    public Component get(String key, Object... placeholders) {
        String template = messages.getString(key, key);
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            template = template.replace("{" + placeholders[i] + "}", String.valueOf(placeholders[i + 1]));
        }
        return miniMessage.deserialize(template);
    }

    public String getPrefix() {
        return messages.getString("prefix", "<gray>[<gold>Copper Heist</gold>]</gray> ");
    }

    public Component getWithPrefix(String key, Object... placeholders) {
        return miniMessage.deserialize(getPrefix()).append(get(key, placeholders));
    }
}
