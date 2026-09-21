package me.psikuvit.copperHeist.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.menu.PartyMenu;
import me.psikuvit.copperHeist.party.PartyInfo;
import me.psikuvit.copperHeist.party.PartyService;
import me.psikuvit.copperHeist.party.PartyService.Departure;
import me.psikuvit.copperHeist.ui.Theme;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static io.papermc.paper.command.brigadier.Commands.argument;
import static io.papermc.paper.command.brigadier.Commands.literal;

/**
 * /party (also /ch party) and /pc: the built-in party. While another provider (the Parties plugin, an addon, or none) owns parties these
 * only say so. The rules live in PartyService; this class turns its results into messages.
 */
public class PartyCommands {

    public static final String PERMISSION = "copperheist.party";

    private final CopperHeist plugin;

    public PartyCommands(CopperHeist plugin) {
        this.plugin = plugin;
    }

    /** The command tree under {@code name}: used as /party and as /ch party. */
    public LiteralArgumentBuilder<CommandSourceStack> tree(String name) {
        SuggestionProvider<CommandSourceStack> players = (ctx, builder) -> {
            String typed = builder.getRemaining().toLowerCase();
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.getName().toLowerCase().startsWith(typed)) builder.suggest(online.getName());
            }
            return builder.buildFuture();
        };
        return literal(name)
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::menu)
                .then(literal("invite").then(argument("player", StringArgumentType.word()).suggests(players).executes(this::invite)))
                .then(literal("accept").executes(this::accept))
                .then(literal("deny").executes(this::deny))
                .then(literal("leave").executes(this::leave))
                .then(literal("kick").then(argument("player", StringArgumentType.word()).suggests(players).executes(this::kick)))
                .then(literal("promote").then(argument("player", StringArgumentType.word()).suggests(players).executes(this::promote)))
                .then(literal("disband").executes(this::disband))
                .then(literal("list").executes(this::list))
                .then(literal("chat").then(argument("message", StringArgumentType.greedyString()).executes(this::chat)));
    }

    /** /pc <message>: party chat without typing /party chat. */
    public LiteralArgumentBuilder<CommandSourceStack> chatShortcut() {
        return literal("pc")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(argument("message", StringArgumentType.greedyString()).executes(this::chat));
    }

    // ---- guards ----

    /** The built-in party state, or null after telling the sender why it is not in use (or that they're not a player). */
    private PartyService service(CommandSender sender) {
        PartyService service = plugin.getParties().builtIn();
        if (service != null) return service;
        var provider = plugin.getParties().provider();
        if (provider.name().equals("none")) Msg.err(sender, "party.disabled");
        else Msg.err(sender, "party.managed-elsewhere", "provider", provider.name());
        return null;
    }

    private Player player(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (sender instanceof Player player) return player;
        Msg.err(sender, "setup.in-game-only");
        return null;
    }

    private Player target(Player sender, CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "player");
        Player target = Bukkit.getPlayerExact(name);
        if (target == null) Msg.err(sender, "party.player-not-found", "player", name);
        return target;
    }

    // ---- commands ----

    private int menu(CommandContext<CommandSourceStack> ctx) {
        Player player = player(ctx);
        if (player == null || service(player) == null) return 0;
        plugin.getMenus().open(player, new PartyMenu(plugin, player));
        return Command.SINGLE_SUCCESS;
    }

    private int invite(CommandContext<CommandSourceStack> ctx) {
        Player player = player(ctx);
        PartyService service = player == null ? null : service(player);
        if (service == null) return 0;
        Player target = target(player, ctx);
        if (target == null) return 0;
        return invite(plugin, service, player, target) ? Command.SINGLE_SUCCESS : 0;
    }

    /** Sends an invite and the messages that go with it; false (after telling the inviter why) when it was refused. */
    public static boolean invite(CopperHeist plugin, PartyService service, Player inviter, Player target) {
        String key = switch (service.invite(inviter.getUniqueId(), target.getUniqueId())) {
            case OK -> null;
            case SELF -> "party.invite-self";
            case NOT_LEADER -> "party.not-leader";
            case TARGET_IN_PARTY -> "party.target-in-party";
            case PARTY_FULL -> "party.full";
            case ALREADY_INVITED -> "party.already-invited";
        };
        if (key != null) {
            Msg.err(inviter, key, "player", target.getName(), "max", plugin.settings().getInt("party.max-size", 4));
            return false;
        }
        Msg.ok(inviter, "party.invite-sent", "player", target.getName());
        target.sendMessage(plugin.getMessageService().get(target, "party.invite-received", "player", inviter.getName(),
                "seconds", plugin.settings().getInt("party.invite-seconds", 60)));
        return true;
    }

    private int accept(CommandContext<CommandSourceStack> ctx) {
        Player player = player(ctx);
        PartyService service = player == null ? null : service(player);
        if (service == null) return 0;
        Optional<UUID> inviter = service.inviterOf(player.getUniqueId());
        String key = switch (service.accept(player.getUniqueId())) {
            case OK -> null;
            case ALREADY_IN_PARTY -> "party.already-in-party";
            case NO_INVITE -> "party.no-invite";
            case EXPIRED -> "party.invite-expired";
            case PARTY_GONE -> "party.party-gone";
            case PARTY_FULL -> "party.full";
        };
        if (key != null) {
            Msg.err(player, key, "max", plugin.settings().getInt("party.max-size", 4));
            return 0;
        }
        Msg.ok(player, "party.joined", "leader", inviter.map(Bukkit::getOfflinePlayer).map(p -> String.valueOf(p.getName())).orElse("?"));
        service.partyOf(player.getUniqueId()).ifPresent(party -> tell(party.members(), player.getUniqueId(), "party.member-joined", "player", player.getName()));
        return Command.SINGLE_SUCCESS;
    }

    private int deny(CommandContext<CommandSourceStack> ctx) {
        Player player = player(ctx);
        PartyService service = player == null ? null : service(player);
        if (service == null) return 0;
        Optional<UUID> leader = service.deny(player.getUniqueId());
        if (leader.isEmpty()) {
            Msg.err(player, "party.no-invite");
            return 0;
        }
        Msg.ok(player, "party.denied");
        Player who = Bukkit.getPlayer(leader.get());
        if (who != null) Msg.info(who, "party.invite-denied", "player", player.getName());
        return Command.SINGLE_SUCCESS;
    }

    private int leave(CommandContext<CommandSourceStack> ctx) {
        Player player = player(ctx);
        PartyService service = player == null ? null : service(player);
        if (service == null) return 0;
        Departure departure = service.leave(player.getUniqueId());
        if (!departure.wasInParty()) {
            Msg.err(player, "party.not-in-party");
            return 0;
        }
        Msg.ok(player, "party.left");
        announceDeparture(plugin, player.getUniqueId(), player.getName(), departure, "party.member-left");
        return Command.SINGLE_SUCCESS;
    }

    /** Tells the people still in a party that someone left, that it ended, or who leads it now. */
    public static void announceDeparture(CopperHeist plugin, UUID who, String name, Departure departure, String memberKey) {
        if (!departure.wasInParty()) return;
        if (departure.disbanded()) {
            tell(plugin, departure.remaining(), null, "party.disbanded-by-leaving", "player", name);
            return;
        }
        tell(plugin, departure.remaining(), null, memberKey, "player", name);
        if (departure.newLeader() != null) {
            Player leader = Bukkit.getPlayer(departure.newLeader());
            tell(plugin, departure.remaining(), null, "party.new-leader", "player", leader != null ? leader.getName() : "?");
        }
    }

    private int kick(CommandContext<CommandSourceStack> ctx) {
        Player player = player(ctx);
        PartyService service = player == null ? null : service(player);
        if (service == null) return 0;
        Player target = target(player, ctx);
        if (target == null) return 0;
        return kick(plugin, service, player, target) ? Command.SINGLE_SUCCESS : 0;
    }

    public static boolean kick(CopperHeist plugin, PartyService service, Player leader, Player target) {
        Optional<PartyInfo> before = service.partyOf(leader.getUniqueId());
        String key = manageKey(service.kick(leader.getUniqueId(), target.getUniqueId()));
        if (key != null) {
            Msg.err(leader, key, "player", target.getName());
            return false;
        }
        Msg.ok(leader, "party.kicked", "player", target.getName());
        Msg.info(target, "party.kicked-notice", "leader", leader.getName());
        before.ifPresent(party -> tell(plugin, party.members(), leader.getUniqueId(), "party.member-kicked", "player", target.getName()));
        return true;
    }

    private int promote(CommandContext<CommandSourceStack> ctx) {
        Player player = player(ctx);
        PartyService service = player == null ? null : service(player);
        if (service == null) return 0;
        Player target = target(player, ctx);
        if (target == null) return 0;
        return promote(plugin, service, player, target) ? Command.SINGLE_SUCCESS : 0;
    }

    public static boolean promote(CopperHeist plugin, PartyService service, Player leader, Player target) {
        String key = manageKey(service.promote(leader.getUniqueId(), target.getUniqueId()));
        if (key != null) {
            Msg.err(leader, key, "player", target.getName());
            return false;
        }
        service.partyOf(target.getUniqueId()).ifPresent(party -> tell(plugin, party.members(), null, "party.new-leader", "player", target.getName()));
        return true;
    }

    private int disband(CommandContext<CommandSourceStack> ctx) {
        Player player = player(ctx);
        PartyService service = player == null ? null : service(player);
        if (service == null) return 0;
        return disband(plugin, service, player) ? Command.SINGLE_SUCCESS : 0;
    }

    public static boolean disband(CopperHeist plugin, PartyService service, Player leader) {
        Optional<PartyInfo> before = service.partyOf(leader.getUniqueId());
        String key = manageKey(service.disband(leader.getUniqueId()));
        if (key != null) {
            Msg.err(leader, key);
            return false;
        }
        before.ifPresent(party -> tell(plugin, party.members(), null, "party.disbanded", "player", leader.getName()));
        return true;
    }

    private int list(CommandContext<CommandSourceStack> ctx) {
        Player player = player(ctx);
        PartyService service = player == null ? null : service(player);
        if (service == null) return 0;
        Optional<PartyInfo> party = service.partyOf(player.getUniqueId());
        if (party.isEmpty()) {
            Msg.err(player, "party.not-in-party");
            return 0;
        }
        Msg.info(player, "party.list-header", "count", party.get().members().size(), "max", plugin.settings().getInt("party.max-size", 4));
        for (UUID id : party.get().members()) {
            Player online = Bukkit.getPlayer(id);
            String name = online != null ? online.getName() : String.valueOf(Bukkit.getOfflinePlayer(id).getName());
            Msg.info(player, party.get().isLeader(id) ? "party.list-leader" : "party.list-member", "player", name,
                    "status", Msg.word(player, online != null ? "party.online" : "party.offline"));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int chat(CommandContext<CommandSourceStack> ctx) {
        Player player = player(ctx);
        if (player == null) return 0;
        Optional<PartyInfo> party = plugin.getParties().provider().partyOf(player.getUniqueId()).filter(info -> info.members().size() > 1);
        if (party.isEmpty()) {
            Msg.err(player, "party.not-in-party");
            return 0;
        }
        String message = StringArgumentType.getString(ctx, "message");
        for (UUID id : party.get().members()) {
            Player member = Bukkit.getPlayer(id);
            if (member != null) member.sendMessage(plugin.getMessageService().get(member, "party.chat", "player", player.getName(),
                    "message", Theme.mini().escapeTags(message)));
        }
        return Command.SINGLE_SUCCESS;
    }

    // ---- helpers ----

    private static String manageKey(PartyService.ManageResult result) {
        return switch (result) {
            case OK -> null;
            case NOT_IN_PARTY -> "party.not-in-party";
            case NOT_LEADER -> "party.not-leader";
            case SELF -> "party.not-yourself";
            case TARGET_NOT_MEMBER -> "party.not-a-member";
        };
    }

    private void tell(Collection<UUID> members, UUID except, String key, Object... placeholders) {
        tell(plugin, members, except, key, placeholders);
    }

    private static void tell(CopperHeist plugin, Collection<UUID> members, UUID except, String key, Object... placeholders) {
        List<UUID> copy = List.copyOf(members);
        for (UUID id : copy) {
            if (id.equals(except)) continue;
            Player member = Bukkit.getPlayer(id);
            if (member != null) member.sendMessage(plugin.getMessageService().get(member, key, placeholders));
        }
    }
}
