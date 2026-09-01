package com.dynatrace.easytrade.creditcardorderservice;

import com.dynatrace.easytrade.creditcardorderservice.models.BitcoinPayment;
import com.dynatrace.easytrade.creditcardorderservice.models.BitcoinPaymentStatusType;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Default {@link BitcoinPaymentProcessor} for the workshop / demo environment.
 *
 * <p>Simulates the latency and unreliability of a real on-chain confirmation:
 * it sleeps for a bounded, randomised interval and then reports a mostly-CONFIRMED,
 * occasionally-FAILED result. Real deployments replace this bean with a client
 * that talks to the actual payment processor. No real (un-instrumented) external
 * call is made here.
 */
public class SimulatedBitcoinPaymentProcessor implements BitcoinPaymentProcessor {

    private final long minLatencyMs;
    private final long maxLatencyMs;

    public SimulatedBitcoinPaymentProcessor() {
        this(200, 1500);
    }

    public SimulatedBitcoinPaymentProcessor(long minLatencyMs, long maxLatencyMs) {
        this.minLatencyMs = minLatencyMs;
        this.maxLatencyMs = maxLatencyMs;
    }

    @Override
    public BitcoinPaymentStatusType confirm(BitcoinPayment payment) throws InterruptedException {
        long latency = ThreadLocalRandom.current().nextLong(minLatencyMs, maxLatencyMs + 1);
        Thread.sleep(latency);
        // ~90% confirm, ~10% fail — models a real processor's reject rate.
        return ThreadLocalRandom.current().nextInt(100) < 90
                ? BitcoinPaymentStatusType.CONFIRMED
                : BitcoinPaymentStatusType.FAILED;
    }
}
