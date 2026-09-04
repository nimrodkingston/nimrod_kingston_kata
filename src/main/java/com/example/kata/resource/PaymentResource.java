package com.example.kata.resource;

import com.example.kata.dto.PaymentCreatedResponse;
import com.example.kata.dto.PaymentDto;
import com.example.kata.exception.DuplicatePaymentException;
import com.example.kata.exception.PaymentNotFoundException;
import com.example.kata.service.PaymentService;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/payments")
public class PaymentResource {
    private final PaymentService paymentService;

    PaymentResource(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @POST
    @Path("/")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response createPayment(@Valid PaymentDto request) {
        try {
            this.paymentService.createPayment(
                request.paymentId(),
                request.customerId(),
                request.amount(),
                request.currency()
            );
        } catch(DuplicatePaymentException $e) {
            return Response.status(Response.Status.CONFLICT).entity(request).build();
        }

        return Response.status(Response.Status.CREATED).entity(new PaymentCreatedResponse(request)).build();
    }

    @GET
    @Path("/{paymentId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getPayment(@PathParam("paymentId") String paymentId) {
        try {
            var payment = paymentService.getPaymentByPaymentId(paymentId);
            return Response.ok(payment).build();
        } catch(PaymentNotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND).entity(paymentId).build();
        }
    }
}
