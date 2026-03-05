package com.payrout.backend.repository;

import com.payrout.backend.domain.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    Optional<Transaction> findByReference(String reference);
    Optional<Transaction> findByProviderReference(String providerReference);

    @Query("SELECT t FROM Transaction t WHERE " +
            "(:status IS NULL OR t.status = :status) AND " +
            "(:startDate IS NULL OR t.createdAt >= :startDate) AND " +
            "(:endDate IS NULL OR t.createdAt <= :endDate)")
    Page<Transaction> findWithFilters(@Param("status") String status,
                                      @Param("startDate") Instant startDate,
                                      @Param("endDate") Instant endDate,
                                      Pageable pageable);
}