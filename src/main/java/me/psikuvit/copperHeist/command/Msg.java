package me.psikuvit.copperHeist.command;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;

/** CommandSender#sendMessage(String) does not parse MiniMessage tags - always go through this instead. */
public final class Msg {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private Msg() {
    }

    public static void send(CommandSender sender, String miniMessageText) {
        sender.sendMessage(MINI_MESSAGE.deserialize(miniMessageText));
    }

    public static void ok(CommandSender sender, String text) {
        send(sender, "<green>" + text + "</green>");
    }

    public static void err(CommandSender sender, String text) {
        send(sender, "<red>" + text + "</red>");
    }
}
