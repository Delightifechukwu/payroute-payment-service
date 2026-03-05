package com.payrout.backend.repository;

import com.payrout.backend.domain.TransactionStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TransactionStatusHistoryRepository
        extends JpaRepository<TransactionStatusHistory, UUID> {

    // Get timeline of status changes for a transaction
    List<TransactionStatusHistory> findByTransactionIdOrderByAtTimeAsc(UUID transactionId);
}