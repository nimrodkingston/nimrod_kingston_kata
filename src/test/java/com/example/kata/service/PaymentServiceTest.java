package com.example.kata.service;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Business rules for accepting, storing and de-duplicating payments, exercised below the HTTP layer.
 *
 * <p>The bodies are deliberately unwritten: the service and payment types are still to be designed,
 * so only the cases are pinned here. Fill each one in as the corresponding behaviour is built.
 */
@QuarkusTest
class PaymentServiceTest {

    // Inject the service under test here once it exists.

    @Test
    void testNewPaymentProcessed() {
        // Given a payment which has not been seen before
        // When the payment is processed
        // Then the payment is stored and reported as accepted
        fail("Not yet implemented");
    }

    @Test
    void testDuplicatePaymentProcessed() {
        // Given a payment which has already been processed
        // When a payment carrying the same identifier is processed
        // Then no further payment is stored and the payment is reported as a duplicate
        fail("Not yet implemented");
    }

    @Test
    void testDuplicatePaymentProcessedAgainstStoredDetail() {
        // Given a payment which has already been processed
        // When a payment carrying the same identifier is processed
        // Then the detail recorded against that identifier is the detail first accepted
        fail("Not yet implemented");
    }

    @Test
    void testMultipleDistinctPaymentsProcessed() {
        // Given several payments which each carry their own identifier
        // When each payment is processed
        // Then every payment is stored and reported as accepted
        fail("Not yet implemented");
    }

    @Test
    void testPaymentsProcessedForSameCustomer() {
        // Given a payment which has already been processed for a customer
        // When a payment carrying a new identifier is processed for that same customer
        // Then the payment is stored and reported as accepted
        fail("Not yet implemented");
    }

    @Test
    void testInvalidPaymentProcessed() {
        // Given a payment which does not satisfy the rules for a well formed payment
        // When the payment is processed
        // Then nothing is stored and the payment is reported as invalid
        fail("Not yet implemented");
    }
}
