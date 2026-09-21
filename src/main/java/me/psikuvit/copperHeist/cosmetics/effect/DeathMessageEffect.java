package me.psikuvit.copperHeist.cosmetics.effect;

import me.psikuvit.copperHeist.cosmetics.CosmeticCategory;
import me.psikuvit.copperHeist.cosmetics.CosmeticDefinition;
import me.psikuvit.copperHeist.cosmetics.EffectContext;
import me.psikuvit.copperHeist.cosmetics.EffectProvider;
import me.psikuvit.copperHeist.ui.Theme;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.Set;

/**
 * A custom message announced when the owner defeats someone ({@code effect: death-message}), in place of the plain death message. Params:
 * <pre>
 *   message: "&lt;accent&gt;{killer}&lt;/accent&gt; &lt;muted&gt;pulled a heist on&lt;/muted&gt; &lt;text&gt;{victim}"   MiniMessage; {killer} and {victim} are the players' names
 * </pre>
 * It is shown to the players of the match. In a menu preview the previewing player is the killer and the victim is "Someone".
 */
public class DeathMessageEffect implements EffectProvider {

    @Override
    public String id() {
        return "death-message";
    }

    @Override
    public Set<CosmeticCategory> categories() {
        return Set.of(CosmeticCategory.DEATH_MESSAGE);
    }

    @Override
    public void validate(CosmeticDefinition cosmetic) {
        String message = String.valueOf(cosmetic.params().getOrDefault("message", ""));
        if (message.isBlank()) throw new IllegalArgumentException("params.message is required");
        if (!message.contains("{victim}")) throw new IllegalArgumentException("params.message must mention {victim}");
    }

    @Override
    public void play(EffectContext context) {
        String victim = context.entity() instanceof Player player ? player.getName() : "Someone";
        Component message = render(String.valueOf(context.cosmetic().params().getOrDefault("message", "")), context.owner().getName(), victim);
        for (Player viewer : context.viewers()) viewer.sendMessage(message);
    }

    /** Fills in the names and parses the MiniMessage. Names cannot contain tag characters, so they can't inject formatting. */
    static Component render(String template, String killer, String victim) {
        return Theme.mini().deserialize(template.replace("{killer}", killer).replace("{victim}", victim));
    }

    /** The template with names filled in, as plain MiniMessage text (for tests). */
    static String fill(String template, String killer, String victim) {
        return template.replace("{killer}", killer).replace("{victim}", victim);
    }
}
