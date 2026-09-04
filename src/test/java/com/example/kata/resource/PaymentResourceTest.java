package com.example.kata.resource;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class PaymentResourceTest {

    static final int ACCEPTED = 200;
    static final int BAD_REQUEST = 400;
    static final int CONFLICT = 409;

    @Test
    void testNewPaymentReceived() {
        // Given a payment which has not been seen before
        var payment = payment(nextPaymentId(), "CUST-001", "125.50", "GBP");


        // When payment creation is requested
        int outcome = createPayment(payment);

        // Then the payment is accepted
        assertEquals(ACCEPTED, outcome);
    }

    @Test
    void testDuplicatePaymentReceived() {
        // Given a payment which has already been processed
        String payment = payment(nextPaymentId(), "CUST-001", "125.50", "GBP");
        createPayment(payment);

        // When the same payment is submitted again
        int outcome = createPayment(payment);

        // Then the payment is reported as a duplicate
        assertEquals(CONFLICT, outcome);
    }

    @Test
    void testDuplicatePaymentReceivedRepeatedly() {
        // Given a payment which has already been processed
        String payment = payment(nextPaymentId(), "CUST-001", "125.50", "GBP");
        createPayment(payment);

        // When the sender retries the same payment several times
        // Then every retry is reported as a duplicate
        for (int retry = 0; retry < 5; retry++) {
            assertEquals(CONFLICT, createPayment(payment));
        }
    }

    @Test
    void testMultipleDistinctPaymentsReceived() {
        // Given several payments which have not been seen before
        String first = payment(nextPaymentId(), "CUST-001", "125.50", "GBP");
        String second = payment(nextPaymentId(), "CUST-001", "10.00", "GBP");
        String third = payment(nextPaymentId(), "CUST-002", "99.99", "GBP");

        // When each payment is submitted
        // Then each payment is accepted independently of the others
        assertEquals(ACCEPTED, createPayment(first));
        assertEquals(ACCEPTED, createPayment(second));
        assertEquals(ACCEPTED, createPayment(third));
    }

    @Test
    void testPaymentReceivedWithoutPaymentId() {
        // Given a payment which carries no identifier
        String payment = """
            {
              "customerId": "CUST-001",
              "amount": 125.50,
              "currency": "GBP"
            }
            """;

        // When payment creation is requested
        int outcome = createPayment(payment);

        // Then the payment is rejected as invalid
        assertEquals(BAD_REQUEST, outcome);
    }

    @Test
    void testPaymentReceivedWithoutCustomerId() {
        // Given a payment which identifies no customer
        String payment = """
            {
              "paymentId": "%s",
              "amount": 125.50,
              "currency": "GBP"
            }
            """.formatted(nextPaymentId());

        // When payment creation is requested
        int outcome = createPayment(payment);

        // Then the payment is rejected as invalid
        assertEquals(BAD_REQUEST, outcome);
    }

    @Test
    void testPaymentReceivedWithoutAmount() {
        // Given a payment which states no amount
        String payment = """
            {
              "paymentId": "%s",
              "customerId": "CUST-001",
              "currency": "GBP"
            }
            """.formatted(nextPaymentId());

        // When payment creation is requested
        int outcome = createPayment(payment);

        // Then the payment is rejected as invalid
        assertEquals(BAD_REQUEST, outcome);
    }

    @Test
    void testPaymentReceivedWithZeroAmount() {
        // Given a payment for no value
        String payment = payment(nextPaymentId(), "CUST-001", "0.00", "GBP");

        // When payment creation is requested
        int outcome = createPayment(payment);

        // Then the payment is rejected as invalid
        assertEquals(BAD_REQUEST, outcome);
    }

    @Test
    void testPaymentReceivedWithNegativeAmount() {
        // Given a payment for a negative value
        String payment = payment(nextPaymentId(), "CUST-001", "-125.50", "GBP");

        // When payment creation is requested
        int outcome = createPayment(payment);

        // Then the payment is rejected as invalid
        assertEquals(BAD_REQUEST, outcome);
    }

    @Test
    void testPaymentReceivedWithoutCurrency() {
        // Given a payment which states no currency
        String payment = """
            {
              "paymentId": "%s",
              "customerId": "CUST-001",
              "amount": 125.50
            }
            """.formatted(nextPaymentId());

        // When payment creation is requested
        int outcome = createPayment(payment);

        // Then the payment is rejected as invalid
        assertEquals(BAD_REQUEST, outcome);
    }

    @Test
    void testPaymentReceivedWithUnrecognisedCurrency() {
        // Given a payment quoted in something which is not a known currency
        String payment = payment(nextPaymentId(), "CUST-001", "125.50", "POUNDS");

        // When payment creation is requested
        int outcome = createPayment(payment);

        // Then the payment is rejected as invalid
        assertEquals(BAD_REQUEST, outcome);
    }

    @Test
    void testEmptyPaymentReceived() {
        // Given a payment which carries no detail at all
        String payment = "{}";

        // When payment creation is requested
        int outcome = createPayment(payment);

        // Then the payment is rejected as invalid
        assertEquals(BAD_REQUEST, outcome);
    }

    @Test
    void testMalformedPaymentReceived() {
        // Given a payment which is not well formed
        String payment = "{ \"paymentId\": ";

        // When payment creation is requested
        int outcome = createPayment(payment);

        // Then the payment is rejected as invalid
        assertEquals(BAD_REQUEST, outcome);
    }

    // --- Cases blocked on an undecided rule (see CLAUDE.md) -------------------

    @Test
    @Disabled("Expected behaviour undecided: is a replay with different detail a duplicate, or a conflict of its own?")
    void testPaymentReceivedWithKnownIdentifierAndDifferentDetail() {
        // Given a payment which has already been processed
        String identifier = nextPaymentId();
        createPayment(payment(identifier, "CUST-001", "125.50", "GBP"));

        // When a payment reusing that identifier arrives carrying different detail
        int outcome = createPayment(payment(identifier, "CUST-999", "999.00", "GBP"));

        // Then the payment is reported as a duplicate
        assertEquals(CONFLICT, outcome);
    }

    @Test
    @Disabled("Expected behaviour undecided: is a lowercase currency rejected, or normalised?")
    void testPaymentReceivedWithLowercaseCurrency() {
        // Given a payment whose currency is stated in lower case
        String payment = payment(nextPaymentId(), "CUST-001", "125.50", "gbp");

        // When payment creation is requested
        int outcome = createPayment(payment);

        // Then the payment is rejected as invalid
        assertEquals(BAD_REQUEST, outcome);
    }

    @Test
    @Disabled("Expected behaviour undecided: is an amount carrying sub-unit precision rejected, or rounded?")
    void testPaymentReceivedWithExcessiveAmountPrecision() {
        // Given a payment for an amount finer than the currency can express
        String payment = payment(nextPaymentId(), "CUST-001", "125.505", "GBP");

        // When payment creation is requested
        int outcome = createPayment(payment);

        // Then the payment is rejected as invalid
        assertEquals(BAD_REQUEST, outcome);
    }

    // --- Helpers -------------------------------------------------------------

    static int createPayment(String payment) {
        return given()
                .contentType(ContentType.JSON)
                .body(payment)
                .when()
                .post("/payments")
                .thenReturn()
                .statusCode();
    }

    static String payment(String paymentId, String customerId, String amount, String currency) {
        return """
            {
              "paymentId": "%s",
              "customerId": "%s",
              "amount": %s,
              "currency": "%s"
            }
            """.formatted(paymentId, customerId, amount, currency);
    }

    static String nextPaymentId() {
        return "PAY-" + UUID.randomUUID();
    }
}
