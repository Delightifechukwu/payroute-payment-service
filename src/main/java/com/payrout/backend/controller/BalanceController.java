package com.payrout.backend.controller;

import com.payrout.backend.domain.AccountBalance;
import com.payrout.backend.repository.AccountBalanceRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/balances")
public class BalanceController {

    private final AccountBalanceRepository repo;

    public BalanceController(AccountBalanceRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    public List<AccountBalance> list() {
        return repo.findAll();
    }
}