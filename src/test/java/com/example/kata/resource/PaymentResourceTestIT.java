package com.example.kata.resource;

import com.example.kata.dto.PaymentDto;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The same contract exercised in packaged mode against real payment processing.
 *
 * <p>Processing is not stood in for here, so a payment which has already been processed is set up
 * by submitting it — which is what makes these cases worth running separately from the ones which
 * stand processing in: they are the only place the de-duplication rule is proven end to end.
 */
@QuarkusIntegrationTest
class PaymentResourceTestIT {

    static final int ACCEPTED = 200;
    static final int BAD_REQUEST = 400;
    static final int CONFLICT = 409;

    @Test
    void testNewPaymentReceived() {
        // Given a payment which has not been seen before
        var payment = payment(nextPaymentId(), "CUST-001", "125.50", "GBP");

        // When payment creation is requested
        var outcome = createPayment(payment);

        // Then the payment is accepted
        assertEquals(ACCEPTED, outcome);
    }

    @Test
    void testDuplicatePaymentReceived() {
        // Given a payment which has already been processed
        var payment = payment(nextPaymentId(), "CUST-001", "125.50", "GBP");
        createPayment(payment);

        // When the same payment is submitted again
        var outcome = createPayment(payment);

        // Then the payment is reported as a duplicate
        assertEquals(CONFLICT, outcome);
    }

    @Test
    void testDuplicatePaymentReceivedRepeatedly() {
        // Given a payment which has already been processed
        var payment = payment(nextPaymentId(), "CUST-001", "125.50", "GBP");
        createPayment(payment);

        // When the sender retries the same payment several times
        // Then every retry is reported as a duplicate
        for (var retry = 0; retry < 5; retry++) {
            assertEquals(CONFLICT, createPayment(payment));
        }
    }

    @Test
    void testMultipleDistinctPaymentsReceived() {
        // Given several payments which have not been seen before
        var first = payment(nextPaymentId(), "CUST-001", "125.50", "GBP");
        var second = payment(nextPaymentId(), "CUST-001", "10.00", "GBP");
        var third = payment(nextPaymentId(), "CUST-002", "99.99", "GBP");

        // When each payment is submitted
        // Then each payment is accepted independently of the others
        assertEquals(ACCEPTED, createPayment(first));
        assertEquals(ACCEPTED, createPayment(second));
        assertEquals(ACCEPTED, createPayment(third));
    }

    @Test
    void testEmptyPaymentReceived() {
        // Given a payment which carries no detail at all
        var payment = "{}";

        // When payment creation is requested
        var outcome = createPayment(payment);

        // Then the payment is rejected as invalid
        assertEquals(BAD_REQUEST, outcome);
    }

    // --- Helpers -------------------------------------------------------------

    static int createPayment(PaymentDto payment) {
        return given()
                .contentType(ContentType.JSON)
                .body(payment)
                .when()
                .post("/payments")
                .thenReturn()
                .statusCode();
    }

    static int createPayment(String payment) {
        return given()
                .contentType(ContentType.JSON)
                .body(payment)
                .when()
                .post("/payments")
                .thenReturn()
                .statusCode();
    }

    static PaymentDto payment(String paymentId, String customerId, String amount, String currency) {
        return new PaymentDto(paymentId, customerId, new BigDecimal(amount), currency);
    }

    static String nextPaymentId() {
        return "PAY-" + UUID.randomUUID();
    }
}
