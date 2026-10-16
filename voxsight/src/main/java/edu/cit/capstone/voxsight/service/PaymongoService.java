package edu.cit.capstone.voxsight.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import edu.cit.capstone.voxsight.dto.CreateCheckoutRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PaymongoService {

    private static final Logger log = LoggerFactory.getLogger(PaymongoService.class);
    private static final String PAYMONGO_CHECKOUT_URL = "https://api.paymongo.com/v1/checkout_sessions";

    @Value("${paymongo.secret-key:}")
    private String secretKey;

    @Value("${paymongo.success-url:https://voxsight.app/payment/success}")
    private String defaultSuccessUrl;

    @Value("${paymongo.cancel-url:https://voxsight.app/payment/cancel}")
    private String defaultCancelUrl;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public PaymongoService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();
    }

    public boolean isConfigured() {
        return secretKey != null && !secretKey.isBlank();
    }

    private String buildBasicAuthHeader() {
        String credentials = secretKey.trim() + ":";
        String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        return "Basic " + encoded;
    }

    public Map<String, Object> createCheckoutSession(CreateCheckoutRequest request) throws Exception {
        if (!isConfigured()) {
            throw new IllegalStateException("PayMongo secret key (PAYMONGO_SECRET_KEY) is not configured on the server.");
        }

        int amountCentavos = (request != null && request.getAmountCentavos() != null && request.getAmountCentavos() >= 2000)
                ? request.getAmountCentavos()
                : 10000; // Default ₱100.00

        String planName = (request != null && request.getPlanName() != null && !request.getPlanName().isBlank())
                ? request.getPlanName()
                : "VoxSight Premium (Monthly)";

        String successUrl = (request != null && request.getSuccessUrl() != null && !request.getSuccessUrl().isBlank())
                ? request.getSuccessUrl()
                : defaultSuccessUrl;

        String cancelUrl = (request != null && request.getCancelUrl() != null && !request.getCancelUrl().isBlank())
                ? request.getCancelUrl()
                : defaultCancelUrl;

        // Candidate payment method sets in case some methods aren't activated on the merchant's PayMongo account
        List<List<String>> methodCandidates = List.of(
                List.of("gcash", "paymaya", "card", "grab_pay", "qrph"),
                List.of("gcash", "paymaya", "card"),
                List.of("gcash", "card"),
                List.of("card")
        );

        String lastErrorBody = null;
        int lastStatusCode = 500;

        for (List<String> methods : methodCandidates) {
            String payload = buildCheckoutPayload(request, planName, amountCentavos, successUrl, cancelUrl, methods);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(PAYMONGO_CHECKOUT_URL))
                    .timeout(Duration.ofSeconds(30))
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .header("Authorization", buildBasicAuthHeader())
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            lastStatusCode = response.statusCode();
            lastErrorBody = response.body();

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode data = root.path("data");
                JsonNode attributes = data.path("attributes");

                String sessionId = data.path("id").asText("");
                String checkoutUrl = attributes.path("checkout_url").asText("");

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("success", true);
                result.put("sessionId", sessionId);
                result.put("checkoutUrl", checkoutUrl);
                result.put("successUrl", successUrl);
                result.put("cancelUrl", cancelUrl);
                return result;
            }

            // Retry with fewer payment methods only if PayMongo complains about payment_method_types
            if (lastErrorBody != null && lastErrorBody.contains("payment_method_types")) {
                log.warn("[PayMongo] Retrying checkout creation with fallback payment methods due to: {}", lastErrorBody);
                continue;
            } else {
                break;
            }
        }

        log.error("[PayMongo] Failed to create checkout session (HTTP {}): {}", lastStatusCode, lastErrorBody);
        throw new RuntimeException("PayMongo error (HTTP " + lastStatusCode + "): " + extractPaymongoErrorMessage(lastErrorBody));
    }

    public Map<String, Object> getCheckoutSessionStatus(String sessionId) throws Exception {
        if (!isConfigured()) {
            throw new IllegalStateException("PayMongo secret key (PAYMONGO_SECRET_KEY) is not configured on the server.");
        }

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(PAYMONGO_CHECKOUT_URL + "/" + sessionId))
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/json")
                .header("Authorization", buildBasicAuthHeader())
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RuntimeException("Failed to fetch checkout session (HTTP " + response.statusCode() + "): "
                    + extractPaymongoErrorMessage(response.body()));
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode attributes = root.path("data").path("attributes");
        String paymentIntentStatus = attributes.path("payment_intent").path("attributes").path("status").asText("");

        boolean isPaid = "succeeded".equalsIgnoreCase(paymentIntentStatus);
        JsonNode payments = attributes.path("payments");
        if (!isPaid && payments.isArray()) {
            for (JsonNode payment : payments) {
                String pStatus = payment.path("attributes").path("status").asText("");
                if ("paid".equalsIgnoreCase(pStatus) || "succeeded".equalsIgnoreCase(pStatus)) {
                    isPaid = true;
                    break;
                }
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("sessionId", sessionId);
        result.put("paid", isPaid);
        result.put("paymentIntentStatus", paymentIntentStatus);
        return result;
    }

    private String buildCheckoutPayload(
            CreateCheckoutRequest request,
            String planName,
            int amountCentavos,
            String successUrl,
            String cancelUrl,
            List<String> paymentMethods
    ) throws Exception {
        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode data = root.putObject("data");
        ObjectNode attributes = data.putObject("attributes");

        attributes.put("send_email_receipt", true);
        attributes.put("show_description", true);
        attributes.put("show_line_items", true);
        attributes.put("description", "VoxSight Premium Subscription - Elevate your choral practice sessions");
        attributes.put("success_url", successUrl);
        attributes.put("cancel_url", cancelUrl);

        if (request != null) {
            String username = request.getUsername();
            String email = request.getEmail();
            boolean hasValidEmail = email != null && email.contains("@") && email.contains(".");
            boolean hasName = username != null && !username.isBlank();
            if (hasName || hasValidEmail) {
                ObjectNode billing = attributes.putObject("billing");
                if (hasName) {
                    billing.put("name", username.trim());
                }
                if (hasValidEmail) {
                    billing.put("email", email.trim());
                }
            }
        }

        ArrayNode lineItems = attributes.putArray("line_items");
        ObjectNode item = lineItems.addObject();
        item.put("currency", "PHP");
        item.put("amount", amountCentavos);
        item.put("name", planName);
        item.put("quantity", 1);
        item.put("description", "Increased sheet music uploads, upgraded cloud storage, and sheet downloads.");

        ArrayNode methodsNode = attributes.putArray("payment_method_types");
        for (String method : paymentMethods) {
            methodsNode.add(method);
        }

        return objectMapper.writeValueAsString(root);
    }

    private String extractPaymongoErrorMessage(String body) {
        if (body == null || body.isBlank()) {
            return "Unknown PayMongo error";
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode errors = root.path("errors");
            if (errors.isArray() && !errors.isEmpty()) {
                return errors.get(0).path("detail").asText(body);
            }
        } catch (Exception ignored) {
        }
        return body;
    }
}

