package me.psikuvit.copperHeist.party;

import java.util.Optional;
import java.util.UUID;

/** {@code party.provider: none}: parties are switched off. */
public class NonePartyProvider implements PartyProvider {

    @Override
    public Optional<PartyInfo> partyOf(UUID player) {
        return Optional.empty();
    }

    @Override
    public String name() {
        return "none";
    }
}
