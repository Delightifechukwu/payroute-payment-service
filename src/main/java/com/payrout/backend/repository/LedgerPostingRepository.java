package com.payrout.backend.repository;

import com.payrout.backend.domain.LedgerPostings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface LedgerPostingRepository extends JpaRepository<LedgerPostings, UUID> {
    boolean existsByUniqueKey(String uniqueKey);
}
