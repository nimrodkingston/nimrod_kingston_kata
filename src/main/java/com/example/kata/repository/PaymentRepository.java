package com.example.kata.repository;

import com.example.kata.exception.DuplicatePaymentException;
import com.example.kata.exception.PaymentNotFoundException;
import com.example.kata.model.PaymentModel;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.NoResultException;
import org.hibernate.exception.ConstraintViolationException;

@ApplicationScoped
public class PaymentRepository implements PanacheRepository<PaymentModel> {
    public void create(PaymentModel payment) {
        try {
            this.persist(payment);
            this.flush();
        } catch(ConstraintViolationException e) {
            // Constraint violations will be treated as a collision, all other exceptions will be propagated up
            throw new DuplicatePaymentException("Payment with payment-id %s has already been created".formatted(payment.getPaymentId()));
        }
    }

    public PaymentModel findPaymentByPaymentId(String paymentId) {
        try {
            return this.find("paymentId", paymentId).singleResult();
        } catch(NoResultException e) {
            throw new PaymentNotFoundException("A payment could not be found with payment-id %s".formatted(paymentId));
        }
    }
}
