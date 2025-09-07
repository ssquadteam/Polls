package com.ssquadteam.polls.storage;

import com.ssquadteam.polls.model.Poll;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface PollStorage {
    void init();
    void close();

    void savePoll(Poll poll);
    Poll getPoll(UUID id);
    Poll findByIdOrCode(String idOrCode);
    List<Poll> getAllPolls();
    void removePoll(UUID id);

    void saveVote(UUID pollId, UUID player, int optionIndex);
    boolean hasVoted(UUID pollId, UUID player);
    Map<Integer, Integer> getVoteTally(UUID pollId);
}