package com.dynatrace.easytrade.creditcardorderservice.models;

/**
 * Response body returned when a bitcoin payment is accepted or queried.
 *
 * @param paymentId server-generated payment identifier the client polls on
 * @param status    current lifecycle state (e.g. "pending", "confirmed")
 */
public record BitcoinPaymentResponse(String paymentId, String status) {
}
