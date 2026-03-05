package com.payrout.backend.service;


import com.payrout.backend.domain.*;
import com.payrout.backend.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class LedgerService {

    private final LedgerPostingRepository postingRepo;
    private final LedgerEntryRepository entryRepo;
    private final LedgerAccountRepository ledgerAccountRepo;

    public LedgerService(LedgerPostingRepository postingRepo,
                         LedgerEntryRepository entryRepo,
                         LedgerAccountRepository ledgerAccountRepo) {
        this.postingRepo = postingRepo;
        this.entryRepo = entryRepo;
        this.ledgerAccountRepo = ledgerAccountRepo;
    }

    @Transactional
    public void post(UUID txId, String postingType, String uniqueKey, String currency,
                     UUID debitLedgerAccountId, UUID creditLedgerAccountId, BigDecimal amount) {

        // Idempotent at ledger-level:
        if (postingRepo.existsByUniqueKey(uniqueKey)) return;

        LedgerPostings posting = new LedgerPostings();
        posting.setId(UUID.randomUUID());
        posting.setTransactionId(txId);
        posting.setPostingType(postingType);
        posting.setUniqueKey(uniqueKey);
        posting.setCreatedAt(Instant.now());
        postingRepo.save(posting);

        LedgerEntry debit = new LedgerEntry();
        debit.setId(UUID.randomUUID());
        debit.setPostingId(posting.getId());
        debit.setTransactionId(txId);
        debit.setLedgerAccountId(debitLedgerAccountId);
        debit.setCurrency(currency);
        debit.setAmount(amount);
        debit.setDirection("DEBIT");
        debit.setCreatedAt(Instant.now());

        LedgerEntry credit = new LedgerEntry();
        credit.setId(UUID.randomUUID());
        credit.setPostingId(posting.getId());
        credit.setTransactionId(txId);
        credit.setLedgerAccountId(creditLedgerAccountId);
        credit.setCurrency(currency);
        credit.setAmount(amount);
        credit.setDirection("CREDIT");
        credit.setCreatedAt(Instant.now());

        entryRepo.saveAll(List.of(debit, credit));

        // Optional: validate (defensive)
        if (amount.signum() <= 0) throw new IllegalArgumentException("Amount must be > 0");
    }

    @Transactional
    public void postLock(UUID txId, String currency, BigDecimal amount) {
        LedgerAccount availableAcct = ledgerAccountRepo.findByCodeAndCurrency("CUST_AVAILABLE", currency)
                .orElseThrow(() -> new IllegalStateException("Ledger account CUST_AVAILABLE:" + currency + " not found"));
        LedgerAccount lockedAcct = ledgerAccountRepo.findByCodeAndCurrency("CUST_LOCKED", currency)
                .orElseThrow(() -> new IllegalStateException("Ledger account CUST_LOCKED:" + currency + " not found"));

        post(txId, "DEBIT_LOCK", "lock:" + txId, currency, availableAcct.getId(), lockedAcct.getId(), amount);
    }

    @Transactional
    public void postSettle(UUID txId, String sourceCurrency, BigDecimal sourceAmount,
                          String destCurrency, BigDecimal destAmount) {
        // Debit locked source currency
        LedgerAccount lockedAcct = ledgerAccountRepo.findByCodeAndCurrency("CUST_LOCKED", sourceCurrency)
                .orElseThrow(() -> new IllegalStateException("Ledger account CUST_LOCKED:" + sourceCurrency + " not found"));
        LedgerAccount clearingSourceAcct = ledgerAccountRepo.findByCodeAndCurrency("PROVIDER_CLEARING", sourceCurrency)
                .orElseThrow(() -> new IllegalStateException("Ledger account PROVIDER_CLEARING:" + sourceCurrency + " not found"));

        post(txId, "SETTLE_SOURCE", "settle-src:" + txId, sourceCurrency, lockedAcct.getId(), clearingSourceAcct.getId(), sourceAmount);

        // Credit recipient in destination currency
        LedgerAccount clearingDestAcct = ledgerAccountRepo.findByCodeAndCurrency("PROVIDER_CLEARING", destCurrency)
                .orElseThrow(() -> new IllegalStateException("Ledger account PROVIDER_CLEARING:" + destCurrency + " not found"));
        LedgerAccount availableDestAcct = ledgerAccountRepo.findByCodeAndCurrency("CUST_AVAILABLE", destCurrency)
                .orElseThrow(() -> new IllegalStateException("Ledger account CUST_AVAILABLE:" + destCurrency + " not found"));

        post(txId, "SETTLE_DEST", "settle-dest:" + txId, destCurrency, clearingDestAcct.getId(), availableDestAcct.getId(), destAmount);
    }

    @Transactional
    public void postReverse(UUID txId, String currency, BigDecimal amount) {
        // Reverse the lock: CUST_LOCKED -> CUST_AVAILABLE
        LedgerAccount lockedAcct = ledgerAccountRepo.findByCodeAndCurrency("CUST_LOCKED", currency)
                .orElseThrow(() -> new IllegalStateException("Ledger account CUST_LOCKED:" + currency + " not found"));
        LedgerAccount availableAcct = ledgerAccountRepo.findByCodeAndCurrency("CUST_AVAILABLE", currency)
                .orElseThrow(() -> new IllegalStateException("Ledger account CUST_AVAILABLE:" + currency + " not found"));

        post(txId, "REVERSE", "reverse:" + txId, currency, lockedAcct.getId(), availableAcct.getId(), amount);
    }
}