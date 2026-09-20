package me.psikuvit.copperHeist.hook;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.arena.Arena;
import me.psikuvit.copperHeist.game.Game;
import me.psikuvit.copperHeist.game.GamePlayer;
import me.psikuvit.copperHeist.stats.Stat;
import me.psikuvit.copperHeist.stats.StatsService;
import me.psikuvit.copperHeist.stats.TopEntry;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * PlaceholderAPI expansion "copperheist". Placeholders:
 * <ul>
 * <li>%copperheist_&lt;stat&gt;% - a stat of the player (wins, kills, loot_delivered ... see /ch top for the ids)</li>
 * <li>%copperheist_top_&lt;stat&gt;_&lt;n&gt;_name% and %copperheist_top_&lt;stat&gt;_&lt;n&gt;_value% - leaderboard rows</li>
 * <li>%copperheist_arena%, %copperheist_state%, %copperheist_team%, %copperheist_role% - the player's current match</li>
 * <li>%copperheist_arena_state_&lt;arena&gt;% and %copperheist_arena_players_&lt;arena&gt;% - any arena</li>
 * </ul>
 * Stat values only exist while stats are enabled; missing data returns an empty string or 0, never an error.
 */
public class PlaceholderHook extends PlaceholderExpansion {

    private final CopperHeist plugin;

    public PlaceholderHook(CopperHeist plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NonNull String getIdentifier() {
        return "copperheist";
    }

    @Override
    public @NonNull String getAuthor() {
        return "Psikuvit";
    }

    @Override
    public @NonNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NonNull String params) {
        String key = params.toLowerCase(Locale.ROOT);

        if (key.startsWith("top_")) return top(key.substring(4));
        if (key.startsWith("arena_state_")) return arenaState(key.substring("arena_state_".length()));
        if (key.startsWith("arena_players_")) return arenaPlayers(key.substring("arena_players_".length()));

        var progress = plugin.getProgress();
        if (progress != null && player != null && progress.enabled()) {
            long xp = progress.xp(player.getUniqueId(), String.valueOf(player.getName()));
            int level = progress.curve().levelFor(xp);
            switch (key) {
                case "level" -> {
                    return String.valueOf(level);
                }
                case "rank" -> {
                    return progress.rankName(level);
                }
                case "xp_next" -> {
                    return String.valueOf(level >= progress.curve().maxLevel() ? 0 : progress.curve().xpForNext(level) - progress.curve().xpIntoLevel(xp));
                }
                default -> {
                }
            }
        }

        // After the level placeholders above: "level" is also accepted as a stat key (it means XP) and must not shadow them.
        if (key.equals("title")) return player instanceof Player online ? plugin.getCosmetics().plainTitle(online) : "";
        if (key.equals("level")) return "1"; // progression is off (otherwise handled above)
        Stat stat = Stat.fromKey(key);
        if (stat != null) return stat(player, stat);

        Game game = player instanceof Player online ? plugin.getGameManager().getGame(online) : null;
        GamePlayer gp = game == null ? null : game.getGamePlayer(player.getUniqueId());
        return switch (key) {
            case "arena" -> game == null ? "" : game.getArena().getName();
            case "state" -> game == null ? "" : game.getState().name();
            case "team" -> gp == null ? "" : gp.getTeam().displayName();
            case "role" -> gp == null || gp.getRole() == null ? "" : gp.getRole().displayName();
            default -> null;
        };
    }

    private String stat(OfflinePlayer player, Stat stat) {
        StatsService stats = plugin.getStats();
        if (stats == null || player == null) return "0";
        return String.valueOf(stats.snapshot(player.getUniqueId(), String.valueOf(player.getName())).get(stat));
    }

    /** "wins_1_name" -> the leaderboard's first name for wins. */
    private String top(String rest) {
        if (plugin.getLeaderboards() == null) return "";
        String[] parts = rest.split("_");
        if (parts.length < 3) return null;
        String field = parts[parts.length - 1];
        String position = parts[parts.length - 2];
        Stat stat = Stat.fromKey(String.join("_", Arrays.copyOfRange(parts, 0, parts.length - 2)));
        if (stat == null || !(field.equals("name") || field.equals("value"))) return null;
        int index;
        try {
            index = Integer.parseInt(position) - 1;
        } catch (NumberFormatException ex) {
            return null;
        }
        List<TopEntry> rows = plugin.getLeaderboards().top(stat);
        if (index < 0 || index >= rows.size()) return field.equals("name") ? "-" : "0";
        TopEntry row = rows.get(index);
        return field.equals("name") ? row.name() : String.valueOf(row.value());
    }

    private String arenaState(String arenaName) {
        Arena arena = plugin.getArenaManager().get(arenaName);
        if (arena == null) return "";
        Game game = plugin.getGameManager().peek(arena);
        return game == null ? "WAITING" : game.getState().name();
    }

    private String arenaPlayers(String arenaName) {
        Arena arena = plugin.getArenaManager().get(arenaName);
        if (arena == null) return "0";
        Game game = plugin.getGameManager().peek(arena);
        return String.valueOf(game == null ? 0 : game.totalPlayers());
    }
}
