package com.payrout.backend.service;

import com.payrout.backend.domain.AccountBalance;
import com.payrout.backend.domain.Transaction;
import com.payrout.backend.domain.WebhookEvent;
import com.payrout.backend.repository.AccountBalanceRepository;
import com.payrout.backend.repository.TransactionRepository;
import com.payrout.backend.repository.WebhookEventRepository;
import com.payrout.backend.state.TransactionStateMachine;
import com.payrout.backend.util.CryptoUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebhookServiceTest {

    @Mock
    private WebhookEventRepository webhookRepo;

    @Mock
    private TransactionRepository txRepo;

    @Mock
    private TransactionStateMachine sm;

    @Mock
    private LedgerService ledgerService;

    @Mock
    private AccountBalanceRepository balanceRepo;

    private WebhookService webhookService;

    private final String webhookSecret = "test_secret";

    @BeforeEach
    void setUp() {
        webhookService = new WebhookService(webhookRepo, txRepo, sm, ledgerService, balanceRepo);
        ReflectionTestUtils.setField(webhookService, "secret", webhookSecret);
    }

    @Test
    void testHandle_ValidWebhook_Completed() {
        // Given
        String providerRef = "prov_123";
        String rawBody = "{\"reference\":\"" + providerRef + "\",\"status\":\"completed\"}";
        byte[] rawBodyBytes = rawBody.getBytes(StandardCharsets.UTF_8);
        String signature = CryptoUtil.hmacSha256Hex(webhookSecret, rawBodyBytes);

        Transaction tx = new Transaction();
        tx.setId(UUID.randomUUID());
        tx.setProviderReference(providerRef);
        tx.setStatus("PROCESSING");
        tx.setSourceCurrency("USD");
        tx.setDestinationCurrency("EUR");
        tx.setSourceAmount(new BigDecimal("100.00"));
        tx.setDestinationAmount(new BigDecimal("92.00"));
        tx.setSenderAccountId(UUID.randomUUID());
        tx.setRecipientAccountId(UUID.randomUUID());

        AccountBalance senderBalance = new AccountBalance();
        senderBalance.setAccountId(tx.getSenderAccountId());
        senderBalance.setCurrency("USD");
        senderBalance.setLocked(new BigDecimal("100.00"));

        AccountBalance recipientBalance = new AccountBalance();
        recipientBalance.setAccountId(tx.getRecipientAccountId());
        recipientBalance.setCurrency("EUR");
        recipientBalance.setAvailable(new BigDecimal("50.00"));

        when(txRepo.findByProviderReference(providerRef)).thenReturn(Optional.of(tx));
        when(balanceRepo.findById(tx.getSenderAccountId())).thenReturn(Optional.of(senderBalance));
        when(balanceRepo.lockByAccountAndCurrency(tx.getRecipientAccountId(), "EUR"))
                .thenReturn(Optional.of(recipientBalance));
        when(webhookRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        webhookService.handle(signature, rawBodyBytes);

        // Then
        verify(sm).validate("PROCESSING", "COMPLETED");
        verify(ledgerService).postSettle(tx.getId(), "USD", new BigDecimal("100.00"), "EUR", new BigDecimal("92.00"));
        verify(txRepo).save(argThat(t -> "COMPLETED".equals(t.getStatus())));
        verify(webhookRepo, times(2)).save(any(WebhookEvent.class));
    }

    @Test
    void testHandle_ValidWebhook_Failed() {
        // Given
        String providerRef = "prov_123";
        String rawBody = "{\"reference\":\"" + providerRef + "\",\"status\":\"failed\"}";
        byte[] rawBodyBytes = rawBody.getBytes(StandardCharsets.UTF_8);
        String signature = CryptoUtil.hmacSha256Hex(webhookSecret, rawBodyBytes);

        Transaction tx = new Transaction();
        tx.setId(UUID.randomUUID());
        tx.setProviderReference(providerRef);
        tx.setStatus("PROCESSING");
        tx.setSourceCurrency("USD");
        tx.setSourceAmount(new BigDecimal("100.00"));
        tx.setSenderAccountId(UUID.randomUUID());

        AccountBalance senderBalance = new AccountBalance();
        senderBalance.setAccountId(tx.getSenderAccountId());
        senderBalance.setCurrency("USD");
        senderBalance.setLocked(new BigDecimal("100.00"));
        senderBalance.setAvailable(new BigDecimal("900.00"));

        when(txRepo.findByProviderReference(providerRef)).thenReturn(Optional.of(tx));
        when(balanceRepo.lockByAccountAndCurrency(tx.getSenderAccountId(), "USD"))
                .thenReturn(Optional.of(senderBalance));
        when(webhookRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        webhookService.handle(signature, rawBodyBytes);

        // Then
        verify(sm).validate("PROCESSING", "FAILED");
        verify(ledgerService).postReverse(tx.getId(), "USD", new BigDecimal("100.00"));
        verify(txRepo).save(argThat(t -> "FAILED".equals(t.getStatus())));
        verify(balanceRepo).save(argThat(bal ->
                bal.getLocked().compareTo(BigDecimal.ZERO) == 0 &&
                        bal.getAvailable().compareTo(new BigDecimal("1000.00")) == 0
        ));
    }

    @Test
    void testHandle_InvalidSignature() {
        // Given
        String rawBody = "{\"reference\":\"prov_123\",\"status\":\"completed\"}";
        byte[] rawBodyBytes = rawBody.getBytes(StandardCharsets.UTF_8);
        String invalidSignature = "invalid_signature";

        when(webhookRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        webhookService.handle(invalidSignature, rawBodyBytes);

        // Then
        verify(txRepo, never()).findByProviderReference(any());
        verify(webhookRepo).save(argThat(event ->
                "ERROR".equals(event.getProcessingStatus()) &&
                        "invalid_signature".equals(event.getErrorMessage())
        ));
    }

    @Test
    void testHandle_TransactionNotFound() {
        // Given
        String providerRef = "prov_unknown";
        String rawBody = "{\"reference\":\"" + providerRef + "\",\"status\":\"completed\"}";
        byte[] rawBodyBytes = rawBody.getBytes(StandardCharsets.UTF_8);
        String signature = CryptoUtil.hmacSha256Hex(webhookSecret, rawBodyBytes);

        when(txRepo.findByProviderReference(providerRef)).thenReturn(Optional.empty());
        when(webhookRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        webhookService.handle(signature, rawBodyBytes);

        // Then
        verify(webhookRepo, times(2)).save(any());
        verify(txRepo, never()).save(any());
        verify(ledgerService, never()).postSettle(any(), any(), any(), any(), any());
    }

    @Test
    void testHandle_InvalidStateTransition() {
        // Given
        String providerRef = "prov_123";
        String rawBody = "{\"reference\":\"" + providerRef + "\",\"status\":\"completed\"}";
        byte[] rawBodyBytes = rawBody.getBytes(StandardCharsets.UTF_8);
        String signature = CryptoUtil.hmacSha256Hex(webhookSecret, rawBodyBytes);

        Transaction tx = new Transaction();
        tx.setId(UUID.randomUUID());
        tx.setProviderReference(providerRef);
        tx.setStatus("COMPLETED"); // Already completed

        when(txRepo.findByProviderReference(providerRef)).thenReturn(Optional.of(tx));
        when(webhookRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new IllegalStateException("Invalid state transition")).when(sm).validate("COMPLETED", "COMPLETED");

        // When
        webhookService.handle(signature, rawBodyBytes);

        // Then
        verify(sm).validate("COMPLETED", "COMPLETED");
        verify(txRepo, never()).save(any());
        verify(webhookRepo, times(2)).save(any());
    }
}
