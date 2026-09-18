package me.psikuvit.copperHeist.ui;

import io.papermc.paper.world.WeatheringCopperState;
import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.arena.Arena;
import me.psikuvit.copperHeist.game.Game;
import me.psikuvit.copperHeist.game.GamePlayer;
import me.psikuvit.copperHeist.game.GameTeam;
import me.psikuvit.copperHeist.game.Team;
import me.psikuvit.copperHeist.golem.HeistGolem;
import me.psikuvit.copperHeist.task.HubSidebarTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Per-player sidebar rendering for any {@link ScoreboardContext}. Two
 * contexts exist today: the server hub board (any online player not
 * currently in a match) and a running match's own board - both configured
 * in scoreboard.yml, both rendered through the same render() method.
 */
public class SidebarService {

    private static final String ENTRY_CODES = "0123456789abcdef";
    private static final String OBJECTIVE_NAME = "ch_sidebar";

    private final CopperHeist plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final Map<UUID, Scoreboard> boards = new HashMap<>();
    private FileConfiguration config;
    private BukkitTask hubTask;

    public SidebarService(CopperHeist plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "scoreboard.yml");
        if (!file.exists()) plugin.saveResource("scoreboard.yml", false);
        config = YamlConfiguration.loadConfiguration(file);
    }

    /** Refreshes the hub board for every online player not currently in a match. */
    public void startHub() {
        if (hubTask != null) hubTask.cancel();
        hubTask = new HubSidebarTask(this).runTaskTimer(plugin, 20L, 20L);
    }

    public void stopHub() {
        if (hubTask != null) hubTask.cancel();
    }

    // ---- hub board ----

    public void showHub(Player player) {
        render(player, buildHubContext());
    }

    public void updateHub() {
        ScoreboardContext context = buildHubContext();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (plugin.getGameManager().getGame(player) != null) continue;
            render(player, context);
        }
    }

    private ScoreboardContext buildHubContext() {
        Component title = miniMessage.deserialize(config.getString("hub.title", config.getString("title", "<gold>COPPER HEIST")));

        int total = plugin.getArenaManager().all().size();
        int enabled = 0;
        for (Arena arena : plugin.getArenaManager().all()) {
            if (arena.isEnabled()) enabled++;
        }
        Map<String, String> placeholders = Map.of(
                "{arenas_enabled}", String.valueOf(enabled),
                "{arenas_total}", String.valueOf(total),
                "{players_online}", String.valueOf(Bukkit.getOnlinePlayers().size()));

        List<Component> lines = new ArrayList<>();
        for (String template : config.getStringList("hub.lines")) {
            lines.add(miniMessage.deserialize(applyAll(template, placeholders)));
        }
        if (lines.size() > 15) lines = lines.subList(0, 15);
        return ScoreboardContext.of(title, lines);
    }

    private String applyAll(String template, Map<String, String> placeholders) {
        String result = template;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result;
    }

    // ---- match board ----

    public void update(Game game) {
        boolean perViewer = game.isScoreHidden();
        ScoreboardContext shared = perViewer ? null : buildGameContext(game, null);
        for (Player player : game.onlinePlayers()) {
            ScoreboardContext context = shared;
            if (perViewer) {
                GamePlayer gp = game.getGamePlayer(player.getUniqueId());
                context = buildGameContext(game, gp == null ? null : gp.getTeam());
            }
            render(player, context);
            GamePlayer gp = game.getGamePlayer(player.getUniqueId());
            Team viewer = gp == null ? null : gp.getTeam();
            syncMatchTeams(player, game);
            updateTabList(player, game, viewer);
        }
        ScoreboardContext spectatorContext = buildGameContext(game, null);
        for (Player spectator : game.spectatorPlayers()) render(spectator, spectatorContext);
    }

    /** Team-colored names and [C]/[I] prefixes, with nametags hidden from the other team; the tab list sorts by these team names. */
    private void syncMatchTeams(Player viewer, Game game) {
        Scoreboard board = boards.get(viewer.getUniqueId());
        if (board == null) return;
        for (Team team : Team.values()) {
            String name = "ch_" + team.name().toLowerCase(Locale.ROOT);
            org.bukkit.scoreboard.Team sbTeam = board.getTeam(name);
            if (sbTeam == null) {
                sbTeam = board.registerNewTeam(name);
                sbTeam.color(team.color());
                sbTeam.prefix(Component.text(team.prefix(), team.color()));
                sbTeam.setAllowFriendlyFire(false);
                sbTeam.setOption(org.bukkit.scoreboard.Team.Option.NAME_TAG_VISIBILITY,
                        org.bukkit.scoreboard.Team.OptionStatus.FOR_OWN_TEAM);
            }
            java.util.Set<String> wanted = new java.util.HashSet<>();
            for (Player member : game.onlinePlayers()) {
                GamePlayer gp = game.getGamePlayer(member.getUniqueId());
                if (gp != null && gp.getTeam() == team) wanted.add(member.getName());
            }
            for (String entry : new ArrayList<>(sbTeam.getEntries())) {
                if (!wanted.contains(entry)) sbTeam.removeEntry(entry);
            }
            for (String entry : wanted) {
                if (!sbTeam.hasEntry(entry)) sbTeam.addEntry(entry);
            }
        }
    }

    private void updateTabList(Player player, Game game, Team viewer) {
        Component header = miniMessage.deserialize(substitute(
                config.getString("tab.header", "<gold><bold>COPPER HEIST <gray>- Arena: <white>{arena}"), game, viewer));
        Component footer = miniMessage.deserialize(substitute(
                config.getString("tab.footer", "<gold>Copper {copper_score} <gray>| Iron {iron_score} <gray>| <white>{time}"), game, viewer));
        player.sendPlayerListHeaderAndFooter(header, footer);

        GamePlayer gp = game.getGamePlayer(player.getUniqueId());
        if (gp != null) {
            player.playerListName(Component.text(player.getName(), gp.getTeam().color())
                    .append(Component.text(" [" + gp.getRole().displayName() + "]", net.kyori.adventure.text.format.NamedTextColor.GRAY)));
        }
    }

    /** Removes the match-only scoreboard teams and tab list decoration when a player leaves a match. */
    public void clearMatchDecor(Player player) {
        Scoreboard board = boards.get(player.getUniqueId());
        if (board != null) {
            for (org.bukkit.scoreboard.Team leftover : new ArrayList<>(board.getTeams())) {
                if (leftover.getName().startsWith("ch_")) leftover.unregister();
            }
        }
        player.playerListName(null);
        player.sendPlayerListHeaderAndFooter(Component.empty(), Component.empty());
    }

    private ScoreboardContext buildGameContext(Game game, Team viewer) {
        Component title = miniMessage.deserialize(config.getString("title", "<gold>COPPER HEIST"));
        return ScoreboardContext.of(title, buildLines(game, viewer));
    }

    private List<Component> buildLines(Game game, Team viewer) {
        String stateKey = game.getState().name().toLowerCase(Locale.ROOT);
        List<String> templates = config.getStringList("states." + stateKey);

        List<Component> lines = new ArrayList<>();
        for (String template : templates) {
            appendLine(lines, template, game, viewer);
        }
        for (String template : config.getStringList("footer")) {
            appendLine(lines, template, game, viewer);
        }
        if (lines.size() > 15) lines = lines.subList(0, 15);
        return lines;
    }

    private void appendLine(List<Component> lines, String template, Game game, Team viewer) {
        if (template.equals("<golems:copper>")) {
            appendGolemLines(lines, game, Team.COPPER);
        } else if (template.equals("<golems:iron>")) {
            appendGolemLines(lines, game, Team.IRON);
        } else {
            lines.add(miniMessage.deserialize(substitute(template, game, viewer)));
        }
    }

    private void appendGolemLines(List<Component> lines, Game game, Team team) {
        String template = config.getString("golem-line", " - {stage}{carrying}");
        for (HeistGolem golem : game.getTeam(team).getGolems()) {
            String line = template
                    .replace("{stage}", stageLabel(golem.getEntity().getWeatheringState()))
                    .replace("{carrying}", golem.isCarrying() ? " [carrying]" : "");
            lines.add(miniMessage.deserialize(line));
        }
    }

    private String substitute(String template, Game game, Team viewer) {
        var holder = game.getRelicManager().getHolder();
        String result = template
                .replace("{arena}", game.getArena().getName())
                .replace("{phase}", game.getState().name())
                .replace("{time}", formatTime(game.getSecondsRemaining()))
                .replace("{players}", String.valueOf(game.totalPlayers()))
                .replace("{min_players}", String.valueOf(plugin.getConfig().getInt("match.min-players", 6)))
                .replace("{max_players}", String.valueOf(plugin.getConfig().getInt("match.max-players", 16)))
                .replace("{relic_holder}", holder != null ? holder.getName() : "-")
                .replace("{relic_countdown}", formatTime(game.getRelicManager().getSecondsUntilSpawn()));
        boolean hidden = game.isScoreHidden();
        for (Team team : Team.values()) {
            GameTeam gameTeam = game.getTeam(team);
            String prefix = team.name().toLowerCase(Locale.ROOT);
            result = result
                    .replace("{" + prefix + "_score}", hidden && team != viewer ? "???" : String.valueOf(gameTeam.getScore()))
                    .replace("{" + prefix + "_steals}", String.valueOf(gameTeam.getSteals()))
                    .replace("{" + prefix + "_golem_count}", String.valueOf(gameTeam.getGolems().size()));
        }
        return result;
    }

    private String stageLabel(WeatheringCopperState state) {
        return switch (state) {
            case UNAFFECTED -> "Fresh";
            case EXPOSED -> "Exposed";
            case WEATHERED -> "Weathered";
            case OXIDIZED -> "Oxidized";
        };
    }

    private String formatTime(int seconds) {
        if (seconds < 0) seconds = 0;
        return String.format("%02d:%02d", seconds / 60, seconds % 60);
    }

    // ---- rendering ----

    private void render(Player player, ScoreboardContext context) {
        Scoreboard board = boards.computeIfAbsent(player.getUniqueId(), _ -> {
            Scoreboard sb = Bukkit.getScoreboardManager().getNewScoreboard();
            player.setScoreboard(sb);
            return sb;
        });

        Objective objective = board.getObjective(OBJECTIVE_NAME);
        if (objective == null) {
            objective = board.registerNewObjective(OBJECTIVE_NAME, Criteria.DUMMY, context.getTitle());
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        } else {
            objective.displayName(context.getTitle());
        }

        for (String entry : new ArrayList<>(board.getEntries())) {
            board.resetScores(entry);
        }
        for (org.bukkit.scoreboard.Team leftoverTeam : new ArrayList<>(board.getTeams())) {
            if (leftoverTeam.getName().startsWith("l")) leftoverTeam.unregister();
        }

        List<Component> lines = context.getLines();
        int size = lines.size();
        for (int i = 0; i < size; i++) {
            String entry = "§" + ENTRY_CODES.charAt(i % ENTRY_CODES.length());
            org.bukkit.scoreboard.Team scoreboardTeam = board.registerNewTeam("l" + i);
            scoreboardTeam.addEntry(entry);
            scoreboardTeam.prefix(lines.get(i));
            objective.getScore(entry).setScore(size - i);
        }
    }

    public void clear(Player player) {
        Scoreboard board = boards.remove(player.getUniqueId());
        if (board != null) {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }
}
