package edu.cit.capstone.voxsight.dto.auth;

public class AuthResponse {
    private boolean success;
    private String message;
    private String token;
    private UserProfileDto user;

    public AuthResponse() {}

    public AuthResponse(boolean success, String message, String token, UserProfileDto user) {
        this.success = success;
        this.message = message;
        this.token = token;
        this.user = user;
    }

    public static AuthResponse ok(String message, String token, UserProfileDto user) {
        return new AuthResponse(true, message, token, user);
    }

    public static AuthResponse error(String message) {
        return new AuthResponse(false, message, null, null);
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public UserProfileDto getUser() { return user; }
    public void setUser(UserProfileDto user) { this.user = user; }
}
