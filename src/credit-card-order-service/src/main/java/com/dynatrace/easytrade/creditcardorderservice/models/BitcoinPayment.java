package com.dynatrace.easytrade.creditcardorderservice.models;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Server-side view of a bitcoin payment, including its current lifecycle state.
 *
 * @param paymentId  server-generated GUID identifying the payment
 * @param accountId  account the payment belongs to
 * @param amount     fiat amount
 * @param currency   ISO fiat currency code
 * @param btcAddress destination bitcoin address
 * @param status     current lifecycle state
 * @param createdAt  when the payment was created
 * @param updatedAt  when the status last changed
 */
public record BitcoinPayment(
        String paymentId,
        Integer accountId,
        BigDecimal amount,
        String currency,
        String btcAddress,
        BitcoinPaymentStatusType status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public BitcoinPayment withStatus(BitcoinPaymentStatusType newStatus, OffsetDateTime updatedAt) {
        return new BitcoinPayment(paymentId, accountId, amount, currency, btcAddress, newStatus, createdAt, updatedAt);
    }
}
