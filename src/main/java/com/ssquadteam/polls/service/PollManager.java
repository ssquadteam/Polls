package com.ssquadteam.polls.service;

import com.ssquadteam.polls.PollsPlugin;
import com.ssquadteam.polls.model.Poll;
import com.ssquadteam.polls.model.PollStatus;
import com.ssquadteam.polls.service.session.PollCreationSession;
import com.ssquadteam.polls.storage.PollStorage;
import com.ssquadteam.polls.util.DurationUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.*;

public class PollManager {

    private final PollsPlugin plugin;
    private final PollStorage storage;
    private final MessageService messages;

    private final Map<UUID, Integer> scheduledTasks = new HashMap<>();

    public PollManager(PollsPlugin plugin, PollStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
        this.messages = plugin.getMessageService();
    }

    public PollStorage getStorage() { return storage; }

    public void publishFromSession(Player player, PollCreationSession session) {
        List<String> options = session.getDefinedOptions();
        if (options.size() < 2) {
            messages.send(player, "errors.nothing_to_publish", Map.of());
            return;
        }
        if (session.getCode() == null || session.getCode().isBlank()) {
            messages.send(player, "errors.missing_code", Map.of());
            return;
        }
        if (!session.isEditingExisting()) {
            if (storage.findByIdOrCode(session.getCode()) != null) {
                messages.send(player, "errors.code_in_use", Map.of());
                return;
            }
        }
        long duration = session.getDurationSeconds() == null ? DurationUtil.DEFAULT_DURATION_SECONDS : session.getDurationSeconds();
        long now = Instant.now().getEpochSecond();
        long closesAt = now + duration;
        UUID id;
        Poll poll;
        if (session.isEditingExisting()) {
            poll = storage.getPoll(session.getEditingPollId());
            if (poll == null) { messages.send(player, "errors.invalid_poll", Map.of()); return; }
            poll.setQuestion(session.getQuestion() == null ? poll.getQuestion() : session.getQuestion());
            poll.setOptions(options);
            poll.setClosesAtEpochSeconds(closesAt);
            poll.setStatus(PollStatus.OPEN);
            poll.setCode(session.getCode());
            id = poll.getId();
            storage.savePoll(poll);
            // reschedule
            trackOpenPoll(poll);
        } else {
            id = UUID.randomUUID();
            poll = new Poll(id, session.getQuestion() == null ? "Untitled Poll" : session.getQuestion(), options, now, closesAt, PollStatus.OPEN);
            poll.setCode(session.getCode());
            storage.savePoll(poll);
            trackOpenPoll(poll);
        }
        plugin.getSessionManager().endSession(player.getUniqueId());

        Map<String, String> ph = Map.of(
                "code", poll.getCode(),
                "question", poll.getQuestion(),
                "pretty", messages.formatRelativeTime(poll.getClosesAtEpochSeconds())
        );
        messages.broadcast("announce.new_poll", ph);
        messages.send(player, "creation.published", Map.of("code", poll.getCode()));
    }

    public void trackOpenPoll(Poll poll) {
        long delayTicks = Math.max(1, (poll.getClosesAtEpochSeconds() - Instant.now().getEpochSecond()) * 20);
        int taskId = Bukkit.getScheduler().runTaskLater(plugin, () -> closePoll(poll, false), delayTicks).getTaskId();
        scheduledTasks.put(poll.getId(), taskId);
    }

    public void closePoll(Poll poll, boolean manual) {
        poll.setStatus(PollStatus.CLOSED);
        storage.savePoll(poll);
        Integer taskId = scheduledTasks.remove(poll.getId());
        if (taskId != null) Bukkit.getScheduler().cancelTask(taskId);
    }

    public void removePoll(UUID id) {
        Integer taskId = scheduledTasks.remove(id);
        if (taskId != null) Bukkit.getScheduler().cancelTask(taskId);
        storage.removePoll(id);
    }

    public void vote(Player player, UUID pollId, int optionIndex) {
        Poll poll = storage.getPoll(pollId);
        if (poll == null) { messages.sendWithSound(player, "errors.invalid_poll", Map.of(), "ui.error"); return; }
        if (!poll.isOpen()) { messages.sendWithSound(player, "errors.poll_closed", Map.of(), "ui.error"); return; }
        if (storage.hasVoted(pollId, player.getUniqueId())) { messages.sendWithSound(player, "errors.already_voted", Map.of(), "ui.error"); return; }
        if (optionIndex < 0 || optionIndex >= poll.getOptions().size()) { messages.sendWithSound(player, "errors.invalid_option", Map.of(), "ui.error"); return; }

        storage.saveVote(pollId, player.getUniqueId(), optionIndex);
        messages.sendWithSound(player, "vote.success", Map.of("index", String.valueOf(optionIndex + 1)), "ui.set_value");
    }

    public void shutdown() {
        for (Integer taskId : scheduledTasks.values()) Bukkit.getScheduler().cancelTask(taskId);
        scheduledTasks.clear();
    }
}
