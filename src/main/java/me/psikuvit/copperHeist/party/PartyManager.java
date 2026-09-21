package me.psikuvit.copperHeist.party;

import me.psikuvit.copperHeist.CopperHeist;
import org.bukkit.Bukkit;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * The party provider that is in use, chosen by {@code party.provider}: {@code auto} (Parties when it is installed, else the built-in
 * system), {@code builtin}, {@code parties}, {@code none}, or an id another plugin registered. The match code asks this for parties and
 * never cares who manages them; the built-in commands and menu work only while the built-in provider is the one in use.
 */
public class PartyManager {

    private final CopperHeist plugin;
    private final PartyService service;
    private PartyProvider provider = new NonePartyProvider();

    public PartyManager(CopperHeist plugin) {
        this.plugin = plugin;
        this.service = new PartyService(System::currentTimeMillis,
                () -> Math.max(2, plugin.settings().getInt("party.max-size", 4)),
                () -> Math.max(5, plugin.settings().getInt("party.invite-seconds", 60)));
    }

    /** Picks the provider from config (also used by /ch reload). A provider that fails to start falls back to the built-in one. */
    public void load() {
        String id = plugin.settings().getString("party.provider", "auto").toLowerCase(Locale.ROOT);
        if (id.equals("auto")) id = Bukkit.getPluginManager().isPluginEnabled("Parties") ? "parties" : "builtin";
        try {
            provider = plugin.providers().party().resolve(id);
        } catch (LinkageError | RuntimeException ex) {
            plugin.getLogger().warning("Party provider '" + id + "' could not start (" + ex + ") - using the built-in party system.");
            provider = new BuiltInPartyProvider(service);
        }
        plugin.getLogger().info("Parties: using " + provider.name() + ".");
    }

    public PartyProvider provider() {
        return provider;
    }

    /** The built-in party state, or null while another provider (or none) owns parties. */
    public PartyService builtIn() {
        return provider.builtIn() ? service : null;
    }

    /** The built-in state regardless of the provider - what the built-in provider is created around. */
    public PartyService service() {
        return service;
    }

    /** The player's party when it has at least two members, else empty. */
    public Optional<PartyInfo> partyOf(UUID player) {
        return provider.partyOf(player).filter(party -> party.members().size() > 1);
    }

    /** The team-mates of {@code player} in their party (not including them), online or not. */
    public List<UUID> partyMates(UUID player) {
        return partyOf(player).map(party -> party.members().stream().filter(id -> !id.equals(player)).toList()).orElse(List.of());
    }
}
