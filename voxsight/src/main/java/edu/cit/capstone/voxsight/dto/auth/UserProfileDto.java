package edu.cit.capstone.voxsight.dto.auth;

import edu.cit.capstone.voxsight.model.UserEntity;

public class UserProfileDto {
    private Long id;
    private String username;
    private String email;
    private String defaultVoicePart;
    private String soundfontTone;
    private Float staffDimmingOpacity;
    private Integer pitchToleranceCents;
    private String memberStatus;

    public UserProfileDto() {}

    public UserProfileDto(UserEntity entity) {
        this.id = entity.getId();
        this.username = entity.getUsername();
        this.email = entity.getEmail();
        this.defaultVoicePart = entity.getDefaultVoicePart();
        this.soundfontTone = entity.getSoundfontTone();
        this.staffDimmingOpacity = entity.getStaffDimmingOpacity();
        this.pitchToleranceCents = entity.getPitchToleranceCents();
        this.memberStatus = entity.getMemberStatus();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getDefaultVoicePart() { return defaultVoicePart; }
    public void setDefaultVoicePart(String defaultVoicePart) { this.defaultVoicePart = defaultVoicePart; }

    public String getSoundfontTone() { return soundfontTone; }
    public void setSoundfontTone(String soundfontTone) { this.soundfontTone = soundfontTone; }

    public Float getStaffDimmingOpacity() { return staffDimmingOpacity; }
    public void setStaffDimmingOpacity(Float staffDimmingOpacity) { this.staffDimmingOpacity = staffDimmingOpacity; }

    public Integer getPitchToleranceCents() { return pitchToleranceCents; }
    public void setPitchToleranceCents(Integer pitchToleranceCents) { this.pitchToleranceCents = pitchToleranceCents; }

    public String getMemberStatus() { return memberStatus; }
    public void setMemberStatus(String memberStatus) { this.memberStatus = memberStatus; }
}
