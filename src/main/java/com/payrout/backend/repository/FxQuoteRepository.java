package com.payrout.backend.repository;

import com.payrout.backend.domain.FxQuote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FxQuoteRepository extends JpaRepository<FxQuote, UUID> {
}