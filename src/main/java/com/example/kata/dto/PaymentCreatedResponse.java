package com.example.kata.dto;

import java.time.Instant;

public record PaymentCreatedResponse(PaymentDto payment, Instant timeCreated) {
    public PaymentCreatedResponse(PaymentDto payment){
        this(payment, Instant.now());
    }
}
