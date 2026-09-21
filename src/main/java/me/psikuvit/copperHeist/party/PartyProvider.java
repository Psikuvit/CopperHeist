package me.psikuvit.copperHeist.party;

import java.util.Optional;
import java.util.UUID;

/**
 * Where parties come from. The match code only ever asks this, so the built-in party system, the Parties plugin or another addon's
 * provider (registered with {@code CopperHeistAPI.registerPartyProvider}) all work the same way.
 */
public interface PartyProvider {

    /** The party the player is in, or empty. A party of one may be returned; callers treat it as no party. */
    Optional<PartyInfo> partyOf(UUID player);

    /** What players are told when they try the built-in /party commands while this provider owns parties. */
    default String name() {
        return getClass().getSimpleName();
    }

    /** True only for the built-in party system, whose commands and menu are then active. */
    default boolean builtIn() {
        return false;
    }
}
