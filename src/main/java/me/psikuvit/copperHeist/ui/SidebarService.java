package me.psikuvit.copperHeist.ui;

import io.papermc.paper.world.WeatheringCopperState;
import me.psikuvit.copperHeist.game.Game;
import me.psikuvit.copperHeist.game.GameState;
import me.psikuvit.copperHeist.game.GameTeam;
import me.psikuvit.copperHeist.game.Team;
import me.psikuvit.copperHeist.golem.HeistGolem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Per-player sidebar using the classic team-prefix trick (each visible line
 * is a team prefix; the scoreboard "entry" itself is an invisible color code
 * so duplicate-looking lines don't collide).
 */
public class SidebarService {

    private static final String ENTRY_CODES = "0123456789abcdef";
    private static final String OBJECTIVE_NAME = "ch_sidebar";

    private final Map<UUID, Scoreboard> boards = new HashMap<>();

    public void update(Game game) {
        List<Component> lines = buildLines(game);
        for (Player player : game.onlinePlayers()) {
            render(player, lines);
        }
    }

    private List<Component> buildLines(Game game) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.text("Phase: " + game.getState(), NamedTextColor.GRAY));
        lines.add(Component.text("Time: " + formatTime(game.getSecondsRemaining()), NamedTextColor.GRAY));
        lines.add(Component.empty());
        for (Team team : Team.values()) {
            GameTeam gameTeam = game.getTeam(team);
            lines.add(Component.text(team.displayName().toUpperCase() + " VAULT  ", team.color())
                    .append(Component.text(gameTeam.getScore(), NamedTextColor.WHITE)));
        }
        lines.add(Component.empty());
        for (Team team : Team.values()) {
            GameTeam gameTeam = game.getTeam(team);
            lines.add(Component.text(team.displayName() + " golems: " + gameTeam.getGolems().size(), team.color()));
            for (HeistGolem golem : gameTeam.getGolems()) {
                String stage = stageLabel(golem.getEntity().getWeatheringState());
                String carrying = golem.isCarrying() ? " [carrying]" : "";
                lines.add(Component.text(" - " + stage + carrying, NamedTextColor.DARK_GRAY));
            }
        }
        if (lines.size() > 15) lines = lines.subList(0, 15);
        return lines;
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

    private void render(Player player, List<Component> lines) {
        Scoreboard board = boards.computeIfAbsent(player.getUniqueId(), id -> {
            Scoreboard sb = Bukkit.getScoreboardManager().getNewScoreboard();
            player.setScoreboard(sb);
            return sb;
        });

        Objective objective = board.getObjective(OBJECTIVE_NAME);
        if (objective == null) {
            objective = board.registerNewObjective(OBJECTIVE_NAME, Criteria.DUMMY, Component.text("COPPER HEIST", NamedTextColor.GOLD));
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
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
