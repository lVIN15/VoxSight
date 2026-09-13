package edu.cit.capstone.voxsight.controller;

import edu.cit.capstone.voxsight.dto.auth.*;
import edu.cit.capstone.voxsight.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/profile")
    public ResponseEntity<?> getProfile(@RequestParam("userId") Long userId) {
        return userService.getProfile(userId)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(SimpleMessageResponse.error("User not found.")));
    }

    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(
            @RequestParam("userId") Long userId,
            @Valid @RequestBody UpdateProfileRequest request) {
        try {
            return userService.updateProfile(userId, request)
                    .<ResponseEntity<?>>map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(SimpleMessageResponse.error("User not found.")));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(SimpleMessageResponse.error(ex.getMessage()));
        }
    }

    @GetMapping("/settings")
    public ResponseEntity<?> getSettings(@RequestParam("userId") Long userId) {
        return userService.getSettings(userId)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(SimpleMessageResponse.error("User not found.")));
    }

    @PutMapping("/settings")
    public ResponseEntity<?> updateSettings(
            @RequestParam("userId") Long userId,
            @RequestBody UpdateSettingsRequest request) {
        return userService.updateSettings(userId, request)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(SimpleMessageResponse.error("User not found.")));
    }

    @PostMapping("/change-password")
    public ResponseEntity<SimpleMessageResponse> changePassword(
            @RequestParam("userId") Long userId,
            @Valid @RequestBody ChangePasswordRequest request) {
        SimpleMessageResponse response = userService.changePassword(userId, request);
        if (!response.isSuccess()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
        return ResponseEntity.ok(response);
    }
}
