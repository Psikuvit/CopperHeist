package me.psikuvit.copperHeist.command;

import me.psikuvit.copperHeist.ui.MessageService;
import me.psikuvit.copperHeist.ui.Text;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;

/** Command feedback in the sender's language. Every text is a lang key - no English lives in the command code. */
public final class Msg {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static MessageService messages;

    private Msg() {
    }

    public static void init(MessageService service) {
        messages = service;
    }

    public static void ok(CommandSender sender, String key, Object... placeholders) {
        sender.sendMessage(messages.ok(sender, key, placeholders));
    }

    public static void err(CommandSender sender, String key, Object... placeholders) {
        sender.sendMessage(messages.err(sender, key, placeholders));
    }

    public static void err(CommandSender sender, Text text) {
        sender.sendMessage(messages.err(sender, text));
    }

    /** A message that carries its own colors, straight from the lang file. */
    public static void info(CommandSender sender, String key, Object... placeholders) {
        sender.sendMessage(messages.get(sender, key, placeholders));
    }

    /** The plain text of a key (for embedding a translated word inside another message). */
    public static String word(CommandSender sender, String key, Object... placeholders) {
        return messages.rawFor(sender, key, placeholders);
    }

    public static void send(CommandSender sender, String miniMessageText) {
        sender.sendMessage(MINI_MESSAGE.deserialize(miniMessageText));
    }
}
