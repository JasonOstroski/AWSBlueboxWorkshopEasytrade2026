package com.dynatrace.easytrade.creditcardorderservice;

import com.dynatrace.easytrade.creditcardorderservice.models.BitcoinPayment;
import com.dynatrace.easytrade.creditcardorderservice.models.BitcoinPaymentRequest;
import com.dynatrace.easytrade.creditcardorderservice.models.BitcoinPaymentStatusType;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BitcoinPaymentServiceTests {

    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    private final BitcoinPaymentRequest request = new BitcoinPaymentRequest(
            42, new BigDecimal("125.50"), "USD", "bc1qexampleaddress");

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void createPaymentReturnsPendingImmediately() {
        // A processor that blocks forever proves createPayment never waits on it.
        BitcoinPaymentProcessor blocking = payment -> {
            Thread.sleep(Long.MAX_VALUE);
            return BitcoinPaymentStatusType.CONFIRMED;
        };
        BitcoinPaymentService service = new BitcoinPaymentService(blocking, executor, 5000);

        long start = System.nanoTime();
        BitcoinPayment payment = service.createPayment(request);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertNotNull(payment.paymentId());
        assertEquals(BitcoinPaymentStatusType.PENDING, payment.status());
        assertTrue(elapsedMs < 1000, "createPayment must not block on the processor (took " + elapsedMs + "ms)");
    }

    @Test
    void confirmedPaymentTransitionsToConfirmed() throws InterruptedException {
        BitcoinPaymentProcessor fast = payment -> BitcoinPaymentStatusType.CONFIRMED;
        BitcoinPaymentService service = new BitcoinPaymentService(fast, executor, 5000);

        BitcoinPayment payment = service.createPayment(request);
        BitcoinPaymentStatusType finalStatus = awaitTerminal(service, payment.paymentId());
        assertEquals(BitcoinPaymentStatusType.CONFIRMED, finalStatus);
    }

    @Test
    void slowProcessorIsTimedOutAndMarkedExpired() throws InterruptedException {
        // Processor slower than the confirm timeout -> must be cancelled and EXPIRED.
        BitcoinPaymentProcessor slow = payment -> {
            Thread.sleep(2000);
            return BitcoinPaymentStatusType.CONFIRMED;
        };
        BitcoinPaymentService service = new BitcoinPaymentService(slow, executor, 100);

        BitcoinPayment payment = service.createPayment(request);
        BitcoinPaymentStatusType finalStatus = awaitTerminal(service, payment.paymentId());
        assertEquals(BitcoinPaymentStatusType.EXPIRED, finalStatus);
    }

    @Test
    void erroringProcessorIsMarkedFailed() throws InterruptedException {
        BitcoinPaymentProcessor erroring = payment -> {
            throw new RuntimeException("processor exploded");
        };
        BitcoinPaymentService service = new BitcoinPaymentService(erroring, executor, 5000);

        BitcoinPayment payment = service.createPayment(request);
        BitcoinPaymentStatusType finalStatus = awaitTerminal(service, payment.paymentId());
        assertEquals(BitcoinPaymentStatusType.FAILED, finalStatus);
    }

    private BitcoinPaymentStatusType awaitTerminal(BitcoinPaymentService service, String paymentId)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            Optional<BitcoinPayment> current = service.getPayment(paymentId);
            if (current.isPresent() && current.get().status().isTerminal()) {
                return current.get().status();
            }
            TimeUnit.MILLISECONDS.sleep(25);
        }
        throw new AssertionError("payment " + paymentId + " did not reach a terminal state in time");
    }
}
