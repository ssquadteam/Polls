package com.ssquadteam.polls.commands;

import com.ssquadteam.polls.PollsPlugin;
import com.ssquadteam.polls.model.Poll;
import com.ssquadteam.polls.model.PollStatus;
import com.ssquadteam.polls.service.MessageService;
import com.ssquadteam.polls.service.PollManager;
import com.ssquadteam.polls.service.SessionManager;
import com.ssquadteam.polls.service.session.PollCreationSession;
import com.ssquadteam.polls.storage.PollStorage;
import com.ssquadteam.polls.util.DurationUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

public class PollCommand implements CommandExecutor, TabCompleter {

    private final PollsPlugin plugin;
    private final MessageService messages;
    private final PollManager pollManager;
    private final PollStorage storage;
    private final SessionManager sessions;

    public PollCommand(PollsPlugin plugin) {
        this.plugin = plugin;
        this.messages = plugin.getMessageService();
        this.pollManager = plugin.getPollManager();
        this.storage = pollManager.getStorage();
        this.sessions = plugin.getSessionManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            messages.send(sender, "errors.invalid_args", Map.of());
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "create" -> handleCreate(sender, Arrays.copyOfRange(args, 1, args.length));
            case "close" -> handleClose(sender, Arrays.copyOfRange(args, 1, args.length));
            case "remove" -> handleRemove(sender, Arrays.copyOfRange(args, 1, args.length));
            case "list" -> handleList(sender);
            case "view" -> handleView(sender, Arrays.copyOfRange(args, 1, args.length));
            case "vote" -> handleVote(sender, Arrays.copyOfRange(args, 1, args.length));
            case "publish" -> handlePublish(sender);
            case "cancel" -> handleCancel(sender);
            case "edit" -> handleEdit(sender, Arrays.copyOfRange(args, 1, args.length));
            default -> messages.send(sender, "errors.invalid_args", Map.of());
        }
        return true;
    }

    private void handleCreate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "errors.only_players", Map.of());
            return;
        }
        if (!sender.hasPermission("polls.create")) {
            messages.send(sender, "errors.no_permission", Map.of());
            return;
        }
        if (sessions.getSession(player.getUniqueId()) != null) {
            messages.send(player, "errors.creation_in_progress", Map.of());
            return;
        }

        Long durationSeconds = null;
        String question = null;
        if (args.length >= 1) {
            Long parsed = DurationUtil.parseDurationSeconds(args[0]);
            if (parsed != null) {
                durationSeconds = parsed;
                if (args.length >= 2) {
                    question = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                }
            } else {
                // No valid duration provided; treat entire args as question
                question = String.join(" ", args);
            }
        }

        PollCreationSession session = sessions.startSession(player.getUniqueId());
        if (question != null && !question.isBlank()) session.setQuestion(question);
        if (durationSeconds != null) session.setDurationSeconds(durationSeconds);

        messages.send(player, "creation.started", Map.of());
        messages.send(player, "creation.book_hint", Map.of());
        plugin.getBookFactory().openCreationBook(player, session);
        plugin.getMessageService().playSound(player, "ui.open_creation");
    }

    private void handleClose(CommandSender sender, String[] args) {
        if (!sender.hasPermission("polls.close")) {
            messages.send(sender, "errors.no_permission", Map.of());
            return;
        }
        if (args.length < 1) {
            messages.send(sender, "errors.invalid_args", Map.of());
            return;
        }
        UUID id = parseUUID(args[0]);
        if (id == null) { messages.send(sender, "errors.invalid_poll", Map.of()); return; }
        Poll poll = storage.getPoll(id);
        if (poll == null) { messages.send(sender, "errors.invalid_poll", Map.of()); return; }
        if (poll.getStatus() == PollStatus.CLOSED) {
            messages.send(sender, "close.success", Map.of("id", id.toString()));
            return;
        }
        pollManager.closePoll(poll, true);
        messages.send(sender, "close.success", Map.of("id", id.toString()));
    }

    private void handleRemove(CommandSender sender, String[] args) {
        if (!sender.hasPermission("polls.remove")) {
            messages.send(sender, "errors.no_permission", Map.of());
            return;
        }
        if (args.length < 1) {
            messages.send(sender, "errors.invalid_args", Map.of());
            return;
        }
        UUID id = parseUUID(args[0]);
        if (id == null) { messages.send(sender, "errors.invalid_poll", Map.of()); return; }
        pollManager.removePoll(id);
        messages.send(sender, "remove.success", Map.of("id", id.toString()));
    }

    private void handleList(CommandSender sender) {
        if (!sender.hasPermission("polls.list")) {
            messages.send(sender, "errors.no_permission", Map.of());
            return;
        }
        List<Poll> polls = storage.getAllPolls();
        messages.send(sender, "list.header", Map.of());
        for (Poll poll : polls) {
            String pretty = plugin.getMessageService().formatRelativeTime(poll.getClosesAtEpochSeconds());
            Map<String, String> ph = Map.of(
                    "id", poll.getId().toString(),
                    "question", poll.getQuestion(),
                    "pretty", pretty
            );
            if (poll.getStatus() == PollStatus.OPEN) {
                messages.send(sender, "list.entry_open", ph);
            } else {
                messages.send(sender, "list.entry_closed", ph);
            }
        }
    }

    private void handleView(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "errors.only_players", Map.of());
            return;
        }
        if (args.length < 1) { messages.send(player, "errors.invalid_args", Map.of()); return; }
        UUID id = parseUUID(args[0]);
        if (id == null) { messages.send(player, "errors.invalid_poll", Map.of()); return; }
        Poll poll = storage.getPoll(id);
        if (poll == null) { messages.send(player, "errors.invalid_poll", Map.of()); return; }
        messages.send(player, "view.opened", Map.of());
        plugin.getBookFactory().openVotingBook(player, poll);
    }

    private void handleVote(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "errors.only_players", Map.of());
            return;
        }
        if (args.length < 2) { messages.send(player, "errors.invalid_args", Map.of()); return; }
        UUID id = parseUUID(args[0]);
        if (id == null) { messages.send(player, "errors.invalid_poll", Map.of()); return; }
        int index;
        try { index = Integer.parseInt(args[1]); } catch (Exception e) { messages.send(player, "errors.invalid_option", Map.of()); return; }

        pollManager.vote(player, id, index);
    }

    private void handlePublish(CommandSender sender) {
        if (!(sender instanceof Player player)) { messages.send(sender, "errors.only_players", Map.of()); return; }
        if (!player.hasPermission("polls.create")) { messages.send(player, "errors.no_permission", Map.of()); return; }
        PollCreationSession session = sessions.getSession(player.getUniqueId());
        if (session == null) { messages.send(player, "errors.invalid_args", Map.of()); return; }
        pollManager.publishFromSession(player, session);
    }

    private void handleCancel(CommandSender sender) {
        if (!(sender instanceof Player player)) { messages.send(sender, "errors.only_players", Map.of()); return; }
        PollCreationSession session = sessions.getSession(player.getUniqueId());
        if (session != null) {
            sessions.endSession(player.getUniqueId());
            messages.send(player, "creation.cancelled", Map.of());
        }
    }

    private void handleEdit(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { messages.send(sender, "errors.only_players", Map.of()); return; }
        if (!player.hasPermission("polls.create")) { messages.send(player, "errors.no_permission", Map.of()); return; }
        if (args.length == 0) { messages.send(player, "errors.invalid_args", Map.of()); return; }

        if (args.length >= 1 && !args[0].equalsIgnoreCase("question") && !args[0].equalsIgnoreCase("duration") && !args[0].equalsIgnoreCase("option") && !args[0].equalsIgnoreCase("code")) {
            String idOrCode = args[0];
            Poll target = storage.findByIdOrCode(idOrCode);
            if (target == null) { messages.send(player, "errors.invalid_poll", Map.of()); return; }
            PollCreationSession session = sessions.startSession(player.getUniqueId());
            session.startEditing(target.getId());
            session.setCode(target.getCode());
            session.setQuestion(target.getQuestion());
            long now = java.time.Instant.now().getEpochSecond();
            if (target.getStatus() == com.ssquadteam.polls.model.PollStatus.OPEN) {
                long remaining = Math.max(0, target.getClosesAtEpochSeconds() - now);
                session.setDurationSeconds(remaining);
            }
            java.util.List<String> opts = target.getOptions();
            for (int i = 0; i < Math.min(6, opts.size()); i++) session.setOption(i, opts.get(i));
            plugin.getBookFactory().openCreationBook(player, session);
            return;
        }

        PollCreationSession session = sessions.getSession(player.getUniqueId());
        if (session == null) { messages.send(player, "errors.invalid_args", Map.of()); return; }
        String type = args[0].toLowerCase(Locale.ROOT);
        switch (type) {
            case "code" -> {
                sessions.awaitCode(player.getUniqueId());
                messages.sendWithSound(player, "creation.prompt_code", Map.of(), "ui.set_value");
            }
            case "question" -> {
                sessions.awaitQuestion(player.getUniqueId());
                messages.sendWithSound(player, "creation.prompt_question", Map.of(), "ui.set_value");
            }
            case "duration" -> {
                sessions.awaitDuration(player.getUniqueId());
                messages.sendWithSound(player, "creation.prompt_duration", Map.of(), "ui.set_value");
            }
            case "option" -> {
                if (args.length < 2) { messages.send(player, "errors.invalid_args", Map.of()); return; }
                int idx;
                try { idx = Integer.parseInt(args[1]); } catch (Exception e) { messages.send(player, "errors.invalid_option", Map.of()); return; }
                if (idx < 1 || idx > 6) { messages.send(player, "errors.invalid_option", Map.of()); return; }
                sessions.awaitOption(player.getUniqueId(), idx - 1);
                String ordinal = com.ssquadteam.polls.util.OrdinalUtil.toOrdinal(idx);
                messages.sendWithSound(player, "creation.prompt_option_ordinal", Map.of("ordinal", ordinal), "ui.set_value");
            }
            default -> messages.send(player, "errors.invalid_args", Map.of());
        }
    }

    @Nullable
    private UUID parseUUID(String raw) {
        try { return UUID.fromString(raw); } catch (Exception e) { return null; }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return Arrays.asList("create", "list", "view", "vote", "close", "remove");
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("view") || args[0].equalsIgnoreCase("close") || args[0].equalsIgnoreCase("remove"))) {
            return storage.getAllPolls().stream().map(p -> p.getId().toString()).collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
