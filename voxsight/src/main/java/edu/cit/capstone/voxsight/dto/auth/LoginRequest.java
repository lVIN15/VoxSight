package edu.cit.capstone.voxsight.dto.auth;

public class LoginRequest {

    private String identifier; // can be email or username
    private String email;
    private String username;
    private String password;

    public LoginRequest() {}

    public LoginRequest(String identifier, String password) {
        this.identifier = identifier;
        this.password = password;
    }

    public String getIdentifier() {
        if (identifier != null && !identifier.isBlank()) return identifier.trim();
        if (email != null && !email.isBlank()) return email.trim();
        if (username != null && !username.isBlank()) return username.trim();
        return "";
    }

    public void setIdentifier(String identifier) { this.identifier = identifier; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
