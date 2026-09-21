package me.psikuvit.copperHeist.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.npc.NavigatorService.Navigator;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;

import static io.papermc.paper.command.brigadier.Commands.argument;
import static io.papermc.paper.command.brigadier.Commands.literal;

/**
 * /ch navigator: place the hub NPCs that open the arena picker.
 * <pre>
 *   create &lt;id&gt; [look]    a navigator where you stand (again with the same id to move it)
 *   look &lt;id&gt; &lt;look&gt;    change how it looks (a look from npcs.yml)
 *   remove &lt;id&gt;
 *   list
 * </pre>
 */
public class NavigatorCommands {

    private final CopperHeist plugin;

    public NavigatorCommands(CopperHeist plugin) {
        this.plugin = plugin;
    }

    public LiteralArgumentBuilder<CommandSourceStack> root(String permission) {
        SuggestionProvider<CommandSourceStack> looks = (ctx, builder) -> {
            for (String id : plugin.getNpcLooks().ids()) builder.suggest(id);
            return builder.buildFuture();
        };
        SuggestionProvider<CommandSourceStack> navigators = (ctx, builder) -> {
            for (Navigator navigator : plugin.getNavigators().all()) builder.suggest(navigator.id());
            return builder.buildFuture();
        };
        return literal("navigator")
                .requires(src -> src.getSender().hasPermission(permission))
                .then(literal("create")
                        .then(argument("id", StringArgumentType.word()).suggests(navigators)
                                .executes(ctx -> create(ctx, null))
                                .then(argument("look", StringArgumentType.word()).suggests(looks)
                                        .executes(ctx -> create(ctx, StringArgumentType.getString(ctx, "look"))))))
                .then(literal("look")
                        .then(argument("id", StringArgumentType.word()).suggests(navigators)
                                .then(argument("look", StringArgumentType.word()).suggests(looks).executes(this::look))))
                .then(literal("remove")
                        .then(argument("id", StringArgumentType.word()).suggests(navigators).executes(this::remove)))
                .then(literal("list").executes(this::list));
    }

    private int create(CommandContext<CommandSourceStack> ctx, String look) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            Msg.err(sender, "setup.in-game-only");
            return 0;
        }
        if (look != null && plugin.getNpcLooks().get(look) == null) {
            Msg.err(sender, "npc.unknown-look", "look", look, "looks", String.join(", ", plugin.getNpcLooks().ids()));
            return 0;
        }
        String id = StringArgumentType.getString(ctx, "id");
        plugin.getNavigators().create(id, player.getLocation(), look);
        Msg.ok(sender, "npc.navigator-created", "id", id.toLowerCase(Locale.ROOT));
        return Command.SINGLE_SUCCESS;
    }

    private int look(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        String look = StringArgumentType.getString(ctx, "look");
        if (plugin.getNpcLooks().get(look) == null) {
            Msg.err(sender, "npc.unknown-look", "look", look, "looks", String.join(", ", plugin.getNpcLooks().ids()));
            return 0;
        }
        String id = StringArgumentType.getString(ctx, "id");
        if (!plugin.getNavigators().setLook(id, look)) {
            Msg.err(sender, "npc.no-such-navigator", "id", id);
            return 0;
        }
        Msg.ok(sender, "npc.navigator-look", "id", id.toLowerCase(Locale.ROOT), "look", look.toLowerCase(Locale.ROOT));
        return Command.SINGLE_SUCCESS;
    }

    private int remove(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        String id = StringArgumentType.getString(ctx, "id");
        if (!plugin.getNavigators().remove(id)) {
            Msg.err(sender, "npc.no-such-navigator", "id", id);
            return 0;
        }
        Msg.ok(sender, "npc.navigator-removed", "id", id.toLowerCase(Locale.ROOT));
        return Command.SINGLE_SUCCESS;
    }

    private int list(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        var all = plugin.getNavigators().all();
        if (all.isEmpty()) {
            Msg.info(sender, "npc.navigator-none");
            return Command.SINGLE_SUCCESS;
        }
        for (Navigator navigator : all) {
            var at = navigator.location();
            Msg.info(sender, "npc.navigator-line", "id", navigator.id(), "look", navigator.look() == null ? "default" : navigator.look(),
                    "world", at.getWorld().getName(), "x", at.getBlockX(), "y", at.getBlockY(), "z", at.getBlockZ());
        }
        return Command.SINGLE_SUCCESS;
    }
}
