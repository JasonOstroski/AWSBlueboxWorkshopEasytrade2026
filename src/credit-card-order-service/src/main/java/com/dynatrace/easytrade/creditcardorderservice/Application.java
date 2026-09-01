package com.dynatrace.easytrade.creditcardorderservice;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.filter.ForwardedHeaderFilter;

import jakarta.servlet.DispatcherType;

@SpringBootApplication
@EnableScheduling
public class Application {
    private static final Logger logger = LoggerFactory.getLogger(Application.class);

    public static void main(String[] args) {
        logger.info("Credit Card Order Service started.");
        SpringApplication.run(Application.class, args);
    }

    @Bean
    @ConditionalOnMissingBean(ForwardedHeaderFilter.class)
    @ConditionalOnProperty(value = "server.forward-headers-strategy", havingValue = "framework")
    public FilterRegistrationBean<ForwardedHeaderFilter> forwardedHeaderFilter() {
        ForwardedHeaderFilter filter = new ForwardedHeaderFilter();
        FilterRegistrationBean<ForwardedHeaderFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setDispatcherTypes(DispatcherType.REQUEST, DispatcherType.ASYNC, DispatcherType.ERROR);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    @Bean
    public DatabaseHelper databaseHelper() { return new DatabaseHelper(); }

    @Bean
    ScheduledExecutorService scheduler() { return Executors.newScheduledThreadPool(1); }

    /**
     * Dedicated, bounded pool for asynchronous bitcoin confirmation work. Kept
     * separate from request-handling threads so a slow/hung external processor
     * call can never starve the synchronous order-status path.
     */
    @Bean(destroyMethod = "shutdown")
    public ExecutorService bitcoinPaymentExecutor() {
        int poolSize = envInt("BITCOIN_CONFIRM_POOL_SIZE", 8);
        return Executors.newFixedThreadPool(poolSize);
    }

    @Bean
    public BitcoinPaymentProcessor bitcoinPaymentProcessor() {
        return new SimulatedBitcoinPaymentProcessor();
    }

    @Bean
    public BitcoinPaymentService bitcoinPaymentService(BitcoinPaymentProcessor processor,
            ExecutorService bitcoinPaymentExecutor) {
        long confirmTimeoutMs = envInt("BITCOIN_CONFIRM_TIMEOUT_MS", 5000);
        return new BitcoinPaymentService(processor, bitcoinPaymentExecutor, confirmTimeoutMs);
    }

    private static int envInt(String name, int defaultValue) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            logger.warn("Invalid integer for env {}='{}', using default {}", name, value, defaultValue);
            return defaultValue;
        }
    }
}

