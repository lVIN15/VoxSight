package edu.cit.capstone.voxsight.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users", indexes = {
    @Index(name = "idx_user_email", columnList = "email"),
    @Index(name = "idx_user_username", columnList = "username")
})
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String username;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(nullable = false, length = 255)
    private String password; // BCrypt-hashed password

    @Column(name = "default_voice_part", length = 20)
    private String defaultVoicePart = "SOPRANO"; // SOPRANO, ALTO, TENOR, BASS

    @Column(name = "soundfont_tone", length = 20)
    private String soundfontTone = "AAH"; // AAH, OOH

    @Column(name = "staff_dimming_opacity")
    private Float staffDimmingOpacity = 0.20f; // Default 20% opacity for inactive parts

    @Column(name = "pitch_tolerance_cents")
    private Integer pitchToleranceCents = 25; // Default normal pitch tolerance: +/- 25 cents

    @Column(name = "member_status", length = 30)
    private String memberStatus = "FREE";

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public UserEntity() {}

    public UserEntity(String username, String email, String password) {
        this.username = username;
        this.email = email;
        this.password = password;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

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

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
