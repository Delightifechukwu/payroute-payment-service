package com.payrout.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.payrout.backend.domain.AccountBalance;
import com.payrout.backend.domain.Transaction;
import com.payrout.backend.domain.WebhookEvent;
import com.payrout.backend.repository.AccountBalanceRepository;
import com.payrout.backend.repository.TransactionRepository;
import com.payrout.backend.repository.WebhookEventRepository;
import com.payrout.backend.state.TransactionStateMachine;
import com.payrout.backend.util.CryptoUtil;
import com.payrout.backend.util.HashUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class WebhookService {

    private final WebhookEventRepository webhookRepo;
    private final TransactionRepository txRepo;
    private final TransactionStateMachine sm;
    private final LedgerService ledgerService;
    private final AccountBalanceRepository balanceRepo;
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${payroute.webhook.secret}")
    private String secret;

    public WebhookService(WebhookEventRepository webhookRepo,
                          TransactionRepository txRepo,
                          TransactionStateMachine sm,
                          LedgerService ledgerService,
                          AccountBalanceRepository balanceRepo) {
        this.webhookRepo = webhookRepo;
        this.txRepo = txRepo;
        this.sm = sm;
        this.ledgerService = ledgerService;
        this.balanceRepo = balanceRepo;
    }

    @Transactional
    public void handle(String signature, byte[] rawBodyBytes) {
        String rawBody = new String(rawBodyBytes, StandardCharsets.UTF_8);
        String expected = CryptoUtil.hmacSha256Hex(secret, rawBodyBytes);
        System.out.println("EXPECTED SIGNATURE: " + expected);

        if (!CryptoUtil.constantTimeEquals(expected, signature)) {
            // still log for forensics, but mark ERROR
            logEvent(null, rawBody, signature, "ERROR", "invalid_signature");
            return;
        }

        String fingerprint = HashUtil.sha256Hex(rawBody);

        // log raw event first (idempotency via unique fingerprint+provider_ref is in DB constraint)
        // We’ll extract provider_reference later when parsing JSON; for now log with nullable ref.
        WebhookEvent ev = new WebhookEvent();
        ev.setId(UUID.randomUUID());
        ev.setProviderReference(extractProviderReference(rawBody)); // implement robustly
        ev.setEventFingerprint(fingerprint);
        ev.setRawBody(rawBody);
        ev.setSignature(signature);
        ev.setReceivedAt(Instant.now());
        ev.setProcessingStatus("RECEIVED");
        webhookRepo.save(ev);

        // If duplicate (constraint hit), catch in controller and return 200. (See controller.)
        Optional<Transaction> txOpt = txRepo.findByProviderReference(ev.getProviderReference());
        if (txOpt.isEmpty()) {
            // IMPORTANT: return 200 anyway; provider will retry otherwise.
            ev.setProcessingStatus("PROCESSED");
            ev.setProcessedAt(Instant.now());
            webhookRepo.save(ev);
            return;
        }

        Transaction tx = txOpt.get();
        String next = extractStatus(rawBody); // "completed" / "failed" mapped to COMPLETED/FAILED

        // validate transition
        try {
            sm.validate(tx.getStatus(), next);
        } catch (Exception ignored) {
            ev.setProcessingStatus("PROCESSED");
            ev.setProcessedAt(Instant.now());
            webhookRepo.save(ev);
            return; // ignore invalid transitions, still 200
        }

        // Handle settlement or reversal
        if ("COMPLETED".equals(next)) {
            handleCompleted(tx);
        } else if ("FAILED".equals(next)) {
            handleFailed(tx);
        }

        tx.setStatus(next);
        tx.setUpdatedAt(Instant.now());
        txRepo.save(tx);

        ev.setProcessingStatus("PROCESSED");
        ev.setProcessedAt(Instant.now());
        webhookRepo.save(ev);
    }

    private void logEvent(String providerRef, String rawBody, String sig, String status, String err) {
        WebhookEvent ev = new WebhookEvent();
        ev.setId(UUID.randomUUID());
        ev.setProviderReference(providerRef);
        ev.setEventFingerprint(HashUtil.sha256Hex(rawBody));
        ev.setRawBody(rawBody);
        ev.setSignature(sig);
        ev.setReceivedAt(Instant.now());
        ev.setProcessingStatus(status);
        ev.setErrorMessage(err);
        webhookRepo.save(ev);
    }

    private String extractProviderReference(String rawBody) {

        try {

            JsonNode node = mapper.readTree(rawBody);

            if (node.has("reference")) {
                return node.get("reference").asText();
            }

            if (node.has("provider_reference")) {
                return node.get("provider_reference").asText();
            }

            throw new IllegalArgumentException("Provider reference missing");

        } catch (Exception e) {
            throw new RuntimeException("Failed to parse webhook provider reference", e);
        }
    }

    private String extractStatus(String rawBody) {

        try {

            JsonNode node = mapper.readTree(rawBody);

            if (!node.has("status")) {
                throw new IllegalArgumentException("Webhook missing status");
            }

            String status = node.get("status").asText().toLowerCase();

            return switch (status) {

                case "completed", "success" -> "COMPLETED";

                case "failed", "error" -> "FAILED";

                default -> throw new IllegalArgumentException("Unknown provider status: " + status);
            };

        } catch (Exception e) {
            throw new RuntimeException("Failed to parse webhook status", e);
        }
    }

    private void handleCompleted(Transaction tx) {
        // Settle: Move locked source to clearing, credit recipient with destination
        ledgerService.postSettle(tx.getId(), tx.getSourceCurrency(), tx.getSourceAmount(),
                tx.getDestinationCurrency(), tx.getDestinationAmount());

        // Update sender balance: locked -> 0
        AccountBalance senderBal = balanceRepo.lockByAccountAndCurrency(tx.getSenderAccountId(), tx.getSourceCurrency())
                .orElseThrow(() -> new IllegalStateException("Sender balance not found"));
        senderBal.setLocked(senderBal.getLocked().subtract(tx.getSourceAmount()));
        balanceRepo.save(senderBal);

        // Update recipient balance: available += destination amount (create row if first time receiving this currency)
        AccountBalance recipientBal = balanceRepo.lockByAccountAndCurrency(tx.getRecipientAccountId(), tx.getDestinationCurrency())
                .orElseGet(() -> {
                    AccountBalance newBal = new AccountBalance();
                    newBal.setAccountId(tx.getRecipientAccountId());
                    newBal.setCurrency(tx.getDestinationCurrency());
                    newBal.setAvailable(java.math.BigDecimal.ZERO);
                    newBal.setLocked(java.math.BigDecimal.ZERO);
                    return newBal;
                });
        recipientBal.setAvailable(recipientBal.getAvailable().add(tx.getDestinationAmount()));
        balanceRepo.save(recipientBal);
    }

    private void handleFailed(Transaction tx) {
        // Reverse: Move locked back to available
        ledgerService.postReverse(tx.getId(), tx.getSourceCurrency(), tx.getSourceAmount());

        // Update sender balance: locked -> available
        AccountBalance senderBal = balanceRepo.lockByAccountAndCurrency(tx.getSenderAccountId(), tx.getSourceCurrency())
                .orElseThrow(() -> new IllegalStateException("Sender balance not found"));
        senderBal.setLocked(senderBal.getLocked().subtract(tx.getSourceAmount()));
        senderBal.setAvailable(senderBal.getAvailable().add(tx.getSourceAmount()));
        balanceRepo.save(senderBal);
    }
}