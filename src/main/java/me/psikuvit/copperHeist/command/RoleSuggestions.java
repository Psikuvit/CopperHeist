package me.psikuvit.copperHeist.command;

import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.psikuvit.copperHeist.CopperHeist;
import me.psikuvit.copperHeist.role.RoleDefinition;

import java.util.Locale;

public final class RoleSuggestions {

    private RoleSuggestions() {
    }

    public static SuggestionProvider<CommandSourceStack> of(CopperHeist plugin) {
        return (ctx, builder) -> {
            String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
            for (RoleDefinition role : plugin.getRoleRegistry().all()) {
                if (role.id().startsWith(remaining)) builder.suggest(role.id());
            }
            return builder.buildFuture();
        };
    }
}
