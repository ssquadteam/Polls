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
        String editQ = plugin.getConfig().getString("books.creation.editQuestionButton", "[Edit Question]");
        String editD = plugin.getConfig().getString("books.creation.editDurationButton", "[Edit Duration]");
        String publish = plugin.getConfig().getString("books.creation.publishButton", "[Publish Poll]");
        String cancel = plugin.getConfig().getString("books.creation.cancelButton", "[Cancel]");
        String optionFmt = plugin.getConfig().getString("books.creation.optionButtonFormat", "[Set Option {index}]");
        String optionUnset = plugin.getConfig().getString("books.creation.optionUnset", "[not set]");
        String pageNote = plugin.getConfig().getString("books.creation.pageNote", "Set up to 6 options.");

        List<Component> pages = new ArrayList<>();

        String question = session.getQuestion() == null ? optionUnset : session.getQuestion();
        String duration = session.getDurationSeconds() == null ? optionUnset : plugin.getMessageService().formatRelativeTime(session.previewClosesAt());

        Component page = mm.deserialize(header)
                .append(newline())
                .append(mm.deserialize("<gray>Question:</gray> "))
                .append(mm.deserialize(question))
                .append(newline())
                .append(mm.deserialize("<gray>Duration:</gray> "))
                .append(mm.deserialize(duration))
                .append(newline())
                .append(newline())
                .append(clickable(editQ, "/poll edit question"))
                .append(Component.text(" "))
                .append(clickable(editD, "/poll edit duration"))
                .append(newline())
                .append(newline())
                .append(mm.deserialize(pageNote))
                .append(newline());

        for (int i = 0; i < 6; i++) {
            String label = optionFmt.replace("{index}", String.valueOf(i + 1));
            String value = session.getOption(i) == null ? optionUnset : session.getOption(i);
            page = page.append(clickable(label, "/poll edit option " + (i + 1)))
                    .append(mm.deserialize(" <gray>"))
                    .append(mm.deserialize(value))
                    .append(mm.deserialize("</gray>"))
                    .append(newline());
        }

        page = page.append(newline())
                .append(clickable(publish, "/poll publish"))
                .append(Component.text(" "))
                .append(clickable(cancel, "/poll cancel"));

        meta.title(Component.text(mm.stripTags(title)));
        meta.author(Component.text(mm.stripTags(author)));
        meta.addPages(page);
        book.setItemMeta(meta);

        open(player, book);
    }

    public void openVotingBook(Player player, Poll poll) {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();

        String title = plugin.getConfig().getString("books.voting.title", "Poll");
        String author = plugin.getConfig().getString("books.voting.author", "Polls");
        String headerFmt = plugin.getConfig().getString("books.voting.headerFormat", "Q: {question}\nCloses: {closes_at}\n");
        String optionLineFmt = plugin.getConfig().getString("books.voting.optionLineFormat", "{icon} {option} (click to vote)");
        String alreadyVotedLineFmt = plugin.getConfig().getString("books.voting.alreadyVotedLineFormat", "{icon} {option} (you voted)");
        List<String> icons = plugin.getConfig().getStringList("icons.default");
        if (icons == null || icons.isEmpty()) {
            icons = List.of("1.", "2.", "3.", "4.", "5.", "6.");
        }

        String header = plugin.getMessageService().apply(Map.of(
                "question", poll.getQuestion(),
                "closes_at", plugin.getMessageService().formatAbsoluteTime(poll.getClosesAtEpochSeconds())
        ), headerFmt);

        Component page = mm.deserialize(header).append(newline());

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
            Component lineComp = mm.deserialize(line);
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

    private Component clickable(String mmText, String command) {
        Component base = mm.deserialize(mmText);
        return base.clickEvent(ClickEvent.runCommand(command));
    }

    private Component newline() {
        return Component.text("\n");
    }

    private void open(Player player, ItemStack book) {
        Bukkit.getScheduler().runTask(plugin, () -> player.openBook(book));
    }
}
