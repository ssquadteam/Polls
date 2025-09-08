package com.ssquadteam.polls.storage;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.ssquadteam.polls.PollsPlugin;
import com.ssquadteam.polls.model.Poll;
import com.ssquadteam.polls.model.PollStatus;

import java.lang.reflect.Type;
import java.sql.*;
import java.time.Instant;
import java.util.*;

public class PostgresPollStorage implements PollStorage {

    private final PollsPlugin plugin;
    private Connection connection;
    private final Gson gson = new Gson();

    public PostgresPollStorage(PollsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {
        try {
            // Attempt to load the driver (optional on modern JVMs but provides clearer error if missing)
            try { Class.forName("org.postgresql.Driver"); } catch (ClassNotFoundException ignored) {}

            String url = plugin.getConfig().getString("storage.postgres.url", null);
            if (url == null || url.isBlank()) {
                String host = plugin.getConfig().getString("storage.postgres.host", "localhost");
                int port = plugin.getConfig().getInt("storage.postgres.port", 5432);
                String database = plugin.getConfig().getString("storage.postgres.database", "polls");
                String params = plugin.getConfig().getString("storage.postgres.params", ""); // e.g., sslmode=require
                String base = "jdbc:postgresql://" + host + ":" + port + "/" + database;
                url = params == null || params.isBlank() ? base : base + "?" + params;
            }
            String user = plugin.getConfig().getString("storage.postgres.user", "postgres");
            String password = plugin.getConfig().getString("storage.postgres.password", "");

            Properties props = new Properties();
            props.setProperty("user", user);
            props.setProperty("password", password);

            connection = DriverManager.getConnection(url, props);

            try (Statement st = connection.createStatement()) {
                st.executeUpdate("CREATE TABLE IF NOT EXISTS polls (" +
                        "id UUID PRIMARY KEY, " +
                        "code TEXT UNIQUE, " +
                        "question TEXT NOT NULL, " +
                        "options_json TEXT NOT NULL, " +
                        "created_at BIGINT NOT NULL, " +
                        "closes_at BIGINT NOT NULL, " +
                        "status TEXT NOT NULL" +
                        ")");

                st.executeUpdate("CREATE TABLE IF NOT EXISTS votes (" +
                        "poll_id UUID NOT NULL, " +
                        "player_uuid UUID NOT NULL, " +
                        "option_index INTEGER NOT NULL, " +
                        "voted_at BIGINT NOT NULL, " +
                        "PRIMARY KEY (poll_id, player_uuid), " +
                        "FOREIGN KEY (poll_id) REFERENCES polls(id) ON DELETE CASCADE" +
                        ")");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to init Postgres: " + e.getMessage());
        }
    }

    @Override
    public void close() {
        try { if (connection != null) connection.close(); } catch (Exception ignored) {}
    }

    @Override
    public void savePoll(Poll poll) {
        String sql = "INSERT INTO polls (id, code, question, options_json, created_at, closes_at, status) VALUES (?, ?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT (id) DO UPDATE SET code=EXCLUDED.code, question=EXCLUDED.question, options_json=EXCLUDED.options_json, created_at=EXCLUDED.created_at, closes_at=EXCLUDED.closes_at, status=EXCLUDED.status";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setObject(1, poll.getId());
            ps.setString(2, poll.getCode());
            ps.setString(3, poll.getQuestion());
            ps.setString(4, gson.toJson(poll.getOptions()));
            ps.setLong(5, poll.getCreatedAtEpochSeconds());
            ps.setLong(6, poll.getClosesAtEpochSeconds());
            ps.setString(7, poll.getStatus().name());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to save poll: " + e.getMessage());
        }
    }

    @Override
    public Poll getPoll(UUID id) {
        String sql = "SELECT code, question, options_json, created_at, closes_at, status FROM polls WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String code = rs.getString(1);
                    String question = rs.getString(2);
                    List<String> options = gson.fromJson(rs.getString(3), listStringType());
                    long created = rs.getLong(4);
                    long closes = rs.getLong(5);
                    PollStatus status = PollStatus.valueOf(rs.getString(6));
                    Poll p = new Poll(id, question, options, created, closes, status);
                    p.setCode(code);
                    return p;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get poll: " + e.getMessage());
        }
        return null;
    }

    @Override
    public Poll findByIdOrCode(String idOrCode) {
        try {
            UUID id = UUID.fromString(idOrCode);
            return getPoll(id);
        } catch (Exception ignored) {}
        String sql = "SELECT id, code, question, options_json, created_at, closes_at, status FROM polls WHERE lower(code) = lower(?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, idOrCode);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    UUID id = (UUID) rs.getObject(1);
                    String code = rs.getString(2);
                    String question = rs.getString(3);
                    List<String> options = gson.fromJson(rs.getString(4), listStringType());
                    long created = rs.getLong(5);
                    long closes = rs.getLong(6);
                    PollStatus status = PollStatus.valueOf(rs.getString(7));
                    Poll p = new Poll(id, question, options, created, closes, status);
                    p.setCode(code);
                    return p;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to find poll: " + e.getMessage());
        }
        return null;
    }

    @Override
    public List<Poll> getAllPolls() {
        List<Poll> list = new ArrayList<>();
        String sql = "SELECT id, code, question, options_json, created_at, closes_at, status FROM polls ORDER BY created_at DESC";
        try (Statement st = connection.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                UUID id = (UUID) rs.getObject(1);
                String code = rs.getString(2);
                String question = rs.getString(3);
                List<String> options = gson.fromJson(rs.getString(4), listStringType());
                long created = rs.getLong(5);
                long closes = rs.getLong(6);
                PollStatus status = PollStatus.valueOf(rs.getString(7));
                Poll p = new Poll(id, question, options, created, closes, status);
                p.setCode(code);
                list.add(p);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to list polls: " + e.getMessage());
        }
        return list;
    }

    @Override
    public void removePoll(UUID id) {
        try (PreparedStatement ps1 = connection.prepareStatement("DELETE FROM votes WHERE poll_id = ?");
             PreparedStatement ps2 = connection.prepareStatement("DELETE FROM polls WHERE id = ?")) {
            ps1.setObject(1, id);
            ps1.executeUpdate();
            ps2.setObject(1, id);
            ps2.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to remove poll: " + e.getMessage());
        }
    }

    @Override
    public void saveVote(UUID pollId, UUID player, int optionIndex) {
        String sql = "INSERT INTO votes (poll_id, player_uuid, option_index, voted_at) VALUES (?, ?, ?, ? ) " +
                "ON CONFLICT (poll_id, player_uuid) DO NOTHING";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setObject(1, pollId);
            ps.setObject(2, player);
            ps.setInt(3, optionIndex);
            ps.setLong(4, Instant.now().getEpochSecond());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to save vote: " + e.getMessage());
        }
    }

    @Override
    public boolean hasVoted(UUID pollId, UUID player) {
        String sql = "SELECT 1 FROM votes WHERE poll_id = ? AND player_uuid = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setObject(1, pollId);
            ps.setObject(2, player);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to check vote: " + e.getMessage());
        }
        return false;
    }

    @Override
    public Integer getPlayerVote(UUID pollId, UUID player) {
        String sql = "SELECT option_index FROM votes WHERE poll_id = ? AND player_uuid = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setObject(1, pollId);
            ps.setObject(2, player);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get player vote: " + e.getMessage());
        }
        return null;
    }

    @Override
    public Map<Integer, Integer> getVoteTally(UUID pollId) {
        Map<Integer, Integer> map = new HashMap<>();
        String sql = "SELECT option_index, COUNT(*) FROM votes WHERE poll_id = ? GROUP BY option_index";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setObject(1, pollId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    map.put(rs.getInt(1), rs.getInt(2));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to tally votes: " + e.getMessage());
        }
        return map;
    }

    private Type listStringType() {
        return new TypeToken<List<String>>(){}.getType();
    }
}
