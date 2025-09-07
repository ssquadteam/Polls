package com.ssquadteam.polls.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Poll {
    private UUID id;
    private String code;
    private String question; // MiniMessage
    private List<String> options; // MiniMessage, max 6
    private long createdAtEpochSeconds;
    private long closesAtEpochSeconds;
    private PollStatus status;

    public Poll(UUID id, String question, List<String> options, long createdAtEpochSeconds, long closesAtEpochSeconds, PollStatus status) {
        this.id = id;
        this.question = question;
        this.options = new ArrayList<>(options);
        this.createdAtEpochSeconds = createdAtEpochSeconds;
        this.closesAtEpochSeconds = closesAtEpochSeconds;
        this.status = status;
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
    public List<String> getOptions() { return options; }
    public void setOptions(List<String> options) { this.options = new ArrayList<>(options); }
    public long getCreatedAtEpochSeconds() { return createdAtEpochSeconds; }
    public long getClosesAtEpochSeconds() { return closesAtEpochSeconds; }
    public void setClosesAtEpochSeconds(long closesAtEpochSeconds) { this.closesAtEpochSeconds = closesAtEpochSeconds; }
    public PollStatus getStatus() { return status; }
    public void setStatus(PollStatus status) { this.status = status; }

    public boolean isOpen() {
        return status == PollStatus.OPEN && Instant.now().getEpochSecond() < closesAtEpochSeconds;
    }
}
