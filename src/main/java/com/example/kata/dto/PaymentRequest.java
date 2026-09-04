package com.example.kata.dto;

import java.math.BigDecimal;

public record PaymentRequest(String paymentId, String customerId, BigDecimal amount, String currency) {
}
