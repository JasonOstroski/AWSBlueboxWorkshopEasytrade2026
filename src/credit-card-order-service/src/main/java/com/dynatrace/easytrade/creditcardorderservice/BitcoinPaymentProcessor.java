package com.dynatrace.easytrade.creditcardorderservice;

import com.dynatrace.easytrade.creditcardorderservice.models.BitcoinPayment;
import com.dynatrace.easytrade.creditcardorderservice.models.BitcoinPaymentStatusType;

/**
 * Abstraction over the external bitcoin payment processor / on-chain
 * confirmation. This is intentionally an interface so the slow, unreliable
 * external dependency can be swapped and mocked in tests, and so it never
 * sits on the synchronous request path (it is only ever invoked from the
 * background confirmation worker with a bounded timeout).
 */
public interface BitcoinPaymentProcessor {

    /**
     * Attempt to resolve the on-chain state of a payment. Implementations may
     * block on network / blockchain confirmation; callers MUST wrap this in a
     * timeout. Returns the terminal state the processor observed, or
     * {@link BitcoinPaymentStatusType#PENDING} if not yet resolved.
     */
    BitcoinPaymentStatusType confirm(BitcoinPayment payment) throws InterruptedException;
}
