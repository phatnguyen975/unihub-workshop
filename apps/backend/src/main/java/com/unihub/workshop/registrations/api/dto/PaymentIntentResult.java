package com.unihub.workshop.registrations.api.dto;

/**
 * Result returned after successfully creating a payment intent.
 *
 * @param paymentIntentId external payment intent identifier (e.g. Stripe PaymentIntent ID)
 * @param clientSecret client secret used by frontend SDKs to complete checkout
 * @param status status of the payment intent (e.g. "requires_payment_method")
 */
public record PaymentIntentResult(
    String paymentIntentId,
    String clientSecret,
    String status
) {}
