package com.example.kata.concurrency;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Behaviour when senders overlap, which is the condition the de-duplication rule exists for.
 *
 * <p>Every case asserts on two things: what each sender was told, and how many payments were
 * actually kept. The second matters because the first can look correct on its own — a sender can
 * be told its payment was a duplicate while a second copy is still written behind it.
 */
@QuarkusTest
class PaymentConcurrencyTest {
    static final int CREATED = 201;
    static final int DUPLICATE = 409;
    static final int SENDERS = 20;

    EntityManager entityManager;

    PaymentConcurrencyTest(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @AfterEach
    void discardStoredPayments() {
        QuarkusTransaction.requiringNew().run(() ->
            this.entityManager.createQuery("DELETE FROM payment").executeUpdate());
    }

    @Test
    void testSamePaymentReceivedConcurrently() throws Exception {
        // Given a payment which has not been seen before
        var paymentId = nextPaymentId();

        // When many senders submit that same payment at the same moment
        var outcomes = submitTogether(Collections.nCopies(SENDERS, payment(paymentId)));

        // Then one sender is told the payment was accepted, every other is told it was a
        // duplicate, and the payment is held exactly once
        assertEquals(1, countOf(CREATED, outcomes));
        assertEquals(SENDERS - 1, countOf(DUPLICATE, outcomes));
        assertEquals(1, storedPaymentCount(paymentId));
    }

    @Test
    void testSamePaymentReceivedConcurrentlyThenReceivedAgain() throws Exception {
        // Given a payment which many senders have already submitted at the same moment
        var paymentId = nextPaymentId();
        submitTogether(Collections.nCopies(SENDERS, payment(paymentId)));

        // When that payment is submitted once more afterwards
        var outcome = submit(payment(paymentId));

        // Then the payment is reported as a duplicate and is still held exactly once
        assertEquals(DUPLICATE, outcome);
        assertEquals(1, storedPaymentCount(paymentId));
    }

    @Test
    void testDistinctPaymentsReceivedConcurrently() throws Exception {
        // Given several payments which each carry their own identifier
        var paymentIds = new ArrayList<String>();
        var payments = new ArrayList<String>();
        for (var sender = 0; sender < SENDERS; sender++) {
            var paymentId = nextPaymentId();
            paymentIds.add(paymentId);
            payments.add(payment(paymentId));
        }

        // When every payment is submitted at the same moment
        var outcomes = submitTogether(payments);

        // Then every sender is told its payment was accepted and each payment is held exactly once
        assertEquals(SENDERS, countOf(CREATED, outcomes));
        for (var paymentId : paymentIds) {
            assertEquals(1, storedPaymentCount(paymentId));
        }
    }

    @Test
    void testDistinctPaymentsEachDuplicatedReceivedConcurrently() throws Exception {
        // Given several payments which each carry their own identifier, each duplicated many times
        var distinctPayments = 5;
        var copiesEach = 4;
        var paymentIds = new ArrayList<String>();
        var payments = new ArrayList<String>();
        for (var index = 0; index < distinctPayments; index++) {
            var paymentId = nextPaymentId();
            paymentIds.add(paymentId);
            payments.addAll(Collections.nCopies(copiesEach, payment(paymentId)));
        }
        // Interleave the copies so that senders carrying the same identifier are not released
        // next to one another, which is the ordering a retrying caller would actually produce
        Collections.shuffle(payments);

        // When every submission is made at the same moment
        var outcomes = submitTogether(payments);

        // Then one sender per identifier is told its payment was accepted, every other is told it
        // was a duplicate, and each payment is held exactly once
        assertEquals(distinctPayments, countOf(CREATED, outcomes));
        assertEquals(distinctPayments * (copiesEach - 1), countOf(DUPLICATE, outcomes));
        for (var paymentId : paymentIds) {
            assertEquals(1, storedPaymentCount(paymentId));
        }
    }

    // --- Helpers -------------------------------------------------------------

    /**
     * Submits every payment from its own thread, each held at a barrier until all of them have
     * arrived. Holding them means the requests will overlap upon sending time
     */
    static List<Integer> submitTogether(List<String> payments) throws Exception {
        var senders = Executors.newFixedThreadPool(payments.size());
        var releaseTogether = new CyclicBarrier(payments.size());
        try {
            var submissions = new ArrayList<Future<Integer>>();
            for (var payment : payments) {
                submissions.add(senders.submit(() -> {
                    releaseTogether.await(30, TimeUnit.SECONDS);
                    return submit(payment);
                }));
            }

            var outcomes = new ArrayList<Integer>();
            for (var submission : submissions) {
                outcomes.add(submission.get(30, TimeUnit.SECONDS));
            }
            return outcomes;
        } finally {
            senders.shutdownNow();
        }
    }

    /** How many payments are held against an identifier, which is the count that must never exceed one. */
    long storedPaymentCount(String paymentId) {
        return this.entityManager
            .createQuery("SELECT count(p) FROM payment p WHERE p.paymentId = :paymentId", Long.class)
            .setParameter("paymentId", paymentId)
            .getSingleResult();
    }

    static long countOf(int outcome, List<Integer> outcomes) {
        return outcomes.stream().filter(status -> status == outcome).count();
    }

    static int submit(String payment) {
        return given()
                .contentType(ContentType.JSON)
                .body(payment)
                .when()
                .post("/payments")
                .thenReturn()
                .statusCode();
    }

    static String payment(String paymentId) {
        return """
            {
              "paymentId": "%s",
              "customerId": "CUST-001",
              "amount": 125.50,
              "currency": "GBP"
            }
            """.formatted(paymentId);
    }

    static String nextPaymentId() {
        return "PAY-" + UUID.randomUUID();
    }
}
