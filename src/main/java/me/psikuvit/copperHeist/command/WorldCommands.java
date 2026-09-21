package me.psikuvit.copperHeist.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.world.VoidWorlds;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import static io.papermc.paper.command.brigadier.Commands.argument;
import static io.papermc.paper.command.brigadier.Commands.literal;

/**
 * /ch world: empty worlds to build arenas in.
 * <pre>
 *   create &lt;name&gt;   makes an empty world (or loads one this plugin made earlier)
 *   tp &lt;name&gt;       takes you to its spawn platform
 *   list            the empty worlds and whether they are loaded
 * </pre>
 */
public class WorldCommands {

    private final CopperHeist plugin;

    public WorldCommands(CopperHeist plugin) {
        this.plugin = plugin;
    }

    public LiteralArgumentBuilder<CommandSourceStack> root(String permission) {
        SuggestionProvider<CommandSourceStack> voidWorlds = (ctx, builder) -> {
            for (String name : VoidWorlds.names()) builder.suggest(name);
            return builder.buildFuture();
        };
        return literal("world")
                .requires(src -> src.getSender().hasPermission(permission))
                .then(literal("create")
                        .then(argument("name", StringArgumentType.word()).executes(this::create)))
                .then(literal("tp")
                        .then(argument("name", StringArgumentType.word()).suggests(voidWorlds).executes(this::teleport)))
                .then(literal("list").executes(this::list));
    }

    private int create(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        String name = StringArgumentType.getString(ctx, "name");
        if (!VoidWorlds.validName(name)) {
            Msg.err(sender, "world.bad-name");
            return 0;
        }
        boolean existed = Bukkit.getWorld(name) != null;
        World world = VoidWorlds.createOrLoad(plugin, name);
        if (world == null) {
            Msg.err(sender, "world.not-ours", "world", name);
            return 0;
        }
        Msg.ok(sender, existed ? "world.already-loaded" : "world.created", "world", name);
        return Command.SINGLE_SUCCESS;
    }

    private int teleport(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            Msg.err(sender, "setup.in-game-only");
            return 0;
        }
        String name = StringArgumentType.getString(ctx, "name");
        World world = VoidWorlds.createOrLoad(plugin, name);
        if (world == null) {
            Msg.err(sender, "world.unknown", "world", name);
            return 0;
        }
        player.teleport(world.getSpawnLocation().add(0.5, 0, 0.5));
        Msg.ok(sender, "world.teleported", "world", name);
        return Command.SINGLE_SUCCESS;
    }

    private int list(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        var names = VoidWorlds.names();
        if (names.isEmpty()) {
            Msg.info(sender, "world.none");
            return Command.SINGLE_SUCCESS;
        }
        for (String name : names) {
            Msg.info(sender, "world.line", "world", name, "state", Bukkit.getWorld(name) != null ? "loaded" : "not loaded");
        }
        return Command.SINGLE_SUCCESS;
    }
}
