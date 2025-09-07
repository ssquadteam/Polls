package com.ssquadteam.polls.service;

import com.ssquadteam.polls.PollsPlugin;
import com.ssquadteam.polls.model.Poll;
import com.ssquadteam.polls.model.PollStatus;
import com.ssquadteam.polls.storage.PollStorage;
import com.ssquadteam.polls.service.session.PollCreationSession;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BookFactory {

    private final PollsPlugin plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public BookFactory(PollsPlugin plugin) {
        this.plugin = plugin;
    }

    public void openCreationBook(Player player, PollCreationSession session) {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();

        String title = plugin.getConfig().getString("books.creation.title", "Poll Creator");
        String author = plugin.getConfig().getString("books.creation.author", "Polls");
        String header = plugin.getConfig().getString("books.creation.header", "Create a new poll\n");
        String editCode = plugin.getConfig().getString("books.creation.editCodeButton", "[Edit ID]");
        String editQ = plugin.getConfig().getString("books.creation.editQuestionButton", "[Edit Question]");
        String editD = plugin.getConfig().getString("books.creation.editDurationButton", "[Edit Duration]");
        String publish = plugin.getConfig().getString("books.creation.publishButton", "[Publish Poll]");
        String cancel = plugin.getConfig().getString("books.creation.cancelButton", "[Cancel]");
        String optionFmt = plugin.getConfig().getString("books.creation.optionButtonFormat", "[Set Option {index}]");
        String optionUnset = plugin.getConfig().getString("books.creation.optionUnset", "[Not Set]");
        String pageNote = plugin.getConfig().getString("books.creation.pageNote", "Set up to 6 options.");
        String hoverCode = plugin.getConfig().getString("books.creation.hover.code", "Click to set ID");
        String hoverQuestion = plugin.getConfig().getString("books.creation.hover.question", "Click to set question");
        String hoverDuration = plugin.getConfig().getString("books.creation.hover.duration", "Click to set duration");
        String hoverOption = plugin.getConfig().getString("books.creation.hover.option", "Click to set option {index}");
        String hoverPublish = plugin.getConfig().getString("books.creation.hover.publish", "Click to publish poll");
        String hoverCancel = plugin.getConfig().getString("books.creation.hover.cancel", "Click to cancel");

        List<Component> pages = new ArrayList<>();

        String code = session.getCode() == null ? optionUnset : session.getCode();
        String question = session.getQuestion() == null ? optionUnset : session.getQuestion();
        String duration = session.getDurationSeconds() == null ? optionUnset : plugin.getMessageService().formatRelativeTime(session.previewClosesAt());

        Component page1 = mm.deserialize(header)
                .append(newline())
                .append(mm.deserialize("<black>ID:</black> "))
                .append(withHover(mm.deserialize(code), hoverCode))
                .append(newline())
                .append(mm.deserialize("<black>Question:</black> "))
                .append(withHover(mm.deserialize(question), hoverQuestion))
                .append(newline())
                .append(mm.deserialize("<black>Duration:</black> "))
                .append(withHover(mm.deserialize(duration), hoverDuration))
                .append(newline())
                .append(newline())
                .append(clickable(editCode, "/poll edit code", hoverCode))
                .append(Component.text(" "))
                .append(clickable(editQ, "/poll edit question", hoverQuestion))
                .append(Component.text(" "))
                .append(clickable(editD, "/poll edit duration", hoverDuration))
                .append(newline())
                .append(newline())
                .append(mm.deserialize(pageNote))
                .append(newline());

        // options 1-3 on page 1
        for (int i = 0; i < 3; i++) {
            String label = optionFmt.replace("{index}", String.valueOf(i + 1));
            String value = session.getOption(i) == null ? optionUnset : session.getOption(i);
            String hover = hoverOption.replace("{index}", String.valueOf(i + 1));
            page1 = page1.append(clickable(label, "/poll edit option " + (i + 1), hover))
                    .append(mm.deserialize(" <black>"))
                    .append(withHover(mm.deserialize(value), hover))
                    .append(mm.deserialize("</black>"))
                    .append(newline());
        }

        // page 2
        Component page2 = mm.deserialize(header).append(newline());
        for (int i = 3; i < 6; i++) {
            String label = optionFmt.replace("{index}", String.valueOf(i + 1));
            String value = session.getOption(i) == null ? optionUnset : session.getOption(i);
            String hover = hoverOption.replace("{index}", String.valueOf(i + 1));
            page2 = page2.append(clickable(label, "/poll edit option " + (i + 1), hover))
                    .append(mm.deserialize(" <black>"))
                    .append(withHover(mm.deserialize(value), hover))
                    .append(mm.deserialize("</black>"))
                    .append(newline());
        }
        page2 = page2.append(newline())
                .append(clickable(publish, "/poll publish", hoverPublish))
                .append(Component.text(" "))
                .append(clickable(cancel, "/poll cancel", hoverCancel));

        meta.title(Component.text(mm.stripTags(title)));
        meta.author(Component.text(mm.stripTags(author)));
        meta.addPages(page1, page2);
        book.setItemMeta(meta);

        open(player, book);
        plugin.getMessageService().playSound(player, "ui.open_creation");
    }

    public void openVotingBook(Player player, Poll poll) {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();

        String title = plugin.getConfig().getString("books.voting.title", "Poll");
        String author = plugin.getConfig().getString("books.voting.author", "Polls");
        String headerFmt = plugin.getConfig().getString("books.voting.headerFormat", null);
        String qLabel = plugin.getConfig().getString("books.voting.labels.question", "<gray>Q:</gray>");
        String cLabel = plugin.getConfig().getString("books.voting.labels.closes", "<gray>Closes:</gray>");
        String optionLineFmt = plugin.getConfig().getString("books.voting.optionLineFormat", "{icon} {option} (click to vote)");
        String alreadyVotedLineFmt = plugin.getConfig().getString("books.voting.alreadyVotedLineFormat", "{icon} {option} (you voted)");
        String hoverOption = plugin.getConfig().getString("books.voting.hover.option", "Pick this option.");
        String hoverAlready = plugin.getConfig().getString("books.voting.hover.alreadyVotedOption", "You already voted.");
        String hoverQuestion = plugin.getConfig().getString("books.voting.hover.question", "Poll question");
        List<String> icons = plugin.getConfig().getStringList("icons.default");
        if (icons == null || icons.isEmpty()) {
            icons = List.of("1.", "2.", "3.", "4.", "5.", "6.");
        }

        Component page;
        if (headerFmt != null) {
            String header = plugin.getMessageService().apply(Map.of(
                    "question", poll.getQuestion(),
                    "closes_at", plugin.getMessageService().formatAbsoluteTime(poll.getClosesAtEpochSeconds())
            ), headerFmt);
            page = mm.deserialize(header).append(newline());
        } else {
            page = mm.deserialize(qLabel + " ")
                    .append(withHover(mm.deserialize(poll.getQuestion()), hoverQuestion))
                    .append(newline())
                    .append(mm.deserialize(cLabel + " "))
                    .append(mm.deserialize(plugin.getMessageService().formatAbsoluteTime(poll.getClosesAtEpochSeconds())))
                    .append(newline());
        }

        boolean alreadyVoted;
        try {
            alreadyVoted = plugin.getPollManager().getStorage().hasVoted(poll.getId(), player.getUniqueId());
        } catch (Exception e) {
            alreadyVoted = false;
        }

        for (int i = 0; i < poll.getOptions().size(); i++) {
            String icon = i < icons.size() ? icons.get(i) : String.valueOf(i + 1) + ".";
            String fmt = alreadyVoted ? alreadyVotedLineFmt : optionLineFmt;
            String line = plugin.getMessageService().apply(Map.of(
                    "icon", icon,
                    "option", poll.getOptions().get(i)
            ), fmt);
            Component lineComp = withHover(mm.deserialize(line), alreadyVoted ? hoverAlready : hoverOption);
            if (!alreadyVoted && poll.isOpen()) {
                lineComp = lineComp.clickEvent(ClickEvent.runCommand("/poll vote " + poll.getId() + " " + i));
            }
            page = page.append(lineComp).append(newline());
        }

        meta.title(Component.text(mm.stripTags(title)));
        meta.author(Component.text(mm.stripTags(author)));
        meta.addPages(page);
        book.setItemMeta(meta);

        open(player, book);
    }

    private Component clickable(String mmText, String command, String hover) {
        Component base = mm.deserialize(mmText);
        base = base.clickEvent(ClickEvent.runCommand(command));
        if (hover != null && !hover.isBlank()) {
            base = base.hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(mm.deserialize(hover)));
        }
        return base;
    }

    private Component newline() {
        return Component.text("\n");
    }

    private void open(Player player, ItemStack book) {
        Bukkit.getScheduler().runTask(plugin, () -> player.openBook(book));
    }

    private Component withHover(Component component, String hover) {
        if (hover == null || hover.isBlank()) return component;
        return component.hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(mm.deserialize(hover)));
    }
}
