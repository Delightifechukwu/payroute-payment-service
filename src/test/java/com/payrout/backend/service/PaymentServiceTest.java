package com.payrout.backend.service;

import com.payrout.backend.domain.*;
import com.payrout.backend.dto.PaymentRequest;
import com.payrout.backend.dto.PaymentResponse;
import com.payrout.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private AccountBalanceRepository balanceRepo;

    @Mock
    private TransactionRepository txRepo;

    @Mock
    private TransactionStatusHistoryRepository historyRepo;

    @Mock
    private LedgerEntryRepository ledgerEntryRepo;

    @Mock
    private FxQuoteRepository fxQuoteRepo;

    @Mock
    private FxService fxService;

    @Mock
    private ProviderClient providerClient;

    @Mock
    private LedgerService ledgerService;

    @Mock
    private IdempotencyService idempotencyService;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(
                balanceRepo, txRepo, historyRepo, ledgerEntryRepo, fxQuoteRepo,
                fxService, providerClient, ledgerService, idempotencyService
        );
    }

    @Test
    void testInitiatePayment_Success() throws Exception {
        // Given
        UUID senderAccountId = UUID.randomUUID();
        UUID recipientAccountId = UUID.randomUUID();
        String idemKey = "test-key-123";
        BigDecimal sourceAmount = new BigDecimal("100.00");

        PaymentRequest request = new PaymentRequest(
                senderAccountId,
                recipientAccountId,
                "USD",
                "EUR",
                sourceAmount
        );

        AccountBalance senderBalance = new AccountBalance();
        senderBalance.setId(UUID.randomUUID());
        senderBalance.setAccountId(senderAccountId);
        senderBalance.setCurrency("USD");
        senderBalance.setAvailable(new BigDecimal("1000.00"));
        senderBalance.setLocked(BigDecimal.ZERO);

        FxQuote quote = new FxQuote();
        quote.setId(UUID.randomUUID());
        quote.setBaseCurrency("USD");
        quote.setQuoteCurrency("EUR");
        quote.setRate(new BigDecimal("0.92"));
        quote.setExpiresAt(Instant.now().plusSeconds(180));

        when(idempotencyService.find(any(), any())).thenReturn(Optional.empty());
        when(balanceRepo.lockByAccountAndCurrency(senderAccountId, "USD")).thenReturn(Optional.of(senderBalance));
        when(fxService.createQuote("USD", "EUR")).thenReturn(quote);
        when(providerClient.submitPayment(any())).thenReturn("prov_ref_123");
        when(txRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        PaymentResponse response = paymentService.initiate(idemKey, request);

        // Then
        assertNotNull(response);
        assertEquals("PROCESSING", response.status());
        assertEquals(0, sourceAmount.compareTo(response.sourceAmount()));
        assertEquals(0, new BigDecimal("92.00").compareTo(response.destinationAmount()));
        assertEquals(0, new BigDecimal("0.92").compareTo(response.fxRate()));

        // Verify balance was locked
        verify(balanceRepo).save(argThat(balance ->
                balance.getAvailable().equals(new BigDecimal("900.00")) &&
                        balance.getLocked().equals(new BigDecimal("100.00"))
        ));

        // Verify ledger posting was created
        verify(ledgerService).postLock(any(UUID.class), eq("USD"), eq(sourceAmount));

        // Verify transaction was saved
        verify(txRepo, times(2)).save(any(Transaction.class));

        // Verify history was recorded
        verify(historyRepo).save(any(TransactionStatusHistory.class));

        // Verify idempotency record was stored
        verify(idempotencyService).store(any(), any(), any(), any(), any());
    }

    @Test
    void testInitiatePayment_InsufficientBalance() {
        // Given
        UUID senderAccountId = UUID.randomUUID();
        UUID recipientAccountId = UUID.randomUUID();
        String idemKey = "test-key-123";
        BigDecimal sourceAmount = new BigDecimal("1000.00");

        PaymentRequest request = new PaymentRequest(
                senderAccountId,
                recipientAccountId,
                "USD",
                "EUR",
                sourceAmount
        );

        AccountBalance senderBalance = new AccountBalance();
        senderBalance.setAccountId(senderAccountId);
        senderBalance.setCurrency("USD");
        senderBalance.setAvailable(new BigDecimal("100.00")); // Insufficient
        senderBalance.setLocked(BigDecimal.ZERO);

        when(idempotencyService.find(any(), any())).thenReturn(Optional.empty());
        when(balanceRepo.lockByAccountAndCurrency(senderAccountId, "USD")).thenReturn(Optional.of(senderBalance));

        // When/Then
        assertThrows(IllegalStateException.class, () -> {
            paymentService.initiate(idemKey, request);
        });

        // Verify no balance changes were made
        verify(balanceRepo, never()).save(any());
        verify(txRepo, never()).save(any());
    }

    @Test
    void testInitiatePayment_Idempotency() throws Exception {
        // Given
        String idemKey = "test-key-123";
        PaymentRequest request = new PaymentRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "USD",
                "EUR",
                new BigDecimal("100.00")
        );

        PaymentResponse cachedResponse = new PaymentResponse(
                "PR_cached",
                "PROCESSING",
                new BigDecimal("100.00"),
                new BigDecimal("92.00"),
                new BigDecimal("0.92")
        );

        IdempotencyRecord existingRecord = new IdempotencyRecord();
        existingRecord.setResponseJson("{\"reference\":\"PR_cached\",\"status\":\"PROCESSING\",\"sourceAmount\":100.00,\"destinationAmount\":92.00,\"fxRate\":0.92}");

        when(idempotencyService.find(any(), eq(idemKey))).thenReturn(Optional.of(existingRecord));

        // When
        PaymentResponse response = paymentService.initiate(idemKey, request);

        // Then
        assertEquals("PR_cached", response.reference());
        assertEquals("PROCESSING", response.status());

        // Verify no new payment was created
        verify(balanceRepo, never()).lockByAccountAndCurrency(any(), any());
        verify(txRepo, never()).save(any());
    }

    @Test
    void testInitiatePayment_BalanceNotFound() {
        // Given
        UUID senderAccountId = UUID.randomUUID();
        String idemKey = "test-key-123";

        PaymentRequest request = new PaymentRequest(
                senderAccountId,
                UUID.randomUUID(),
                "USD",
                "EUR",
                new BigDecimal("100.00")
        );

        when(idempotencyService.find(any(), any())).thenReturn(Optional.empty());
        when(balanceRepo.lockByAccountAndCurrency(senderAccountId, "USD")).thenReturn(Optional.empty());

        // When/Then
        assertThrows(IllegalArgumentException.class, () -> {
            paymentService.initiate(idemKey, request);
        });
    }
}
