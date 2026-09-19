package me.psikuvit.copperHeist.stats;

import me.psikuvit.copperHeist.database.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Every SQL statement the stats system uses. All methods are asynchronous - results arrive off the main thread. */
public class StatsRepository {

    private interface Query<T> {
        T run(Connection connection) throws Exception;
    }

    private interface Update {
        void run(Connection connection) throws Exception;
    }

    private final Database database;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "CopperHeist-Database");
        thread.setDaemon(true);
        return thread;
    });

    public StatsRepository(Database database) {
        this.database = database;
    }

    /** Stops accepting work; anything already queued still runs. */
    public void close() {
        executor.shutdown();
    }

    private <T> CompletableFuture<T> query(Query<T> work) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = database.getConnection()) {
                return work.run(connection);
            } catch (Exception ex) {
                throw new CompletionException(ex);
            }
        }, executor);
    }

    private CompletableFuture<Void> execute(Update work) {
        return query(connection -> {
            work.run(connection);
            return null;
        });
    }

    /** Creates the player row if new, or refreshes their name and last-seen time. */
    public CompletableFuture<Void> touchPlayer(UUID uuid, String name) {
        long now = System.currentTimeMillis();
        return execute(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(database.upsertPlayer())) {
                statement.setString(1, uuid.toString());
                statement.setString(2, name);
                statement.setLong(3, now);
                statement.setLong(4, now);
                statement.executeUpdate();
            }
        });
    }

    public CompletableFuture<Map<Stat, Long>> load(UUID uuid) {
        return query(connection -> {
            Map<Stat, Long> values = new EnumMap<>(Stat.class);
            try (PreparedStatement statement = connection.prepareStatement("SELECT stat, value FROM ch_stats WHERE uuid = ?")) {
                statement.setString(1, uuid.toString());
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        Stat stat = Stat.fromKey(result.getString(1));
                        if (stat != null) values.put(stat, result.getLong(2));
                    }
                }
            }
            return values;
        });
    }

    /** Adds every delta in one transaction, so a player's stats from a match land together or not at all. */
    public CompletableFuture<Void> addAll(UUID uuid, Map<Stat, Long> deltas) {
        return execute(connection -> {
            boolean auto = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(database.upsertStat())) {
                for (Map.Entry<Stat, Long> delta : deltas.entrySet()) {
                    if (delta.getValue() == 0) continue;
                    statement.setString(1, uuid.toString());
                    statement.setString(2, delta.getKey().key());
                    statement.setLong(3, delta.getValue());
                    statement.addBatch();
                }
                statement.executeBatch();
                connection.commit();
            } catch (Exception ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(auto);
            }
        });
    }

    /** Overwrites one stat (delete + insert, so it works the same on SQLite and MySQL). */
    public CompletableFuture<Void> setStat(UUID uuid, Stat stat, long value) {
        return execute(connection -> {
            boolean auto = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (PreparedStatement delete = connection.prepareStatement("DELETE FROM ch_stats WHERE uuid = ? AND stat = ?");
                 PreparedStatement insert = connection.prepareStatement("INSERT INTO ch_stats (uuid, stat, value) VALUES (?, ?, ?)")) {
                delete.setString(1, uuid.toString());
                delete.setString(2, stat.key());
                delete.executeUpdate();
                insert.setString(1, uuid.toString());
                insert.setString(2, stat.key());
                insert.setLong(3, value);
                insert.executeUpdate();
                connection.commit();
            } catch (Exception ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(auto);
            }
        });
    }

    /** Deletes every stat of a player (their name row stays, so they can still be looked up). */
    public CompletableFuture<Void> resetPlayer(UUID uuid) {
        return execute(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM ch_stats WHERE uuid = ?")) {
                statement.setString(1, uuid.toString());
                statement.executeUpdate();
            }
        });
    }

    /** The best {@code limit} players for a stat, highest first (players with a zero value are left out). */
    public CompletableFuture<List<TopEntry>> top(Stat stat, int limit) {
        return query(connection -> {
            List<TopEntry> rows = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT s.uuid, p.name, s.value FROM ch_stats s JOIN ch_players p ON p.uuid = s.uuid "
                            + "WHERE s.stat = ? AND s.value > 0 ORDER BY s.value DESC, p.name ASC LIMIT ?")) {
                statement.setString(1, stat.key());
                statement.setInt(2, limit);
                try (ResultSet result = statement.executeQuery()) {
                    int rank = 1;
                    while (result.next()) {
                        rows.add(new TopEntry(rank++, UUID.fromString(result.getString(1)), result.getString(2), result.getLong(3)));
                    }
                }
            }
            return rows;
        });
    }

    /** 1-based rank of a player for a stat, or 0 if they have none yet. */
    public CompletableFuture<Integer> rank(UUID uuid, Stat stat) {
        return query(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT 1 + COUNT(*) FROM ch_stats WHERE stat = ? AND value > "
                            + "(SELECT value FROM ch_stats WHERE uuid = ? AND stat = ?)")) {
                statement.setString(1, stat.key());
                statement.setString(2, uuid.toString());
                statement.setString(3, stat.key());
                try (ResultSet result = statement.executeQuery()) {
                    return result.next() ? result.getInt(1) : 0;
                }
            }
        });
    }

    /** Looks a player up by (case-insensitive) last-known name; null if nobody by that name has played. */
    public CompletableFuture<PlayerStats> findByName(String name) {
        return query(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT uuid, name FROM ch_players WHERE LOWER(name) = LOWER(?) ORDER BY last_seen DESC LIMIT 1")) {
                statement.setString(1, name);
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) return null;
                    return PlayerStats.empty(UUID.fromString(result.getString(1)), result.getString(2));
                }
            }
        }).thenCompose(found -> {
            if (found == null) return CompletableFuture.completedFuture(null);
            return load(found.uuid()).thenApply(values -> new PlayerStats(found.uuid(), found.name(), values));
        });
    }

    public CompletableFuture<Void> recordMatch(MatchRecord match) {
        return execute(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO ch_matches (match_id, arena, winner, copper_score, iron_score, duration_seconds, ended_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                statement.setString(1, match.matchId());
                statement.setString(2, match.arena());
                statement.setString(3, match.winner());
                statement.setInt(4, match.copperScore());
                statement.setInt(5, match.ironScore());
                statement.setInt(6, match.durationSeconds());
                statement.setLong(7, match.endedAtMillis());
                statement.executeUpdate();
            }
        });
    }
}
