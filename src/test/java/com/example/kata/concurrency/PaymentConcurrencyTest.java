package com.example.kata.concurrency;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class PaymentConcurrencyTest {

    static final int ACCEPTED = 200;
    static final int DUPLICATE = 409;
    static final int SENDERS = 20;

    @Test
    void testSamePaymentReceivedConcurrently() throws Exception {
        // Given a payment which has not been seen before
        String payment = payment(nextPaymentId());

        // When many senders submit that same payment at the same moment
        List<Integer> outcomes = submitTogether(Collections.nCopies(SENDERS, payment));

        // Then exactly one submission is accepted and every other is reported as a duplicate
        assertEquals(1, countOf(ACCEPTED, outcomes));
        assertEquals(SENDERS - 1, countOf(DUPLICATE, outcomes));
    }

    @Test
    void testSamePaymentReceivedConcurrentlyThenReceivedAgain() throws Exception {
        // Given a payment which many senders have already submitted at the same moment
        String payment = payment(nextPaymentId());
        submitTogether(Collections.nCopies(SENDERS, payment));

        // When that payment is submitted once more afterwards
        int outcome = submit(payment);

        // Then the payment is reported as a duplicate
        assertEquals(DUPLICATE, outcome);
    }

    @Test
    void testDistinctPaymentsReceivedConcurrently() throws Exception {
        // Given several payments which each carry their own identifier
        List<String> payments = new ArrayList<>();
        for (int sender = 0; sender < SENDERS; sender++) {
            payments.add(payment(nextPaymentId()));
        }

        // When every payment is submitted at the same moment
        List<Integer> outcomes = submitTogether(payments);

        // Then every payment is accepted
        assertEquals(SENDERS, countOf(ACCEPTED, outcomes));
    }

    @Test
    void testDistinctPaymentsEachDuplicatedReceivedConcurrently() throws Exception {
        // Given several payments which each carry their own identifier, each duplicated many times
        int distinctPayments = 5;
        int copiesEach = 4;
        List<String> payments = new ArrayList<>();
        for (int index = 0; index < distinctPayments; index++) {
            payments.addAll(Collections.nCopies(copiesEach, payment(nextPaymentId())));
        }
        Collections.shuffle(payments);

        // When every submission is made at the same moment
        List<Integer> outcomes = submitTogether(payments);

        // Then one submission per identifier is accepted and every other is reported as a duplicate
        assertEquals(distinctPayments, countOf(ACCEPTED, outcomes));
        assertEquals(distinctPayments * (copiesEach - 1), countOf(DUPLICATE, outcomes));
    }

    // --- Helpers -------------------------------------------------------------

    /** Submits every payment from its own thread, released together so the requests genuinely overlap. */
    static List<Integer> submitTogether(List<String> payments) throws Exception {
        ExecutorService senders = Executors.newFixedThreadPool(payments.size());
        CountDownLatch startGate = new CountDownLatch(1);
        try {
            List<Future<Integer>> submissions = new ArrayList<>();
            for (String payment : payments) {
                submissions.add(senders.submit(() -> {
                    startGate.await();
                    return submit(payment);
                }));
            }

            startGate.countDown();

            List<Integer> outcomes = new ArrayList<>();
            for (Future<Integer> submission : submissions) {
                outcomes.add(submission.get(30, TimeUnit.SECONDS));
            }
            return outcomes;
        } finally {
            senders.shutdownNow();
        }
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
