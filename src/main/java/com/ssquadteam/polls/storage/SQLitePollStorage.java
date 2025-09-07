package com.ssquadteam.polls.storage;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.ssquadteam.polls.PollsPlugin;
import com.ssquadteam.polls.model.Poll;
import com.ssquadteam.polls.model.PollStatus;

import java.io.File;
import java.lang.reflect.Type;
import java.sql.*;
import java.time.Instant;
import java.util.*;

public class SQLitePollStorage implements PollStorage {

    private final PollsPlugin plugin;
    private Connection connection;
    private final Gson gson = new Gson();

    public SQLitePollStorage(PollsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {
        try {
            File dbFile = new File(plugin.getDataFolder(), plugin.getConfig().getString("storage.sqlite.file", "polls.db"));
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();
            connection = DriverManager.getConnection(url);
            try (Statement st = connection.createStatement()) {
                st.executeUpdate("CREATE TABLE IF NOT EXISTS polls (id TEXT PRIMARY KEY, question TEXT NOT NULL, options_json TEXT NOT NULL, created_at INTEGER NOT NULL, closes_at INTEGER NOT NULL, status TEXT NOT NULL)");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS votes (poll_id TEXT NOT NULL, player_uuid TEXT NOT NULL, option_index INTEGER NOT NULL, voted_at INTEGER NOT NULL, PRIMARY KEY (poll_id, player_uuid))");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to init SQLite: " + e.getMessage());
        }
    }

    @Override
    public void close() {
        try { if (connection != null) connection.close(); } catch (Exception ignored) {}
    }

    @Override
    public void savePoll(Poll poll) {
        String sql = "INSERT INTO polls (id, question, options_json, created_at, closes_at, status) VALUES (?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT(id) DO UPDATE SET question=excluded.question, options_json=excluded.options_json, created_at=excluded.created_at, closes_at=excluded.closes_at, status=excluded.status";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, poll.getId().toString());
            ps.setString(2, poll.getQuestion());
            ps.setString(3, gson.toJson(poll.getOptions()));
            ps.setLong(4, poll.getCreatedAtEpochSeconds());
            ps.setLong(5, poll.getClosesAtEpochSeconds());
            ps.setString(6, poll.getStatus().name());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to save poll: " + e.getMessage());
        }
    }

    @Override
    public Poll getPoll(UUID id) {
        String sql = "SELECT question, options_json, created_at, closes_at, status FROM polls WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, id.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String question = rs.getString(1);
                    List<String> options = gson.fromJson(rs.getString(2), listStringType());
                    long created = rs.getLong(3);
                    long closes = rs.getLong(4);
                    PollStatus status = PollStatus.valueOf(rs.getString(5));
                    return new Poll(id, question, options, created, closes, status);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get poll: " + e.getMessage());
        }
        return null;
    }

    @Override
    public List<Poll> getAllPolls() {
        List<Poll> list = new ArrayList<>();
        String sql = "SELECT id, question, options_json, created_at, closes_at, status FROM polls ORDER BY created_at DESC";
        try (Statement st = connection.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                UUID id = UUID.fromString(rs.getString(1));
                String question = rs.getString(2);
                List<String> options = gson.fromJson(rs.getString(3), listStringType());
                long created = rs.getLong(4);
                long closes = rs.getLong(5);
                PollStatus status = PollStatus.valueOf(rs.getString(6));
                list.add(new Poll(id, question, options, created, closes, status));
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
            ps1.setString(1, id.toString());
            ps1.executeUpdate();
            ps2.setString(1, id.toString());
            ps2.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to remove poll: " + e.getMessage());
        }
    }

    @Override
    public void saveVote(UUID pollId, UUID player, int optionIndex) {
        String sql = "INSERT INTO votes (poll_id, player_uuid, option_index, voted_at) VALUES (?, ?, ?, ? ) " +
                "ON CONFLICT(poll_id, player_uuid) DO NOTHING";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, pollId.toString());
            ps.setString(2, player.toString());
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
            ps.setString(1, pollId.toString());
            ps.setString(2, player.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to check vote: " + e.getMessage());
        }
        return false;
    }

    @Override
    public Map<Integer, Integer> getVoteTally(UUID pollId) {
        Map<Integer, Integer> map = new HashMap<>();
        String sql = "SELECT option_index, COUNT(*) FROM votes WHERE poll_id = ? GROUP BY option_index";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, pollId.toString());
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
