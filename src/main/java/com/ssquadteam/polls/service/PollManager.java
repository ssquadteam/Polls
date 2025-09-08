package com.ssquadteam.polls.service;

import com.ssquadteam.polls.PollsPlugin;
import com.ssquadteam.polls.model.Poll;
import com.ssquadteam.polls.model.PollStatus;
import com.ssquadteam.polls.service.session.PollCreationSession;
import com.ssquadteam.polls.storage.PollStorage;
import com.ssquadteam.polls.util.DurationUtil;
import com.tcoded.folialib.wrapper.task.WrappedTask;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.*;

public class PollManager {

    private final PollsPlugin plugin;
    private final PollStorage storage;
    private final MessageService messages;

    private final Map<UUID, WrappedTask> scheduledTasks = new HashMap<>();

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
            if (poll.getStatus() == PollStatus.CLOSED) {
                messages.send(player, "errors.cannot_edit_closed", Map.of());
                return;
            }
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
        WrappedTask existing = scheduledTasks.remove(poll.getId());
        if (existing != null) existing.cancel();
        WrappedTask task = plugin.getFolia().getScheduler().runTimer(() -> closePoll(poll, false), delayTicks, Long.MAX_VALUE);
        scheduledTasks.put(poll.getId(), task);
    }

    public void closePoll(Poll poll, boolean manual) {
        poll.setStatus(PollStatus.CLOSED);
        storage.savePoll(poll);
        WrappedTask task = scheduledTasks.remove(poll.getId());
        if (task != null) task.cancel();

        announceResults(poll);
    }

    private void announceResults(Poll poll) {
        Map<Integer, Integer> tally = storage.getVoteTally(poll.getId());

        if (tally.isEmpty()) {
            Map<String, String> ph = Map.of(
                "question", poll.getQuestion()
            );
            messages.broadcast("announce.poll_closed_no_votes", ph);
            return;
        }

        int maxVotes = 0;
        for (Integer votes : tally.values()) {
            if (votes > maxVotes) {
                maxVotes = votes;
            }
        }
        List<Integer> winners = new ArrayList<>();
        for (Map.Entry<Integer, Integer> entry : tally.entrySet()) {
            if (entry.getValue() == maxVotes) {
                winners.add(entry.getKey());
            }
        }

        if (winners.size() > 1) {
            Map<String, String> ph = Map.of(
                "question", poll.getQuestion(),
                "votes", String.valueOf(maxVotes)
            );
            messages.broadcast("announce.poll_closed_tie", ph);
        } else {
            int winnerIndex = winners.get(0);
            String winningOption = poll.getOptions().get(winnerIndex);
            Map<String, String> ph = Map.of(
                "question", poll.getQuestion(),
                "winning_option", winningOption,
                "votes", String.valueOf(maxVotes)
            );
            messages.broadcast("announce.poll_closed", ph);
        }
    }

    public void removePoll(UUID id) {
        WrappedTask task = scheduledTasks.remove(id);
        if (task != null) task.cancel();
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
        for (WrappedTask task : scheduledTasks.values()) task.cancel();
        scheduledTasks.clear();
    }
}
