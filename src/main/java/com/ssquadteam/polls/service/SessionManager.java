package com.ssquadteam.polls.service;

import com.ssquadteam.polls.PollsPlugin;
import com.ssquadteam.polls.service.session.PollCreationSession;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class SessionManager {

    private final PollsPlugin plugin;
    private final Map<UUID, PollCreationSession> sessions = new HashMap<>();
    private final Map<UUID, Integer> autoReopenTasks = new HashMap<>();
    private final Set<UUID> playersWithOpenBooks = new HashSet<>();

    public SessionManager(PollsPlugin plugin) {
        this.plugin = plugin;
    }

    public PollCreationSession startSession(UUID playerId) {
        PollCreationSession session = new PollCreationSession(playerId);
        sessions.put(playerId, session);
        scheduleAutoReopen(playerId);
        return session;
    }

    public PollCreationSession getSession(UUID playerId) { return sessions.get(playerId); }

    public void endSession(UUID playerId) {
        sessions.remove(playerId);
        Integer taskId = autoReopenTasks.remove(playerId);
        if (taskId != null) plugin.getServer().getScheduler().cancelTask(taskId);
    }

    public void awaitQuestion(UUID playerId) {
        PollCreationSession s = sessions.get(playerId);
        if (s != null) s.awaitQuestion();
    }

    public void awaitCode(UUID playerId) {
        PollCreationSession s = sessions.get(playerId);
        if (s != null) s.awaitCode();
    }

    public void awaitDuration(UUID playerId) {
        PollCreationSession s = sessions.get(playerId);
        if (s != null) s.awaitDuration();
    }

    public void awaitOption(UUID playerId, int index) {
        PollCreationSession s = sessions.get(playerId);
        if (s != null) s.awaitOption(index);
    }

    public void markBookOpened(UUID playerId) {
        playersWithOpenBooks.add(playerId);
    }

    public void markBookClosed(UUID playerId) {
        playersWithOpenBooks.remove(playerId);
    }

    private void scheduleAutoReopen(UUID playerId) {
        boolean enabled = plugin.getConfig().getBoolean("books.creation.autoReopen.enabled", true);
        if (!enabled) return;
        int interval = plugin.getConfig().getInt("books.creation.autoReopen.intervalTicks", 60);
        boolean onlyWhenIdle = plugin.getConfig().getBoolean("books.creation.autoReopen.onlyWhenIdle", true);

        Integer existing = autoReopenTasks.remove(playerId);
        if (existing != null) plugin.getServer().getScheduler().cancelTask(existing);

        int taskId = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            PollCreationSession s = sessions.get(playerId);
            if (s == null) {
                Integer tid = autoReopenTasks.remove(playerId);
                if (tid != null) plugin.getServer().getScheduler().cancelTask(tid);
                return;
            }
            if (onlyWhenIdle && s.getAwaiting() != PollCreationSession.Awaiting.NONE) return;
            if (playersWithOpenBooks.contains(playerId)) return; // Don't reopen if player has book open
            Player p = plugin.getServer().getPlayer(playerId);
            if (p == null || !p.isOnline()) return;
            plugin.getBookFactory().openCreationBook(p, s);
        }, interval, interval).getTaskId();
        autoReopenTasks.put(playerId, taskId);
    }
}
