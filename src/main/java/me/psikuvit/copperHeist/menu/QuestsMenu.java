package me.psikuvit.copperHeist.menu;

import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.daily.DailyRewardService;
import me.psikuvit.copperHeist.profile.PlayerProfile;
import me.psikuvit.copperHeist.quest.QuestDefinition;
import me.psikuvit.copperHeist.quest.QuestPeriod;
import me.psikuvit.copperHeist.quest.QuestService;
import me.psikuvit.copperHeist.ui.MessageService;
import me.psikuvit.copperHeist.ui.Theme;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The goals screen: today's daily quests, this week's quest with their progress, the daily login reward (with your streak) and a button to the
 * achievements. Opened from the hub book, {@code /ch quests} and {@code /ch daily}.
 */
public class QuestsMenu extends Menu {

    private static final int[] DAILY_SLOTS = {11, 13, 15};
    private static final int WEEKLY_SLOT = 22;

    public QuestsMenu(CopperHeist plugin, Player viewer) {
        super(plugin, viewer);
    }

    @Override
    protected Component title() {
        return Theme.mini().deserialize(plugin.getMessageService().rawFor(viewer, "quests.menu.title"));
    }

    @Override
    protected int rows() {
        return 5;
    }

    @Override
    protected void draw() {
        border(Material.GRAY_STAINED_GLASS_PANE);
        QuestService quests = plugin.getQuests();
        PlayerProfile profile = quests.profile(viewer);
        var messages = plugin.getMessageService();

        if (quests.enabled() && profile != null) {
            List<QuestDefinition> daily = quests.current(viewer, QuestPeriod.DAILY);
            for (int i = 0; i < daily.size() && i < DAILY_SLOTS.length; i++) set(DAILY_SLOTS[i], questItem(quests, profile, daily.get(i)));
            List<QuestDefinition> weekly = quests.current(viewer, QuestPeriod.WEEKLY);
            if (!weekly.isEmpty()) set(WEEKLY_SLOT, questItem(quests, profile, weekly.get(0)));
        } else {
            set(22, Gui.item(Material.BARRIER, messages.rawFor(viewer, "quests.menu.none"), List.of()));
        }

        int bottom = size() - 9;
        drawDaily(bottom + 4, messages);
        if (plugin.getAchievements().enabled()) {
            set(bottom + 6, Gui.item(Material.NETHER_STAR, messages.rawFor(viewer, "achievements.menu.button-name"),
                    List.of(messages.rawFor(viewer, "achievements.menu.button-lore"))), click -> {
                Gui.click(click.player());
                openChild(new AchievementsMenu(plugin, viewer));
            });
        }
        backButton(bottom);
        closeButton();
    }

    private ItemStack questItem(QuestService quests, PlayerProfile profile, QuestDefinition quest) {
        var messages = plugin.getMessageService();
        boolean done = quests.isDone(profile, quest);
        int progress = quests.progress(profile, quest);
        long left = quest.period().millisLeft(System.currentTimeMillis()) / 60_000;

        List<String> lore = new ArrayList<>();
        lore.add("<muted>" + quest.description());
        lore.add("");
        lore.add(done ? messages.rawFor(viewer, "quests.menu.done")
                : Gui.bar((double) progress / quest.target(), 12) + " <accent>" + progress + "</accent><dim>/</dim><text>" + quest.target());
        lore.add(messages.rawFor(viewer, "quests.menu.reward", "xp", quest.xp(), "coins", quest.coins()));
        lore.add(messages.rawFor(viewer, "quests.menu.resets", "period", messages.rawFor(viewer, "quests.period." + quest.period().key()),
                "hours", left / 60, "minutes", left % 60));

        ItemStack item = Gui.item(done ? Material.LIME_DYE : quest.period() == QuestPeriod.WEEKLY ? Material.WRITABLE_BOOK : Material.PAPER,
                quest.name(), lore);
        if (done) Gui.glow(item);
        return item;
    }

    /** The daily login reward: a glowing chest to click when it is ready, otherwise the time until the next one. */
    private void drawDaily(int slot, MessageService messages) {
        DailyRewardService daily = plugin.getDaily();
        if (!daily.enabled()) return;
        int streak = Math.max(1, daily.streak(viewer));
        var reward = daily.rewardFor(streak);
        if (daily.canClaim(viewer)) {
            ItemStack item = Gui.item(Material.ENDER_CHEST, messages.rawFor(viewer, "daily.menu.ready"),
                    List.of(messages.rawFor(viewer, "daily.menu.streak", "streak", streak),
                            messages.rawFor(viewer, "daily.menu.reward", "xp", reward.xp(), "coins", reward.coins()),
                            "", messages.rawFor(viewer, "daily.menu.click")));
            Gui.glow(item);
            set(slot, item, click -> {
                if (daily.claim(viewer)) Gui.success(viewer);
                else Gui.deny(viewer);
                refresh();
            });
        } else {
            long minutes = QuestPeriod.DAILY.millisLeft(System.currentTimeMillis()) / 60_000;
            set(slot, Gui.item(Material.CHEST, messages.rawFor(viewer, "daily.menu.claimed"),
                    List.of(messages.rawFor(viewer, "daily.menu.streak", "streak", streak),
                            messages.rawFor(viewer, "daily.menu.next", "hours", minutes / 60, "minutes", minutes % 60))));
        }
    }
}
