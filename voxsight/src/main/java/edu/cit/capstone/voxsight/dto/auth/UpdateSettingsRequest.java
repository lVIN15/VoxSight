package edu.cit.capstone.voxsight.dto.auth;

public class UpdateSettingsRequest {
    private String defaultVoicePart;
    private String soundfontTone;
    private Float staffDimmingOpacity;
    private Integer pitchToleranceCents;

    public UpdateSettingsRequest() {}

    public String getDefaultVoicePart() { return defaultVoicePart; }
    public void setDefaultVoicePart(String defaultVoicePart) { this.defaultVoicePart = defaultVoicePart; }

    public String getSoundfontTone() { return soundfontTone; }
    public void setSoundfontTone(String soundfontTone) { this.soundfontTone = soundfontTone; }

    public Float getStaffDimmingOpacity() { return staffDimmingOpacity; }
    public void setStaffDimmingOpacity(Float staffDimmingOpacity) { this.staffDimmingOpacity = staffDimmingOpacity; }

    public Integer getPitchToleranceCents() { return pitchToleranceCents; }
    public void setPitchToleranceCents(Integer pitchToleranceCents) { this.pitchToleranceCents = pitchToleranceCents; }
}
