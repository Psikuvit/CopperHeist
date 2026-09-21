package me.psikuvit.copperHeist.party;

import java.util.Optional;
import java.util.UUID;

/** The party system that ships with the plugin (/party). */
public class BuiltInPartyProvider implements PartyProvider {

    private final PartyService service;

    public BuiltInPartyProvider(PartyService service) {
        this.service = service;
    }

    @Override
    public Optional<PartyInfo> partyOf(UUID player) {
        return service.partyOf(player);
    }

    @Override
    public String name() {
        return "Copper Heist";
    }

    @Override
    public boolean builtIn() {
        return true;
    }
}
