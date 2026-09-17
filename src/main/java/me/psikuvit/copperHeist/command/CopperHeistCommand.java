package me.psikuvit.copperHeist.command;

import com.mojang.brigadier.Command;
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
import me.psikuvit.copperHeist.game.Game;
import me.psikuvit.copperHeist.game.Team;
import me.psikuvit.copperHeist.loot.LootItem;
import org.bukkit.Location;
import org.bukkit.block.Chest;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

import static io.papermc.paper.command.brigadier.Commands.argument;
import static io.papermc.paper.command.brigadier.Commands.literal;

/** /ch ... - see copper_heist_design.txt §13 for the command list this mirrors. */
public final class CopperHeistCommand {

    private static final String ADMIN_ARENA = "copperheist.admin.arena";
    private static final String ADMIN_DEBUG = "copperheist.admin.debug";

    private final CopperHeist plugin;
    private final SuggestionProvider<CommandSourceStack> arenaSuggestions;

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
                    .then(literal("list").executes(commands::executeList))
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
                                    .executes(commands::executeGiveLoot)))
                    .then(commands.arenaRoot());

            registrar.register(root.build(), "Copper Heist");
        });
    }

    // ---- player commands ----

    private int executeJoin(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            Msg.err(sender, "Only players can join a match.");
            return 0;
        }
        String arenaName = optionalString(ctx, "arena");
        String error = plugin.getGameManager().join(player, arenaName);
        if (error != null) {
            Msg.err(player, error);
            return 0;
        }
        return Command.SINGLE_SUCCESS;
    }

    private int executeLeave(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) return 0;
        if (plugin.getGameManager().getGame(player) == null) {
            Msg.err(player, "You're not in a match.");
            return 0;
        }
        plugin.getGameManager().leave(player);
        Msg.ok(player, "You left the arena.");
        return Command.SINGLE_SUCCESS;
    }

    private int executeList(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (plugin.getArenaManager().all().isEmpty()) {
            Msg.err(sender, "No arenas configured yet.");
            return Command.SINGLE_SUCCESS;
        }
        for (Arena arena : plugin.getArenaManager().all()) {
            Game game = plugin.getGameManager().peek(arena);
            String state = game != null ? game.getState().name() : "WAITING";
            int players = game != null ? game.totalPlayers() : 0;
            Msg.send(sender, "<gold>" + arena.getName() + "</gold> <gray>[" + (arena.isEnabled() ? "enabled" : "disabled")
                    + "]</gray> " + state + " (" + players + " players)");
        }
        return Command.SINGLE_SUCCESS;
    }

    private int executeForceStart(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        Arena arena = requireArena(sender, StringArgumentType.getString(ctx, "arena"));
        if (arena == null) return 0;
        plugin.getGameManager().getGame(arena).forceStart();
        Msg.ok(sender, "Forced start on " + arena.getName() + ".");
        return Command.SINGLE_SUCCESS;
    }

    private int executeForceStop(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        Arena arena = requireArena(sender, StringArgumentType.getString(ctx, "arena"));
        if (arena == null) return 0;
        plugin.getGameManager().getGame(arena).forceStop();
        Msg.ok(sender, "Forced stop on " + arena.getName() + ".");
        return Command.SINGLE_SUCCESS;
    }

    private int executeGiveLoot(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            Msg.err(sender, "Only players can be given loot.");
            return 0;
        }
        Game game = plugin.getGameManager().getGame(player);
        if (game == null) {
            Msg.err(player, "Join a match first.");
            return 0;
        }
        int amount = 1;
        try {
            amount = IntegerArgumentType.getInteger(ctx, "amount");
        } catch (IllegalArgumentException ignored) {
        }
        for (int i = 0; i < amount; i++) {
            LootItem.Tier tier = LootItem.randomTier(ThreadLocalRandom.current());
            player.getInventory().addItem(LootItem.create(tier, game.getMatchId()));
        }
        Msg.ok(player, "Gave you " + amount + " loot item(s).");
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
                    Msg.ok(player, "Set lobby for " + arena.getName() + ".");
                }))
                .then(arenaTeam("setspawn", (player, arena, team) -> {
                    arena.site(team).spawn = player.getLocation();
                    Msg.ok(player, "Set " + team.displayName() + " spawn for " + arena.getName() + ".");
                }))
                .then(arenaTeam("adddock", (player, arena, team) -> {
                    Location loc = targetedChest(player);
                    if (loc == null) {
                        Msg.err(player, "Look directly at a chest to mark it as a dock chest.");
                        return;
                    }
                    arena.site(team).dockChests.add(loc);
                    Msg.ok(player, "Added " + team.displayName() + " dock chest (" + arena.site(team).dockChests.size() + " total).");
                }))
                .then(arenaTeam("addvaultchest", (player, arena, team) -> {
                    Location loc = targetedChest(player);
                    if (loc == null) {
                        Msg.err(player, "Look directly at a chest to mark it as a vault chest.");
                        return;
                    }
                    arena.site(team).vaultChests.add(loc);
                    Msg.ok(player, "Added " + team.displayName() + " vault chest (" + arena.site(team).vaultChests.size() + " total).");
                }))
                .then(arenaTeam("setgolemidle", (player, arena, team) -> {
                    arena.site(team).golemIdle = player.getLocation();
                    Msg.ok(player, "Set " + team.displayName() + " golem idle point.");
                }))
                .then(arenaTeam("addwaypoint", (player, arena, team) -> {
                    arena.site(team).waypoints.add(player.getLocation());
                    Msg.ok(player, "Added " + team.displayName() + " waypoint #" + arena.site(team).waypoints.size()
                            + " (dock->vault order matters - last one should be at the vault).");
                }))
                .then(arenaOnly("addloot", (player, arena) -> {
                    arena.getLootPoints().add(player.getLocation());
                    Msg.ok(player, "Added loot point (" + arena.getLootPoints().size() + " total).");
                }))
                .then(arenaOnly("setbounds1", (player, arena) -> {
                    arena.setBound1(player.getLocation());
                    Msg.ok(player, "Set corner 1 of arena bounds.");
                }))
                .then(arenaOnly("setbounds2", (player, arena) -> {
                    arena.setBound2(player.getLocation());
                    Msg.ok(player, "Set corner 2 of arena bounds.");
                }))
                .then(arenaOnly("validate", (player, arena) -> {
                    List<String> issues = arena.validate();
                    if (issues.isEmpty()) {
                        Msg.ok(player, "All checks passed for " + arena.getName() + ".");
                        return;
                    }
                    Msg.err(player, issues.size() + " issue(s) with " + arena.getName() + ":");
                    for (String issue : issues) Msg.err(player, " - " + issue);
                }))
                .then(arenaOnly("save", (player, arena) -> {
                    plugin.getArenaManager().save(arena);
                    Msg.ok(player, "Saved " + arena.getName() + ".");
                }))
                .then(arenaOnly("enable", (player, arena) -> {
                    List<String> issues = arena.validate();
                    if (!issues.isEmpty()) {
                        Msg.err(player, "Fix these before enabling: " + String.join(", ", issues));
                        return;
                    }
                    arena.setEnabled(true);
                    plugin.getArenaManager().save(arena);
                    Msg.ok(player, arena.getName() + " is now enabled and joinable.");
                }));
    }

    private int executeCreate(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            Msg.err(sender, "Arena setup must be run in-game.");
            return 0;
        }
        String name = StringArgumentType.getString(ctx, "name");
        if (plugin.getArenaManager().get(name) != null) {
            Msg.err(player, "Arena '" + name + "' already exists.");
            return 0;
        }
        Arena arena = plugin.getArenaManager().create(name);
        arena.setWorldName(player.getWorld().getName());
        Msg.ok(player, "Created arena '" + name + "' in world " + arena.getWorldName() + ".");
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
            Msg.err(sender, "Arena setup must be run in-game.");
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
            Msg.err(sender, "Arena setup must be run in-game.");
            return 0;
        }
        Arena arena = requireArena(sender, StringArgumentType.getString(ctx, "name"));
        if (arena == null) return 0;
        Team team;
        try {
            team = Team.valueOf(StringArgumentType.getString(ctx, "team").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            Msg.err(sender, "Unknown team '" + StringArgumentType.getString(ctx, "team") + "'. Use copper or iron.");
            return 0;
        }
        action.run(player, arena, team);
        return Command.SINGLE_SUCCESS;
    }

    // ---- helpers ----

    private Arena requireArena(CommandSender sender, String name) {
        Arena arena = plugin.getArenaManager().get(name);
        if (arena == null) Msg.err(sender, "No arena named '" + name + "'.");
        return arena;
    }

    private Location targetedChest(Player player) {
        RayTraceResult result = player.rayTraceBlocks(6);
        if (result == null || result.getHitBlock() == null) return null;
        if (!(result.getHitBlock().getState() instanceof Chest)) return null;
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
