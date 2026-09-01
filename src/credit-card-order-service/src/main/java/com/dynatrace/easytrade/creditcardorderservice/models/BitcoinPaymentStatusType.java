package com.dynatrace.easytrade.creditcardorderservice.models;

import lombok.Getter;

/**
 * Lifecycle of a bitcoin payment.
 *
 * <p>Bitcoin settlement is asynchronous: the incoming request only records
 * {@link #PENDING}; a background worker advances the payment to a terminal
 * state ({@link #CONFIRMED}, {@link #FAILED} or {@link #EXPIRED}) once the
 * external payment processor / blockchain confirmation resolves. This keeps
 * the slow, unreliable external call off the synchronous request path.
 */
@Getter
public enum BitcoinPaymentStatusType {
    PENDING(10, "pending", "Bitcoin payment created and awaiting on-chain confirmation."),
    CONFIRMED(20, "confirmed", "Bitcoin payment confirmed on-chain."),
    FAILED(30, "failed", "Bitcoin payment failed or was rejected by the processor."),
    EXPIRED(40, "expired", "Bitcoin payment was not confirmed before the confirmation window elapsed.");

    private final int sequence;
    private final String type;
    private final String description;

    BitcoinPaymentStatusType(int sequence, String type, String description) {
        this.sequence = sequence;
        this.type = type;
        this.description = description;
    }

    public boolean isTerminal() {
        return this != PENDING;
    }
}
