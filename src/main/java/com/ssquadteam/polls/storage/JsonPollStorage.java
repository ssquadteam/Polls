package com.ssquadteam.polls.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.ssquadteam.polls.PollsPlugin;
import com.ssquadteam.polls.model.Poll;
import com.ssquadteam.polls.model.PollStatus;

import java.io.*;
import java.lang.reflect.Type;
import java.time.Instant;
import java.util.*;

public class JsonPollStorage implements PollStorage {

    private final PollsPlugin plugin;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private File pollFile;
    private File voteFile;

    private final Map<UUID, Poll> polls = new HashMap<>();
    private final Map<UUID, Map<UUID, Integer>> votes = new HashMap<>();
    private final Map<String, UUID> codeIndex = new HashMap<>();

    public JsonPollStorage(PollsPlugin plugin) { this.plugin = plugin; }

    @Override
    public void init() {
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
        pollFile = new File(plugin.getDataFolder(), plugin.getConfig().getString("storage.json.file", "polls.json"));
        voteFile = new File(plugin.getDataFolder(), "votes.json");
        load();
    }

    private void load() {
        try {
            if (pollFile.exists()) {
                try (Reader r = new FileReader(pollFile)) {
                    Type type = new TypeToken<Map<String, JsonPoll>>(){}.getType();
                    Map<String, JsonPoll> map = gson.fromJson(r, type);
                    polls.clear();
                    codeIndex.clear();
                    if (map != null) {
                        for (Map.Entry<String, JsonPoll> e : map.entrySet()) {
                            UUID id = UUID.fromString(e.getKey());
                            JsonPoll jp = e.getValue();
                            Poll p = new Poll(id, jp.question, jp.options, jp.createdAt, jp.closesAt, PollStatus.valueOf(jp.status));
                            p.setCode(jp.code);
                            polls.put(id, p);
                            if (jp.code != null && !jp.code.isBlank()) codeIndex.put(jp.code.toLowerCase(), id);
                        }
                    }
                }
            }
            if (voteFile.exists()) {
                try (Reader r = new FileReader(voteFile)) {
                    Type type = new TypeToken<Map<String, Map<String, Integer>>>(){}.getType();
                    Map<String, Map<String, Integer>> raw = gson.fromJson(r, type);
                    votes.clear();
                    if (raw != null) {
                        for (Map.Entry<String, Map<String, Integer>> e : raw.entrySet()) {
                            UUID pollId = UUID.fromString(e.getKey());
                            Map<UUID, Integer> inner = new HashMap<>();
                            for (Map.Entry<String, Integer> v : e.getValue().entrySet()) {
                                inner.put(UUID.fromString(v.getKey()), v.getValue());
                            }
                            votes.put(pollId, inner);
                        }
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to load JSON storage: " + e.getMessage());
        }
    }

    private void persist() {
        try {
            Map<String, JsonPoll> out = new HashMap<>();
            for (Map.Entry<UUID, Poll> e : polls.entrySet()) {
                Poll p = e.getValue();
                out.put(e.getKey().toString(), new JsonPoll(p.getCode(), p.getQuestion(), p.getOptions(), p.getCreatedAtEpochSeconds(), p.getClosesAtEpochSeconds(), p.getStatus().name()));
            }
            try (Writer w = new FileWriter(pollFile)) { gson.toJson(out, w); }

            Map<String, Map<String, Integer>> vout = new HashMap<>();
            for (Map.Entry<UUID, Map<UUID, Integer>> e : votes.entrySet()) {
                Map<String, Integer> inner = new HashMap<>();
                for (Map.Entry<UUID, Integer> v : e.getValue().entrySet()) inner.put(v.getKey().toString(), v.getValue());
                vout.put(e.getKey().toString(), inner);
            }
            try (Writer w = new FileWriter(voteFile)) { gson.toJson(vout, w); }
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save JSON storage: " + e.getMessage());
        }
    }

    @Override
    public void close() { persist(); }

    @Override
    public void savePoll(Poll poll) {
        polls.put(poll.getId(), poll);
        if (poll.getCode() != null) codeIndex.put(poll.getCode().toLowerCase(), poll.getId());
        persist();
    }

    @Override
    public Poll getPoll(UUID id) { return polls.get(id); }

    @Override
    public List<Poll> getAllPolls() {
        List<Poll> list = new ArrayList<>(polls.values());
        list.sort(Comparator.comparingLong(Poll::getCreatedAtEpochSeconds).reversed());
        return list;
    }

    @Override
    public void removePoll(UUID id) {
        polls.remove(id);
        votes.remove(id);
        codeIndex.values().removeIf(u -> u.equals(id));
        persist();
    }

    @Override
    public void saveVote(UUID pollId, UUID player, int optionIndex) {
        votes.computeIfAbsent(pollId, k -> new HashMap<>()).putIfAbsent(player, optionIndex);
        persist();
    }

    @Override
    public boolean hasVoted(UUID pollId, UUID player) {
        Map<UUID, Integer> inner = votes.get(pollId);
        return inner != null && inner.containsKey(player);
    }

    @Override
    public Map<Integer, Integer> getVoteTally(UUID pollId) {
        Map<Integer, Integer> tally = new HashMap<>();
        Map<UUID, Integer> inner = votes.get(pollId);
        if (inner != null) {
            for (Integer v : inner.values()) tally.merge(v, 1, Integer::sum);
        }
        return tally;
    }

    @Override
    public Poll findByIdOrCode(String idOrCode) {
        try {
            UUID id = UUID.fromString(idOrCode);
            return getPoll(id);
        } catch (Exception ignored) {}
        UUID mapped = codeIndex.get(idOrCode.toLowerCase());
        if (mapped != null) return getPoll(mapped);
        return null;
    }

    private static class JsonPoll {
        String code;
        String question;
        List<String> options;
        long createdAt;
        long closesAt;
        String status;
        public JsonPoll(String code, String question, List<String> options, long createdAt, long closesAt, String status) {
            this.code = code; this.question = question; this.options = options; this.createdAt = createdAt; this.closesAt = closesAt; this.status = status;
        }
    }
}
