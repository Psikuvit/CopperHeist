package me.psikuvit.copperHeist.achievement;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.ui.Theme;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.entity.Player;

import java.util.logging.Level;

/**
 * How an unlocked achievement is shown on screen. In {@code toast} mode (achievements.display in config.yml) it is a real toast in the
 * top-right corner with the achievement's own icon, title and description: Paper lets a plugin create an advancement on the fly, the
 * player is granted it (which is what makes the client pop the toast) and it is removed again a few seconds later, so nothing is left in
 * the advancements screen. If the toast can't be created (a server version whose advancement format differs) it falls back to a title, so
 * the player always sees something. In {@code title} mode it is always a title.
 */
public class AchievementDisplay {

    private static final String CRITERION = "done";
    private static final long CLEANUP_TICKS = 100L;

    private final CopperHeist plugin;
    private int counter;

    public AchievementDisplay(CopperHeist plugin) {
        this.plugin = plugin;
    }

    public void show(Player player, AchievementDefinition achievement) {
        boolean toast = "toast".equalsIgnoreCase(plugin.settings().getString("achievements.display", "toast")) && tryToast(player, achievement);
        if (toast) return;
        var messages = plugin.getMessageService();
        player.showTitle(Title.title(messages.get(player, "achievements.title"),
                messages.get(player, "achievements.subtitle", "name", achievement.name())));
    }

    @SuppressWarnings("deprecation") // Paper's advancement loading is "unsafe" API, but it is the only way to show a real toast
    private boolean tryToast(Player player, AchievementDefinition achievement) {
        NamespacedKey key = new NamespacedKey(plugin, "toast_" + (counter++));
        try {
            var serializer = GsonComponentSerializer.gson();
            String json = toastJson(achievement.icon().getKey().toString(),
                    serializer.serialize(Theme.mini().deserialize(achievement.name())),
                    serializer.serialize(Theme.mini().deserialize(achievement.description())), achievement.frame());
            Advancement advancement = Bukkit.getUnsafe().loadAdvancement(key, json);
            if (advancement == null) return false;
            player.getAdvancementProgress(advancement).awardCriteria(CRITERION);
            Bukkit.getScheduler().runTaskLater(plugin, () -> remove(player, advancement, key), CLEANUP_TICKS);
            return true;
        } catch (RuntimeException | LinkageError ex) {
            plugin.getLogger().log(Level.FINE, "Could not show the achievement toast - using a title instead", ex);
            Bukkit.getUnsafe().removeAdvancement(key);
            return false;
        }
    }

    @SuppressWarnings("deprecation")
    private void remove(Player player, Advancement advancement, NamespacedKey key) {
        if (player.isOnline()) player.getAdvancementProgress(advancement).revokeCriteria(CRITERION);
        Bukkit.getUnsafe().removeAdvancement(key);
    }

    /**
     * The advancement that produces a toast: one criterion that can never happen on its own (the plugin awards it), a display with the icon,
     * title and description, no chat announcement, and hidden from the advancements screen.
     *
     * @param iconKey         the icon item, e.g. {@code minecraft:copper_ingot}
     * @param titleJson       the title as a JSON text component
     * @param descriptionJson the description as a JSON text component
     * @param frame           task, goal or challenge
     */
    static String toastJson(String iconKey, String titleJson, String descriptionJson, String frame) {
        JsonObject icon = new JsonObject();
        icon.addProperty("id", iconKey);

        JsonObject display = new JsonObject();
        display.add("icon", icon);
        display.add("title", JsonParser.parseString(titleJson));
        display.add("description", JsonParser.parseString(descriptionJson));
        display.addProperty("frame", frame);
        display.addProperty("announce_to_chat", false);
        display.addProperty("show_toast", true);
        display.addProperty("hidden", true);

        JsonObject trigger = new JsonObject();
        trigger.addProperty("trigger", "minecraft:impossible");
        JsonObject criteria = new JsonObject();
        criteria.add(CRITERION, trigger);

        JsonObject root = new JsonObject();
        root.add("criteria", criteria);
        root.add("display", display);
        return root.toString();
    }
}
