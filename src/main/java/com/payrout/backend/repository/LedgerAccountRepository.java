package com.payrout.backend.repository;

import com.payrout.backend.domain.LedgerAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LedgerAccountRepository extends JpaRepository<LedgerAccount, UUID> {
    Optional<LedgerAccount> findByCodeAndCurrency(String code, String currency);
}
