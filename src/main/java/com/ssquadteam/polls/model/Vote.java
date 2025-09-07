package com.ssquadteam.polls.model;

import java.util.UUID;

public class Vote {
    private final UUID pollId;
    private final UUID playerUuid;
    private final int optionIndex; // 0-based
    private final long votedAtEpochSeconds;

    public Vote(UUID pollId, UUID playerUuid, int optionIndex, long votedAtEpochSeconds) {
        this.pollId = pollId;
        this.playerUuid = playerUuid;
        this.optionIndex = optionIndex;
        this.votedAtEpochSeconds = votedAtEpochSeconds;
    }

    public UUID getPollId() { return pollId; }
    public UUID getPlayerUuid() { return playerUuid; }
    public int getOptionIndex() { return optionIndex; }
    public long getVotedAtEpochSeconds() { return votedAtEpochSeconds; }
}
