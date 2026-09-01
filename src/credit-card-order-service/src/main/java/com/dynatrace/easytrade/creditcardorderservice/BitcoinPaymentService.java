package com.dynatrace.easytrade.creditcardorderservice;

import com.dynatrace.easytrade.creditcardorderservice.models.BitcoinPayment;
import com.dynatrace.easytrade.creditcardorderservice.models.BitcoinPaymentRequest;
import com.dynatrace.easytrade.creditcardorderservice.models.BitcoinPaymentStatusType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Handles bitcoin payments asynchronously.
 *
 * <p><b>Why async?</b> Production telemetry (Bluebox) shows the payment path is
 * currently 100% synchronous, and the external {@code third-party-service} is
 * already the highest-latency dependency (p95 ~352ms) at trivial volume. A
 * bitcoin confirmation is far slower and less reliable than that. Placing it on
 * the synchronous request path would make user-facing latency track the
 * blockchain — catastrophic at 10x traffic. So:
 *
 * <ul>
 *   <li>{@link #createPayment} records the payment as {@code PENDING} and returns
 *       immediately (HTTP 202). It never blocks on the processor.</li>
 *   <li>A background worker calls the processor with a <b>hard timeout</b> and a
 *       bounded confirmation window, so one slow/hung processor call can never
 *       exhaust request threads.</li>
 *   <li>Clients poll {@link #getPayment} for the terminal state.</li>
 * </ul>
 *
 * <p>The in-memory store here is adequate for the workshop demo; a production
 * deployment should persist payments to the existing SQL Server so state
 * survives restarts and scales horizontally (see PR notes).
 */
public class BitcoinPaymentService {

    private static final Logger logger = LoggerFactory.getLogger(BitcoinPaymentService.class);

    /** Hard cap on a single processor confirmation attempt. */
    private final long confirmTimeoutMs;

    private final BitcoinPaymentProcessor processor;
    private final ExecutorService executor;
    private final Map<String, BitcoinPayment> payments = new ConcurrentHashMap<>();

    public BitcoinPaymentService(BitcoinPaymentProcessor processor, ExecutorService executor, long confirmTimeoutMs) {
        this.processor = processor;
        this.executor = executor;
        this.confirmTimeoutMs = confirmTimeoutMs;
    }

    /**
     * Accept a payment and kick off asynchronous confirmation. Returns the
     * PENDING payment without waiting for the processor.
     */
    public BitcoinPayment createPayment(BitcoinPaymentRequest request) {
        OffsetDateTime now = OffsetDateTime.now();
        String paymentId = UUID.randomUUID().toString();
        BitcoinPayment payment = new BitcoinPayment(
                paymentId,
                request.accountId(),
                request.amount(),
                request.currency(),
                request.btcAddress(),
                BitcoinPaymentStatusType.PENDING,
                now,
                now);
        payments.put(paymentId, payment);
        logger.info("Created bitcoin payment [{}] for account [{}], status PENDING", paymentId, request.accountId());

        // Fire-and-forget: confirmation happens off the request thread.
        executor.submit(() -> confirmPayment(paymentId));
        return payment;
    }

    public Optional<BitcoinPayment> getPayment(String paymentId) {
        return Optional.ofNullable(payments.get(paymentId));
    }

    /**
     * Runs on the background executor. Calls the external processor with a hard
     * timeout; any timeout/interrupt/error leaves the payment in a defined
     * terminal state rather than hanging.
     */
    void confirmPayment(String paymentId) {
        BitcoinPayment payment = payments.get(paymentId);
        if (payment == null) {
            return;
        }

        BitcoinPaymentStatusType result;
        Future<BitcoinPaymentStatusType> future = executor.submit(() -> processor.confirm(payment));
        try {
            result = future.get(confirmTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            logger.warn("Bitcoin payment [{}] confirmation timed out after {}ms; marking EXPIRED",
                    paymentId, confirmTimeoutMs);
            result = BitcoinPaymentStatusType.EXPIRED;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Bitcoin payment [{}] confirmation interrupted; marking FAILED", paymentId);
            result = BitcoinPaymentStatusType.FAILED;
        } catch (ExecutionException e) {
            logger.error("Bitcoin payment [{}] confirmation errored; marking FAILED", paymentId, e.getCause());
            result = BitcoinPaymentStatusType.FAILED;
        }

        updateStatus(paymentId, result);
    }

    private void updateStatus(String paymentId, BitcoinPaymentStatusType status) {
        payments.computeIfPresent(paymentId,
                (id, existing) -> existing.withStatus(status, OffsetDateTime.now()));
        logger.info("Bitcoin payment [{}] transitioned to {}", paymentId, status);
    }
}
