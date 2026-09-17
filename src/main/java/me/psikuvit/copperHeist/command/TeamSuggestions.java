package me.psikuvit.copperHeist.command;

import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.papermc.paper.command.brigadier.CommandSourceStack;

import java.util.Locale;

public final class TeamSuggestions {

    public static final SuggestionProvider<CommandSourceStack> TEAMS = (ctx, builder) -> {
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        for (String team : new String[]{"copper", "iron"}) {
            if (team.startsWith(remaining)) builder.suggest(team);
        }
        return builder.buildFuture();
    };

    private TeamSuggestions() {
    }
}
