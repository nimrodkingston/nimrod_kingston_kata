package com.example.kata.resource;

import com.example.kata.dto.PaymentCreatedResponse;
import com.example.kata.dto.PaymentRequest;
import com.example.kata.exception.DuplicatePaymentException;
import com.example.kata.service.PaymentService;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
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
    public Response createPayment(@Valid PaymentRequest request) {
        try {
            this.paymentService.createPayment();
        } catch(DuplicatePaymentException $e) {
            return Response.status(Response.Status.CONFLICT).build();
        }

        // TODO come back to this part, make sure that this return value makes sense. May be worth returning a URI after adding the get endpoint
        return Response.status(Response.Status.CREATED).entity(new PaymentCreatedResponse(request)).build();
    }
}
