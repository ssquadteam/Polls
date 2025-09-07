package com.ssquadteam.polls.service.session;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PollCreationSession {

    public enum Awaiting { NONE, CODE, QUESTION, DURATION, OPTION }

    private final UUID creator;
    private String code;
    private String question; // minimessage
    private Long durationSeconds; // nullable
    private final List<String> options = new ArrayList<>(); // up to 6

    private Awaiting awaiting = Awaiting.NONE;
    private int awaitingOptionIndex = -1;

    private boolean editingExisting;
    private java.util.UUID editingPollId;

    public PollCreationSession(UUID creator) {
        this.creator = creator;
        for (int i = 0; i < 6; i++) options.add(null);
    }

    public UUID getCreator() { return creator; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getQuestion() { return question; }

    public void setQuestion(String question) { this.question = question; }

    public Long getDurationSeconds() { return durationSeconds; }

    public void setDurationSeconds(Long durationSeconds) { this.durationSeconds = durationSeconds; }

    public String getOption(int index) { return options.get(index); }

    public void setOption(int index, String value) { options.set(index, value); }

    public List<String> getDefinedOptions() {
        List<String> out = new ArrayList<>();
        for (String s : options) if (s != null && !s.isBlank()) out.add(s);
        return out;
    }

    public Awaiting getAwaiting() { return awaiting; }

    public int getAwaitingOptionIndex() { return awaitingOptionIndex; }

    public void awaitQuestion() { awaiting = Awaiting.QUESTION; awaitingOptionIndex = -1; }

    public void awaitDuration() { awaiting = Awaiting.DURATION; awaitingOptionIndex = -1; }

    public void awaitOption(int index) { awaiting = Awaiting.OPTION; awaitingOptionIndex = index; }

    public void awaitCode() { awaiting = Awaiting.CODE; awaitingOptionIndex = -1; }

    public void clearAwaiting() { awaiting = Awaiting.NONE; awaitingOptionIndex = -1; }

    public long previewClosesAt() {
        long base = Instant.now().getEpochSecond();
        return base + (durationSeconds == null ? 0 : durationSeconds);
    }

    public boolean isEditingExisting() { return editingExisting; }

    public void startEditing(java.util.UUID pollId) { this.editingExisting = true; this.editingPollId = pollId; }

    public java.util.UUID getEditingPollId() { return editingPollId; }
}
