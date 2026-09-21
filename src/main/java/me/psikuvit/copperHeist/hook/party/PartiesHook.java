package me.psikuvit.copperHeist.hook.party;

import com.alessiodp.parties.api.Parties;
import com.alessiodp.parties.api.interfaces.PartiesAPI;
import com.alessiodp.parties.api.interfaces.Party;
import com.alessiodp.parties.api.interfaces.PartyPlayer;
import me.psikuvit.copperHeist.party.PartyInfo;
import me.psikuvit.copperHeist.party.PartyProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads parties from Parties by AlessioDP through its API (a provided dependency, so nothing is shaded). This class is only loaded once the
 * Parties plugin is known to be present; if its API can't be linked, the caller falls back to the built-in party system.
 */
public final class PartiesHook implements PartyProvider {

    @Override
    public Optional<PartyInfo> partyOf(UUID player) {
        PartiesAPI api = Parties.getApi();
        PartyPlayer partyPlayer = api.getPartyPlayer(player);
        if (partyPlayer == null || partyPlayer.getPartyId() == null) return Optional.empty();
        Party party = api.getParty(partyPlayer.getPartyId());
        if (party == null) return Optional.empty();

        List<UUID> members = new ArrayList<>(party.getMembers());
        if (members.isEmpty()) return Optional.empty();
        UUID leader = party.getLeader();
        return Optional.of(new PartyInfo(leader != null ? leader : members.getFirst(), members));
    }

    @Override
    public String name() {
        return "Parties";
    }
}
