package com.payrout.backend.controller;

import com.payrout.backend.domain.Transaction;
import com.payrout.backend.dto.PaymentRequest;
import com.payrout.backend.dto.PaymentResponse;
import com.payrout.backend.dto.TransactionDetailResponse;
import com.payrout.backend.service.PaymentService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService){
        this.paymentService = paymentService;
    }

    @PostMapping
    public PaymentResponse createPayment(
            @RequestHeader("Idempotency-Key") String key,
            @RequestBody PaymentRequest request
    ) throws Exception {

        return paymentService.initiate(key, request);
    }

    @GetMapping
    public Page<Transaction> listPayments(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return paymentService.listPayments(status, startDate, endDate,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    @GetMapping("/{id}")
    public TransactionDetailResponse getPaymentDetails(@PathVariable String id) {
        return paymentService.getPaymentDetails(id);
    }
}