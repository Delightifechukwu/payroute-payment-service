package com.payrout.backend.repository;

import com.payrout.backend.domain.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, UUID> {
    Optional<IdempotencyRecord> findByEndpointAndIdemKey(String endpoint, String idemKey);
}