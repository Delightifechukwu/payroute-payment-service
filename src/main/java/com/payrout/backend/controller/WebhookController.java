package com.payrout.backend.controller;


import com.payrout.backend.config.RawBodyCachingFilter;
import com.payrout.backend.service.WebhookService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/webhooks")
public class WebhookController {

    private final WebhookService webhookService;

    public WebhookController(WebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public String providerWebhook(@RequestHeader(value = "X-Webhook-Signature", required = false) String sig,
                                  HttpServletRequest request) {
        byte[] raw = (byte[]) request.getAttribute(RawBodyCachingFilter.RAW_BODY_ATTR);
        if (raw == null) raw = new byte[0];

        try {
            webhookService.handle(sig, raw);
        } catch (DataIntegrityViolationException duplicate) {
            // unique constraint hit => same webhook processed twice => return 200 (idempotent)
        } catch (Exception ignored) {
            // Return 200 anyway to avoid provider retry storms; errors are logged in webhook_events
        }
        return "OK";
    }
}