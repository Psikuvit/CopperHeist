package me.psikuvit.copperHeist.command;

import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.psikuvit.copperHeist.role.Role;

import java.util.Locale;

public final class RoleSuggestions {

    public static final SuggestionProvider<CommandSourceStack> ROLES = (ctx, builder) -> {
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        for (Role role : Role.values()) {
            String name = role.name().toLowerCase(Locale.ROOT);
            if (name.startsWith(remaining)) builder.suggest(name);
        }
        return builder.buildFuture();
    };

    private RoleSuggestions() {
    }
}
