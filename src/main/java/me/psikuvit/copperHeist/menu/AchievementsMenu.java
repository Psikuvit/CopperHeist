package me.psikuvit.copperHeist.menu;

import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.achievement.AchievementDefinition;
import me.psikuvit.copperHeist.achievement.AchievementService;
import me.psikuvit.copperHeist.profile.PlayerProfile;
import me.psikuvit.copperHeist.ui.Theme;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Every achievement over pages: unlocked ones glow with their icon, the rest show a progress bar, and secret ones stay hidden until earned. */
public class AchievementsMenu extends PagedMenu<AchievementDefinition> {

    public AchievementsMenu(CopperHeist plugin, Player viewer) {
        super(plugin, viewer);
    }

    @Override
    protected Component title() {
        PlayerProfile profile = plugin.getAchievements().profile(viewer);
        int done = profile == null ? 0 : plugin.getAchievements().unlockedCount(profile);
        return Theme.mini().deserialize(plugin.getMessageService().rawFor(viewer, "achievements.menu.title",
                "done", done, "total", plugin.getAchievements().registry().all().size()));
    }

    @Override
    protected boolean isValid() {
        return plugin.getAchievements().enabled();
    }

    @Override
    protected List<AchievementDefinition> items() {
        return plugin.getAchievements().registry().all();
    }

    @Override
    protected ItemStack icon(AchievementDefinition achievement) {
        AchievementService service = plugin.getAchievements();
        var messages = plugin.getMessageService();
        PlayerProfile profile = service.profile(viewer);
        boolean unlocked = profile != null && service.isUnlocked(profile, achievement);

        if (!unlocked && achievement.secret()) {
            return Gui.item(Material.GRAY_DYE, messages.rawFor(viewer, "achievements.menu.secret-name"),
                    List.of(messages.rawFor(viewer, "achievements.menu.secret-lore")));
        }
        List<String> lore = new ArrayList<>();
        lore.add("<muted>" + achievement.description());
        lore.add("");
        if (unlocked) {
            lore.add(messages.rawFor(viewer, "achievements.menu.unlocked"));
        } else {
            long value = Math.min(achievement.target(), service.value(viewer, achievement));
            lore.add(Gui.bar((double) value / achievement.target(), 12) + " <accent>" + value + "</accent><dim>/</dim><text>" + achievement.target());
        }
        lore.add(messages.rawFor(viewer, "achievements.menu.reward", "xp", achievement.xp(), "coins", achievement.coins()));

        ItemStack item = Gui.item(unlocked ? achievement.icon() : Material.GRAY_STAINED_GLASS_PANE, achievement.name(), lore);
        if (unlocked) Gui.glow(item);
        return item;
    }

    @Override
    protected void onSelect(AchievementDefinition achievement, Click click) {
        Gui.click(click.player());
    }
}
