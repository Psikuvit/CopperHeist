package me.psikuvit.copperHeist.menu;

import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.achievement.AchievementService;
import me.psikuvit.copperHeist.cosmetics.CosmeticCategory;
import me.psikuvit.copperHeist.cosmetics.CosmeticDefinition;
import me.psikuvit.copperHeist.cosmetics.CosmeticService;
import me.psikuvit.copperHeist.daily.DailyRewardService;
import me.psikuvit.copperHeist.game.Game;
import me.psikuvit.copperHeist.game.GamePlayer;
import me.psikuvit.copperHeist.game.Team;
import me.psikuvit.copperHeist.party.PartyInfo;
import me.psikuvit.copperHeist.profile.PlayerProfile;
import me.psikuvit.copperHeist.progress.ProgressService;
import me.psikuvit.copperHeist.quest.QuestPeriod;
import me.psikuvit.copperHeist.quest.QuestService;
import me.psikuvit.copperHeist.stats.PlayerStats;
import me.psikuvit.copperHeist.stats.Stat;
import me.psikuvit.copperHeist.stats.StatsService;
import me.psikuvit.copperHeist.ui.Theme;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A player's own profile at a glance: level and coins, their current match (if any), their built-in party, key lifetime stats, and
 * shortcuts into the quests, achievements, daily reward and cosmetics menus. Opened from the hub's profile item and {@code /ch profile}.
 * Everything here is read-only - it only opens the menus that let you act.
 */
public class ProfileMenu extends Menu {

    /** null (not another player's profile - yet); reserved for a future /ch profile <player>. */
    private final OfflinePlayer subject;

    public ProfileMenu(CopperHeist plugin, Player viewer) {
        this(plugin, viewer, viewer);
    }

    public ProfileMenu(CopperHeist plugin, Player viewer, OfflinePlayer subject) {
        super(plugin, viewer);
        this.subject = subject;
    }

    @Override
    protected Component title() {
        return Theme.mini().deserialize(plugin.getMessageService().rawFor(viewer, "profile.menu.title", "player", subject.getName()));
    }

    @Override
    protected int rows() {
        return 5;
    }

    @Override
    protected void draw() {
        border(Material.GRAY_STAINED_GLASS_PANE);

        set(4, headItem());
        drawProgress();
        drawMatch();
        drawParty();
        drawShortcuts();
        drawStats();

        backButton(size() - 9);
        closeButton();
    }

    private ItemStack headItem() {
        var messages = plugin.getMessageService();
        StatsService stats = plugin.getStats();
        List<String> lore = new ArrayList<>();
        if (stats == null) {
            lore.add(messages.rawFor(viewer, "stats.disabled"));
        } else if (!stats.isLoaded(subject.getUniqueId()) && subject.equals(viewer)) {
            lore.add(messages.rawFor(viewer, "stats.loading"));
        }
        ItemStack head = Gui.item(Material.PLAYER_HEAD, "<primary><bold>" + subject.getName(), lore);
        if (head.getItemMeta() instanceof SkullMeta meta) {
            meta.setOwningPlayer(subject);
            head.setItemMeta(meta);
        }
        return head;
    }

    /** Level, rank, XP bar and coins - the same numbers as the scoreboard and /ch level. */
    private void drawProgress() {
        var messages = plugin.getMessageService();
        ProgressService progress = plugin.getProgress();
        if (progress == null || !progress.enabled()) {
            set(11, Gui.item(Material.EXPERIENCE_BOTTLE, messages.rawFor(viewer, "profile.menu.progress-off-name"),
                    List.of(messages.rawFor(viewer, "profile.menu.progress-off-lore"))));
            return;
        }
        long xp = progress.xp(subject.getUniqueId(), subject.getName());
        int level = progress.curve().levelFor(xp);
        long coins = progress.coins(subject.getUniqueId(), subject.getName());

        List<String> lore = new ArrayList<>();
        lore.add(messages.rawFor(viewer, "profile.menu.level-line", "rank", progress.rank(level)));
        lore.add(progress.bar(xp));
        lore.add(messages.rawFor(viewer, "profile.menu.xp-into", "into", progress.curve().xpIntoLevel(xp),
                "need", level >= progress.curve().maxLevel() ? 0 : progress.curve().xpForNext(level)));
        lore.add("");
        lore.add(messages.rawFor(viewer, "profile.menu.coins-line", "coins", coins));
        set(11, Gui.item(Material.EXPERIENCE_BOTTLE, messages.rawFor(viewer, "profile.menu.level-name", "level", level), lore));
    }

    /** The match the subject is playing right now, if any and if they are online. */
    private void drawMatch() {
        var messages = plugin.getMessageService();
        Player online = subject instanceof Player p ? p : Bukkit.getPlayer(subject.getUniqueId());
        Game game = online == null ? null : plugin.getGameManager().getGame(online);
        GamePlayer gp = game == null ? null : game.getGamePlayer(online.getUniqueId());

        if (gp == null) {
            set(13, Gui.item(Material.COMPASS, messages.rawFor(viewer, "profile.menu.no-match-name"),
                    List.of(messages.rawFor(viewer, "profile.menu.no-match-lore"))));
            return;
        }
        Team team = gp.getTeam();
        List<String> lore = new ArrayList<>();
        lore.add(messages.rawFor(viewer, "profile.menu.match-arena", "arena", game.getArena().getName()));
        lore.add(messages.rawFor(viewer, "profile.menu.match-team", "team", team.displayName()));
        if (gp.getRole() != null) lore.add(messages.rawFor(viewer, "profile.menu.match-role", "role", gp.getRole().displayName()));
        set(13, Gui.item(Material.SHIELD, messages.rawFor(viewer, "profile.menu.match-name"), lore));
    }

    /** The subject's built-in party (a Parties-managed one isn't shown here - it has its own UI). */
    private void drawParty() {
        var messages = plugin.getMessageService();
        var parties = plugin.getParties();
        var provider = parties.provider();
        if (!provider.builtIn()) return; // another plugin's party UI is the right place for this

        List<String> lore = new ArrayList<>();
        var party = parties.partyOf(subject.getUniqueId());
        if (party.isEmpty()) {
            lore.add(messages.rawFor(viewer, "profile.menu.no-party-lore"));
        } else {
            PartyInfo info = party.get();
            for (UUID id : info.members()) {
                String name = Bukkit.getOfflinePlayer(id).getName();
                if (name == null) continue;
                lore.add(messages.rawFor(viewer, info.isLeader(id) ? "party.list-leader" : "party.list-member", "player", name,
                        "status", messages.rawFor(viewer, Bukkit.getPlayer(id) != null ? "party.online" : "party.offline")));
            }
        }
        set(15, Gui.item(Material.WRITABLE_BOOK, messages.rawFor(viewer, "profile.menu.party-name"), lore), click -> {
            if (subject.equals(viewer) && plugin.getParties().builtIn() != null) {
                Gui.click(click.player());
                openChild(new PartyMenu(plugin, viewer));
            } else {
                Gui.deny(click.player());
            }
        });
    }

    /** Buttons into the other progression menus, each showing a one-line summary. */
    private void drawShortcuts() {
        var messages = plugin.getMessageService();
        boolean self = subject.equals(viewer);

        DailyRewardService daily = plugin.getDaily();
        if (daily.enabled() && self) {
            int streak = daily.streak(viewer);
            set(20, Gui.item(Material.SUNFLOWER, messages.rawFor(viewer, "profile.menu.daily-name"),
                    List.of(messages.rawFor(viewer, "profile.menu.daily-streak", "streak", streak),
                            messages.rawFor(viewer, daily.canClaim(viewer) ? "profile.menu.daily-ready" : "profile.menu.daily-claimed"))),
                    click -> {
                        Gui.click(click.player());
                        daily.claim(viewer);
                        refresh();
                    });
        }

        QuestService quests = plugin.getQuests();
        if (quests.enabled() && self) {
            PlayerProfile profile = quests.profile(viewer);
            int done = 0, total = 0;
            if (profile != null) {
                for (QuestPeriod period : QuestPeriod.values()) {
                    for (var quest : quests.current(viewer, period)) {
                        total++;
                        if (quests.isDone(profile, quest)) done++;
                    }
                }
            }
            set(22, Gui.item(Material.MAP, messages.rawFor(viewer, "profile.menu.quests-name"),
                    List.of(messages.rawFor(viewer, "profile.menu.quests-lore", "done", done, "total", total))), click -> {
                Gui.click(click.player());
                openChild(new QuestsMenu(plugin, viewer));
            });
        }

        AchievementService achievements = plugin.getAchievements();
        if (achievements.enabled() && self) {
            PlayerProfile profile = achievements.profile(viewer);
            int done = profile == null ? 0 : achievements.unlockedCount(profile);
            set(24, Gui.item(Material.GOLDEN_APPLE, messages.rawFor(viewer, "profile.menu.achievements-name"),
                    List.of(messages.rawFor(viewer, "profile.menu.achievements-lore", "done", done,
                            "total", achievements.registry().all().size()))), click -> {
                Gui.click(click.player());
                openChild(new AchievementsMenu(plugin, viewer));
            });
        }

        CosmeticService cosmetics = plugin.getCosmetics();
        if (cosmetics.enabled()) {
            int equipped = 0;
            for (CosmeticCategory category : CosmeticCategory.values()) {
                if (cosmetics.equipped(viewer, category) != null) equipped++;
            }
            List<String> lore = new ArrayList<>();
            lore.add(messages.rawFor(viewer, "profile.menu.cosmetics-lore", "equipped", equipped));
            CosmeticDefinition title = cosmetics.equipped(viewer, CosmeticCategory.TITLE);
            if (title != null) lore.add(messages.rawFor(viewer, "profile.menu.cosmetics-title", "title", title.name()));
            set(16, Gui.item(Material.NETHER_STAR, messages.rawFor(viewer, "profile.menu.cosmetics-name"), lore), click -> {
                if (self) {
                    Gui.click(click.player());
                    openChild(new CosmeticsMenu(plugin, viewer));
                } else {
                    Gui.deny(click.player());
                }
            });
        }
    }

    /** Lifetime stats, grouped into four tiles so the menu reads at a glance instead of as one long list. */
    private void drawStats() {
        var messages = plugin.getMessageService();
        StatsService stats = plugin.getStats();
        if (stats == null) return;
        PlayerStats snapshot = stats.isLoaded(subject.getUniqueId()) || !(subject instanceof Player)
                ? stats.snapshot(subject.getUniqueId(), subject.getName()) : null;
        if (snapshot == null) return;

        set(29, statTile(Material.IRON_SWORD, "profile.menu.combat-name", snapshot, Stat.KILLS, Stat.DEATHS, Stat.MVPS));
        set(31, statTile(Material.COPPER_INGOT, "profile.menu.loot-name", snapshot, Stat.LOOT_DELIVERED, Stat.LOOT_STOLEN,
                Stat.STEALS, Stat.RELICS_DELIVERED));
        set(33, statTile(Material.COPPER_AXE, "profile.menu.golem-name", snapshot, Stat.GOLEMS_SCRAPED, Stat.DRILLS_COMPLETED,
                Stat.DRILLS_DESTROYED));

        long played = snapshot.get(Stat.GAMES_PLAYED);
        long wins = snapshot.get(Stat.WINS);
        List<String> lore = new ArrayList<>();
        lore.add(messages.rawFor(viewer, "stats.line", "stat", messages.rawFor(viewer, Stat.GAMES_PLAYED.langKey()), "value", played));
        lore.add(messages.rawFor(viewer, "stats.line", "stat", messages.rawFor(viewer, Stat.WINS.langKey()), "value", wins));
        lore.add(messages.rawFor(viewer, "stats.line", "stat", messages.rawFor(viewer, Stat.LOSSES.langKey()), "value", snapshot.get(Stat.LOSSES)));
        lore.add(messages.rawFor(viewer, "profile.menu.win-rate", "rate", played == 0 ? 0 : Math.round(100.0 * wins / played)));
        set(35, Gui.item(Material.TOTEM_OF_UNDYING, messages.rawFor(viewer, "profile.menu.games-name"), lore));
    }

    private ItemStack statTile(Material icon, String nameKey, PlayerStats snapshot, Stat... shown) {
        var messages = plugin.getMessageService();
        List<String> lore = new ArrayList<>();
        for (Stat stat : shown) {
            lore.add(messages.rawFor(viewer, "stats.line", "stat", messages.rawFor(viewer, stat.langKey()), "value", snapshot.get(stat)));
        }
        return Gui.item(icon, messages.rawFor(viewer, nameKey), lore);
    }
}
