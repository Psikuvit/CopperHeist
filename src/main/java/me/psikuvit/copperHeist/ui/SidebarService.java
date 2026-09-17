package me.psikuvit.copperHeist.ui;

import io.papermc.paper.world.WeatheringCopperState;
import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.game.Game;
import me.psikuvit.copperHeist.game.GameTeam;
import me.psikuvit.copperHeist.game.Team;
import me.psikuvit.copperHeist.golem.HeistGolem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
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
 * Per-player sidebar, one layout per {@link me.psikuvit.copperHeist.game.GameState}
 * loaded from scoreboard.yml (so the lobby board and every in-game phase are
 * configurable without touching code). Rendering uses the classic team-prefix
 * trick: each visible line is a team prefix, and the scoreboard "entry" itself
 * is an invisible color code so duplicate-looking lines don't collide.
 */
public class SidebarService {

    private static final String ENTRY_CODES = "0123456789abcdef";
    private static final String OBJECTIVE_NAME = "ch_sidebar";

    private final CopperHeist plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final Map<UUID, Scoreboard> boards = new HashMap<>();
    private FileConfiguration config;

    public SidebarService(CopperHeist plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "scoreboard.yml");
        if (!file.exists()) plugin.saveResource("scoreboard.yml", false);
        config = YamlConfiguration.loadConfiguration(file);
    }

    public void update(Game game) {
        List<Component> lines = buildLines(game);
        Component title = miniMessage.deserialize(config.getString("title", "<gold>COPPER HEIST"));
        for (Player player : game.onlinePlayers()) {
            render(player, title, lines);
        }
    }

    private List<Component> buildLines(Game game) {
        String stateKey = game.getState().name().toLowerCase(Locale.ROOT);
        List<String> templates = config.getStringList("states." + stateKey);

        List<Component> lines = new ArrayList<>();
        for (String template : templates) {
            appendLine(lines, template, game);
        }
        for (String template : config.getStringList("footer")) {
            appendLine(lines, template, game);
        }
        if (lines.size() > 15) lines = lines.subList(0, 15);
        return lines;
    }

    private void appendLine(List<Component> lines, String template, Game game) {
        if (template.equals("<golems:copper>")) {
            appendGolemLines(lines, game, Team.COPPER);
        } else if (template.equals("<golems:iron>")) {
            appendGolemLines(lines, game, Team.IRON);
        } else {
            lines.add(miniMessage.deserialize(substitute(template, game)));
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

    private String substitute(String template, Game game) {
        String result = template
                .replace("{arena}", game.getArena().getName())
                .replace("{phase}", game.getState().name())
                .replace("{time}", formatTime(game.getSecondsRemaining()))
                .replace("{players}", String.valueOf(game.totalPlayers()))
                .replace("{min_players}", String.valueOf(plugin.getConfig().getInt("match.min-players", 6)))
                .replace("{max_players}", String.valueOf(plugin.getConfig().getInt("match.max-players", 16)));
        for (Team team : Team.values()) {
            GameTeam gameTeam = game.getTeam(team);
            String prefix = team.name().toLowerCase(Locale.ROOT);
            result = result
                    .replace("{" + prefix + "_score}", String.valueOf(gameTeam.getScore()))
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

    private void render(Player player, Component title, List<Component> lines) {
        Scoreboard board = boards.computeIfAbsent(player.getUniqueId(), id -> {
            Scoreboard sb = Bukkit.getScoreboardManager().getNewScoreboard();
            player.setScoreboard(sb);
            return sb;
        });

        Objective objective = board.getObjective(OBJECTIVE_NAME);
        if (objective == null) {
            objective = board.registerNewObjective(OBJECTIVE_NAME, Criteria.DUMMY, title);
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        } else {
            objective.displayName(title);
        }

        for (String entry : new ArrayList<>(board.getEntries())) {
            board.resetScores(entry);
        }
        for (org.bukkit.scoreboard.Team leftoverTeam : new ArrayList<>(board.getTeams())) {
            leftoverTeam.unregister();
        }

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
