package com.payrout.backend.repository;

import com.payrout.backend.domain.WebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WebhookEventRepository extends JpaRepository<WebhookEvent, UUID> {

    // Find webhook by provider reference (useful for debugging / reconciliation)
    Optional<WebhookEvent> findByProviderReference(String providerReference);

    // Check if a webhook with same fingerprint already exists (deduplication)
    boolean existsByProviderReferenceAndEventFingerprint(String providerReference,
                                                         String eventFingerprint);
}