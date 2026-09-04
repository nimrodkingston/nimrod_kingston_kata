package com.example.kata.resource;

import com.example.kata.dto.PaymentRequest;
import com.example.kata.exception.DuplicatePaymentException;
import com.example.kata.service.PaymentService;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * The contract offered to callers over HTTP, exercised with payment processing stood in for.
 *
 * <p>Standing processing in lets each case state its own starting position directly — whether a
 * payment has been seen before is set up here rather than built up by earlier requests — and lets
 * the rejection cases assert that an invalid payment never reaches processing at all.
 *
 * <p>Payments are stated as the type the endpoint accepts, so a change to the shape of a payment
 * is caught when these compile. The two cases which carry something that is not a payment at all
 * are the exception, and state their body directly.
 */
@QuarkusTest
class PaymentResourceTest {

    static final int CREATED = 201;
    static final int BAD_REQUEST = 400;
    static final int CONFLICT = 409;
    static final int SERVER_ERROR = 500;

    @InjectMock
    PaymentService paymentService;

    @Test
    void testNewPaymentReceived() {
        // Given a payment which has not been seen before
        doNothing().when(paymentService).createPayment();
        var payment = payment("PAY-123", "CUST-001", "125.50", "GBP");

        // When payment creation is requested
        var outcome = createPayment(payment);

        // Then the payment is accepted and taken forward for processing
        assertEquals(CREATED, outcome);
        verify(paymentService).createPayment();
    }

    @Test
    void testDuplicatePaymentReceived() {
        // Given a payment which has already been processed
        doThrow(new DuplicatePaymentException("PAY-123")).when(paymentService).createPayment();
        var payment = payment("PAY-123", "CUST-001", "125.50", "GBP");

        // When the same payment is submitted again
        var outcome = createPayment(payment);

        // Then the payment is reported as a duplicate
        assertEquals(CONFLICT, outcome);
    }

    @Test
    void testDuplicatePaymentReceivedRepeatedly() {
        // Given a payment which has already been processed
        doThrow(new DuplicatePaymentException("PAY-123")).when(paymentService).createPayment();
        var payment = payment("PAY-123", "CUST-001", "125.50", "GBP");

        // When the sender retries the same payment several times
        // Then every retry is reported as a duplicate
        for (var retry = 0; retry < 5; retry++) {
            assertEquals(CONFLICT, createPayment(payment));
        }
        verify(paymentService, times(5)).createPayment();
    }

    @Test
    void testMultipleDistinctPaymentsReceived() {
        // Given several payments which have not been seen before
        doNothing().when(paymentService).createPayment();
        var first = payment("PAY-123", "CUST-001", "125.50", "GBP");
        var second = payment("PAY-124", "CUST-001", "10.00", "GBP");
        var third = payment("PAY-125", "CUST-002", "99.99", "GBP");

        // When each payment is submitted
        // Then each payment is accepted independently of the others
        assertEquals(CREATED, createPayment(first));
        assertEquals(CREATED, createPayment(second));
        assertEquals(CREATED, createPayment(third));
        verify(paymentService, times(3)).createPayment();
    }

    @Test
    void testPaymentReceivedWithoutPaymentId() {
        // Given a payment which carries no identifier
        var payment = payment(null, "CUST-001", "125.50", "GBP");

        // When payment creation is requested
        var outcome = createPayment(payment);

        // Then the payment is rejected as invalid and is never taken forward for processing
        assertEquals(BAD_REQUEST, outcome);
        verifyNoInteractions(paymentService);
    }

    @Test
    void testPaymentReceivedWithoutCustomerId() {
        // Given a payment which identifies no customer
        var payment = payment("PAY-123", null, "125.50", "GBP");

        // When payment creation is requested
        var outcome = createPayment(payment);

        // Then the payment is rejected as invalid and is never taken forward for processing
        assertEquals(BAD_REQUEST, outcome);
        verifyNoInteractions(paymentService);
    }

    @Test
    void testPaymentReceivedWithoutAmount() {
        // Given a payment which states no amount
        var payment = payment("PAY-123", "CUST-001", null, "GBP");

        // When payment creation is requested
        var outcome = createPayment(payment);

        // Then the payment is rejected as invalid and is never taken forward for processing
        assertEquals(BAD_REQUEST, outcome);
        verifyNoInteractions(paymentService);
    }

    @Test
    void testPaymentReceivedWithZeroAmount() {
        // Given a payment for no value
        var payment = payment("PAY-123", "CUST-001", "0.00", "GBP");

        // When payment creation is requested
        var outcome = createPayment(payment);

        // Then the payment is rejected as invalid and is never taken forward for processing
        assertEquals(BAD_REQUEST, outcome);
        verifyNoInteractions(paymentService);
    }

    @Test
    void testPaymentReceivedWithNegativeAmount() {
        // Given a payment for a negative value
        var payment = payment("PAY-123", "CUST-001", "-125.50", "GBP");

        // When payment creation is requested
        var outcome = createPayment(payment);

        // Then the payment is rejected as invalid and is never taken forward for processing
        assertEquals(BAD_REQUEST, outcome);
        verifyNoInteractions(paymentService);
    }

    @Test
    void testPaymentReceivedWithoutCurrency() {
        // Given a payment which states no currency
        var payment = payment("PAY-123", "CUST-001", "125.50", null);

        // When payment creation is requested
        var outcome = createPayment(payment);

        // Then the payment is rejected as invalid and is never taken forward for processing
        assertEquals(BAD_REQUEST, outcome);
        verifyNoInteractions(paymentService);
    }

    @Test
    void testPaymentReceivedWithUnrecognisedCurrency() {
        // Given a payment quoted in something which is not a known currencyCLAUDE.md
        var payment = payment("PAY-123", "CUST-001", "125.50", "POUNDS");

        // When payment creation is requested
        var outcome = createPayment(payment);

        // Then the payment is rejected as invalid and is never taken forward for processing
        assertEquals(BAD_REQUEST, outcome);
        verifyNoInteractions(paymentService);
    }

    @Test
    void testEmptyPaymentReceived() {
        // Given a payment which carries no detail at all
        var payment = "{}";

        // When payment creation is requested
        var outcome = createPayment(payment);

        // Then the payment is rejected as invalid and is never taken forward for processing
        assertEquals(BAD_REQUEST, outcome);
        verifyNoInteractions(paymentService);
    }

    @Test
    void testMalformedPaymentReceived() {
        // Given a payment which is not well formed
        var payment = "{ \"paymentId\": ";

        // When payment creation is requested
        var outcome = createPayment(payment);

        // Then the payment is rejected as invalid and is never taken forward for processing
        assertEquals(BAD_REQUEST, outcome);
        verifyNoInteractions(paymentService);
    }

    // --- Cases blocked on an undecided rule (see CLAUDE.md) -------------------

    @Test
    @Disabled("Expected behaviour undecided: is a replay with different detail a duplicate, or a conflict of its own?")
    void testPaymentReceivedWithKnownIdentifierAndDifferentDetail() {
        // Given a payment which has already been processed
        doThrow(new DuplicatePaymentException("PAY-123")).when(paymentService).createPayment();

        // When a payment reusing that identifier arrives carrying different detail
        var outcome = createPayment(payment("PAY-123", "CUST-999", "999.00", "GBP"));

        // Then the payment is reported as a duplicate
        assertEquals(CONFLICT, outcome);
    }

    @Test
    @Disabled("Expected behaviour undecided: is a lowercase currency rejected, or normalised?")
    void testPaymentReceivedWithLowercaseCurrency() {
        // Given a payment whose currency is stated in lower case
        var payment = payment("PAY-123", "CUST-001", "125.50", "gbp");

        // When payment creation is requested
        var outcome = createPayment(payment);

        // Then the payment is rejected as invalid and is never taken forward for processing
        assertEquals(BAD_REQUEST, outcome);
        verifyNoInteractions(paymentService);
    }

    @Test
    @Disabled("Expected behaviour undecided: is an amount carrying sub-unit precision rejected, or rounded?")
    void testPaymentReceivedWithExcessiveAmountPrecision() {
        // Given a payment for an amount finer than the currency can express
        var payment = payment("PAY-123", "CUST-001", "125.505", "GBP");

        // When payment creation is requested
        var outcome = createPayment(payment);

        // Then the payment is rejected as invalid and is never taken forward for processing
        assertEquals(BAD_REQUEST, outcome);
        verifyNoInteractions(paymentService);
    }

    @Test
    @Disabled("Expected behaviour undecided: how should a payment which cannot be processed be reported?")
    void testPaymentReceivedWhichCannotBeProcessed() {
        // Given a payment which cannot be processed for reasons outside the sender's control
        doThrow(new IllegalStateException("processing unavailable")).when(paymentService).createPayment();

        // When payment creation is requested
        var outcome = createPayment(payment("PAY-123", "CUST-001", "125.50", "GBP"));

        // Then the failure is reported to the sender as something other than their own fault
        assertEquals(SERVER_ERROR, outcome);
    }

    // --- Helpers -------------------------------------------------------------

    static int createPayment(PaymentRequest payment) {
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

    static PaymentRequest payment(String paymentId, String customerId, String amount, String currency) {
        return new PaymentRequest(
                paymentId,
                customerId,
                amount == null ? null : new BigDecimal(amount),
                currency);
    }
}
