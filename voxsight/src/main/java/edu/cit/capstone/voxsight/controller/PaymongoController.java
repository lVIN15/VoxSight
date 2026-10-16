package edu.cit.capstone.voxsight.controller;

import edu.cit.capstone.voxsight.dto.CreateCheckoutRequest;
import edu.cit.capstone.voxsight.service.PaymongoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/payments")
public class PaymongoController {

    private final PaymongoService paymongoService;

    public PaymongoController(PaymongoService paymongoService) {
        this.paymongoService = paymongoService;
    }

    @PostMapping("/create-checkout-session")
    public ResponseEntity<Map<String, Object>> createCheckoutSession(
            @RequestBody(required = false) CreateCheckoutRequest request
    ) {
        try {
            Map<String, Object> result = paymongoService.createCheckoutSession(request);
            return ResponseEntity.ok(result);
        } catch (IllegalStateException e) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error);
        } catch (Exception e) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(error);
        }
    }

    @GetMapping("/checkout-session/{sessionId}")
    public ResponseEntity<Map<String, Object>> getCheckoutSessionStatus(
            @PathVariable String sessionId
    ) {
        try {
            Map<String, Object> result = paymongoService.getCheckoutSessionStatus(sessionId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(error);
        }
    }

    @GetMapping(value = "/success", produces = MediaType.TEXT_HTML_VALUE)
    public String paymentSuccessPage() {
        return """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>Payment Successful - VoxSight</title>
                    <style>
                        body { font-family: sans-serif; background: #38036B; color: #fff; display: flex; align-items: center; justify-content: center; height: 100vh; margin: 0; text-align: center; }
                        .card { background: rgba(255,255,255,0.1); padding: 32px 24px; border-radius: 20px; max-width: 320px; }
                        h1 { color: #FACC15; margin-bottom: 8px; }
                        p { color: #E5E7EB; font-size: 14px; }
                    </style>
                </head>
                <body>
                    <div class="card">
                        <h1>Payment Successful!</h1>
                        <p>Welcome to VoxSight Premium. Returning you to the app...</p>
                    </div>
                </body>
                </html>
                """;
    }

    @GetMapping(value = "/cancel", produces = MediaType.TEXT_HTML_VALUE)
    public String paymentCancelPage() {
        return """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>Payment Cancelled - VoxSight</title>
                    <style>
                        body { font-family: sans-serif; background: #F4F4FA; color: #1F2937; display: flex; align-items: center; justify-content: center; height: 100vh; margin: 0; text-align: center; }
                        .card { background: #fff; padding: 32px 24px; border-radius: 20px; max-width: 320px; box-shadow: 0 4px 12px rgba(0,0,0,0.08); }
                        h2 { color: #DC2626; margin-bottom: 8px; }
                        p { color: #6B7280; font-size: 14px; }
                    </style>
                </head>
                <body>
                    <div class="card">
                        <h2>Payment Cancelled</h2>
                        <p>Returning you to VoxSight...</p>
                    </div>
                </body>
                </html>
                """;
    }
}

