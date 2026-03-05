package com.payrout.backend.dto;

import com.payrout.backend.domain.Transaction;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentResponse(
        String reference,
        String status,
        BigDecimal sourceAmount,
        BigDecimal destinationAmount,
        BigDecimal fxRate
) {
    public static PaymentResponse from(Transaction tx, BigDecimal fxRate) {

        return new PaymentResponse(
                tx.getReference(),
                tx.getStatus(),
                tx.getSourceAmount(),
                tx.getDestinationAmount(),
                fxRate
        );
    }
}