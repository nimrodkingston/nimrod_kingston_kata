package com.example.kata.service;

import com.example.kata.dto.PaymentDto;
import com.example.kata.exception.DuplicatePaymentException;
import com.example.kata.exception.PaymentNotFoundException;
import com.example.kata.model.PaymentModel;
import com.example.kata.repository.PaymentRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

import java.math.BigDecimal;

@ApplicationScoped
public class PaymentService {
    private final PaymentRepository paymentRepository;

    private final Emitter<PaymentDto> paymentProcessedEmitter;
    private final MeterRegistry meterRegistry;

    public PaymentService(
        PaymentRepository paymentRepository,
        @Channel("payment-processed") Emitter<PaymentDto> paymentProcessedEmitter,
        MeterRegistry meterRegistry) {
        this.paymentRepository = paymentRepository;
        this.paymentProcessedEmitter = paymentProcessedEmitter;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public void createPayment(String paymentId, String customerId, BigDecimal amount, String currency) {
        var payment = new PaymentModel();
        payment.setPaymentId(paymentId);
        payment.setCustomerId(customerId);
        payment.setAmount(amount);
        payment.setCurrency(currency);

        try {
            this.paymentRepository.create(payment);
            this.paymentProcessedEmitter.send(new PaymentDto(paymentId, customerId, amount, currency));
            meterRegistry.counter("successful.payment.count").increment();
            Log.infof("Payment with id %s has been stored successfully", paymentId);
        } catch (DuplicatePaymentException e) {
            meterRegistry.counter("duplicate.payment.count").increment();
            Log.infof("A conflict has occurred when attempting to create payment with payment-id %s", paymentId);
            throw e;
        }
    }

    public PaymentDto getPaymentByPaymentId(String paymentId) {
        try {
            var payment = paymentRepository.findPaymentByPaymentId(paymentId);

            return new PaymentDto(
                    payment.getPaymentId(),
                    payment.getCustomerId(),
                    payment.getAmount(),
                    payment.getCurrency()
            );
        } catch(PaymentNotFoundException e) {
            Log.infof("A payment could not be found with payment-id %s", paymentId);
            throw e;
        }
    }
}
