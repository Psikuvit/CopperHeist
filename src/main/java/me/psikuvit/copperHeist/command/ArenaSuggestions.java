package me.psikuvit.copperHeist.command;

import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.arena.Arena;

import java.util.Locale;

public final class ArenaSuggestions {

    private ArenaSuggestions() {
    }

    public static SuggestionProvider<CommandSourceStack> of(CopperHeist plugin) {
        return (ctx, builder) -> {
            String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
            for (Arena arena : plugin.getArenaManager().all()) {
                if (arena.getName().toLowerCase(Locale.ROOT).startsWith(remaining)) {
                    builder.suggest(arena.getName());
                }
            }
            return builder.buildFuture();
        };
    }
}
