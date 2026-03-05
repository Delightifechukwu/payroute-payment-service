package com.payrout.backend.service;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.payrout.backend.domain.*;
import com.payrout.backend.dto.PaymentRequest;
import com.payrout.backend.dto.PaymentResponse;
import com.payrout.backend.dto.TransactionDetailResponse;
import com.payrout.backend.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class PaymentService {

    private final AccountBalanceRepository balanceRepo;
    private final TransactionRepository txRepo;
    private final TransactionStatusHistoryRepository historyRepo;
    private final LedgerEntryRepository ledgerEntryRepo;
    private final FxQuoteRepository fxQuoteRepo;
    private final FxService fxService;
    private final ProviderClient providerClient;
    private final LedgerService ledgerService;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper mapper = new ObjectMapper();

    public PaymentService(AccountBalanceRepository balanceRepo,
                          TransactionRepository txRepo,
                          TransactionStatusHistoryRepository historyRepo,
                          LedgerEntryRepository ledgerEntryRepo,
                          FxQuoteRepository fxQuoteRepo,
                          FxService fxService,
                          ProviderClient providerClient,
                          LedgerService ledgerService,
                          IdempotencyService idempotencyService) {
        this.balanceRepo = balanceRepo;
        this.txRepo = txRepo;
        this.historyRepo = historyRepo;
        this.ledgerEntryRepo = ledgerEntryRepo;
        this.fxQuoteRepo = fxQuoteRepo;
        this.fxService = fxService;
        this.providerClient = providerClient;
        this.ledgerService = ledgerService;
        this.idempotencyService = idempotencyService;
    }

    @Transactional
    public PaymentResponse initiate(String idemKey, PaymentRequest req) throws Exception {

        final String endpoint = "POST:/payments";
        final String reqJson = mapper.writeValueAsString(req);

        var existing = idempotencyService.find(endpoint, idemKey);
        if (existing.isPresent()) {
            return mapper.readValue(existing.get().getResponseJson(), PaymentResponse.class);
        }

        // Lock balance row to prevent double-spend
        AccountBalance bal = balanceRepo.lockByAccountAndCurrency(req.senderAccountId(), req.sourceCurrency())
                .orElseThrow(() -> new IllegalArgumentException("Sender balance not found"));

        if (bal.getAvailable().compareTo(req.sourceAmount()) < 0) {
            throw new IllegalStateException("Insufficient balance");
        }

        // Create FX quote and compute dest amount
        FxQuote quote = fxService.createQuote(req.sourceCurrency(), req.destinationCurrency());
        BigDecimal destAmount = req.sourceAmount().multiply(quote.getRate());

        // Move available -> locked (also mirrored with ledger posting)
        bal.setAvailable(bal.getAvailable().subtract(req.sourceAmount()));
        bal.setLocked(bal.getLocked().add(req.sourceAmount()));
        balanceRepo.save(bal);

        Transaction tx = new Transaction();
        tx.setId(UUID.randomUUID());
        tx.setReference("PR_" + UUID.randomUUID());
        tx.setSenderAccountId(req.senderAccountId());
        tx.setRecipientAccountId(req.recipientAccountId());
        tx.setSourceCurrency(req.sourceCurrency());
        tx.setDestinationCurrency(req.destinationCurrency());
        tx.setSourceAmount(req.sourceAmount());
        tx.setDestinationAmount(destAmount);
        tx.setStatus("INITIATED");
        tx.setFxQuoteId(quote.getId());
        tx.setCreatedAt(Instant.now());
        tx.setUpdatedAt(Instant.now());
        txRepo.save(tx);

        appendHistory(tx.getId(), "INITIATED", "PROCESSING", "submitted_to_provider");

        // Submit to provider
        String providerRef = providerClient.submitPayment(tx.getReference());
        tx.setProviderReference(providerRef);
        tx.setStatus("PROCESSING");
        tx.setUpdatedAt(Instant.now());
        txRepo.save(tx);

        // Ledger posting for lock: CUSTOMER_AVAILABLE -> CUSTOMER_LOCKED
        ledgerService.postLock(tx.getId(), req.sourceCurrency(), req.sourceAmount());

        PaymentResponse resp = PaymentResponse.from(tx, quote.getRate());

        String respJson = mapper.writeValueAsString(resp);
        idempotencyService.store(endpoint, idemKey, reqJson, respJson, tx.getId());

        return resp;
    }

    private void appendHistory(UUID txId, String from, String to, String reason) {
        TransactionStatusHistory h = new TransactionStatusHistory();
        h.setId(UUID.randomUUID());
        h.setTransactionId(txId);
        h.setFromStatus(from);
        h.setToStatus(to);
        h.setAtTime(Instant.now());
        h.setReason(reason);
        historyRepo.save(h);
    }

    public Page<Transaction> listPayments(String status, Instant startDate, Instant endDate, Pageable pageable) {
        return txRepo.findWithFilters(status, startDate, endDate, pageable);
    }

    public TransactionDetailResponse getPaymentDetails(String reference) {
        Transaction tx = txRepo.findByReference(reference)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found"));

        FxQuote quote = fxQuoteRepo.findById(tx.getFxQuoteId())
                .orElseThrow(() -> new IllegalStateException("FX quote not found"));

        List<TransactionStatusHistory> history = historyRepo.findByTransactionIdOrderByAtTimeAsc(tx.getId());
        List<LedgerEntry> entries = ledgerEntryRepo.findByTransactionIdOrderByCreatedAtAsc(tx.getId());

        return TransactionDetailResponse.from(tx, quote.getRate(), history, entries);
    }
}