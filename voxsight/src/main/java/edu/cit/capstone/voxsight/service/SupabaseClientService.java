package edu.cit.capstone.voxsight.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;

@Service
public class SupabaseClientService {

    private static final Logger log = LoggerFactory.getLogger(SupabaseClientService.class);

    private final String supabaseUrl;
    private final String supabaseSecretKey;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public SupabaseClientService(
            @Value("${SUPABASE_URL:https://zffkuhdslokigfhawwrd.supabase.co}") String supabaseUrl,
            @Value("${SUPABASE_SECRET_KEY:}") String supabaseSecretKey,
            ObjectMapper objectMapper) {
        this.supabaseUrl = supabaseUrl.replaceAll("/+$", "");
        this.supabaseSecretKey = supabaseSecretKey;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    public boolean isConfigured() {
        return supabaseSecretKey != null && !supabaseSecretKey.isBlank();
    }

    public static String hashPasswordSha256(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 error: " + e.getMessage(), e);
        }
    }

    public Optional<SupabaseUser> findByIdentifier(String identifier) {
        if (!isConfigured() || identifier == null || identifier.isBlank()) return Optional.empty();
        try {
            String encoded = URLEncoder.encode(identifier.trim(), StandardCharsets.UTF_8);
            // Check email or username
            String query = "or=(email.ilike." + encoded + ",username.ilike." + encoded + ")";
            String url = supabaseUrl + "/rest/v1/User?" + query + "&limit=1";

            HttpRequest request = buildRequest(URI.create(url))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                SupabaseUser[] users = objectMapper.readValue(response.body(), SupabaseUser[].class);
                if (users.length > 0) {
                    return Optional.of(users[0]);
                }
            }
        } catch (Exception e) {
            log.error("Supabase findByIdentifier failed: {}", e.getMessage());
        }
        return Optional.empty();
    }

    public Optional<SupabaseUser> findById(String id) {
        if (!isConfigured() || id == null || id.isBlank()) return Optional.empty();
        try {
            String url = supabaseUrl + "/rest/v1/User?id=eq." + URLEncoder.encode(id.trim(), StandardCharsets.UTF_8) + "&limit=1";
            HttpRequest request = buildRequest(URI.create(url)).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                SupabaseUser[] users = objectMapper.readValue(response.body(), SupabaseUser[].class);
                if (users.length > 0) {
                    return Optional.of(users[0]);
                }
            }
        } catch (Exception e) {
            log.error("Supabase findById failed: {}", e.getMessage());
        }
        return Optional.empty();
    }

    public boolean existsByEmail(String email) {
        if (!isConfigured() || email == null) return false;
        try {
            String url = supabaseUrl + "/rest/v1/User?email=ilike." + URLEncoder.encode(email.trim(), StandardCharsets.UTF_8) + "&limit=1";
            HttpRequest request = buildRequest(URI.create(url)).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                SupabaseUser[] users = objectMapper.readValue(response.body(), SupabaseUser[].class);
                return users.length > 0;
            }
        } catch (Exception e) {
            log.error("Supabase existsByEmail error: {}", e.getMessage());
        }
        return false;
    }

    public boolean existsByUsername(String username) {
        if (!isConfigured() || username == null) return false;
        try {
            String url = supabaseUrl + "/rest/v1/User?username=ilike." + URLEncoder.encode(username.trim(), StandardCharsets.UTF_8) + "&limit=1";
            HttpRequest request = buildRequest(URI.create(url)).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                SupabaseUser[] users = objectMapper.readValue(response.body(), SupabaseUser[].class);
                return users.length > 0;
            }
        } catch (Exception e) {
            log.error("Supabase existsByUsername error: {}", e.getMessage());
        }
        return false;
    }

    public Optional<SupabaseUser> register(String username, String email, String password) {
        if (!isConfigured()) return Optional.empty();
        try {
            String hash = hashPasswordSha256(password);
            Map<String, Object> bodyMap = new HashMap<>();
            bodyMap.put("username", username.trim());
            bodyMap.put("email", email.trim().toLowerCase());
            bodyMap.put("password_hash", hash);

            String json = objectMapper.writeValueAsString(bodyMap);

            HttpRequest request = buildRequest(URI.create(supabaseUrl + "/rest/v1/User"))
                    .header("Content-Type", "application/json")
                    .header("Prefer", "return=representation")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 201 || response.statusCode() == 200) {
                SupabaseUser[] users = objectMapper.readValue(response.body(), SupabaseUser[].class);
                if (users.length > 0) {
                    return Optional.of(users[0]);
                }
            } else {
                log.error("Supabase register error: {} - {}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.error("Supabase register exception: {}", e.getMessage());
        }
        return Optional.empty();
    }

    public boolean updatePassword(String id, String newPassword) {
        if (!isConfigured() || id == null) return false;
        try {
            String hash = hashPasswordSha256(newPassword);
            Map<String, Object> map = Collections.singletonMap("password_hash", hash);
            String json = objectMapper.writeValueAsString(map);

            HttpRequest request = buildRequest(URI.create(supabaseUrl + "/rest/v1/User?id=eq." + URLEncoder.encode(id.trim(), StandardCharsets.UTF_8)))
                    .header("Content-Type", "application/json")
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200 || response.statusCode() == 204;
        } catch (Exception e) {
            log.error("Supabase updatePassword error: {}", e.getMessage());
            return false;
        }
    }

    public boolean updateProfile(String id, String newUsername, String newEmail) {
        if (!isConfigured() || id == null) return false;
        try {
            Map<String, Object> map = new HashMap<>();
            map.put("username", newUsername.trim());
            map.put("email", newEmail.trim().toLowerCase());
            String json = objectMapper.writeValueAsString(map);

            HttpRequest request = buildRequest(URI.create(supabaseUrl + "/rest/v1/User?id=eq." + URLEncoder.encode(id.trim(), StandardCharsets.UTF_8)))
                    .header("Content-Type", "application/json")
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200 || response.statusCode() == 204;
        } catch (Exception e) {
            log.error("Supabase updateProfile error: {}", e.getMessage());
            return false;
        }
    }

    public void updateLastLogin(String id) {
        if (!isConfigured() || id == null) return;
        try {
            Map<String, Object> map = Collections.singletonMap("last_login", java.time.Instant.now().toString());
            String json = objectMapper.writeValueAsString(map);

            HttpRequest request = buildRequest(URI.create(supabaseUrl + "/rest/v1/User?id=eq." + URLEncoder.encode(id.trim(), StandardCharsets.UTF_8)))
                    .header("Content-Type", "application/json")
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            log.warn("Supabase updateLastLogin error: {}", e.getMessage());
        }
    }

    private HttpRequest.Builder buildRequest(URI uri) {
        return HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(15))
                .header("apikey", supabaseSecretKey)
                .header("Authorization", "Bearer " + supabaseSecretKey)
                .header("User-Agent", "VoxSight-Backend/1.0");
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SupabaseUser {
        @JsonProperty("id")
        private String id;

        @JsonProperty("username")
        private String username;

        @JsonProperty("email")
        private String email;

        @JsonProperty("password_hash")
        private String passwordHash;

        @JsonProperty("account_created")
        private String accountCreated;

        @JsonProperty("last_login")
        private String lastLogin;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }

        public String getPasswordHash() { return passwordHash; }
        public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

        public String getAccountCreated() { return accountCreated; }
        public void setAccountCreated(String accountCreated) { this.accountCreated = accountCreated; }

        public String getLastLogin() { return lastLogin; }
        public void setLastLogin(String lastLogin) { this.lastLogin = lastLogin; }
    }
}
