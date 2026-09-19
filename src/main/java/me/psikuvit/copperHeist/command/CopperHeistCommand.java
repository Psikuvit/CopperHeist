package me.psikuvit.copperHeist.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.arena.Arena;
import me.psikuvit.copperHeist.arena.ArenaCheck;
import me.psikuvit.copperHeist.arena.ArenaSnapshot;
import me.psikuvit.copperHeist.game.Game;
import me.psikuvit.copperHeist.game.GamePlayer;
import me.psikuvit.copperHeist.game.GameState;
import me.psikuvit.copperHeist.game.Team;
import me.psikuvit.copperHeist.loot.LootItem;
import me.psikuvit.copperHeist.loot.LootTierDefinition;
import me.psikuvit.copperHeist.network.RemoteArena;
import me.psikuvit.copperHeist.role.RoleDefinition;
import me.psikuvit.copperHeist.stats.LeaderboardService;
import me.psikuvit.copperHeist.stats.PlayerStats;
import me.psikuvit.copperHeist.stats.Stat;
import me.psikuvit.copperHeist.stats.StatsService;
import me.psikuvit.copperHeist.stats.TopEntry;
import me.psikuvit.copperHeist.task.SnapshotRestoreTask;
import me.psikuvit.copperHeist.ui.Text;
import org.bukkit.Location;
import org.bukkit.block.Chest;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

import static io.papermc.paper.command.brigadier.Commands.argument;
import static io.papermc.paper.command.brigadier.Commands.literal;

/** The /ch command tree. Every message it sends is a lang key resolved in the sender's language. */
public final class CopperHeistCommand {

    private static final String STATS = "copperheist.stats";
    private static final String ADMIN_ARENA ="copperheist.admin.arena";
    private static final String ADMIN_DEBUG = "copperheist.admin.debug";
    private static final String ADMIN_RELOAD = "copperheist.admin.reload";

    private static final SuggestionProvider<CommandSourceStack> PHASE_SUGGESTIONS = (ctx, builder) -> {
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        for (GameState phase : new GameState[]{GameState.SETUP, GameState.COLLECTION, GameState.HEIST, GameState.FINAL_RUSH}) {
            String name = phase.name().toLowerCase(Locale.ROOT);
            if (name.startsWith(remaining)) builder.suggest(name);
        }
        return builder.buildFuture();
    };

    private static final SuggestionProvider<CommandSourceStack> STAT_SUGGESTIONS = (ctx, builder) -> {
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        for (Stat stat : Stat.values()) {
            if (stat.key().startsWith(remaining)) builder.suggest(stat.key());
        }
        return builder.buildFuture();
    };

    private static final SuggestionProvider<CommandSourceStack> LOOT_ZONES = (ctx, builder) -> {
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        for (Arena.LootZone zone : Arena.LootZone.values()) {
            String name = zone.name().toLowerCase(Locale.ROOT);
            if (name.startsWith(remaining)) builder.suggest(name);
        }
        return builder.buildFuture();
    };

    private final CopperHeist plugin;
    private final SuggestionProvider<CommandSourceStack> arenaSuggestions;
    private final SuggestionProvider<CommandSourceStack> tierSuggestions = (ctx, builder) -> {
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        for (LootTierDefinition tier : LootItem.tiers().all()) {
            if (tier.id().startsWith(remaining)) builder.suggest(tier.id());
        }
        return builder.buildFuture();
    };

    private CopperHeistCommand(CopperHeist plugin) {
        this.plugin = plugin;
        this.arenaSuggestions = ArenaSuggestions.of(plugin);
    }

    public static void register(CopperHeist plugin) {
        CopperHeistCommand commands = new CopperHeistCommand(plugin);
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands registrar = event.registrar();
            LiteralArgumentBuilder<CommandSourceStack> root = literal("ch")
                    .then(literal("join")
                            .executes(commands::executeJoin)
                            .then(argument("arena", StringArgumentType.word())
                                    .suggests(commands.arenaSuggestions)
                                    .executes(commands::executeJoin)))
                    .then(literal("leave").executes(commands::executeLeave))
                    .then(literal("lobby").executes(commands::executeLobby))
                    .then(literal("setlobby")
                            .requires(src -> src.getSender().hasPermission(ADMIN_ARENA))
                            .executes(commands::executeSetLobby))
                    .then(literal("list").executes(commands::executeList))
                    .then(literal("shop").executes(commands::executeShop))
                    .then(literal("stats")
                            .requires(src -> src.getSender().hasPermission(STATS))
                            .executes(commands::executeStats)
                            .then(argument("player", StringArgumentType.word())
                                    .executes(commands::executeStats)))
                    .then(literal("role")
                            .executes(commands::executeRoleMenu)
                            .then(argument("role", StringArgumentType.word())
                                    .suggests(RoleSuggestions.of(plugin))
                                    .executes(commands::executeRole)))
                    .then(literal("forcestart")
                            .requires(src -> src.getSender().hasPermission(ADMIN_DEBUG))
                            .then(argument("arena", StringArgumentType.word())
                                    .suggests(commands.arenaSuggestions)
                                    .executes(commands::executeForceStart)))
                    .then(literal("forcestop")
                            .requires(src -> src.getSender().hasPermission(ADMIN_DEBUG))
                            .then(argument("arena", StringArgumentType.word())
                                    .suggests(commands.arenaSuggestions)
                                    .executes(commands::executeForceStop)))
                    .then(literal("giveloot")
                            .requires(src -> src.getSender().hasPermission(ADMIN_DEBUG))
                            .executes(commands::executeGiveLoot)
                            .then(argument("amount", IntegerArgumentType.integer(1))
                                    .executes(commands::executeGiveLoot))
                            .then(argument("tier", StringArgumentType.word())
                                    .suggests(commands.tierSuggestions)
                                    .executes(commands::executeGiveLoot)
                                    .then(argument("amount", IntegerArgumentType.integer(1))
                                            .executes(commands::executeGiveLoot))))
                    .then(literal("reload")
                            .requires(src -> src.getSender().hasPermission(ADMIN_RELOAD))
                            .executes(commands::executeReload))
                    .then(literal("setphase")
                            .requires(src -> src.getSender().hasPermission(ADMIN_DEBUG))
                            .then(argument("arena", StringArgumentType.word())
                                    .suggests(commands.arenaSuggestions)
                                    .then(argument("phase", StringArgumentType.word())
                                            .suggests(PHASE_SUGGESTIONS)
                                            .executes(commands::executeSetPhase))))
                    .then(literal("spawnrelic")
                            .requires(src -> src.getSender().hasPermission(ADMIN_DEBUG))
                            .then(argument("arena", StringArgumentType.word())
                                    .suggests(commands.arenaSuggestions)
                                    .executes(commands::executeSpawnRelic)))
                    .then(literal("spectate")
                            .then(argument("arena", StringArgumentType.word())
                                    .suggests(commands.arenaSuggestions)
                                    .executes(commands::executeSpectate)))
                    .then(literal("top")
                            .requires(src -> src.getSender().hasPermission(STATS))
                            .executes(commands::executeTop)
                            .then(argument("stat", StringArgumentType.word())
                                    .suggests(STAT_SUGGESTIONS)
                                    .executes(commands::executeTop)))
                    .then(literal("info")
                            .requires(src -> src.getSender().hasPermission(ADMIN_DEBUG))
                            .executes(commands::executeInfo))
                    .then(literal("setup")
                            .requires(src -> src.getSender().hasPermission(ADMIN_ARENA))
                            .then(argument("arena", StringArgumentType.word())
                                    .suggests(commands.arenaSuggestions)
                                    .executes(ctx -> {
                                        CommandSender sender = ctx.getSource().getSender();
                                        Arena arena = commands.requireArena(sender, StringArgumentType.getString(ctx, "arena"));
                                        if (arena == null) return 0;
                                        new SetupWizard(plugin).show(sender, arena);
                                        return Command.SINGLE_SUCCESS;
                                    })))
                    .then(new AdminCommands(plugin, commands.arenaSuggestions).root())
                    .then(commands.leaderboardRoot())
                    .then(commands.arenaRoot());

            registrar.register(root.build(), "Copper Heist");
        });
    }

    // ---- player commands ----

    private int executeJoin(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            Msg.err(sender, "command.player-only-join");
            return 0;
        }
        String arenaName = optionalString(ctx, "arena");
        Text error = plugin.getGameManager().join(player, arenaName);
        if (error != null) {
            if (plugin.getNetwork().isConnected() && !plugin.getNetwork().isMaintenance()) {
                RemoteArena remote = plugin.getNetwork().findRemote(arenaName);
                if (remote != null) {
                    Msg.ok(player, "network.sending", "arena", remote.arena(), "server", remote.serverId());
                    plugin.getNetwork().transfer(player, remote).thenAccept(sent -> {
                        if (!sent) Msg.err(player, "network.transfer-failed");
                    });
                    return Command.SINGLE_SUCCESS;
                }
            }
            Msg.err(player, error);
            return 0;
        }
        return Command.SINGLE_SUCCESS;
    }

    private int executeLeave(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) return 0;
        if (plugin.getGameManager().getGame(player) == null) {
            Msg.err(player, "command.not-in-match");
            return 0;
        }
        plugin.getGameManager().leave(player);
        Msg.ok(player, "command.left-arena");
        return Command.SINGLE_SUCCESS;
    }

    /** /ch lobby - back to the hub spawn (leaving a match first). */
    private int executeLobby(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            Msg.err(sender, "setup.in-game-only");
            return 0;
        }
        Location hub = plugin.getHubSpawn().get();
        if (hub == null) {
            Msg.err(player, "hub.not-set");
            return 0;
        }
        if (plugin.getGameManager().getGame(player) != null) plugin.getGameManager().leave(player);
        player.teleport(hub);
        Msg.ok(player, "hub.teleported");
        return Command.SINGLE_SUCCESS;
    }

    /** /ch setlobby - the hub spawn for the whole server (not an arena's waiting room; that is /ch arena setlobby). */
    private int executeSetLobby(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            Msg.err(sender, "setup.in-game-only");
            return 0;
        }
        plugin.getHubSpawn().set(player.getLocation());
        Msg.ok(player, "hub.set");
        return Command.SINGLE_SUCCESS;
    }

    private int executeList(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (plugin.getArenaManager().all().isEmpty() && plugin.getNetwork().remoteArenas().isEmpty()) {
            Msg.err(sender, "command.no-arenas");
            return Command.SINGLE_SUCCESS;
        }
        for (RemoteArena remote : plugin.getNetwork().remoteArenas()) AdminCommands.sendRemoteLine(sender, remote);
        for (Arena arena : plugin.getArenaManager().all()) {
            Game game = plugin.getGameManager().peek(arena);
            String state = game != null ? game.getState().name() : GameState.WAITING.name();
            int players = game != null ? game.totalPlayers() : 0;
            Msg.info(sender, "command.arena-line", "arena", arena.getName(),
                    "status", Msg.word(sender, arena.isEnabled() ? "status.enabled" : "status.disabled"),
                    "state", state, "players", players);
        }
        return Command.SINGLE_SUCCESS;
    }

    private int executeStats(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        StatsService stats = plugin.getStats();
        if (stats == null) {
            Msg.err(sender, "stats.disabled");
            return 0;
        }
        String requested = optionalString(ctx, "player");
        if (requested == null) {
            if (!(sender instanceof Player self)) {
                Msg.err(sender, "stats.console-needs-player");
                return 0;
            }
            if (!stats.isLoaded(self.getUniqueId())) {
                Msg.err(sender, "stats.loading");
                return 0;
            }
            sendStats(sender, stats.snapshot(self.getUniqueId(), self.getName()));
            return Command.SINGLE_SUCCESS;
        }

        Player online = plugin.getServer().getPlayerExact(requested);
        if (online != null && stats.isLoaded(online.getUniqueId())) {
            sendStats(sender, stats.snapshot(online.getUniqueId(), online.getName()));
            return Command.SINGLE_SUCCESS;
        }
        stats.repository().findByName(requested).whenComplete((found, error) -> {
            if (error != null || found == null) Msg.err(sender, "stats.unknown-player", "player", requested);
            else sendStats(sender, found);
        });
        return Command.SINGLE_SUCCESS;
    }

    /** A one-screen health check: what is loaded and which optional parts are switched on. */
    private int executeInfo(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        var pm = plugin.getServer().getPluginManager();
        Msg.info(sender, "info.header", "version", plugin.getPluginMeta().getVersion());
        Msg.info(sender, "info.line", "key", "arenas", "value", plugin.getArenaManager().all().size()
                + " (" + plugin.getGameManager().all().size() + " with a game object)");
        Msg.info(sender, "info.line", "key", "roles / loot tiers", "value", plugin.getRoleRegistry().all().size()
                + " / " + LootItem.tiers().all().size());
        Msg.info(sender, "info.line", "key", "presets", "value", String.join(", ", plugin.getPresets().names()));
        Msg.info(sender, "info.line", "key", "stats", "value", plugin.getStats() == null
                ? "off" : plugin.settings().getString("database.type", "sqlite"));
        Msg.info(sender, "info.line", "key", "leaderboards", "value", plugin.getLeaderboards() == null
                ? "off" : String.valueOf(plugin.getLeaderboards().boards().size()));
        Msg.info(sender, "info.line", "key", "npc / menu / reset", "value", plugin.settings().getString("npc.type", "villager")
                + " / " + plugin.settings().getString("ui.menu", "chest") + " / " + plugin.settings().getString("reset.method", "entities"));
        Msg.info(sender, "info.line", "key", "PlaceholderAPI / Vault", "value", pm.isPluginEnabled("PlaceholderAPI")
                + " / " + pm.isPluginEnabled("Vault"));
        return Command.SINGLE_SUCCESS;
    }

    private int executeTop(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        LeaderboardService boards = plugin.getLeaderboards();
        if (boards == null) {
            Msg.err(sender, "stats.disabled");
            return 0;
        }
        String requested = optionalString(ctx, "stat");
        Stat stat = Stat.fromKey(requested != null ? requested : plugin.settings().getString("leaderboards.default-stat", "wins"));
        if (stat == null) {
            Msg.err(sender, "leaderboard.unknown-stat", "stat", requested, "stats", statNames());
            return 0;
        }
        Msg.info(sender, "leaderboard.title", "stat", Msg.word(sender, stat.langKey()));
        List<TopEntry> rows = boards.top(stat);
        if (rows.isEmpty()) Msg.info(sender, "leaderboard.empty");
        for (TopEntry row : rows) {
            Msg.info(sender, "leaderboard.line", "rank", row.rank(), "player", row.name(), "value", row.value());
        }
        return Command.SINGLE_SUCCESS;
    }

    private static String statNames() {
        StringBuilder names = new StringBuilder();
        for (Stat stat : Stat.values()) names.append(names.isEmpty() ? "" : ", ").append(stat.key());
        return names.toString();
    }

    /** /ch leaderboard create|remove|list - floating text boards showing a stat's top players. */
    private LiteralArgumentBuilder<CommandSourceStack> leaderboardRoot() {
        return literal("leaderboard")
                .requires(src -> src.getSender().hasPermission(ADMIN_ARENA))
                .then(literal("create")
                        .then(argument("id", StringArgumentType.word())
                                .then(argument("stat", StringArgumentType.word())
                                        .suggests(STAT_SUGGESTIONS)
                                        .executes(this::executeBoardCreate))))
                .then(literal("remove")
                        .then(argument("id", StringArgumentType.word())
                                .executes(this::executeBoardRemove)))
                .then(literal("list").executes(this::executeBoardList));
    }

    private int executeBoardCreate(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        LeaderboardService boards = plugin.getLeaderboards();
        if (boards == null) {
            Msg.err(sender, "stats.disabled");
            return 0;
        }
        if (!(sender instanceof Player player)) {
            Msg.err(sender, "setup.in-game-only");
            return 0;
        }
        String statName = StringArgumentType.getString(ctx, "stat");
        Stat stat = Stat.fromKey(statName);
        if (stat == null) {
            Msg.err(sender, "leaderboard.unknown-stat", "stat", statName, "stats", statNames());
            return 0;
        }
        String id = StringArgumentType.getString(ctx, "id");
        boards.create(id, stat, player.getLocation().add(0, 1.5, 0));
        Msg.ok(sender, "leaderboard.created", "id", id, "stat", stat.key());
        return Command.SINGLE_SUCCESS;
    }

    private int executeBoardRemove(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        LeaderboardService boards = plugin.getLeaderboards();
        String id = StringArgumentType.getString(ctx, "id");
        if (boards == null || !boards.remove(id)) {
            Msg.err(sender, "leaderboard.not-found", "id", id);
            return 0;
        }
        Msg.ok(sender, "leaderboard.removed", "id", id);
        return Command.SINGLE_SUCCESS;
    }

    private int executeBoardList(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        LeaderboardService boards = plugin.getLeaderboards();
        if (boards == null || boards.boards().isEmpty()) {
            Msg.err(sender, "leaderboard.none");
            return Command.SINGLE_SUCCESS;
        }
        for (LeaderboardService.Board board : boards.boards()) {
            Location at = board.location();
            Msg.info(sender, "leaderboard.list-line", "id", board.id(), "stat", board.stat().key(), "world", at.getWorld().getName(),
                    "x", at.getBlockX(), "y", at.getBlockY(), "z", at.getBlockZ());
        }
        return Command.SINGLE_SUCCESS;
    }

    private void sendStats(CommandSender sender, PlayerStats stats) {
        Msg.info(sender, "stats.header", "player", stats.name());
        for (Stat stat : Stat.values()) {
            Msg.info(sender, "stats.line", "stat", Msg.word(sender, stat.langKey()), "value", stats.get(stat));
        }
    }

    private int executeRole(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            Msg.err(sender, "command.player-only-role");
            return 0;
        }
        Game game = plugin.getGameManager().getGame(player);
        if (game == null) {
            Msg.err(player, "command.join-first");
            return 0;
        }
        GamePlayer gp = game.getGamePlayer(player.getUniqueId());
        if (gp == null) return 0;

        if (!game.feature("roles")) {
            Msg.err(player, "command.roles-disabled");
            return 0;
        }
        RoleDefinition role = plugin.getRoleRegistry().get(StringArgumentType.getString(ctx, "role"));
        if (role == null) {
            StringBuilder ids = new StringBuilder();
            for (RoleDefinition def : plugin.getRoleRegistry().all()) ids.append(ids.isEmpty() ? "" : ", ").append(def.id());
            Msg.err(player, "command.unknown-role", "roles", ids);
            return 0;
        }

        Text error = game.getRoleService().trySetRole(gp, role);
        if (error != null) {
            Msg.err(player, error);
            return 0;
        }
        Msg.ok(player, game.isActive() ? "command.role-set-next" : "command.role-set", "role", role.displayName());
        return Command.SINGLE_SUCCESS;
    }

    private int executeRoleMenu(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            Msg.err(sender, "command.player-only-role");
            return 0;
        }
        Game game = plugin.getGameManager().getGame(player);
        if (game == null || game.getGamePlayer(player.getUniqueId()) == null) {
            Msg.err(player, "command.join-first");
            return 0;
        }
        if (!game.feature("roles")) {
            Msg.err(player, "command.roles-disabled");
            return 0;
        }
        plugin.providers().menu().resolve(plugin.settings().getString("ui.menu", "chest"))
                .openRoles(player, game, game.getGamePlayer(player.getUniqueId()));
        return Command.SINGLE_SUCCESS;
    }

    private int executeShop(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            Msg.err(sender, "command.player-only-shop");
            return 0;
        }
        Game shopGame = plugin.getGameManager().getGame(player);
        if (shopGame == null) {
            Msg.err(player, "command.join-first");
            return 0;
        }
        if (!shopGame.feature("shop")) {
            Msg.err(player, "command.shop-disabled");
            return 0;
        }
        GamePlayer shopGp = shopGame.getGamePlayer(player.getUniqueId());
        if (shopGame.isActive() && shopGp != null && !shopGame.isNearOwnSpawn(player, shopGp)) {
            Msg.err(player, "command.shop-too-far");
            return 0;
        }
        plugin.providers().menu().resolve(plugin.settings().getString("ui.menu", "chest")).openShop(player, shopGame, shopGp);
        return Command.SINGLE_SUCCESS;
    }

    // ---- admin / debug commands ----

    private int executeReload(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        plugin.reloadConfig();
        plugin.getMessageService().load();
        plugin.getSidebarService().load();
        plugin.getShopService().load();
        plugin.getLobbyKitService().load();
        Team.configure(plugin.getConfig().getConfigurationSection("teams"));
        plugin.getRoleRegistry().load();
        plugin.getLootTiers().load();
        plugin.getPresets().load();
        Msg.ok(sender, "command.reloaded");
        return Command.SINGLE_SUCCESS;
    }

    private int executeSetPhase(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        Arena arena = requireArena(sender, StringArgumentType.getString(ctx, "arena"));
        if (arena == null) return 0;
        GameState phase;
        try {
            phase = GameState.valueOf(StringArgumentType.getString(ctx, "phase").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            Msg.err(sender, "command.unknown-phase");
            return 0;
        }
        Game game = plugin.getGameManager().peek(arena);
        if (game == null || !game.forcePhase(phase)) {
            Msg.err(sender, "command.phase-failed");
            return 0;
        }
        Msg.ok(sender, "command.jumping", "arena", arena.getName(), "phase", phase.name());
        return Command.SINGLE_SUCCESS;
    }

    private int executeSpawnRelic(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        Arena arena = requireArena(sender, StringArgumentType.getString(ctx, "arena"));
        if (arena == null) return 0;
        Game game = plugin.getGameManager().peek(arena);
        if (game == null || !game.isActive() || !game.getRelicManager().forceSpawn()) {
            Msg.err(sender, "command.relic-failed");
            return 0;
        }
        Msg.ok(sender, "command.relic-spawned", "arena", arena.getName());
        return Command.SINGLE_SUCCESS;
    }

    private int executeSpectate(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            Msg.err(sender, "command.player-only-spectate");
            return 0;
        }
        Text error = plugin.getGameManager().spectate(player, StringArgumentType.getString(ctx, "arena"));
        if (error != null) {
            Msg.err(player, error);
            return 0;
        }
        Msg.ok(player, "command.spectating");
        return Command.SINGLE_SUCCESS;
    }

    private int executeForceStart(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        Arena arena = requireArena(sender, StringArgumentType.getString(ctx, "arena"));
        if (arena == null) return 0;
        plugin.getGameManager().getGame(arena).forceStart();
        Msg.ok(sender, "command.forced-start", "arena", arena.getName());
        return Command.SINGLE_SUCCESS;
    }

    private int executeForceStop(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        Arena arena = requireArena(sender, StringArgumentType.getString(ctx, "arena"));
        if (arena == null) return 0;
        plugin.getGameManager().getGame(arena).forceStop();
        Msg.ok(sender, "command.forced-stop", "arena", arena.getName());
        return Command.SINGLE_SUCCESS;
    }

    private int executeGiveLoot(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            Msg.err(sender, "command.player-only-giveloot");
            return 0;
        }
        Game game = plugin.getGameManager().getGame(player);
        if (game == null) {
            Msg.err(player, "command.join-first");
            return 0;
        }
        int amount = 1;
        try {
            amount = IntegerArgumentType.getInteger(ctx, "amount");
        } catch (IllegalArgumentException ignored) {
        }
        LootTierDefinition fixedTier = null;
        String tierName = optionalString(ctx, "tier");
        if (tierName != null) {
            fixedTier = LootItem.tiers().get(tierName);
            if (fixedTier == null) {
                StringBuilder ids = new StringBuilder();
                for (LootTierDefinition def : LootItem.tiers().all()) ids.append(ids.isEmpty() ? "" : ", ").append(def.id());
                Msg.err(player, "command.unknown-tier", "tiers", ids);
                return 0;
            }
        }
        for (int i = 0; i < amount; i++) {
            LootTierDefinition tier = fixedTier != null ? fixedTier : LootItem.tiers().rollAny(ThreadLocalRandom.current());
            if (tier != null) player.getInventory().addItem(LootItem.create(tier, game.getMatchId()));
        }
        Msg.ok(player, "command.gave-loot", "amount", amount);
        return Command.SINGLE_SUCCESS;
    }

    // ---- arena setup commands ----

    private LiteralArgumentBuilder<CommandSourceStack> arenaRoot() {
        return literal("arena")
                .requires(src -> src.getSender().hasPermission(ADMIN_ARENA))
                .then(literal("create")
                        .then(argument("name", StringArgumentType.word())
                                .executes(this::executeCreate)))
                .then(arenaOnly("setlobby", (player, arena) -> {
                    arena.setLobby(player.getLocation());
                    Msg.ok(player, "setup.lobby-set", "arena", arena.getName());
                }))
                .then(arenaOnly("setspectator", (player, arena) -> {
                    arena.setSpectator(player.getLocation());
                    Msg.ok(player, "setup.spectator-set", "arena", arena.getName());
                }))
                .then(arenaTeam("setspawn", (player, arena, team) -> {
                    arena.site(team).spawn = player.getLocation();
                    Msg.ok(player, "setup.spawn-set", "team", team.displayName(), "arena", arena.getName());
                }))
                .then(arenaTeam("adddock", (player, arena, team) -> {
                    Location loc = targetedChest(player);
                    if (loc == null) {
                        Msg.err(player, "setup.dock-look");
                        return;
                    }
                    arena.site(team).dockChests.add(loc);
                    Msg.ok(player, "setup.dock-added", "team", team.displayName(), "count", arena.site(team).dockChests.size());
                }))
                .then(arenaTeam("addvaultchest", (player, arena, team) -> {
                    Location loc = targetedChest(player);
                    if (loc == null) {
                        Msg.err(player, "setup.vault-chest-look");
                        return;
                    }
                    arena.site(team).vaultChests.add(loc);
                    Msg.ok(player, "setup.vault-chest-added", "team", team.displayName(), "count", arena.site(team).vaultChests.size());
                }))
                .then(arenaTeam("setvaultdoor", (player, arena, team) -> {
                    Location loc = targetedBlock(player);
                    if (loc == null) {
                        Msg.err(player, "setup.vault-door-look");
                        return;
                    }
                    arena.site(team).vaultDoor = loc;
                    Msg.ok(player, "setup.vault-door-set", "team", team.displayName(), "arena", arena.getName());
                }))
                .then(arenaTeam("setgolemidle", (player, arena, team) -> {
                    arena.site(team).golemIdle = player.getLocation();
                    Msg.ok(player, "setup.golem-idle-set", "team", team.displayName());
                }))
                .then(arenaTeam("addwaypoint", (player, arena, team) -> {
                    arena.site(team).waypoints.add(player.getLocation());
                    Msg.ok(player, "setup.waypoint-added", "team", team.displayName(), "number", arena.site(team).waypoints.size());
                }))
                .then(addLootCommand())
                .then(arenaOnly("addrelic", (player, arena) -> {
                    arena.getRelicPoints().add(player.getLocation());
                    Msg.ok(player, "setup.relic-added", "count", arena.getRelicPoints().size());
                }))
                .then(arenaOnly("setbounds1", (player, arena) -> {
                    arena.setBound1(player.getLocation());
                    Msg.ok(player, "setup.bounds-1");
                }))
                .then(arenaOnly("setbounds2", (player, arena) -> {
                    arena.setBound2(player.getLocation());
                    Msg.ok(player, "setup.bounds-2");
                }))
                .then(arenaOnly("validate", (player, arena) -> {
                    for (ArenaCheck check : arena.report()) {
                        String key = switch (check.level()) {
                            case OK -> "command.check-ok";
                            case WARN -> "command.check-warn";
                            case ERROR -> "command.check-fail";
                        };
                        Msg.info(player, key, "text", Msg.word(player, check.text().key(), check.text().args()));
                    }
                    if (arena.validate().isEmpty()) Msg.ok(player, "setup.ready", "arena", arena.getName());
                }))
                .then(arenaTeam("setshop", (player, arena, team) -> {
                    arena.site(team).shop = player.getLocation();
                    Msg.ok(player, "setup.shop-set", "team", team.displayName(), "arena", arena.getName());
                }))
                .then(arenaTeam("setbase1", (player, arena, team) -> {
                    arena.site(team).baseCorner1 = player.getLocation();
                    Msg.ok(player, "setup.region-corner-set", "region", "base", "n", 1, "team", team.displayName(), "arena", arena.getName());
                }))
                .then(arenaTeam("setbase2", (player, arena, team) -> {
                    arena.site(team).baseCorner2 = player.getLocation();
                    Msg.ok(player, "setup.region-corner-set", "region", "base", "n", 2, "team", team.displayName(), "arena", arena.getName());
                }))
                .then(arenaTeam("setvaultregion1", (player, arena, team) -> {
                    arena.site(team).vaultCorner1 = player.getLocation();
                    Msg.ok(player, "setup.region-corner-set", "region", "vault", "n", 1, "team", team.displayName(), "arena", arena.getName());
                }))
                .then(arenaTeam("setvaultregion2", (player, arena, team) -> {
                    arena.site(team).vaultCorner2 = player.getLocation();
                    Msg.ok(player, "setup.region-corner-set", "region", "vault", "n", 2, "team", team.displayName(), "arena", arena.getName());
                }))
                .then(arenaOnly("snapshot", (player, arena) -> {
                    ArenaSnapshot snapshot = ArenaSnapshot.capture(arena);
                    if (snapshot == null) {
                        Msg.err(player, "setup.snapshot-no-bounds");
                        return;
                    }
                    try {
                        snapshot.write(plugin.getArenaManager().snapshotFile(arena));
                    } catch (IOException ex) {
                        Msg.err(player, "setup.snapshot-failed", "reason", ex.getMessage());
                        return;
                    }
                    Msg.ok(player, "setup.snapshot-saved", "arena", arena.getName(), "blocks", snapshot.blockCount());
                }))
                .then(arenaOnly("paste", (player, arena) -> {
                    File file = plugin.getArenaManager().snapshotFile(arena);
                    if (!file.exists()) {
                        Msg.err(player, "setup.paste-no-snapshot", "arena", arena.getName());
                        return;
                    }
                    try {
                        ArenaSnapshot snapshot = ArenaSnapshot.read(file);
                        int perTick = Math.max(1000, plugin.settings().getInt("reset.snapshot.blocks-per-tick", 20000));
                        new SnapshotRestoreTask(snapshot, perTick).runTaskTimer(plugin, 1L, 1L);
                        Msg.ok(player, "setup.paste-started", "arena", arena.getName(), "blocks", snapshot.blockCount());
                    } catch (IOException ex) {
                        Msg.err(player, "setup.snapshot-failed", "reason", ex.getMessage());
                    }
                }))
                .then(addPadCommand())
                .then(optionCommands())
                .then(arenaOnly("save", (player, arena) -> {
                    plugin.getArenaManager().save(arena);
                    Msg.ok(player, "setup.saved", "arena", arena.getName());
                }))
                .then(arenaOnly("enable", (player, arena) -> {
                    List<ArenaCheck> issues = arena.validate();
                    if (!issues.isEmpty()) {
                        Msg.err(player, "setup.fix-before-enabling");
                        for (ArenaCheck issue : issues) Msg.err(player, issue.text());
                        return;
                    }
                    arena.setEnabled(true);
                    plugin.getArenaManager().save(arena);
                    Msg.ok(player, "setup.enabled", "arena", arena.getName());
                }));
    }

    /** /ch arena addloot [arena] [common|rare|cache] - the zone defaults to common. */
    private LiteralArgumentBuilder<CommandSourceStack> addLootCommand() {
        return literal("addloot")
                .then(argument("name", StringArgumentType.word())
                        .suggests(arenaSuggestions)
                        .executes(ctx -> runArenaOnly(ctx, (player, arena) -> addLootPoint(player, arena, Arena.LootZone.COMMON)))
                        .then(argument("tier", StringArgumentType.word())
                                .suggests(LOOT_ZONES)
                                .executes(ctx -> {
                                    Arena.LootZone zone;
                                    try {
                                        zone = Arena.LootZone.valueOf(StringArgumentType.getString(ctx, "tier").toUpperCase(Locale.ROOT));
                                    } catch (IllegalArgumentException ex) {
                                        Msg.err(ctx.getSource().getSender(), "setup.unknown-loot-tier");
                                        return 0;
                                    }
                                    return runArenaOnly(ctx, (player, arena) -> addLootPoint(player, arena, zone));
                                })));
    }

    /** /ch arena addpad [arena] [power] - marks the block you're standing in as a gust pad. */
    /** setpreset / setoption / clearoption / options: per-arena tweaks layered over config.yml (no player needed). */
    private LiteralArgumentBuilder<CommandSourceStack> optionCommands() {
        SuggestionProvider<CommandSourceStack> presets = (ctx, builder) -> {
            String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
            for (String name : plugin.getPresets().names()) if (name.startsWith(remaining)) builder.suggest(name);
            if ("none".startsWith(remaining)) builder.suggest("none");
            return builder.buildFuture();
        };
        return literal("options")
                .then(argument("name", StringArgumentType.word()).suggests(arenaSuggestions)
                        .executes(this::executeOptions)
                        .then(literal("preset")
                                .then(argument("preset", StringArgumentType.word()).suggests(presets)
                                        .executes(this::executeSetPreset)))
                        .then(literal("set")
                                .then(argument("path", StringArgumentType.word())
                                        .then(argument("value", StringArgumentType.greedyString())
                                                .executes(this::executeSetOption))))
                        .then(literal("clear")
                                .then(argument("path", StringArgumentType.word())
                                        .executes(this::executeClearOption))));
    }

    private int executeOptions(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        Arena arena = requireArena(sender, StringArgumentType.getString(ctx, "name"));
        if (arena == null) return 0;
        Msg.info(sender, "options.header", "arena", arena.getName(), "preset", arena.getPreset() == null ? "-" : arena.getPreset());
        var values = arena.getOverrides().getValues(true);
        if (values.isEmpty()) Msg.info(sender, "options.none");
        values.forEach((path, value) -> Msg.info(sender, "options.line", "path", path, "value", String.valueOf(value)));
        return Command.SINGLE_SUCCESS;
    }

    private int executeSetPreset(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        Arena arena = requireArena(sender, StringArgumentType.getString(ctx, "name"));
        if (arena == null) return 0;
        String preset = StringArgumentType.getString(ctx, "preset");
        if (preset.equalsIgnoreCase("none")) {
            arena.setPreset(null);
        } else if (plugin.getPresets().get(preset) == null) {
            Msg.err(sender, "options.preset-unknown", "preset", preset, "presets", String.join(", ", plugin.getPresets().names()));
            return 0;
        } else {
            arena.setPreset(preset.toLowerCase(Locale.ROOT));
        }
        plugin.getArenaManager().save(arena);
        Msg.ok(sender, "options.preset-set", "arena", arena.getName(), "preset", arena.getPreset() == null ? "-" : arena.getPreset());
        return Command.SINGLE_SUCCESS;
    }

    private int executeSetOption(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        Arena arena = requireArena(sender, StringArgumentType.getString(ctx, "name"));
        if (arena == null) return 0;
        String path = StringArgumentType.getString(ctx, "path");
        String raw = StringArgumentType.getString(ctx, "value").trim();
        arena.getOverrides().set(path, parseOption(raw));
        plugin.getArenaManager().save(arena);
        Msg.ok(sender, "options.set", "arena", arena.getName(), "path", path, "value", raw);
        return Command.SINGLE_SUCCESS;
    }

    private int executeClearOption(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        Arena arena = requireArena(sender, StringArgumentType.getString(ctx, "name"));
        if (arena == null) return 0;
        String path = StringArgumentType.getString(ctx, "path");
        arena.getOverrides().set(path, null);
        plugin.getArenaManager().save(arena);
        Msg.ok(sender, "options.cleared", "arena", arena.getName(), "path", path);
        return Command.SINGLE_SUCCESS;
    }

    /** true/false, whole numbers and decimals become real values; anything else stays text. */
    private static Object parseOption(String raw) {
        if (raw.equalsIgnoreCase("true") || raw.equalsIgnoreCase("false")) return Boolean.parseBoolean(raw);
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ignored) {
            // not a whole number
        }
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException ignored) {
            return raw;
        }
    }

    private LiteralArgumentBuilder<CommandSourceStack> addPadCommand() {
        return literal("addpad")
                .then(argument("name", StringArgumentType.word())
                        .suggests(arenaSuggestions)
                        .executes(ctx -> runArenaOnly(ctx, (player, arena) -> addPad(player, arena, 1.4)))
                        .then(argument("power", DoubleArgumentType.doubleArg(0.2, 5.0))
                                .executes(ctx -> {
                                    double power = DoubleArgumentType.getDouble(ctx, "power");
                                    return runArenaOnly(ctx, (player, arena) -> addPad(player, arena, power));
                                })));
    }

    private void addPad(Player player, Arena arena, double power) {
        arena.getGustPads().add(new Arena.GustPad(player.getLocation().getBlock().getLocation(), power));
        Msg.ok(player, "setup.pad-added", "power", power, "count", arena.getGustPads().size());
    }

    private void addLootPoint(Player player, Arena arena, Arena.LootZone zone) {
        arena.getLootPoints(zone).add(player.getLocation());
        Msg.ok(player, "setup.loot-added", "zone", zone.name().toLowerCase(Locale.ROOT), "count", arena.getLootPoints(zone).size());
    }

    private int executeCreate(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            Msg.err(sender, "setup.in-game-only");
            return 0;
        }
        String name = StringArgumentType.getString(ctx, "name");
        if (plugin.getArenaManager().get(name) != null) {
            Msg.err(player, "setup.arena-exists", "arena", name);
            return 0;
        }
        Arena arena = plugin.getArenaManager().create(name);
        arena.setWorldName(player.getWorld().getName());
        Msg.ok(player, "setup.arena-created", "arena", name, "world", arena.getWorldName());
        return Command.SINGLE_SUCCESS;
    }

    @FunctionalInterface
    private interface ArenaAction {
        void run(Player player, Arena arena);
    }

    @FunctionalInterface
    private interface ArenaTeamAction {
        void run(Player player, Arena arena, Team team);
    }

    private LiteralArgumentBuilder<CommandSourceStack> arenaOnly(String name, ArenaAction action) {
        return literal(name)
                .then(argument("name", StringArgumentType.word())
                        .suggests(arenaSuggestions)
                        .executes(ctx -> runArenaOnly(ctx, action)));
    }

    private LiteralArgumentBuilder<CommandSourceStack> arenaTeam(String name, ArenaTeamAction action) {
        return literal(name)
                .then(argument("name", StringArgumentType.word())
                        .suggests(arenaSuggestions)
                        .then(argument("team", StringArgumentType.word())
                                .suggests(TeamSuggestions.TEAMS)
                                .executes(ctx -> runArenaTeam(ctx, action))));
    }

    private int runArenaOnly(CommandContext<CommandSourceStack> ctx, ArenaAction action) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            Msg.err(sender, "setup.in-game-only");
            return 0;
        }
        Arena arena = requireArena(sender, StringArgumentType.getString(ctx, "name"));
        if (arena == null) return 0;
        action.run(player, arena);
        return Command.SINGLE_SUCCESS;
    }

    private int runArenaTeam(CommandContext<CommandSourceStack> ctx, ArenaTeamAction action) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            Msg.err(sender, "setup.in-game-only");
            return 0;
        }
        Arena arena = requireArena(sender, StringArgumentType.getString(ctx, "name"));
        if (arena == null) return 0;
        Team team;
        try {
            team = Team.valueOf(StringArgumentType.getString(ctx, "team").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            Msg.err(sender, "setup.unknown-team", "team", StringArgumentType.getString(ctx, "team"));
            return 0;
        }
        action.run(player, arena, team);
        return Command.SINGLE_SUCCESS;
    }

    // ---- helpers ----

    private Arena requireArena(CommandSender sender, String name) {
        Arena arena = plugin.getArenaManager().get(name);
        if (arena == null) Msg.err(sender, "command.no-such-arena", "arena", name);
        return arena;
    }

    private Location targetedChest(Player player) {
        RayTraceResult result = player.rayTraceBlocks(6);
        if (result == null || result.getHitBlock() == null) return null;
        if (!(result.getHitBlock().getState() instanceof Chest)) return null;
        return result.getHitBlock().getLocation();
    }

    private Location targetedBlock(Player player) {
        RayTraceResult result = player.rayTraceBlocks(6);
        if (result == null || result.getHitBlock() == null) return null;
        return result.getHitBlock().getLocation();
    }

    private String optionalString(CommandContext<CommandSourceStack> ctx, String name) {
        try {
            return StringArgumentType.getString(ctx, name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
