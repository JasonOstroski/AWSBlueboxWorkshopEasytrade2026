package com.dynatrace.easytrade.creditcardorderservice;

import com.dynatrace.easytrade.creditcardorderservice.models.*;

import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.OpenFeatureAPI;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
public class OrderControllerTests {
    @Mock
    DatabaseHelper helper;
    @Mock
    OpenFeatureAPI openFeatureAPI;
    @Mock
    BitcoinPaymentService bitcoinPaymentService;

    private final String GUID = "a67a075f-f1b3-4fd4-bfbd-e4986cd11956";
    private final StatusRequest STATUS_REQUEST = new StatusRequest(GUID, StatusType.CARD_ORDERED.getType(),
            OffsetDateTime.now(), null);

    @Test
    @SneakyThrows
    void getConnectionThrowsExceptionTest() {
        Mockito.when(helper.getConnection()).thenThrow(new SQLException("XXX"));

        OrderController controller = new OrderController(helper, openFeatureAPI, bitcoinPaymentService);
        ResponseEntity<StandardResponse> response = controller.createCreditCardOrder(null);
        var body = response.getBody();
        assertNotNull(body, "Expected response body to not be null");
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), body.statusCode());
    }

    @Test
    @SneakyThrows
    void createAnotherOrderForSameAccountTest() {
        CreditCardOrderRequest request = new CreditCardOrderRequest(
                13, "email@mail.ail", "Jane Doe", "Box 13", "silver");

        Mockito.when(helper.getConnection()).thenReturn(null);
        Mockito.when(helper.getOrderCountForAccountId(eq(null), anyInt())).thenReturn(1);

        checkOrderCreationResult(HttpStatus.BAD_REQUEST.value(), OrderController.ORDER_ALREADY_EXISTS, request);
    }

    @Test
    @SneakyThrows
    void createCreditCardOrderTest() {
        CreditCardOrderRequest request = new CreditCardOrderRequest(
                13, "email@mail.ail", "Jane Doe", "Box 13", "silver");
        String guid = "b78a075f-e1b3-4fd4-afbd-f4986cd11945";

        Mockito.when(helper.getConnection()).thenReturn(null);
        Mockito.when(helper.getOrderCountForAccountId(eq(null), anyInt())).thenReturn(0);
        Mockito.when(helper.insertNewOrder(null, request)).thenReturn(guid);
        Mockito.doNothing().when(helper).insertNewStatus(null, guid, StatusType.ORDER_CREATED);

        checkOrderCreationResult(HttpStatus.CREATED.value(), OrderController.ORDER_CREATED, request);
    }

    @Test
    @SneakyThrows
    public void updateStatusWithMismatchingIdsTest() {
        String anotherGuid = "b78a075f-e1b3-4fd4-afbd-f4986cd11945";

        Mockito.when(helper.getConnection()).thenReturn(null);

        checkStatusUpdateResult(HttpStatus.BAD_REQUEST.value(), OrderController.ORDER_IDS_DO_NOT_MATCH, STATUS_REQUEST,
                anotherGuid);
    }

    @Test
    @SneakyThrows
    public void updateStatusWithNonExistentOrderIdTest() {
        Mockito.when(helper.getConnection()).thenReturn(null);
        Mockito.when(helper.getLastOrderStatus(null, GUID)).thenReturn(null);
        checkStatusUpdateResult(HttpStatus.BAD_REQUEST.value(), String.format(OrderController.NO_STATUS_FOR_ID, GUID),
                STATUS_REQUEST, GUID);
    }

    @Test
    @SneakyThrows
    public void updateStatusNormallyTest() {
        CreditCardOrderStatus status = new CreditCardOrderStatus(123, GUID, OffsetDateTime.now(),
                StatusType.ORDER_CREATED.getType(), "");

        Mockito.when(helper.getConnection()).thenReturn(null);
        Mockito.when(helper.getLastOrderStatus(null, GUID)).thenReturn(status);
        checkStatusUpdateResult(HttpStatus.OK.value(), OrderController.STATUS_UPDATED, STATUS_REQUEST, GUID);
    }

    @Test
    @SneakyThrows
    public void updateStatusInWrongSequenceTest() {
        CreditCardOrderStatus status = new CreditCardOrderStatus(123, GUID, OffsetDateTime.now(),
                StatusType.CARD_SHIPPED.getType(), "");

        // Test with wrong status change sequence
        Mockito.when(helper.getConnection()).thenReturn(null);
        Mockito.when(helper.getLastOrderStatus(null, GUID)).thenReturn(status);
        checkStatusUpdateResult(HttpStatus.BAD_REQUEST.value(),
                String.format(OrderController.WRONG_SEQUENCE, StatusType.SEQUENCE_ERROR.getDescription(),
                        status.status(), STATUS_REQUEST.type()),
                STATUS_REQUEST, GUID);
    }

    @Test
    @SneakyThrows
    public void createBitcoinPaymentReturnsAcceptedWhenFlagEnabledTest() {
        Client client = Mockito.mock(Client.class);
        Mockito.when(openFeatureAPI.getClient()).thenReturn(client);
        Mockito.when(client.getBooleanValue(OrderController.BITCOIN_PAYMENTS_FLAG, false)).thenReturn(true);

        BitcoinPaymentRequest request = new BitcoinPaymentRequest(
                13, new BigDecimal("100.00"), "USD", "bc1qexampleaddress");
        String paymentId = "e2f1c0d4-1111-2222-3333-444455556666";
        BitcoinPayment pending = new BitcoinPayment(paymentId, 13, request.amount(), "USD",
                request.btcAddress(), BitcoinPaymentStatusType.PENDING, OffsetDateTime.now(), OffsetDateTime.now());
        Mockito.when(bitcoinPaymentService.createPayment(request)).thenReturn(pending);

        OrderController controller = new OrderController(helper, openFeatureAPI, bitcoinPaymentService);
        ResponseEntity<StandardResponse> response = controller.createBitcoinPayment(request);
        var body = response.getBody();
        assertNotNull(body, "Expected response body to not be null");
        assertEquals(HttpStatus.ACCEPTED.value(), body.statusCode());
        assertEquals(OrderController.BITCOIN_PAYMENT_ACCEPTED, body.message());
        assertNotNull(body.results(), "Expected a payment response payload");
    }

    @Test
    @SneakyThrows
    public void createBitcoinPaymentBlockedWhenFlagDisabledTest() {
        Client client = Mockito.mock(Client.class);
        Mockito.when(openFeatureAPI.getClient()).thenReturn(client);
        Mockito.when(client.getBooleanValue(OrderController.BITCOIN_PAYMENTS_FLAG, false)).thenReturn(false);

        BitcoinPaymentRequest request = new BitcoinPaymentRequest(
                13, new BigDecimal("50.00"), "USD", "bc1qexampleaddress");

        OrderController controller = new OrderController(helper, openFeatureAPI, bitcoinPaymentService);
        ResponseEntity<StandardResponse> response = controller.createBitcoinPayment(request);
        var body = response.getBody();
        assertNotNull(body, "Expected response body to not be null");
        assertEquals(HttpStatus.NOT_FOUND.value(), body.statusCode());
        assertEquals(OrderController.BITCOIN_PAYMENTS_DISABLED, body.message());
        // The service must never be touched when the feature is off.
        Mockito.verify(bitcoinPaymentService, Mockito.never()).createPayment(any());
    }

    @Test
    @SneakyThrows
    public void getBitcoinPaymentStatusNotFoundTest() {
        Client client = Mockito.mock(Client.class);
        Mockito.when(openFeatureAPI.getClient()).thenReturn(client);
        Mockito.when(client.getBooleanValue(OrderController.BITCOIN_PAYMENTS_FLAG, false)).thenReturn(true);
        Mockito.when(bitcoinPaymentService.getPayment("missing")).thenReturn(Optional.empty());

        OrderController controller = new OrderController(helper, openFeatureAPI, bitcoinPaymentService);
        ResponseEntity<StandardResponse> response = controller.getBitcoinPaymentStatus("missing");
        var body = response.getBody();
        assertNotNull(body, "Expected response body to not be null");
        assertEquals(HttpStatus.NOT_FOUND.value(), body.statusCode());
        assertEquals(OrderController.BITCOIN_PAYMENT_NOT_FOUND, body.message());
    }

    @Test
    @SneakyThrows
    public void getLatestStatusDoesNotThrowWhenMeltdownFlagOnTest() {
        // Regression guard for the historical divide-by-zero: with the meltdown
        // flag ON, getLatestStatus must complete normally (no ArithmeticException).
        Client client = Mockito.mock(Client.class);
        Mockito.when(openFeatureAPI.getClient()).thenReturn(client);
        Mockito.when(client.getBooleanValue("credit_card_meltdown", false)).thenReturn(true);
        Mockito.when(helper.getConnection()).thenReturn(null);
        Mockito.when(helper.getLastOrderStatusForAccountId(eq(null), anyInt())).thenReturn(Optional.empty());

        OrderController controller = new OrderController(helper, openFeatureAPI, bitcoinPaymentService);
        ResponseEntity<StandardResponse> response = controller.getLatestStatus(17);
        var body = response.getBody();
        assertNotNull(body, "Expected response body to not be null");
        assertEquals(HttpStatus.NOT_FOUND.value(), body.statusCode());
    }

    private void checkOrderCreationResult(int statusCode, String message, CreditCardOrderRequest request) {
        OrderController controller = new OrderController(helper, openFeatureAPI, bitcoinPaymentService);
        ResponseEntity<StandardResponse> response = controller.createCreditCardOrder(request);
        var body = response.getBody();
        assertNotNull(body, "Expected response body to not be null");
        assertEquals(statusCode, body.statusCode());
        assertEquals(message, body.message());
    }

    private void checkStatusUpdateResult(int statusCode, String message, StatusRequest request, String id) {
        OrderController controller = new OrderController(helper, openFeatureAPI, bitcoinPaymentService);
        ResponseEntity<StandardResponse> response = controller.updateStatus(id, request);
        var body = response.getBody();
        assertNotNull(body, "Expected response body to not be null");
        assertEquals(statusCode, body.statusCode());
        assertEquals(message, body.message());
    }
}
