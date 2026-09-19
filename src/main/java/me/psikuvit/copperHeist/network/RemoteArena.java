package me.psikuvit.copperHeist.network;

/** An arena that lives on another server of the same cluster, as last reported through Redis. */
public record RemoteArena(String serverId, String proxyName, String arena, String state, int players, int maxPlayers, boolean enabled) {

    /** Can a new player be sent there right now? */
    public boolean joinable() {
        return enabled && players < maxPlayers && (state.equals("WAITING") || state.equals("STARTING"));
    }

    /** "server.arena" - how players refer to it in /ch join. */
    public String fullName() {
        return serverId + "." + arena;
    }
}
