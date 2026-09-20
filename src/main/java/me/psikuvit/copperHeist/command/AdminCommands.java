package me.psikuvit.copperHeist.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.arena.Arena;
import me.psikuvit.copperHeist.cosmetics.CosmeticService;
import me.psikuvit.copperHeist.game.Game;
import me.psikuvit.copperHeist.game.GamePlayer;
import me.psikuvit.copperHeist.game.Team;
import me.psikuvit.copperHeist.golem.GolemDebug;
import me.psikuvit.copperHeist.network.NetworkService;
import me.psikuvit.copperHeist.network.RemoteArena;
import me.psikuvit.copperHeist.stats.Stat;
import me.psikuvit.copperHeist.stats.StatsService;
import me.psikuvit.copperHeist.util.HeistEntities;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.BiConsumer;

import static io.papermc.paper.command.brigadier.Commands.argument;
import static io.papermc.paper.command.brigadier.Commands.literal;

/**
 * /ch admin ... - day-to-day server administration: look at and remove players in a match, jump to arena points,
 * edit stats, take arenas down, put the (whole) network in maintenance and broadcast to it.
 */
public final class AdminCommands {

    public static final String PERMISSION = "copperheist.admin.manage";

    private final CopperHeist plugin;
    private final SuggestionProvider<CommandSourceStack> arenaSuggestions;

    private static final SuggestionProvider<CommandSourceStack> ONLINE_PLAYERS = (ctx, builder) -> {
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName().toLowerCase(Locale.ROOT).startsWith(remaining)) builder.suggest(player.getName());
        }
        return builder.buildFuture();
    };

    private static final SuggestionProvider<CommandSourceStack> STATS = (ctx, builder) -> {
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        for (Stat stat : Stat.values()) {
            if (stat.key().startsWith(remaining)) builder.suggest(stat.key());
        }
        return builder.buildFuture();
    };

    private static final SuggestionProvider<CommandSourceStack> POINTS = (ctx, builder) -> {
        for (String point : new String[]{"lobby", "spectator", "copper", "iron"}) builder.suggest(point);
        return builder.buildFuture();
    };

    public AdminCommands(CopperHeist plugin, SuggestionProvider<CommandSourceStack> arenaSuggestions) {
        this.plugin = plugin;
        this.arenaSuggestions = arenaSuggestions;
    }

    public LiteralArgumentBuilder<CommandSourceStack> root() {
        return literal("admin")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(literal("players")
                        .then(argument("arena", StringArgumentType.word()).suggests(arenaSuggestions).executes(this::players)))
                .then(literal("kick")
                        .then(argument("player", StringArgumentType.word()).suggests(ONLINE_PLAYERS).executes(this::kick)))
                .then(literal("tp")
                        .then(argument("arena", StringArgumentType.word()).suggests(arenaSuggestions)
                                .then(argument("point", StringArgumentType.word()).suggests(POINTS).executes(this::teleport))))
                .then(literal("maintenance")
                        .then(literal("on").executes(ctx -> maintenance(ctx, true)))
                        .then(literal("off").executes(ctx -> maintenance(ctx, false)))
                        .executes(this::maintenanceStatus))
                .then(literal("broadcast")
                        .then(argument("message", StringArgumentType.greedyString()).executes(this::broadcast)))
                .then(literal("servers").executes(this::servers))
                .then(literal("clean").executes(this::clean))
                .then(literal("booster")
                        .then(argument("multiplier", DoubleArgumentType.doubleArg(1.0, 10.0))
                                .then(argument("minutes", IntegerArgumentType.integer(1, 1440)).executes(this::booster)))
                        .then(literal("off").executes(this::boosterOff)))
                .then(literal("cosmetic")
                        .then(literal("give")
                                .then(argument("player", StringArgumentType.word()).suggests(ONLINE_PLAYERS)
                                        .then(argument("id", StringArgumentType.word()).suggests(cosmeticSuggestions())
                                                .executes(ctx -> changeCosmetic(ctx, true)))))
                        .then(literal("take")
                                .then(argument("player", StringArgumentType.word()).suggests(ONLINE_PLAYERS)
                                        .then(argument("id", StringArgumentType.word()).suggests(cosmeticSuggestions())
                                                .executes(ctx -> changeCosmetic(ctx, false))))))
                .then(literal("coins")
                        .then(literal("give")
                                .then(argument("player", StringArgumentType.word()).suggests(ONLINE_PLAYERS)
                                        .then(argument("amount", IntegerArgumentType.integer(1)).executes(this::giveCoins)))))
                .then(literal("debug")
                        .then(literal("golems")
                                .executes(ctx -> debugGolems(ctx, null))
                                .then(literal("on").executes(ctx -> debugGolems(ctx, true)))
                                .then(literal("off").executes(ctx -> debugGolems(ctx, false))))
                        .then(literal("verbose")
                                .then(literal("on").executes(ctx -> debugFlag(ctx, "verbose", true)))
                                .then(literal("off").executes(ctx -> debugFlag(ctx, "verbose", false))))
                        .then(literal("visuals")
                                .then(literal("on").executes(ctx -> debugFlag(ctx, "visuals", true)))
                                .then(literal("off").executes(ctx -> debugFlag(ctx, "visuals", false))))
                        .then(literal("dump").executes(this::debugDump)))
                .then(literal("stats")
                        .then(literal("set").then(statArgs(true)))
                        .then(literal("add").then(statArgs(false)))
                        .then(literal("reset")
                                .then(argument("player", StringArgumentType.word()).suggests(ONLINE_PLAYERS).executes(this::resetStats))))
                .then(literal("arena")
                        .then(literal("disable")
                                .then(argument("arena", StringArgumentType.word()).suggests(arenaSuggestions).executes(this::disableArena)))
                        .then(literal("delete")
                                .then(argument("arena", StringArgumentType.word()).suggests(arenaSuggestions)
                                        .then(literal("confirm").executes(this::deleteArena)))));
    }

    private RequiredArgumentBuilder<CommandSourceStack, String> statArgs(boolean set) {
        return argument("player", StringArgumentType.word()).suggests(ONLINE_PLAYERS)
                .then(argument("stat", StringArgumentType.word()).suggests(STATS)
                        .then(argument("value", LongArgumentType.longArg(0)).executes(ctx -> editStat(ctx, set))));
    }

    // ---- helpers ----

    private Arena arena(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        String name = StringArgumentType.getString(ctx, "arena");
        Arena arena = plugin.getArenaManager().get(name);
        if (arena == null) Msg.err(sender, "command.no-such-arena", "arena", name);
        return arena;
    }

    /** Resolves a player by name - online players instantly, others from the stats database. */
    private void withPlayer(CommandSender sender, String name, BiConsumer<UUID, String> action) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            action.accept(online.getUniqueId(), online.getName());
            return;
        }
        StatsService stats = plugin.getStats();
        if (stats == null) {
            Msg.err(sender, "stats.disabled");
            return;
        }
        stats.repository().findByName(name).whenComplete((found, error) -> {
            if (error != null || found == null) Msg.err(sender, "stats.unknown-player", "player", name);
            else action.accept(found.uuid(), found.name());
        });
    }

    // ---- commands ----

    private int players(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        Arena arena = arena(ctx);
        if (arena == null) return 0;
        Game game = plugin.getGameManager().peek(arena);
        if (game == null || game.totalPlayers() == 0) {
            Msg.info(sender, "admin.no-players", "arena", arena.getName());
            return Command.SINGLE_SUCCESS;
        }
        Msg.info(sender, "admin.players-header", "arena", arena.getName(), "state", game.getState().name(), "count", game.totalPlayers());
        for (GamePlayer gp : game.gamePlayers()) {
            OfflinePlayer player = Bukkit.getOfflinePlayer(gp.getUuid());
            Msg.info(sender, "admin.player-line", "player", String.valueOf(player.getName()), "team", gp.getTeam().displayName(),
                    "role", gp.getRole() == null ? "-" : gp.getRole().id(),
                    "online", Msg.word(sender, player.isOnline() ? "status.enabled" : "status.disabled"));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int kick(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        String name = StringArgumentType.getString(ctx, "player");
        Player target = Bukkit.getPlayerExact(name);
        if (target == null || plugin.getGameManager().getGame(target) == null) {
            Msg.err(sender, "admin.not-in-match", "player", name);
            return 0;
        }
        plugin.getGameManager().leave(target);
        Msg.err(target, "admin.kicked");
        Msg.ok(sender, "admin.kick-done", "player", target.getName());
        return Command.SINGLE_SUCCESS;
    }

    private int teleport(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            Msg.err(sender, "setup.in-game-only");
            return 0;
        }
        Arena arena = arena(ctx);
        if (arena == null) return 0;
        String point = StringArgumentType.getString(ctx, "point").toLowerCase(Locale.ROOT);
        Location target = switch (point) {
            case "lobby" -> arena.getLobby();
            case "spectator" -> arena.getSpectator();
            case "copper" -> arena.site(Team.COPPER).spawn;
            case "iron" -> arena.site(Team.IRON).spawn;
            default -> null;
        };
        if (target == null) {
            Msg.err(sender, "admin.no-point", "point", point, "arena", arena.getName());
            return 0;
        }
        player.teleport(target);
        Msg.ok(sender, "admin.teleported", "point", point, "arena", arena.getName());
        return Command.SINGLE_SUCCESS;
    }

    private int maintenanceStatus(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        Msg.info(sender, "admin.maintenance-status", "state", Msg.word(sender,
                plugin.getNetwork().isMaintenance() ? "status.enabled" : "status.disabled"));
        return Command.SINGLE_SUCCESS;
    }

    private int maintenance(CommandContext<CommandSourceStack> ctx, boolean on) {
        CommandSender sender = ctx.getSource().getSender();
        plugin.getNetwork().setMaintenance(on);
        Msg.ok(sender, on ? "admin.maintenance-on" : "admin.maintenance-off");
        return Command.SINGLE_SUCCESS;
    }

    private int booster(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (plugin.getProgress() == null) {
            Msg.err(sender, "progress.disabled");
            return 0;
        }
        double multiplier = DoubleArgumentType.getDouble(ctx, "multiplier");
        int minutes = IntegerArgumentType.getInteger(ctx, "minutes");
        plugin.getProgress().setBooster(multiplier, minutes);
        Msg.ok(sender, "admin.booster-on", "multiplier", multiplier, "minutes", minutes);
        return Command.SINGLE_SUCCESS;
    }

    private int boosterOff(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (plugin.getProgress() == null) {
            Msg.err(sender, "progress.disabled");
            return 0;
        }
        plugin.getProgress().setBooster(1.0, 0);
        Msg.ok(sender, "admin.booster-off");
        return Command.SINGLE_SUCCESS;
    }

    private SuggestionProvider<CommandSourceStack> cosmeticSuggestions() {
        return (ctx, builder) -> {
            for (var cosmetic : plugin.getCosmeticRegistry().all()) builder.suggest(cosmetic.id());
            return builder.buildFuture();
        };
    }

    /** Gives or takes a cosmetic (for testing, support and store plugins that run console commands). */
    private int changeCosmetic(CommandContext<CommandSourceStack> ctx, boolean give) {
        CommandSender sender = ctx.getSource().getSender();
        String playerName = StringArgumentType.getString(ctx, "player");
        Player target = Bukkit.getPlayerExact(playerName);
        if (target == null) {
            Msg.err(sender, "admin.player-offline", "player", playerName);
            return 0;
        }
        var cosmetic = plugin.getCosmeticRegistry().get(StringArgumentType.getString(ctx, "id"));
        if (cosmetic == null) {
            Msg.err(sender, "cosmetics.unknown", "id", StringArgumentType.getString(ctx, "id"));
            return 0;
        }
        var outcome = give ? plugin.getCosmetics().grant(target, cosmetic, "admin") : plugin.getCosmetics().take(target, cosmetic);
        if (outcome == CosmeticService.Outcome.OK) {
            Msg.ok(sender, give ? "cosmetics.given" : "cosmetics.taken", "id", cosmetic.id(), "player", target.getName());
            return Command.SINGLE_SUCCESS;
        }
        Msg.err(sender, "cosmetics.outcome." + outcome.name().toLowerCase(Locale.ROOT));
        return 0;
    }

    private int giveCoins(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (plugin.getProgress() == null) {
            Msg.err(sender, "progress.disabled");
            return 0;
        }
        Player target = Bukkit.getPlayerExact(StringArgumentType.getString(ctx, "player"));
        if (target == null) {
            Msg.err(sender, "admin.player-offline", "player", StringArgumentType.getString(ctx, "player"));
            return 0;
        }
        int amount = IntegerArgumentType.getInteger(ctx, "amount");
        plugin.getProgress().grantCoins(target.getUniqueId(), target.getName(), amount);
        Msg.ok(sender, "admin.coins-given", "amount", amount, "player", target.getName());
        return Command.SINGLE_SUCCESS;
    }

    private int broadcast(CommandContext<CommandSourceStack> ctx) {
        String message = StringArgumentType.getString(ctx, "message");
        plugin.getNetwork().broadcast(plugin.getMessageService().rawFor(Bukkit.getConsoleSender(), "admin.broadcast-format", "message", message));
        return Command.SINGLE_SUCCESS;
    }

    /** Removes leftover match entities (golems, labels, NPCs, hitboxes, displays, stray loot). Only stale ones while a match is running. */
    private int clean(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        boolean running = false;
        for (Game game : plugin.getGameManager().all()) running |= game.isActive() || game.totalPlayers() > 0;
        int removed = running
                ? HeistEntities.sweepStaleEverywhere(plugin.getGameManager()::isMatchRunning)
                : HeistEntities.removeAllTagged();
        Msg.ok(sender, running ? "admin.clean-stale" : "admin.clean-all", "count", removed);
        return Command.SINGLE_SUCCESS;
    }

    /** Subscribes (or unsubscribes) the sender to the golem debug log; with no argument it toggles. */
    private int debugGolems(CommandContext<CommandSourceStack> ctx, Boolean on) {
        CommandSender sender = ctx.getSource().getSender();
        GolemDebug debug = plugin.getGolemDebug();
        boolean enable = on != null ? on : !debug.subscribed(sender);
        debug.subscribe(sender, enable);
        Msg.ok(sender, enable ? "admin.debug-on" : "admin.debug-off");
        return Command.SINGLE_SUCCESS;
    }

    private int debugFlag(CommandContext<CommandSourceStack> ctx, String flag, boolean on) {
        CommandSender sender = ctx.getSource().getSender();
        GolemDebug debug = plugin.getGolemDebug();
        if (flag.equals("verbose")) debug.setVerbose(on);
        else debug.setVisuals(on);
        Msg.ok(sender, "admin.debug-flag", "flag", flag, "state", Msg.word(sender, on ? "status.enabled" : "status.disabled"));
        return Command.SINGLE_SUCCESS;
    }

    /** Full snapshot of every golem in the sender's match (or in every match, if they aren't in one). */
    private int debugDump(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        List<Game> games = new ArrayList<>();
        if (sender instanceof Player player && plugin.getGameManager().getGame(player) != null) {
            games.add(plugin.getGameManager().getGame(player));
        } else {
            games.addAll(plugin.getGameManager().all());
        }
        boolean any = false;
        for (Game game : games) {
            if (game.getGolemManager().all().isEmpty()) continue;
            any = true;
            Msg.info(sender, "admin.debug-dump-header", "arena", game.getArena().getName(), "state", game.getState().name());
            for (String line : game.getGolemManager().describeAll()) Msg.info(sender, "admin.debug-line", "text", line);
        }
        if (!any) Msg.info(sender, "admin.debug-none");
        return Command.SINGLE_SUCCESS;
    }

    private int servers(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        NetworkService network = plugin.getNetwork();
        Msg.info(sender, "admin.network-header", "server", network.serverId(), "redis", Msg.word(sender,
                network.isConnected() ? "status.enabled" : "status.disabled"));
        if (network.remoteArenas().isEmpty()) Msg.info(sender, "network.none");
        for (RemoteArena remote : network.remoteArenas()) sendRemoteLine(sender, remote);
        return Command.SINGLE_SUCCESS;
    }

    static void sendRemoteLine(CommandSender sender, RemoteArena remote) {
        Msg.info(sender, "network.remote-line", "server", remote.serverId(), "arena", remote.arena(), "state", remote.state(),
                "players", remote.players(), "max", remote.maxPlayers());
    }

    private int editStat(CommandContext<CommandSourceStack> ctx, boolean set) {
        CommandSender sender = ctx.getSource().getSender();
        StatsService stats = plugin.getStats();
        if (stats == null) {
            Msg.err(sender, "stats.disabled");
            return 0;
        }
        Stat stat = Stat.fromKey(StringArgumentType.getString(ctx, "stat"));
        if (stat == null) {
            Msg.err(sender, "leaderboard.unknown-stat", "stat", StringArgumentType.getString(ctx, "stat"), "stats", "see /ch top");
            return 0;
        }
        long value = LongArgumentType.getLong(ctx, "value");
        withPlayer(sender, StringArgumentType.getString(ctx, "player"), (uuid, name) -> {
            if (set) {
                stats.set(uuid, stat, value).thenRun(() -> done(sender, name, stat, value));
            } else {
                stats.add(uuid, name, stat, value);
                stats.flush(uuid).thenRun(() -> done(sender, name, stat, value));
            }
        });
        return Command.SINGLE_SUCCESS;
    }

    private void done(CommandSender sender, String player, Stat stat, long value) {
        Msg.ok(sender, "admin.stat-done", "player", player, "stat", Msg.word(sender, stat.langKey()), "value", value);
        if (plugin.getLeaderboards() != null) plugin.getLeaderboards().refreshAll();
    }

    private int resetStats(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        StatsService stats = plugin.getStats();
        if (stats == null) {
            Msg.err(sender, "stats.disabled");
            return 0;
        }
        withPlayer(sender, StringArgumentType.getString(ctx, "player"), (uuid, name) -> stats.reset(uuid).thenRun(() -> {
            Msg.ok(sender, "admin.stat-reset", "player", name);
            if (plugin.getLeaderboards() != null) plugin.getLeaderboards().refreshAll();
        }));
        return Command.SINGLE_SUCCESS;
    }

    private int disableArena(CommandContext<CommandSourceStack> ctx) {
        Arena arena = arena(ctx);
        if (arena == null) return 0;
        arena.setEnabled(false);
        plugin.getArenaManager().save(arena);
        Msg.ok(ctx.getSource().getSender(), "admin.arena-disabled", "arena", arena.getName());
        return Command.SINGLE_SUCCESS;
    }

    private int deleteArena(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        Arena arena = arena(ctx);
        if (arena == null) return 0;
        Game game = plugin.getGameManager().peek(arena);
        if (game != null && game.totalPlayers() > 0) {
            Msg.err(sender, "admin.arena-busy", "arena", arena.getName());
            return 0;
        }
        plugin.getGameManager().discard(arena);
        plugin.getArenaManager().delete(arena);
        Msg.ok(sender, "admin.arena-deleted", "arena", arena.getName());
        return Command.SINGLE_SUCCESS;
    }
}
