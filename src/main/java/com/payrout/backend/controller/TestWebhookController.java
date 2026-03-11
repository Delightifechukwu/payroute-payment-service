package com.payrout.backend.controller;

import com.payrout.backend.service.WebhookService;
import com.payrout.backend.util.CryptoUtil;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/test")
public class TestWebhookController {

    private final WebhookService webhookService;

    public TestWebhookController(WebhookService webhookService) {
        this.webhookService = webhookService;
    }

    /**
     * Test endpoint to simulate completing a payment
     * Usage: POST /test/complete-payment/{providerReference}
     */
    @PostMapping("/complete-payment/{providerReference}")
    public String completePayment(@PathVariable String providerReference) {
        String body = "{\"reference\":\"" + providerReference + "\",\"status\":\"completed\"}";
        String signature = CryptoUtil.hmacSha256Hex("dev_secret_change_me", body.getBytes());

        webhookService.handle(signature, body.getBytes());
        return "OK - Payment completed";
    }

    /**
     * Test endpoint to simulate failing a payment
     * Usage: POST /test/fail-payment/{providerReference}
     */
    @PostMapping("/fail-payment/{providerReference}")
    public String failPayment(@PathVariable String providerReference) {
        String body = "{\"reference\":\"" + providerReference + "\",\"status\":\"failed\"}";
        String signature = CryptoUtil.hmacSha256Hex("dev_secret_change_me", body.getBytes());

        webhookService.handle(signature, body.getBytes());
        return "OK - Payment failed";
    }
}
