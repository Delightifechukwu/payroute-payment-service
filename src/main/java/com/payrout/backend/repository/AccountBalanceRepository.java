package com.payrout.backend.repository;

import com.payrout.backend.domain.AccountBalance;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccountBalanceRepository
        extends JpaRepository<AccountBalance, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from AccountBalance b where b.accountId = :accountId and b.currency = :currency")
    Optional<AccountBalance> lockByAccountAndCurrency(
            UUID accountId,
            String currency
    );
}
