package com.payrout.backend.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Setter
@Table(name="webhook_events",
        indexes = @Index(name="idx_webhook_provider_ref", columnList="provider_reference"))
public class WebhookEvent {

    @Id
    private UUID id;

    @Column(name="provider_reference")
    private String providerReference;

    @Column(name="event_fingerprint", nullable=false)
    private String eventFingerprint;

    @Column(name="raw_body", nullable=false, columnDefinition="text")
    private String rawBody;

    @Column(name="signature", nullable=false)
    private String signature;

    @Column(name="received_at", nullable=false)
    private Instant receivedAt;

    @Column(name="processed_at")
    private Instant processedAt;

    @Column(name="processing_status", nullable=false)
    private String processingStatus; // RECEIVED, PROCESSED, DUPLICATE, ERROR

    @Column(name="error_message")
    private String errorMessage;


}