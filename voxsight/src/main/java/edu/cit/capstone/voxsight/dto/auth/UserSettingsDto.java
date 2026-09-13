package edu.cit.capstone.voxsight.dto.auth;

import edu.cit.capstone.voxsight.model.UserEntity;

public class UserSettingsDto {
    private String defaultVoicePart;
    private String soundfontTone;
    private Float staffDimmingOpacity;
    private Integer pitchToleranceCents;

    public UserSettingsDto() {}

    public UserSettingsDto(UserEntity user) {
        this.defaultVoicePart = user.getDefaultVoicePart();
        this.soundfontTone = user.getSoundfontTone();
        this.staffDimmingOpacity = user.getStaffDimmingOpacity();
        this.pitchToleranceCents = user.getPitchToleranceCents();
    }

    public String getDefaultVoicePart() { return defaultVoicePart; }
    public void setDefaultVoicePart(String defaultVoicePart) { this.defaultVoicePart = defaultVoicePart; }

    public String getSoundfontTone() { return soundfontTone; }
    public void setSoundfontTone(String soundfontTone) { this.soundfontTone = soundfontTone; }

    public Float getStaffDimmingOpacity() { return staffDimmingOpacity; }
    public void setStaffDimmingOpacity(Float staffDimmingOpacity) { this.staffDimmingOpacity = staffDimmingOpacity; }

    public Integer getPitchToleranceCents() { return pitchToleranceCents; }
    public void setPitchToleranceCents(Integer pitchToleranceCents) { this.pitchToleranceCents = pitchToleranceCents; }
}
