package me.psikuvit.copperHeist.ui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import org.bukkit.configuration.ConfigurationSection;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The plugin's look, in one place. Every message, menu, scoreboard line and book page is written with semantic MiniMessage
 * tags instead of fixed colors, and this class turns them into the colors set under {@code theme:} in config.yml:
 *
 * <pre>
 *   &lt;primary&gt; &lt;secondary&gt; &lt;accent&gt;   brand colors (headings, highlights, values)
 *   &lt;ok&gt; &lt;bad&gt; &lt;info&gt;                success, failure, neutral notices
 *   &lt;text&gt; &lt;muted&gt; &lt;dim&gt;               body text, secondary text, separators
 *   &lt;special&gt;                          relics and rare things
 *   &lt;copper&gt; &lt;iron&gt;                    the two teams
 * </pre>
 *
 * plus a few symbols: {@code <arrow> <check> <cross> <dot> <bar> <star> <line>}. Change the palette and every text in the plugin
 * follows, including per-language files, the scoreboard and the shop. Colors are read live, so /ch reload applies a new theme.
 */
public final class Theme {

    private static final Map<String, String> DEFAULTS = new LinkedHashMap<>();
    private static final Map<String, TextColor> colors = new LinkedHashMap<>();
    private static final MiniMessage MINI;

    static {
        DEFAULTS.put("primary", "#E8853A");
        DEFAULTS.put("secondary", "#B87333");
        DEFAULTS.put("accent", "#FFC857");
        DEFAULTS.put("ok", "#7BD88F");
        DEFAULTS.put("bad", "#F26D6D");
        DEFAULTS.put("info", "#6CB6FF");
        DEFAULTS.put("text", "#EDEDED");
        DEFAULTS.put("muted", "#A0A4AB");
        DEFAULTS.put("dim", "#5C6067");
        DEFAULTS.put("special", "#C792EA");
        DEFAULTS.put("copper", "#E8853A");
        DEFAULTS.put("iron", "#B8C4D0");
        for (Map.Entry<String, String> entry : DEFAULTS.entrySet()) colors.put(entry.getKey(), parse(entry.getValue(), NamedTextColor.WHITE));

        TagResolver.Builder resolvers = TagResolver.builder().resolver(StandardTags.defaults());
        for (String name : DEFAULTS.keySet()) {
            resolvers.resolver(TagResolver.resolver(name, (_, _) -> Tag.styling(colors.get(name))));
        }
        resolvers.resolver(TagResolver.resolver("arrow", Tag.selfClosingInserting(Component.text("▸"))));
        resolvers.resolver(TagResolver.resolver("check", Tag.selfClosingInserting(Component.text("✔"))));
        resolvers.resolver(TagResolver.resolver("cross", Tag.selfClosingInserting(Component.text("✘"))));
        resolvers.resolver(TagResolver.resolver("dot", Tag.selfClosingInserting(Component.text("•"))));
        resolvers.resolver(TagResolver.resolver("bar", Tag.selfClosingInserting(Component.text("│"))));
        resolvers.resolver(TagResolver.resolver("star", Tag.selfClosingInserting(Component.text("★"))));
        resolvers.resolver(TagResolver.resolver("line", Tag.selfClosingInserting(Component.text("━━━━━━━━━━"))));
        MINI = MiniMessage.builder().tags(resolvers.build()).build();
    }

    private Theme() {
    }

    /** A MiniMessage that understands the theme tags. Use this instead of MiniMessage.miniMessage(). */
    public static MiniMessage mini() {
        return MINI;
    }

    /** Reads theme.* from config.yml; anything missing or invalid keeps its default. */
    public static void load(ConfigurationSection section) {
        for (Map.Entry<String, String> entry : DEFAULTS.entrySet()) {
            String value = section == null ? null : section.getString(entry.getKey());
            colors.put(entry.getKey(), parse(value != null ? value : entry.getValue(), parse(entry.getValue(), NamedTextColor.WHITE)));
        }
    }

    public static TextColor color(String name) {
        return colors.getOrDefault(name, NamedTextColor.WHITE);
    }

    /** A hex color (#RRGGBB) or a vanilla color name; the fallback if it isn't one. */
    private static TextColor parse(String value, TextColor fallback) {
        if (value == null) return fallback;
        String text = value.trim();
        TextColor parsed = text.startsWith("#") ? TextColor.fromHexString(text) : NamedTextColor.NAMES.value(text.toLowerCase(Locale.ROOT));
        return parsed != null ? parsed : fallback;
    }
}
