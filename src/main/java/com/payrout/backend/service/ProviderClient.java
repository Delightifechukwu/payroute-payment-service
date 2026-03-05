package com.payrout.backend.service;


import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ProviderClient {

    public String submitPayment(String txReference) {
        // Simulate provider ack immediately
        return "prov_" + UUID.randomUUID();
    }
}
