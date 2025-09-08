package com.ssquadteam.polls;

import com.ssquadteam.polls.commands.PollCommand;
import com.ssquadteam.polls.listener.BookListener;
import com.ssquadteam.polls.listener.ChatListener;
import com.ssquadteam.polls.model.Poll;
import com.ssquadteam.polls.model.PollStatus;
import com.ssquadteam.polls.service.BookFactory;
import com.ssquadteam.polls.service.MessageService;
import com.ssquadteam.polls.service.PollManager;
import com.ssquadteam.polls.service.SessionManager;
import com.ssquadteam.polls.storage.JsonPollStorage;
import com.ssquadteam.polls.storage.PollStorage;
import com.ssquadteam.polls.storage.SQLitePollStorage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class PollsPlugin extends JavaPlugin {

    private static PollsPlugin instance;

    private PollStorage storage;
    private PollManager pollManager;
    private SessionManager sessionManager;
    private MessageService messageService;
    private BookFactory bookFactory;

    public static PollsPlugin getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        saveResource("messages.yml", false);

        this.messageService = new MessageService(this);
        this.bookFactory = new BookFactory(this);

        // Choose storage backend
        String type = getConfig().getString("storage.type", "sqlite").toLowerCase();
        switch (type) {
            case "json" -> this.storage = new JsonPollStorage(this);
            case "sqlite" -> this.storage = new SQLitePollStorage(this);
            default -> {
                getLogger().warning("Unknown storage.type '" + type + "', defaulting to sqlite");
                this.storage = new SQLitePollStorage(this);
            }
        }
        this.storage.init();

        this.pollManager = new PollManager(this, storage);
        this.sessionManager = new SessionManager(this);

        // Load open polls and schedule closings
        List<Poll> polls = storage.getAllPolls();
        polls.stream().filter(p -> p.getStatus() == PollStatus.OPEN).forEach(pollManager::trackOpenPoll);

        // Register command and listeners
        PollCommand pollCommand = new PollCommand(this);
        getCommand("poll").setExecutor(pollCommand);
        getCommand("poll").setTabCompleter(pollCommand);

        Bukkit.getPluginManager().registerEvents(new ChatListener(this), this);
        Bukkit.getPluginManager().registerEvents(new BookListener(this), this);

        getLogger().info("Polls enabled with storage: " + type);
    }

    @Override
    public void onDisable() {
        if (pollManager != null) pollManager.shutdown();
        if (storage != null) storage.close();
    }

    public PollManager getPollManager() { return pollManager; }
    public SessionManager getSessionManager() { return sessionManager; }
    public MessageService getMessageService() { return messageService; }
    public BookFactory getBookFactory() { return bookFactory; }
}
