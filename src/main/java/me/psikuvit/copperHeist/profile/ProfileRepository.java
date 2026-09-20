package me.psikuvit.copperHeist.profile;

import me.psikuvit.copperHeist.stats.StatsRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * The SQL behind {@link PlayerProfile}. It runs on the stats repository's database thread, so SQLite only ever has one writer and
 * profile writes stay in order with the stats writes made just before them (which the cross-server hand-off relies on).
 */
public class ProfileRepository {

    private final StatsRepository database;

    public ProfileRepository(StatsRepository database) {
        this.database = database;
    }

    public CompletableFuture<PlayerProfile.Data> load(UUID uuid) {
        return database.query(connection -> {
            Map<String, String> fields = new LinkedHashMap<>();
            Map<String, Long> unlocks = new LinkedHashMap<>();
            Map<String, String> equipped = new LinkedHashMap<>();
            String id = uuid.toString();
            read(connection, "SELECT field, value FROM ch_profile WHERE uuid = ?", id, row -> fields.put(row.getString(1), row.getString(2)));
            read(connection, "SELECT item_id, acquired FROM ch_unlocks WHERE uuid = ?", id, row -> unlocks.put(row.getString(1), row.getLong(2)));
            read(connection, "SELECT category, item_id FROM ch_equipped WHERE uuid = ?", id, row -> equipped.put(row.getString(1), row.getString(2)));
            return new PlayerProfile.Data(fields, unlocks, equipped);
        });
    }

    /** Replaces the player's stored profile with this snapshot, all in one transaction. */
    public CompletableFuture<Void> save(UUID uuid, PlayerProfile.Data data) {
        return database.execute(connection -> {
            boolean auto = connection.getAutoCommit();
            connection.setAutoCommit(false);
            String id = uuid.toString();
            try {
                for (String table : new String[]{"ch_profile", "ch_unlocks", "ch_equipped"}) {
                    try (PreparedStatement delete = connection.prepareStatement("DELETE FROM " + table + " WHERE uuid = ?")) {
                        delete.setString(1, id);
                        delete.executeUpdate();
                    }
                }
                try (PreparedStatement insert = connection.prepareStatement("INSERT INTO ch_profile (uuid, field, value) VALUES (?, ?, ?)")) {
                    for (Map.Entry<String, String> field : data.fields().entrySet()) {
                        insert.setString(1, id);
                        insert.setString(2, field.getKey());
                        insert.setString(3, field.getValue());
                        insert.addBatch();
                    }
                    insert.executeBatch();
                }
                try (PreparedStatement insert = connection.prepareStatement("INSERT INTO ch_unlocks (uuid, item_id, acquired) VALUES (?, ?, ?)")) {
                    for (Map.Entry<String, Long> unlock : data.unlocks().entrySet()) {
                        insert.setString(1, id);
                        insert.setString(2, unlock.getKey());
                        insert.setLong(3, unlock.getValue());
                        insert.addBatch();
                    }
                    insert.executeBatch();
                }
                try (PreparedStatement insert = connection.prepareStatement("INSERT INTO ch_equipped (uuid, category, item_id) VALUES (?, ?, ?)")) {
                    for (Map.Entry<String, String> item : data.equipped().entrySet()) {
                        insert.setString(1, id);
                        insert.setString(2, item.getKey());
                        insert.setString(3, item.getValue());
                        insert.addBatch();
                    }
                    insert.executeBatch();
                }
                connection.commit();
            } catch (Exception ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(auto);
            }
        });
    }

    /** Admin: forget everything about a player's profile. */
    public CompletableFuture<Void> delete(UUID uuid) {
        return save(uuid, PlayerProfile.Data.empty());
    }

    private interface RowReader {
        void read(ResultSet row) throws Exception;
    }

    private static void read(Connection connection, String sql, String uuid, RowReader reader) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) reader.read(result);
            }
        }
    }
}
