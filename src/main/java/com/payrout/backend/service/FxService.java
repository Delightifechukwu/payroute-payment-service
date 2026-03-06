package com.payrout.backend.service;

import com.payrout.backend.domain.FxQuote;
import com.payrout.backend.repository.FxQuoteRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
public class FxService {

    private final FxQuoteRepository fxRepo;

    @Value("${payroute.fx.quote_ttl_seconds:180}")
    private long ttlSeconds;

    public FxService(FxQuoteRepository fxRepo) {
        this.fxRepo = fxRepo;
    }

    public FxQuote createQuote(String base, String quote) {
        // Simulate realistic FX rates based on base currency
        BigDecimal rate;

        if ("USD".equals(base)) {
            rate = switch (quote) {
                case "EUR" -> new BigDecimal("0.92");
                case "GBP" -> new BigDecimal("0.79");
                case "NGN" -> new BigDecimal("1580.00");
                default -> new BigDecimal("1.0");
            };
        } else if ("NGN".equals(base)) {
            rate = switch (quote) {
                case "USD" -> new BigDecimal("0.00063");
                case "EUR" -> new BigDecimal("0.00058");
                case "GBP" -> new BigDecimal("0.00050");
                default -> new BigDecimal("1.0");
            };
        } else {
            rate = new BigDecimal("1.0");
        }

        FxQuote q = new FxQuote();
        q.setId(UUID.randomUUID());
        q.setBaseCurrency(base);
        q.setQuoteCurrency(quote);
        q.setRate(rate);
        q.setStatus("LOCKED");
        q.setExpiresAt(Instant.now().plusSeconds(ttlSeconds));
        q.setCreatedAt(Instant.now());
        q.setLockedAt(Instant.now());
        return fxRepo.save(q);
    }
}