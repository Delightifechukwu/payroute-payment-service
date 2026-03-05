package com.payrout.backend.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentRequest(
        UUID senderAccountId,
        UUID recipientAccountId,
        String sourceCurrency,
        String destinationCurrency,
        BigDecimal sourceAmount
) {}