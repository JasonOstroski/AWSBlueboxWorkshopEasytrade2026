package com.dynatrace.easytrade.creditcardorderservice.models;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Inbound request to start a bitcoin payment for an account.
 *
 * @param accountId       account initiating the payment
 * @param amount          fiat amount to be paid in bitcoin (must be &gt; 0)
 * @param currency        ISO fiat currency code for {@code amount} (e.g. "USD")
 * @param btcAddress      destination bitcoin address for the payment
 */
public record BitcoinPaymentRequest(Integer accountId, BigDecimal amount, String currency, String btcAddress) {
    public BitcoinPaymentRequest {
        Objects.requireNonNull(accountId, "accountId is required");
        Objects.requireNonNull(amount, "amount is required");
        Objects.requireNonNull(currency, "currency is required");
        Objects.requireNonNull(btcAddress, "btcAddress is required");
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be greater than zero");
        }
    }
}
