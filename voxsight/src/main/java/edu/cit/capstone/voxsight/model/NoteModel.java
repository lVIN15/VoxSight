package edu.cit.capstone.voxsight.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class NoteModel {
    private String pitch;      // e.g., "C4"
    private String duration;   // Tone.js notation, e.g., "4n"
    private double durationBeats; // in beats (quarter note = 1)
    @JsonProperty("time")
    private double startTime;  // in beats (quarter note = 1)
    private int staff;         // staff number (optional)
    private int voice;         // voice number (optional)

    public NoteModel() {}

    public NoteModel(String pitch, String duration, double startTime, int staff, int voice) {
        this.pitch = pitch;
        this.duration = duration;
        this.startTime = startTime;
        this.staff = staff;
        this.voice = voice;
    }

    public NoteModel(String pitch, String duration, double durationBeats, double startTime, int staff, int voice) {
        this.pitch = pitch;
        this.duration = duration;
        this.durationBeats = durationBeats;
        this.startTime = startTime;
        this.staff = staff;
        this.voice = voice;
    }

    public String getPitch() { return pitch; }
    public void setPitch(String pitch) { this.pitch = pitch; }

    public String getDuration() { return duration; }
    public void setDuration(String duration) { this.duration = duration; }

    public double getDurationBeats() { return durationBeats; }
    public void setDurationBeats(double durationBeats) { this.durationBeats = durationBeats; }

    public double getStartTime() { return startTime; }
    public void setStartTime(double startTime) { this.startTime = startTime; }

    public int getStaff() { return staff; }
    public void setStaff(int staff) { this.staff = staff; }

    public int getVoice() { return voice; }
    public void setVoice(int voice) { this.voice = voice; }
}
