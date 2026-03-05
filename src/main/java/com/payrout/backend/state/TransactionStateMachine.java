package com.payrout.backend.state;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class TransactionStateMachine {

    private static final Map<String, List<String>> transitions =
            Map.of(
                    "INITIATED", List.of("PROCESSING"),
                    "PROCESSING", List.of("COMPLETED", "FAILED"),
                    "FAILED", List.of(),
                    "COMPLETED", List.of()
            );

    public void validate(String current, String next){
        if(!transitions.get(current).contains(next)){
            throw new IllegalStateException("Invalid state transition");
        }
    }
}
