package com.payrout.backend.service;

import com.payrout.backend.domain.IdempotencyRecord;
import com.payrout.backend.repository.IdempotencyRecordRepository;
import com.payrout.backend.util.HashUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class IdempotencyService {

    private final IdempotencyRecordRepository repo;

    public IdempotencyService(IdempotencyRecordRepository repo) {
        this.repo = repo;
    }

    public Optional<IdempotencyRecord> find(String endpoint, String key) {
        return repo.findByEndpointAndIdemKey(endpoint, key);
    }

    @Transactional
    public IdempotencyRecord store(String endpoint, String key, String requestJson, String responseJson, UUID txId) {
        IdempotencyRecord r = new IdempotencyRecord();
        r.setId(UUID.randomUUID());
        r.setEndpoint(endpoint);
        r.setIdemKey(key);
        r.setRequestHash(HashUtil.sha256Hex(requestJson));
        r.setResponseJson(responseJson);
        r.setTransactionId(txId);
        r.setCreatedAt(Instant.now());
        return repo.save(r);
    }
}