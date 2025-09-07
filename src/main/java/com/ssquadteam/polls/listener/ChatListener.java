package com.ssquadteam.polls.listener;

import com.ssquadteam.polls.PollsPlugin;
import com.ssquadteam.polls.service.SessionManager;
import com.ssquadteam.polls.service.session.PollCreationSession;
import com.ssquadteam.polls.util.DurationUtil;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.Map;
import java.util.UUID;

public class ChatListener implements Listener {

    private final PollsPlugin plugin;

    public ChatListener(PollsPlugin plugin) { this.plugin = plugin; }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        SessionManager sessions = plugin.getSessionManager();
        PollCreationSession session = sessions.getSession(id);
        if (session == null) return;

        PollCreationSession.Awaiting awaiting = session.getAwaiting();
        if (awaiting == PollCreationSession.Awaiting.NONE) return;

        event.setCancelled(true);
        String msg = event.getMessage();

        switch (awaiting) {
            case QUESTION -> {
                session.setQuestion(msg);
                session.clearAwaiting();
                plugin.getMessageService().send(event.getPlayer(), "creation.set_question", Map.of());
            }
            case DURATION -> {
                Long seconds = DurationUtil.parseDurationSeconds(msg);
                if (seconds == null) {
                    plugin.getMessageService().send(event.getPlayer(), "errors.invalid_duration", Map.of());
                    return;
                }
                session.setDurationSeconds(seconds);
                session.clearAwaiting();
                plugin.getMessageService().send(event.getPlayer(), "creation.set_duration", Map.of(
                        "pretty", plugin.getMessageService().formatRelativeTime(session.previewClosesAt())
                ));
            }
            case OPTION -> {
                int idx = session.getAwaitingOptionIndex();
                session.setOption(idx, msg);
                session.clearAwaiting();
                plugin.getMessageService().send(event.getPlayer(), "creation.set_option", Map.of("index", String.valueOf(idx + 1)));
            }
        }

        // Re-open updated creation book synchronously
        plugin.getServer().getScheduler().runTask(plugin, () -> plugin.getBookFactory().openCreationBook(event.getPlayer(), session));
    }
}
