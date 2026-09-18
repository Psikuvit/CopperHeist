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
import me.psikuvit.copperHeist.game.Game;
import me.psikuvit.copperHeist.game.GamePlayer;
import me.psikuvit.copperHeist.game.GameState;
import me.psikuvit.copperHeist.game.Team;
import me.psikuvit.copperHeist.loot.LootItem;
import me.psikuvit.copperHeist.loot.LootTierDefinition;
import me.psikuvit.copperHeist.role.RoleDefinition;
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
    private static final String ADMIN_RELOAD = "copperheist.admin.reload";

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
                    .then(literal("shop").executes(commands::executeShop))
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

    private int executeRole(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            Msg.err(sender, "Only players can pick a role.");
            return 0;
        }
        Game game = plugin.getGameManager().getGame(player);
        if (game == null) {
            Msg.err(player, "Join a match first.");
            return 0;
        }
        GamePlayer gp = game.getGamePlayer(player.getUniqueId());
        if (gp == null) return 0;

        RoleDefinition role = plugin.getRoleRegistry().get(StringArgumentType.getString(ctx, "role"));
        if (role == null) {
            StringBuilder ids = new StringBuilder();
            for (RoleDefinition def : plugin.getRoleRegistry().all()) ids.append(ids.isEmpty() ? "" : ", ").append(def.id());
            Msg.err(player, "Unknown role. Choose: " + ids + ".");
            return 0;
        }

        String error = game.getRoleService().trySetRole(gp, role);
        if (error != null) {
            Msg.err(player, error);
            return 0;
        }
        Msg.ok(player, "Role set to " + role.displayName() + (game.isActive() ? " - applies next respawn." : "."));
        return Command.SINGLE_SUCCESS;
    }

    private int executeRoleMenu(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            Msg.err(sender, "Only players can pick a role.");
            return 0;
        }
        Game game = plugin.getGameManager().getGame(player);
        if (game == null || game.getGamePlayer(player.getUniqueId()) == null) {
            Msg.err(player, "Join a match first.");
            return 0;
        }
        player.openInventory(game.getRoleService().buildRoleMenu());
        return Command.SINGLE_SUCCESS;
    }

    private int executeShop(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            Msg.err(sender, "Only players can open the shop.");
            return 0;
        }
        if (plugin.getGameManager().getGame(player) == null) {
            Msg.err(player, "Join a match first.");
            return 0;
        }
        Game shopGame = plugin.getGameManager().getGame(player);
        GamePlayer shopGp = shopGame.getGamePlayer(player.getUniqueId());
        if (shopGame.isActive() && shopGp != null && !shopGame.isNearOwnSpawn(player, shopGp)) {
            Msg.err(player, "Use your team's shop NPC, or go back near your spawn to open the shop.");
            return 0;
        }
        player.openInventory(plugin.getShopService().buildMenu());
        return Command.SINGLE_SUCCESS;
    }

    private final SuggestionProvider<CommandSourceStack> tierSuggestions = (ctx, builder) -> {
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        for (LootTierDefinition tier : LootItem.tiers().all()) {
            if (tier.id().startsWith(remaining)) builder.suggest(tier.id());
        }
        return builder.buildFuture();
    };

    private static final SuggestionProvider<CommandSourceStack> PHASE_SUGGESTIONS = (ctx, builder) -> {
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        for (GameState phase : new GameState[]{GameState.SETUP, GameState.COLLECTION, GameState.HEIST, GameState.FINAL_RUSH}) {
            String name = phase.name().toLowerCase(Locale.ROOT);
            if (name.startsWith(remaining)) builder.suggest(name);
        }
        return builder.buildFuture();
    };

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
        Msg.ok(sender, "Reloaded config, messages, scoreboard, shop, roles and lobby kit (arenas and running matches are untouched).");
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
            Msg.err(sender, "Unknown phase. Use setup, collection, heist or final_rush.");
            return 0;
        }
        Game game = plugin.getGameManager().peek(arena);
        if (game == null || !game.forcePhase(phase)) {
            Msg.err(sender, "That arena has no running match, or that isn't a match phase.");
            return 0;
        }
        Msg.ok(sender, "Jumping " + arena.getName() + " to " + phase.name() + " on the next tick.");
        return Command.SINGLE_SUCCESS;
    }

    private int executeSpawnRelic(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        Arena arena = requireArena(sender, StringArgumentType.getString(ctx, "arena"));
        if (arena == null) return 0;
        Game game = plugin.getGameManager().peek(arena);
        if (game == null || !game.isActive() || !game.getRelicManager().forceSpawn()) {
            Msg.err(sender, "No running match there, or a relic is already in play.");
            return 0;
        }
        Msg.ok(sender, "Spawned the relic in " + arena.getName() + ".");
        return Command.SINGLE_SUCCESS;
    }

    private int executeSpectate(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            Msg.err(sender, "Only players can spectate.");
            return 0;
        }
        String error = plugin.getGameManager().spectate(player, StringArgumentType.getString(ctx, "arena"));
        if (error != null) {
            Msg.err(player, error);
            return 0;
        }
        Msg.ok(player, "Spectating - use /ch leave to stop.");
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
        LootTierDefinition fixedTier = null;
        String tierName = optionalString(ctx, "tier");
        if (tierName != null) {
            fixedTier = LootItem.tiers().get(tierName);
            if (fixedTier == null) {
                StringBuilder ids = new StringBuilder();
                for (LootTierDefinition def : LootItem.tiers().all()) ids.append(ids.isEmpty() ? "" : ", ").append(def.id());
                Msg.err(player, "Unknown tier. Use one of: " + ids + ".");
                return 0;
            }
        }
        for (int i = 0; i < amount; i++) {
            LootTierDefinition tier = fixedTier != null ? fixedTier : LootItem.tiers().rollAny(ThreadLocalRandom.current());
            if (tier != null) player.getInventory().addItem(LootItem.create(tier, game.getMatchId()));
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
                .then(arenaOnly("setspectator", (player, arena) -> {
                    arena.setSpectator(player.getLocation());
                    Msg.ok(player, "Set spectator point for " + arena.getName() + ".");
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
                .then(arenaTeam("setvaultdoor", (player, arena, team) -> {
                    Location loc = targetedBlock(player);
                    if (loc == null) {
                        Msg.err(player, "Look directly at the vault door block to mark it.");
                        return;
                    }
                    arena.site(team).vaultDoor = loc;
                    Msg.ok(player, "Set " + team.displayName() + " vault door for " + arena.getName() + ".");
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
                .then(addLootCommand())
                .then(arenaOnly("addrelic", (player, arena) -> {
                    arena.getRelicPoints().add(player.getLocation());
                    Msg.ok(player, "Added relic point (" + arena.getRelicPoints().size() + " total).");
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
                    for (String line : arena.report()) Msg.send(player, line);
                    if (arena.validate().isEmpty()) Msg.ok(player, arena.getName() + " is ready to enable.");
                }))
                .then(arenaTeam("setshop", (player, arena, team) -> {
                    arena.site(team).shop = player.getLocation();
                    Msg.ok(player, "Set " + team.displayName() + " shop NPC point for " + arena.getName() + ".");
                }))
                .then(addPadCommand())
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

    private static final SuggestionProvider<CommandSourceStack> LOOT_TIERS = (ctx, builder) -> {
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        for (Arena.LootZone zone : Arena.LootZone.values()) {
            String name = zone.name().toLowerCase(Locale.ROOT);
            if (name.startsWith(remaining)) builder.suggest(name);
        }
        return builder.buildFuture();
    };

    /** /ch arena addloot <arena> [common|rare|cache] - tier defaults to common. */
    private LiteralArgumentBuilder<CommandSourceStack> addLootCommand() {
        return literal("addloot")
                .then(argument("name", StringArgumentType.word())
                        .suggests(arenaSuggestions)
                        .executes(ctx -> runArenaOnly(ctx, (player, arena) -> addLootPoint(player, arena, Arena.LootZone.COMMON)))
                        .then(argument("tier", StringArgumentType.word())
                                .suggests(LOOT_TIERS)
                                .executes(ctx -> {
                                    Arena.LootZone zone;
                                    try {
                                        zone = Arena.LootZone.valueOf(StringArgumentType.getString(ctx, "tier").toUpperCase(Locale.ROOT));
                                    } catch (IllegalArgumentException ex) {
                                        Msg.err(ctx.getSource().getSender(), "Unknown tier. Use common, rare or cache.");
                                        return 0;
                                    }
                                    return runArenaOnly(ctx, (player, arena) -> addLootPoint(player, arena, zone));
                                })));
    }

    /** /ch arena addpad <arena> [power] - marks the block you're standing in as a gust pad. */
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
        Msg.ok(player, "Added gust pad (power " + power + ", " + arena.getGustPads().size() + " total).");
    }

    private void addLootPoint(Player player, Arena arena, Arena.LootZone zone) {
        arena.getLootPoints(zone).add(player.getLocation());
        Msg.ok(player, "Added " + zone.name().toLowerCase(Locale.ROOT) + " loot point ("
                + arena.getLootPoints(zone).size() + " total).");
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
