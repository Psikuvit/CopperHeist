package me.psikuvit.copperHeist.ui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ThemeTest {

    private static final Pattern FIXED_COLOR = Pattern.compile("</?(gold|yellow|gray|grey|white|dark_gray|green|red|light_purple|blue|aqua)>");

    @Test
    void semanticTagsResolveToTheThemeColors() {
        Component text = Theme.mini().deserialize("<primary>hello</primary>");
        assertEquals("hello", PlainTextComponentSerializer.plainText().serialize(text));
        assertEquals(TextColor.fromHexString("#E8853A"), text.children().isEmpty() ? text.color() : text.children().get(0).color());
    }

    @Test
    void symbolsRenderAsCharacters() {
        assertEquals("✔", PlainTextComponentSerializer.plainText().serialize(Theme.mini().deserialize("<check>")));
        assertEquals("✘", PlainTextComponentSerializer.plainText().serialize(Theme.mini().deserialize("<cross>")));
        assertEquals("▸", PlainTextComponentSerializer.plainText().serialize(Theme.mini().deserialize("<arrow>")));
    }

    @Test
    void shippedFilesUseThemeTagsNotFixedColors() throws Exception {
        for (String name : List.of("lang/en.yml", "scoreboard.yml", "shop.yml", "roles.yml", "guide.yml")) {
            try (InputStream in = ThemeTest.class.getClassLoader().getResourceAsStream(name)) {
                assertNotNull(in, name);
                String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                assertFalse(FIXED_COLOR.matcher(text).find(), name + " still uses a fixed color tag - use a theme tag (<primary>, <muted>, <ok> ...)");
            }
        }
    }
}
