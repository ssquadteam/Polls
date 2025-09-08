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
        String nextPageNote = plugin.getConfig().getString("books.creation.nextPageNote", "Turn to next page to continue →");
        String indent = plugin.getConfig().getString("books.creation.indent", "  ");
        String labelId = plugin.getConfig().getString("books.creation.labels.id", "<black>ID:</black>");
        String labelQ = plugin.getConfig().getString("books.creation.labels.question", "<black>Question:</black>");
        String labelD = plugin.getConfig().getString("books.creation.labels.duration", "<black>Duration:</black>");
        String hoverCode = plugin.getConfig().getString("books.creation.hover.code", "Click to set ID");
        String hoverQuestion = plugin.getConfig().getString("books.creation.hover.question", "Click to set question");
        String hoverDuration = plugin.getConfig().getString("books.creation.hover.duration", "Click to set duration");
        String hoverOption = plugin.getConfig().getString("books.creation.hover.option", "Click to set option {index}");
        String hoverPublish = plugin.getConfig().getString("books.creation.hover.publish", "Click to publish poll");
        String hoverCancel = plugin.getConfig().getString("books.creation.hover.cancel", "Click to cancel");

        List<Component> pages = new ArrayList<>();

        String code = session.getCode() == null ? optionUnset : plugin.getMessageService().sanitizeForMiniMessage(session.getCode());
        String question = session.getQuestion() == null ? optionUnset : plugin.getMessageService().sanitizeForMiniMessage(session.getQuestion());
        String duration = session.getDurationSeconds() == null ? optionUnset : plugin.getMessageService().formatRelativeTime(session.previewClosesAt());

        Component page1 = mm.deserialize(header)
                .append(newline())
                .append(mm.deserialize(indent))
                .append(clickable(label(labelId, code), "/poll edit code", hoverCode))
                .append(newline())
                .append(mm.deserialize(indent))
                .append(clickable(label(labelQ, question), "/poll edit question", hoverQuestion))
                .append(newline())
                .append(mm.deserialize(indent))
                .append(clickable(label(labelD, duration), "/poll edit duration", hoverDuration))
                .append(newline())
                .append(newline())
                .append(mm.deserialize(pageNote))
                .append(newline());

        // options 1-3 on page 1
        for (int i = 0; i < 3; i++) {
            String label = optionFmt.replace("{index}", String.valueOf(i + 1));
            String value = session.getOption(i) == null ? optionUnset : session.getOption(i);
            String hover = hoverOption.replace("{index}", String.valueOf(i + 1));
            page1 = page1.append(mm.deserialize(indent))
                    .append(clickable(label + " "+ value, "/poll edit option " + (i + 1), hover))
                    .append(newline());
        }

        // page 2
        Component page2 = mm.deserialize(header).append(newline());
        for (int i = 3; i < 6; i++) {
            String label = optionFmt.replace("{index}", String.valueOf(i + 1));
            String value = session.getOption(i) == null ? optionUnset : session.getOption(i);
            String hover = hoverOption.replace("{index}", String.valueOf(i + 1));
            page2 = page2.append(mm.deserialize(indent))
                    .append(clickable(label + " " + value, "/poll edit option " + (i + 1), hover))
                    .append(newline());
        }
        // If editing a closed poll, show a disabled note instead of publish button
        boolean editingClosed = session.isEditingExisting() && plugin.getPollManager().getStorage().getPoll(session.getEditingPollId()).getStatus() == PollStatus.CLOSED;
        if (editingClosed) {
            page2 = page2.append(newline())
                    .append(mm.deserialize(indent))
                    .append(mm.deserialize("<red>Closed polls cannot be edited.</red>"))
                    .append(newline())
                    .append(mm.deserialize(indent))
                    .append(clickable(cancel, "/poll cancel", hoverCancel));
        } else {
            page2 = page2.append(newline())
                    .append(mm.deserialize(indent))
                    .append(clickable(publish, "/poll publish", hoverPublish))
                    .append(Component.text(" "))
                    .append(clickable(cancel, "/poll cancel", hoverCancel));
        }

        // Next page info at end of page 1
        page1 = page1.append(newline()).append(mm.deserialize(indent + nextPageNote));

        meta.title(Component.text(mm.stripTags(title)));
        meta.author(Component.text(mm.stripTags(author)));
        meta.addPages(page1, page2);
        book.setItemMeta(meta);

        open(player, book);
        plugin.getSessionManager().markBookOpened(player.getUniqueId());
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
        String rowFormat = plugin.getConfig().getString("books.voting.rows.rowFormat", "%icon% <dark_gray>%question%</dark_gray> <gray>(%state%)</gray>");
        String nextPageHint = plugin.getConfig().getString("books.voting.nextPageNote", "<dark_gray>Turn page to continue →</dark_gray>");
        String closedMessage = plugin.getConfig().getString("books.voting.closedMessage", "<red>Poll has closed</red>");
        String indent = plugin.getConfig().getString("books.voting.indent", "  ");
        int maxQuestionLength = plugin.getConfig().getInt("books.voting.maxQuestionLength", 100);
        int maxOptionLength = plugin.getConfig().getInt("books.voting.maxOptionLength", 50);
        String stClick = plugin.getConfig().getString("books.voting.state.click", "<gray>Click to vote for %number%</gray>");
        String stVoted = plugin.getConfig().getString("books.voting.state.voted", "<yellow>You voted for %number%</yellow>");
        String stMajority = plugin.getConfig().getString("books.voting.state.majority", "<green>%number% has most votes</green>");
        String stClickClosed = plugin.getConfig().getString("books.voting.state.click_closed", "<gray>Voting closed</gray>");
        String stVotedClosed = plugin.getConfig().getString("books.voting.state.voted_closed", "<yellow>You voted for %number%</yellow>");
        String stMajorityClosed = plugin.getConfig().getString("books.voting.state.majority_closed", "<green>%number% had highest votes</green>");
        String hoverOption = plugin.getConfig().getString("books.voting.hover.option", "Pick this option.");
        String hoverAlready = plugin.getConfig().getString("books.voting.hover.alreadyVotedOption", "You already voted.");
        String hoverQuestion = plugin.getConfig().getString("books.voting.hover.question", "Poll question");
        List<String> iconsList = plugin.getConfig().getStringList("icons.default");
        if (iconsList == null || iconsList.isEmpty()) {
            iconsList = List.of("1.", "2.", "3.", "4.", "5.", "6.");
        }

        Component page;
        String truncatedQuestion = truncateText(poll.getQuestion(), maxQuestionLength);
        if (headerFmt != null) {
            String header = plugin.getMessageService().apply(Map.of(
                    "question", truncatedQuestion,
                    "closes_at", plugin.getMessageService().formatAbsoluteTime(poll.getClosesAtEpochSeconds())
            ), headerFmt);
            page = mm.deserialize(header).append(newline());
        } else {
            page = mm.deserialize(qLabel + " ")
                    .append(withHover(mm.deserialize(truncatedQuestion), hoverQuestion))
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
        Integer playerVote = plugin.getPollManager().getStorage().getPlayerVote(poll.getId(), player.getUniqueId());
        Map<Integer, Integer> tally = plugin.getPollManager().getStorage().getVoteTally(poll.getId());
        int majorityIndex = -1; int max = -1;
        for (Map.Entry<Integer, Integer> en : tally.entrySet()) {
            if (en.getValue() > max) { max = en.getValue(); majorityIndex = en.getKey(); }
        }

        // Build pages with 3 options per page maximum
        List<String> icons2 = iconsList;
        List<Component> pages = new ArrayList<>();
        int totalOptions = poll.getOptions().size();
        int optionsPerPage = 3;
        int totalPages = (int) Math.ceil((double) totalOptions / optionsPerPage);

        for (int pageNum = 0; pageNum < totalPages; pageNum++) {
            Component currentPage = page;
            if (pageNum > 0) {
                currentPage = Component.empty();
            }

            int startIndex = pageNum * optionsPerPage;
            int endIndex = Math.min(startIndex + optionsPerPage, totalOptions);

            for (int i = startIndex; i < endIndex; i++) {
                String icon = i < icons2.size() ? icons2.get(i) : String.valueOf(i + 1) + ".";
                String state;
                if (poll.isOpen()) {
                    if (majorityIndex == i) state = stMajority.replace("%number%", String.valueOf(i + 1));
                    else if (playerVote != null && playerVote == i) state = stVoted.replace("%number%", String.valueOf(i + 1));
                    else state = stClick.replace("%number%", String.valueOf(i + 1));
                } else {
                    if (majorityIndex == i) state = stMajorityClosed.replace("%number%", String.valueOf(i + 1));
                    else if (playerVote != null && playerVote == i) state = stVotedClosed.replace("%number%", String.valueOf(i + 1));
                    else state = stClickClosed.replace("%number%", String.valueOf(i + 1));
                }
                String truncatedOption = truncateText(poll.getOptions().get(i), maxOptionLength);
                String line = rowFormat
                        .replace("%icon%", icon)
                        .replace("%question%", truncatedOption)
                        .replace("%state%", state);
                Component comp = withHover(mm.deserialize(line), (playerVote != null || !poll.isOpen()) ? hoverAlready : hoverOption);
                if (playerVote == null && poll.isOpen()) {
                    comp = comp.clickEvent(ClickEvent.runCommand("/poll vote " + poll.getCode() + " " + i));
                }
                currentPage = currentPage.append(mm.deserialize(indent)).append(comp).append(newline());
            }

            if (pageNum < totalPages - 1) {
                currentPage = currentPage.append(newline()).append(mm.deserialize(indent + nextPageHint));
            }

            if (!poll.isOpen()) {
                currentPage = currentPage.append(newline()).append(mm.deserialize(indent + closedMessage));
            }

            pages.add(currentPage);
        }

        meta.title(Component.text(mm.stripTags(title)));
        meta.author(Component.text(mm.stripTags(author)));
        meta.addPages(pages.toArray(new Component[0]));
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

    private String truncateText(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength - 3) + "...";
    }

    private Component withHover(Component component, String hover) {
        if (hover == null || hover.isBlank()) return component;
        return component.hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(mm.deserialize(hover)));
    }

    private String label(String labelMm, String valueMm) {
        return labelMm + " " + valueMm;
    }
}
