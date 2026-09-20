package me.psikuvit.copperHeist.progress;

import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.config.ConfigFiles;
import me.psikuvit.copperHeist.event.CoinsChangedEvent;
import me.psikuvit.copperHeist.event.LevelUpEvent;
import me.psikuvit.copperHeist.stats.PlayerStats;
import me.psikuvit.copperHeist.stats.Stat;
import me.psikuvit.copperHeist.stats.StatsService;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Levels, XP and coins. Everything is stored as ordinary stats ({@link Stat#XP}, {@link Stat#COINS}, {@link Stat#COINS_EARNED}), so it
 * shares the stats service's batching, caching, MySQL/network behaviour, leaderboards and placeholders; the level is never stored, it is
 * worked out from total XP with the {@link LevelCurve} from progress.yml. Only call from the main thread.
 */
public class ProgressService {

    /** What one payout gave (after the booster) and the levels the player was on before and after it. */
    public record Award(long xp, long coins, int levelBefore, int levelAfter) {

        public boolean leveledUp() {
            return levelAfter > levelBefore;
        }
    }

    /** A named rank shown from {@code from} upwards; {@code color} is a theme tag, colour name or #hex. */
    public record Bracket(int from, String name, String color) {
    }

    private final CopperHeist plugin;
    private YamlConfiguration config;
    private LevelCurve curve = new LevelCurve(100, 30, 100);
    private final List<Bracket> brackets = new ArrayList<>();
    private double tempBooster = 1.0;
    private long tempBoosterUntil;

    public ProgressService(CopperHeist plugin) {
        this.plugin = plugin;
    }

    public void load() {
        config = ConfigFiles.load(plugin, "progress.yml");
        curve = new LevelCurve(config.getLong("levels.base-xp", 100), config.getLong("levels.step-xp", 30),
                config.getInt("levels.max-level", 100));
        brackets.clear();
        for (Map<?, ?> raw : config.getMapList("brackets")) {
            Object from = raw.get("from");
            Object name = raw.get("name");
            Object color = raw.get("color");
            if (!(from instanceof Number number) || name == null) continue;
            brackets.add(new Bracket(number.intValue(), String.valueOf(name), color == null ? "text" : String.valueOf(color)));
        }
        brackets.sort(Comparator.comparingInt(Bracket::from));
        if (brackets.isEmpty()) brackets.add(new Bracket(1, "Player", "text"));
    }

    /** False when progress.yml turns it off or stats (where XP and coins live) are unavailable. */
    public boolean enabled() {
        return config != null && config.getBoolean("enabled", true) && plugin.getStats() != null;
    }

    public LevelCurve curve() {
        return curve;
    }

    /** The loaded progress.yml (with the bundled defaults underneath). */
    public YamlConfiguration config() {
        return config;
    }

    // ---- reading ----

    private PlayerStats stats(UUID uuid, String name) {
        StatsService stats = plugin.getStats();
        return stats == null ? PlayerStats.empty(uuid, name) : stats.snapshot(uuid, name);
    }

    public long xp(UUID uuid, String name) {
        return stats(uuid, name).get(Stat.XP);
    }

    public int level(UUID uuid, String name) {
        return curve.levelFor(xp(uuid, name));
    }

    public long coins(UUID uuid, String name) {
        return stats(uuid, name).get(Stat.COINS);
    }

    private Bracket bracket(int level) {
        Bracket current = brackets.getFirst();
        for (Bracket bracket : brackets) {
            if (bracket.from() <= level) current = bracket;
        }
        return current;
    }

    /** The rank for a level as a MiniMessage string, e.g. {@code <ok>Pickpocket</ok>}. */
    public String rank(int level) {
        Bracket current = bracket(level);
        return "<" + current.color() + ">" + current.name() + "</" + current.color() + ">";
    }

    /** The rank's plain name, without colour - for placeholders read by other plugins. */
    public String rankName(int level) {
        return bracket(level).name();
    }

    /** A progress bar for the XP inside the current level, e.g. {@code <ok>██████</ok><dim>░░░░</dim>}. */
    public String bar(long xp) {
        int length = Math.max(3, config == null ? 10 : config.getInt("levels.bar-length", 10));
        int filled = (int) Math.round(curve.progress(xp) * length);
        return "<ok>" + "█".repeat(filled) + "</ok><dim>" + "░".repeat(length - filled) + "</dim>";
    }

    // ---- paying and spending ----

    public double booster() {
        double permanent = config == null ? 1.0 : config.getDouble("booster", 1.0);
        double temporary = System.currentTimeMillis() < tempBoosterUntil ? tempBooster : 1.0;
        return Math.max(permanent, temporary);
    }

    /** Runtime booster for {@code minutes} (the permanent {@code booster:} in progress.yml still applies if it is higher). */
    public void setBooster(double multiplier, int minutes) {
        tempBooster = Math.max(1.0, multiplier);
        tempBoosterUntil = minutes <= 0 ? 0 : System.currentTimeMillis() + minutes * 60_000L;
    }

    public long boosterSecondsLeft() {
        return Math.max(0, (tempBoosterUntil - System.currentTimeMillis()) / 1000);
    }

    /** Pays XP and coins (both multiplied by the booster), then handles any level-ups. Returns what was actually paid. */
    public Award award(UUID uuid, String name, double xp, double coins) {
        StatsService stats = plugin.getStats();
        long baseXp = xp(uuid, name);
        int before = curve.levelFor(baseXp);
        double boost = booster();
        long paidXp = Math.max(0, Math.round(xp * boost));
        long paidCoins = Math.max(0, Math.round(coins * boost));
        if (stats == null || (paidXp == 0 && paidCoins == 0)) return new Award(0, 0, before, before);

        int after = curve.levelFor(baseXp + paidXp);
        long levelCoins = 0;
        for (int level = before + 1; level <= after; level++) levelCoins += coinsForLevel(level);

        stats.add(uuid, name, Stat.XP, paidXp);
        stats.add(uuid, name, Stat.COINS, paidCoins + levelCoins);
        stats.add(uuid, name, Stat.COINS_EARNED, paidCoins + levelCoins);

        Award award = new Award(paidXp, paidCoins + levelCoins, before, after);
        if (award.coins() > 0) fireCoinsChanged(uuid, name, award.coins(), "match");
        Player player = Bukkit.getPlayer(uuid);
        if (player != null && award.leveledUp()) levelUp(player, before, after);
        return award;
    }

    /** Coins paid for reaching {@code level}. */
    public long coinsForLevel(int level) {
        long perLevel = config == null ? 10 : config.getLong("levels.coins-per-level", 10);
        long cap = config == null ? 200 : config.getLong("levels.max-coins", 200);
        return Math.min(cap, perLevel * level);
    }

    /** Coins with no XP (quests, admin gifts): counts towards coins earned. */
    public void grantCoins(UUID uuid, String name, long amount) {
        grantCoins(uuid, name, amount, "grant");
    }

    /** As above, with a short reason id ("quest", "admin" ...) that is passed on in the {@link CoinsChangedEvent}. */
    public void grantCoins(UUID uuid, String name, long amount, String reason) {
        StatsService stats = plugin.getStats();
        if (stats == null || amount <= 0) return;
        stats.add(uuid, name, Stat.COINS, amount);
        stats.add(uuid, name, Stat.COINS_EARNED, amount);
        fireCoinsChanged(uuid, name, amount, reason);
    }

    /** Takes coins if the player has enough. Returns false (and takes nothing) if they don't. */
    public boolean spend(UUID uuid, String name, long amount) {
        return spend(uuid, name, amount, "spend");
    }

    public boolean spend(UUID uuid, String name, long amount, String reason) {
        StatsService stats = plugin.getStats();
        if (stats == null || amount < 0 || coins(uuid, name) < amount) return false;
        if (amount == 0) return true;
        stats.add(uuid, name, Stat.COINS, -amount);
        fireCoinsChanged(uuid, name, -amount, reason);
        return true;
    }

    /** Puts coins back after a failed purchase (not counted as earned). */
    public void refundCoins(UUID uuid, String name, long amount) {
        StatsService stats = plugin.getStats();
        if (stats == null || amount <= 0) return;
        stats.add(uuid, name, Stat.COINS, amount);
        fireCoinsChanged(uuid, name, amount, "refund");
    }

    private void fireCoinsChanged(UUID uuid, String name, long delta, String reason) {
        Bukkit.getPluginManager().callEvent(new CoinsChangedEvent(uuid, delta, coins(uuid, name), reason));
    }

    private void levelUp(Player player, int from, int to) {
        var messages = plugin.getMessageService();
        Bukkit.getPluginManager().callEvent(new LevelUpEvent(player, from, to));
        player.showTitle(Title.title(messages.get(player, "progress.level-up-title", "level", to),
                messages.get(player, "progress.level-up-subtitle", "level", to, "rank", rank(to), "coins", coinsForLevel(to))));
        player.sendMessage(messages.get(player, "progress.level-up-chat", "level", to, "rank", rank(to)));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);
    }
}
