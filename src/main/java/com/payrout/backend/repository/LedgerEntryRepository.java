package com.payrout.backend.repository;


import com.payrout.backend.domain.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {
    List<LedgerEntry> findByTransactionIdOrderByCreatedAtAsc(UUID transactionId);
}
