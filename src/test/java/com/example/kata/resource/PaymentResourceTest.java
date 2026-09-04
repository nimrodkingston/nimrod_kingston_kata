package com.example.kata.resource;

import com.example.kata.dto.PaymentDto;
import com.example.kata.exception.DuplicatePaymentException;
import com.example.kata.exception.PaymentNotFoundException;
import com.example.kata.service.PaymentService;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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

    static final int OK = 200;
    static final int CREATED = 201;
    static final int BAD_REQUEST = 400;
    static final int NOT_FOUND = 404;
    static final int CONFLICT = 409;
    static final int SERVER_ERROR = 500;

    @InjectMock
    PaymentService paymentService;

    @Test
    void testNewPaymentReceived() {
        // Given a payment which has not been seen before
        var payment = new PaymentDto("PAY-123", "CUST-001", new BigDecimal ("125.50"), "GBP");

        // When payment creation is requested
        var outcome = createPayment(payment);

        // Then the payment is accepted and its detail is taken forward for processing
        assertEquals(CREATED, outcome);
        verify(paymentService).createPayment(
                payment.paymentId(), payment.customerId(), payment.amount(), payment.currency());
    }

    @Test
    void testDuplicatePaymentReceived() {
        // Given a payment which has already been processed
        doThrow(new DuplicatePaymentException("PAY-123"))
                .when(paymentService).createPayment(any(), any(), any(), any());
        var payment =new PaymentDto("PAY-123", "CUST-001", new BigDecimal ("125.50"), "GBP");

        // When the same payment is submitted again
        var outcome = createPayment(payment);

        // Then the payment is reported as a duplicate
        assertEquals(CONFLICT, outcome);
    }

    @Test
    void testDuplicatePaymentReceivedRepeatedly() {
        // Given a payment which has already been processed
        doThrow(new DuplicatePaymentException("PAY-123"))
                .when(paymentService).createPayment(any(), any(), any(), any());
        var payment =new PaymentDto("PAY-123", "CUST-001", new BigDecimal ("125.50"), "GBP");

        // When the sender retries the same payment several times
        // Then every retry is reported as a duplicate
        for (var retry = 0; retry < 5; retry++) {
            assertEquals(CONFLICT, createPayment(payment));
        }
        verify(paymentService, times(5)).createPayment(
                payment.paymentId(), payment.customerId(), payment.amount(), payment.currency());
    }

    @Test
    void testMultipleDistinctPaymentsReceived() {
        // Given several payments which have not been seen before
        var first =new PaymentDto("PAY-123", "CUST-001", new BigDecimal ("125.50"), "GBP");
        var second =new PaymentDto("PAY-124", "CUST-001", new BigDecimal("10.00"), "GBP");
        var third =new PaymentDto("PAY-125", "CUST-002", new BigDecimal("99.99"), "GBP");

        // When each payment is submitted
        // Then each payment is accepted independently of the others
        assertEquals(CREATED, createPayment(first));
        assertEquals(CREATED, createPayment(second));
        assertEquals(CREATED, createPayment(third));
        verify(paymentService).createPayment(
                first.paymentId(), first.customerId(), first.amount(), first.currency());
        verify(paymentService).createPayment(
                second.paymentId(), second.customerId(), second.amount(), second.currency());
        verify(paymentService).createPayment(
                third.paymentId(), third.customerId(), third.amount(), third.currency());
    }

    @Test
    void testPaymentReceivedWithoutPaymentId() {
        // Given a payment which carries no identifier
        var payment =new PaymentDto(null, "CUST-001", new BigDecimal ("125.50"), "GBP");

        // When payment creation is requested
        var outcome = createPayment(payment);

        // Then the payment is rejected as invalid and is never taken forward for processing
        assertEquals(BAD_REQUEST, outcome);
        verifyNoInteractions(paymentService);
    }

    @Test
    void testPaymentReceivedWithoutCustomerId() {
        // Given a payment which identifies no customer
        var payment =new PaymentDto("PAY-123", null, new BigDecimal ("125.50"), "GBP");

        // When payment creation is requested
        var outcome = createPayment(payment);

        // Then the payment is rejected as invalid and is never taken forward for processing
        assertEquals(BAD_REQUEST, outcome);
        verifyNoInteractions(paymentService);
    }

    @Test
    void testPaymentReceivedWithoutAmount() {
        // Given a payment which states no amount
        var payment =new PaymentDto("PAY-123", "CUST-001", null, "GBP");

        // When payment creation is requested
        var outcome = createPayment(payment);

        // Then the payment is rejected as invalid and is never taken forward for processing
        assertEquals(BAD_REQUEST, outcome);
        verifyNoInteractions(paymentService);
    }

    @Test
    void testPaymentReceivedWithZeroAmount() {
        // Given a payment for no value
        var payment =new PaymentDto("PAY-123", "CUST-001", new BigDecimal("0.00"), "GBP");

        // When payment creation is requested
        var outcome = createPayment(payment);

        // Then the payment is rejected as invalid and is never taken forward for processing
        assertEquals(BAD_REQUEST, outcome);
        verifyNoInteractions(paymentService);
    }

    @Test
    void testPaymentReceivedWithNegativeAmount() {
        // Given a payment for a negative value
        var payment =new PaymentDto("PAY-123", "CUST-001", new BigDecimal("-125.50"), "GBP");

        // When payment creation is requested
        var outcome = createPayment(payment);

        // Then the payment is rejected as invalid and is never taken forward for processing
        assertEquals(BAD_REQUEST, outcome);
        verifyNoInteractions(paymentService);
    }

    @Test
    void testPaymentReceivedWithoutCurrency() {
        // Given a payment which states no currency
        var payment =new PaymentDto("PAY-123", "CUST-001", new BigDecimal ("125.50"), null);

        // When payment creation is requested
        var outcome = createPayment(payment);

        // Then the payment is rejected as invalid and is never taken forward for processing
        assertEquals(BAD_REQUEST, outcome);
        verifyNoInteractions(paymentService);
    }

    @Test
    void testPaymentReceivedWithUnrecognisedCurrency() {
        // Given a payment quoted in something which is not a known currencyCLAUDE.md
        var payment =new PaymentDto("PAY-123", "CUST-001", new BigDecimal ("125.50"), "POUNDS");

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
    void testPaymentReceivedWithLowercaseCurrency() {
        // Given a payment whose currency is stated in lower case
        var payment =new PaymentDto("PAY-123", "CUST-001", new BigDecimal ("125.50"), "gbp");

        // When payment creation is requested
        var outcome = createPayment(payment);

        // Then the payment is rejected as invalid and is never taken forward for processing
        assertEquals(BAD_REQUEST, outcome);
        verifyNoInteractions(paymentService);
    }

    @Test
    void testPaymentReceivedWhichCannotBeProcessed() {
        // Given a payment which cannot be processed for reasons outside the sender's control
        doThrow(new IllegalStateException("processing unavailable"))
                .when(paymentService).createPayment(any(), any(), any(), any());

        // When payment creation is requested
        var outcome = createPayment(new PaymentDto("PAY-123", "CUST-001", new BigDecimal ("125.50"), "GBP"));

        // Then the failure is reported to the sender as something other than their own fault
        assertEquals(SERVER_ERROR, outcome);
    }

    @Test
    void testGetPaymentWithExistingPayment() {
        // Given a payment has been saved in the system
        var payment = new PaymentDto("PAY-123", "CUST-001", new BigDecimal("125.50"), "GBP");
        when(paymentService.getPaymentByPaymentId("PAY-123")).thenReturn(payment);

        // When the payment is requested by payment-id
        var response = requestPayment("PAY-123");

        // Then the response should be successful and payment should be returned
        assertEquals(OK, response.statusCode());
        assertEquals("PAY-123", response.jsonPath().getString("paymentId"));
        assertEquals("CUST-001", response.jsonPath().getString("customerId"));
        assertEquals(0, payment.amount().compareTo(new BigDecimal(response.jsonPath().getString("amount"))));
        assertEquals("GBP", response.jsonPath().getString("currency"));
    }

    @Test
    void testGetPaymentWithNoExistingPayment() {
        // Given a payment has not been saved in the system
        doThrow(new PaymentNotFoundException("PAY-404"))
                .when(paymentService).getPaymentByPaymentId(any());

        // When the payment is requested by payment-id
        var response = requestPayment("PAY-404");

        // Then the response should not be found
        assertEquals(NOT_FOUND, response.statusCode());
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

    /** Returns the whole response, since these cases assert on what came back as well as on the outcome. */
    static Response requestPayment(String paymentId) {
        return given()
                .when()
                .get("/payments/{paymentId}", paymentId)
                .thenReturn();
    }
}
