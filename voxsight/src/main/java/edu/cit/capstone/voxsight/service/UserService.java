package edu.cit.capstone.voxsight.service;

import edu.cit.capstone.voxsight.dto.auth.*;
import edu.cit.capstone.voxsight.model.UserEntity;
import edu.cit.capstone.voxsight.repository.UserRepository;
import edu.cit.capstone.voxsight.service.SupabaseClientService.SupabaseUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SupabaseClientService supabaseClientService;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       SupabaseClientService supabaseClientService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.supabaseClientService = supabaseClientService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String trimmedUsername = request.getUsername().trim();
        String trimmedEmail = request.getEmail().trim().toLowerCase();

        if (supabaseClientService.isConfigured()) {
            if (supabaseClientService.existsByEmail(trimmedEmail)) {
                return AuthResponse.error("An account with this email already exists.");
            }
            if (supabaseClientService.existsByUsername(trimmedUsername)) {
                return AuthResponse.error("Username is already taken.");
            }
            Optional<SupabaseUser> sbUser = supabaseClientService.register(trimmedUsername, trimmedEmail, request.getPassword());
            if (sbUser.isEmpty()) {
                return AuthResponse.error("Failed to create account in database.");
            }
            log.info("Successfully registered user in Supabase: {} ({})", trimmedUsername, trimmedEmail);
        } else {
            if (userRepository.existsByEmail(trimmedEmail)) {
                return AuthResponse.error("An account with this email already exists.");
            }
            if (userRepository.existsByUsername(trimmedUsername)) {
                return AuthResponse.error("Username is already taken.");
            }
        }

        // Sync or save to local UserEntity for fast local access and numeric ID
        UserEntity user = userRepository.findByEmail(trimmedEmail)
                .orElseGet(() -> new UserEntity(trimmedUsername, trimmedEmail, passwordEncoder.encode(request.getPassword())));
        user.setUsername(trimmedUsername);
        user.setEmail(trimmedEmail);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        UserEntity savedUser = userRepository.save(user);

        String sessionToken = UUID.randomUUID().toString();
        return AuthResponse.ok("Registration successful.", sessionToken, new UserProfileDto(savedUser));
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String identifier = request.getIdentifier().trim();

        if (supabaseClientService.isConfigured()) {
            Optional<SupabaseUser> sbUserOpt = supabaseClientService.findByIdentifier(identifier);
            if (sbUserOpt.isPresent()) {
                SupabaseUser sbUser = sbUserOpt.get();
                String rawPass = request.getPassword();
                String sha256 = SupabaseClientService.hashPasswordSha256(rawPass);

                boolean match = (sbUser.getPasswordHash() != null && sbUser.getPasswordHash().equalsIgnoreCase(sha256))
                        || (sbUser.getPasswordHash() != null && passwordEncoder.matches(rawPass, sbUser.getPasswordHash()));

                if (!match) {
                    return AuthResponse.error("Invalid email/username or password.");
                }

                // Update last login in Supabase asynchronously
                supabaseClientService.updateLastLogin(sbUser.getId());

                // Sync with local UserEntity so settings and numeric ID are always available
                String sbEmail = sbUser.getEmail() != null ? sbUser.getEmail().toLowerCase() : "";
                String sbUsername = sbUser.getUsername() != null ? sbUser.getUsername() : identifier;

                UserEntity user = userRepository.findByEmail(sbEmail)
                        .or(() -> userRepository.findByUsername(sbUsername))
                        .orElseGet(() -> new UserEntity(sbUsername, sbEmail, passwordEncoder.encode(rawPass)));

                user.setUsername(sbUsername);
                user.setEmail(sbEmail);
                user.setPassword(passwordEncoder.encode(rawPass));
                UserEntity savedUser = userRepository.save(user);

                String sessionToken = UUID.randomUUID().toString();
                log.info("User logged in via Supabase: {} ({})", sbUsername, sbEmail);
                return AuthResponse.ok("Login successful.", sessionToken, new UserProfileDto(savedUser));
            }
            // Supabase configured and identifier was not found in Supabase
            return AuthResponse.error("Invalid email/username or password.");
        }

        // Local DB fallback when Supabase is not configured
        Optional<UserEntity> userOpt = identifier.contains("@")
                ? userRepository.findByEmail(identifier.toLowerCase())
                : userRepository.findByUsername(identifier);

        if (userOpt.isEmpty()) {
            userOpt = userRepository.findByEmailOrUsername(identifier.toLowerCase(), identifier);
        }

        if (userOpt.isEmpty()) {
            return AuthResponse.error("Invalid email/username or password.");
        }

        UserEntity user = userOpt.get();

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            return AuthResponse.error("Invalid email/username or password.");
        }

        String sessionToken = UUID.randomUUID().toString();
        return AuthResponse.ok("Login successful.", sessionToken, new UserProfileDto(user));
    }

    @Transactional(readOnly = true)
    public Optional<UserProfileDto> getProfile(Long userId) {
        return userRepository.findById(userId).map(UserProfileDto::new);
    }

    @Transactional
    public Optional<UserProfileDto> updateProfile(Long userId, UpdateProfileRequest request) {
        return userRepository.findById(userId).map(user -> {
            String newUsername = request.getUsername().trim();
            String newEmail = request.getEmail().trim().toLowerCase();

            if (supabaseClientService.isConfigured()) {
                Optional<SupabaseUser> sbUserOpt = supabaseClientService.findByIdentifier(user.getEmail())
                        .or(() -> supabaseClientService.findByIdentifier(user.getUsername()));
                if (sbUserOpt.isPresent()) {
                    SupabaseUser sbUser = sbUserOpt.get();
                    if (!sbUser.getUsername().equalsIgnoreCase(newUsername) && supabaseClientService.existsByUsername(newUsername)) {
                        throw new IllegalArgumentException("Username is already taken.");
                    }
                    if (!sbUser.getEmail().equalsIgnoreCase(newEmail) && supabaseClientService.existsByEmail(newEmail)) {
                        throw new IllegalArgumentException("Email is already taken.");
                    }
                    supabaseClientService.updateProfile(sbUser.getId(), newUsername, newEmail);
                }
            } else {
                if (!user.getUsername().equalsIgnoreCase(newUsername) && userRepository.existsByUsername(newUsername)) {
                    throw new IllegalArgumentException("Username is already taken.");
                }
                if (!user.getEmail().equalsIgnoreCase(newEmail) && userRepository.existsByEmail(newEmail)) {
                    throw new IllegalArgumentException("Email is already taken.");
                }
            }

            user.setUsername(newUsername);
            user.setEmail(newEmail);
            if (request.getDefaultVoicePart() != null && !request.getDefaultVoicePart().isBlank()) {
                user.setDefaultVoicePart(request.getDefaultVoicePart().toUpperCase());
            }

            UserEntity updated = userRepository.save(user);
            return new UserProfileDto(updated);
        });
    }

    @Transactional(readOnly = true)
    public Optional<UserSettingsDto> getSettings(Long userId) {
        return userRepository.findById(userId).map(UserSettingsDto::new);
    }

    @Transactional
    public Optional<UserSettingsDto> updateSettings(Long userId, UpdateSettingsRequest request) {
        return userRepository.findById(userId).map(user -> {
            if (request.getDefaultVoicePart() != null && !request.getDefaultVoicePart().isBlank()) {
                user.setDefaultVoicePart(request.getDefaultVoicePart().toUpperCase());
            }
            if (request.getSoundfontTone() != null && !request.getSoundfontTone().isBlank()) {
                user.setSoundfontTone(request.getSoundfontTone().toUpperCase());
            }
            if (request.getStaffDimmingOpacity() != null) {
                user.setStaffDimmingOpacity(request.getStaffDimmingOpacity());
            }
            if (request.getPitchToleranceCents() != null) {
                user.setPitchToleranceCents(request.getPitchToleranceCents());
            }

            UserEntity updated = userRepository.save(user);
            return new UserSettingsDto(updated);
        });
    }

    @Transactional
    public SimpleMessageResponse changePassword(Long userId, ChangePasswordRequest request) {
        Optional<UserEntity> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return SimpleMessageResponse.error("User not found.");
        }

        UserEntity user = userOpt.get();
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            return SimpleMessageResponse.error("Current password does not match.");
        }

        if (request.getNewPassword().length() < 6) {
            return SimpleMessageResponse.error("New password must be at least 6 characters.");
        }

        if (supabaseClientService.isConfigured()) {
            Optional<SupabaseUser> sbUserOpt = supabaseClientService.findByIdentifier(user.getEmail())
                    .or(() -> supabaseClientService.findByIdentifier(user.getUsername()));
            if (sbUserOpt.isPresent()) {
                supabaseClientService.updatePassword(sbUserOpt.get().getId(), request.getNewPassword());
            }
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        return SimpleMessageResponse.ok("Password updated successfully.");
    }
}
