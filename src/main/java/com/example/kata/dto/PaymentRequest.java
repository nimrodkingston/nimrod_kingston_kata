package com.example.kata.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.hibernate.validator.constraints.Length;
import java.math.BigDecimal;

/*
Current currency validation is basic in that it only checks
the length of the given currency code.

This can be improved validating that it is an ISO validated currency
 */
public record PaymentRequest(
        @NotBlank(message="paymentId must be provided") String paymentId,
        @NotBlank(message="customerId must be provided") String customerId,
        @Positive(message="Provided amounts must be greater than 0") @NotNull BigDecimal amount,
        @NotBlank @Length(min=3, max=3, message= "Invalid currency format") String currency) {
}
