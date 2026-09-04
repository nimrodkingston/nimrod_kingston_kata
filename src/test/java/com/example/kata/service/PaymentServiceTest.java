package com.example.kata.service;

import com.example.kata.dto.PaymentDto;
import com.example.kata.exception.DuplicatePaymentException;
import com.example.kata.exception.PaymentNotFoundException;
import com.example.kata.model.PaymentModel;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySink;
import jakarta.enterprise.inject.Any;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Business rules for accepting, storing and de-duplicating payments, exercised below the HTTP layer.
 *
 * <p>Each case processes payments for real and then reads back what was kept, so the rules are
 * proven against stored state rather than against anything held in memory by the processing.
 */
@QuarkusTest
class PaymentServiceTest {
    PaymentService paymentService;
    EntityManager entityManager;
    InMemoryConnector connector;
    InMemorySink<PaymentDto> sink;

    PaymentServiceTest(PaymentService paymentService, EntityManager entityManager, @Any InMemoryConnector connector) {
        this.paymentService = paymentService;
        this.entityManager = entityManager;
        this.connector = connector;
    }

    @BeforeEach
    void setup() {
        this.sink = this.connector.sink("payment-processed");
    }

    @AfterEach
    void discardStoredPayments() {
        // Processing commits for real, so what each case stores outlives it and has to be cleared.
        // Removal is a write, which unlike the reads below cannot run without a unit of work.
        QuarkusTransaction.requiringNew().run(() ->
                this.entityManager.createQuery("DELETE FROM payment").executeUpdate());
    }

    @Test
    void testNewPaymentProcessed() {
        // Given a payment which has not been seen before
        // When the payment is processed
        this.paymentService.createPayment(
            "new-payment-id",
            "some-customer-id",
            new BigDecimal("237.45"),
            "GBP"
        );

        // Then the payment is stored and reported as accepted and a payment process message is sent
        var createdPayment = storedPayment("new-payment-id");
        assertEquals("some-customer-id", createdPayment.getCustomerId());
        assertEquals(0, new BigDecimal("237.45").compareTo(createdPayment.getAmount()));
        assertEquals("GBP", createdPayment.getCurrency());

        assertTrue(hasPaymentMessageSent("new-payment-id"));
    }

    @Test
    void testDuplicatePaymentProcessed() {
        // Given a payment which has already been processed
        this.paymentService.createPayment(
            "duplicated-payment-id",
            "some-customer-id",
            new BigDecimal("237.45"),
            "GBP"
        );

        // When a payment carrying the same identifier is processed
        // Then no further payment is stored and the payment is reported as a duplicate
        assertThrows(DuplicatePaymentException.class, () -> this.paymentService.createPayment(
            "duplicated-payment-id",
            "some-customer-id",
            new BigDecimal("237.45"),
            "GBP"
        ));
        assertEquals(1, storedPaymentCount());
        assertTrue(hasPaymentMessageSent("duplicated-payment-id"));
    }

    @Test
    void testMultipleDistinctPaymentsProcessed() {
        // Given several payments which each carry their own identifier
        // When each payment is processed
        this.paymentService.createPayment(
            "first-payment-id",
            "some-customer-id",
            new BigDecimal("237.45"),
            "GBP"
        );
        this.paymentService.createPayment(
            "second-payment-id",
            "another-customer-id",
            new BigDecimal("10.00"),
            "GBP"
        );
        this.paymentService.createPayment(
            "third-payment-id",
            "further-customer-id",
            new BigDecimal("99.99"),
            "EUR"
        );

        // Then every payment is stored and reported as accepted
        assertEquals(3, storedPaymentCount());
        assertEquals("some-customer-id", storedPayment("first-payment-id").getCustomerId());
        assertEquals("another-customer-id", storedPayment("second-payment-id").getCustomerId());
        assertEquals("further-customer-id", storedPayment("third-payment-id").getCustomerId());

        assertTrue(hasPaymentMessageSent("first-payment-id"));
        assertTrue(hasPaymentMessageSent("second-payment-id"));
        assertTrue(hasPaymentMessageSent("third-payment-id"));
    }

    @Test
    void testPaymentsProcessedForSameCustomer() {
        // Given a payment which has already been processed for a customer
        this.paymentService.createPayment(
            "earlier-payment-id",
            "returning-customer-id",
            new BigDecimal("237.45"),
            "GBP"
        );

        // When a payment carrying a new identifier is processed for that same customer
        this.paymentService.createPayment(
            "later-payment-id",
            "returning-customer-id",
            new BigDecimal("10.00"),
            "GBP"
        );

        // Then the payment is stored and reported as accepted
        assertEquals(2, storedPaymentCount());
        var laterPayment = storedPayment("later-payment-id");
        assertEquals("returning-customer-id", laterPayment.getCustomerId());
        assertEquals(0, new BigDecimal("10.00").compareTo(laterPayment.getAmount()));

        assertTrue(hasPaymentMessageSent("earlier-payment-id"));
        assertTrue(hasPaymentMessageSent("later-payment-id"));
    }

    @Test
    void testGetPaymentDetails() {
        // Given a payment has been created with a given paymentId
        this.paymentService.createPayment(
            "requested-payment-id",
            "some-customer-id",
            new BigDecimal("237.45"),
            "GBP"
        );

        // When the payment is requested
        var requestedPayment = this.paymentService.getPaymentByPaymentId("requested-payment-id");

        // Then the given payment is returned
        assertEquals("requested-payment-id", requestedPayment.paymentId());
        assertEquals("some-customer-id", requestedPayment.customerId());
        assertEquals(0, new BigDecimal("237.45").compareTo(requestedPayment.amount()));
        assertEquals("GBP", requestedPayment.currency());
    }

    @Test
    void testGetPaymentDetailsWithNoPaymentExisting() {
        // Given a payment does not exist
        assertEquals(0, storedPaymentCount());

        // When a payment with a given payment-id is requested
        // Then a not found response is returned
        assertThrows(PaymentNotFoundException.class, () ->
            this.paymentService.getPaymentByPaymentId("unknown-payment-id"));
    }

    // --- Helpers -------------------------------------------------------------

    /**
     * Reads back what was actually kept against an identifier, so that the assertions are made
     * against stored state rather than against anything the processing above left in memory.
     */
    PaymentModel storedPayment(String paymentId) {
        return entityManager
            .createQuery("SELECT p FROM payment p WHERE p.paymentId = :paymentId", PaymentModel.class)
            .setParameter("paymentId", paymentId)
            .getSingleResult();
    }

    /**
     * How many payments are held in total. Each case starts from nothing, so this counts only what
     * the case itself stored, and is how the cases assert that nothing further was kept.
     */
    long storedPaymentCount() {
        return entityManager
            .createQuery("SELECT COUNT(p) FROM payment p", Long.class)
            .getSingleResult();
    }

    boolean hasPaymentMessageSent(String paymentId) {
        return sink.received().stream().anyMatch(message -> message.getPayload().paymentId().equals(paymentId));
    }
}
