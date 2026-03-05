package com.payrout.backend.controller;

import com.payrout.backend.domain.Transaction;
import com.payrout.backend.repository.TransactionRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/transactions")
public class TransactionController {

    private final TransactionRepository txRepo;

    public TransactionController(TransactionRepository txRepo) {
        this.txRepo = txRepo;
    }

    @GetMapping
    public List<Transaction> list() {
        return txRepo.findAll();
    }


    @GetMapping("/{reference}")
    public Transaction getTransaction(@PathVariable String reference) {
        return txRepo.findByReference(reference).orElseThrow();
    }
}