package com.ssquadteam.polls.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PollDraft {
    private String code;
    private UUID creatorId;
    private String question;
    private Long durationSeconds;
    private List<String> options;
    private long createdAtEpochSeconds;

    public PollDraft(String code, UUID creatorId, String question, Long durationSeconds, List<String> options, long createdAtEpochSeconds) {
        this.code = code;
        this.creatorId = creatorId;
        this.question = question;
        this.durationSeconds = durationSeconds;
        this.options = new ArrayList<>(options);
        this.createdAtEpochSeconds = createdAtEpochSeconds;
    }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public UUID getCreatorId() { return creatorId; }
    public void setCreatorId(UUID creatorId) { this.creatorId = creatorId; }

    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }

    public Long getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(Long durationSeconds) { this.durationSeconds = durationSeconds; }

    public List<String> getOptions() { return new ArrayList<>(options); }
    public void setOptions(List<String> options) { this.options = new ArrayList<>(options); }

    public long getCreatedAtEpochSeconds() { return createdAtEpochSeconds; }
    public void setCreatedAtEpochSeconds(long createdAtEpochSeconds) { this.createdAtEpochSeconds = createdAtEpochSeconds; }
}
