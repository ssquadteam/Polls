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
            sendHelp(sender, label);
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
            case "cancelcreation" -> handleCancel(sender);
            case "edit" -> handleEdit(sender, Arrays.copyOfRange(args, 1, args.length));
            default -> messages.send(sender, "errors.invalid_args", Map.of());
        }
        return true;
    }

    private void sendHelp(CommandSender sender, String label) {
        messages.send(sender, "help.header", Map.of());
        messages.send(sender, "help.usage_create", Map.of("label", label));
        messages.send(sender, "help.usage_edit", Map.of("label", label));
        messages.send(sender, "help.usage_view", Map.of("label", label));
        messages.send(sender, "help.usage_vote", Map.of("label", label));
        messages.send(sender, "help.usage_list", Map.of("label", label));
        messages.send(sender, "help.usage_close", Map.of("label", label));
        messages.send(sender, "help.usage_remove", Map.of("label", label));
        messages.send(sender, "help.usage_cancelcreation", Map.of("label", label));
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

        if (args.length < 1) {
            messages.send(player, "errors.missing_id", Map.of());
            return;
        }

        String providedId = plugin.getMessageService().sanitizeForMiniMessage(args[0]).trim();
        if (providedId.isEmpty()) {
            messages.send(player, "errors.missing_id", Map.of());
            return;
        }
        // Ensure not in use already for new creation
        if (pollManager.getStorage().findByIdOrCode(providedId) != null) {
            messages.send(player, "errors.code_in_use", Map.of());
            return;
        }

        Long durationSeconds = null;
        String question = null;
        if (args.length >= 2) {
            Long parsed = DurationUtil.parseDurationSeconds(args[1]);
            if (parsed != null) {
                durationSeconds = parsed;
                if (args.length >= 3) {
                    question = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                }
            } else {
                // Not a duration; treat rest as question
                question = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
            }
        }

        PollCreationSession session = sessions.startSession(player.getUniqueId());
        session.setCode(providedId);
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
        Poll poll = storage.findByIdOrCode(args[0]);
        if (poll == null) { messages.send(sender, "errors.invalid_poll", Map.of()); return; }
        if (poll.getStatus() == PollStatus.CLOSED) {
            messages.send(sender, "close.success", Map.of("code", poll.getCode()));
            return;
        }
        pollManager.closePoll(poll, true);
        messages.send(sender, "close.success", Map.of("code", poll.getCode()));
        if (sender instanceof Player p) plugin.getMessageService().playSound(p, "ui.set_value");
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
        Poll poll = storage.findByIdOrCode(args[0]);
        if (poll == null) { messages.send(sender, "errors.invalid_poll", Map.of()); return; }
        pollManager.removePoll(poll.getId());
        messages.send(sender, "remove.success", Map.of("code", poll.getCode()));
        if (sender instanceof Player p) plugin.getMessageService().playSound(p, "ui.cancel");
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
                    "code", poll.getCode(),
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
        Poll poll = storage.findByIdOrCode(args[0]);
        if (poll == null) { messages.send(player, "errors.invalid_poll", Map.of()); return; }
        messages.send(player, "view.opened", Map.of());
        plugin.getBookFactory().openVotingBook(player, poll);
        plugin.getMessageService().playSound(player, "ui.open_voting");
    }

    private void handleVote(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "errors.only_players", Map.of());
            return;
        }
        if (args.length < 2) { messages.send(player, "errors.invalid_args", Map.of()); return; }
        Poll poll = storage.findByIdOrCode(args[0]);
        if (poll == null) { messages.send(player, "errors.invalid_poll", Map.of()); plugin.getMessageService().playSound(player, "ui.error"); return; }
        int index;
        try { index = Integer.parseInt(args[1]); } catch (Exception e) { messages.send(player, "errors.invalid_option", Map.of()); plugin.getMessageService().playSound(player, "ui.error"); return; }

        pollManager.vote(player, poll.getId(), index);
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
            plugin.getMessageService().playSound(player, "ui.cancel");
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
            return Arrays.asList("create", "list", "view", "vote", "close", "remove", "edit", "cancelcreation");
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("view") || args[0].equalsIgnoreCase("close") || args[0].equalsIgnoreCase("remove") || args[0].equalsIgnoreCase("edit"))) {
            List<String> items = new ArrayList<>();
            for (Poll p : storage.getAllPolls()) {
                items.add(p.getId().toString());
                if (p.getCode() != null && !p.getCode().isBlank()) items.add(p.getCode());
            }
            return items;
        }
        return Collections.emptyList();
    }
}
