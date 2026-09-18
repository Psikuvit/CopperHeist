package me.psikuvit.copperHeist.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/** database.type: mysql - a shared MySQL/MariaDB server, so several game servers can use the same stats. */
public class MysqlDatabase implements Database {

    private final String url;
    private final String user;
    private final String password;

    public MysqlDatabase(String host, int port, String database, String user, String password, boolean ssl) {
        this.url = "jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useSSL=" + ssl + "&characterEncoding=utf8&useUnicode=true&connectTimeout=8000";
        this.user = user;
        this.password = password;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    @Override
    public String upsertStat() {
        return "INSERT INTO ch_stats (uuid, stat, value) VALUES (?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE value = value + VALUES(value)";
    }

    @Override
    public String upsertPlayer() {
        return "INSERT INTO ch_players (uuid, name, first_seen, last_seen) VALUES (?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE name = VALUES(name), last_seen = VALUES(last_seen)";
    }
}
