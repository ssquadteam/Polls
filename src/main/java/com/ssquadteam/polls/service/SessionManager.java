package com.ssquadteam.polls.service;

import com.ssquadteam.polls.service.session.PollCreationSession;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SessionManager {

    private final Plugin plugin;
    private final Map<UUID, PollCreationSession> sessions = new HashMap<>();

    public SessionManager(Plugin plugin) {
        this.plugin = plugin;
    }

    public PollCreationSession startSession(UUID playerId) {
        PollCreationSession session = new PollCreationSession(playerId);
        sessions.put(playerId, session);
        return session;
    }

    public PollCreationSession getSession(UUID playerId) { return sessions.get(playerId); }

    public void endSession(UUID playerId) { sessions.remove(playerId); }

    public void awaitQuestion(UUID playerId) {
        PollCreationSession s = sessions.get(playerId);
        if (s != null) s.awaitQuestion();
    }

    public void awaitDuration(UUID playerId) {
        PollCreationSession s = sessions.get(playerId);
        if (s != null) s.awaitDuration();
    }

    public void awaitOption(UUID playerId, int index) {
        PollCreationSession s = sessions.get(playerId);
        if (s != null) s.awaitOption(index);
    }
}
