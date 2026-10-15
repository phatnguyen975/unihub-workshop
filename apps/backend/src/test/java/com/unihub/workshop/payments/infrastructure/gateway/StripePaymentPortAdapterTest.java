package com.unihub.workshop.payments.infrastructure.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stripe.StripeClient;
import com.stripe.exception.ApiConnectionException;
import com.stripe.model.PaymentIntent;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.service.PaymentIntentService;
import com.stripe.service.V1Services;
import com.unihub.workshop.registrations.api.dto.CreatePaymentIntentCommand;
import com.unihub.workshop.registrations.api.dto.PaymentIntentResult;
import com.unihub.workshop.shared.exception.ServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class StripePaymentPortAdapterTest {

    private StripeClient stripeClient;
    private PaymentIntentService paymentIntentService;
    private CurrencyUnitConverter currencyUnitConverter;
    private CircuitBreaker circuitBreaker;
    private StripePaymentPortAdapter adapter;

    private final UUID registrationId = UUID.randomUUID();
    private final UUID workshopId = UUID.randomUUID();
    private final String idempotencyKey = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        stripeClient = mock(StripeClient.class);
        V1Services v1Services = mock(V1Services.class);
        paymentIntentService = mock(PaymentIntentService.class);

        when(stripeClient.v1()).thenReturn(v1Services);
        when(v1Services.paymentIntents()).thenReturn(paymentIntentService);

        currencyUnitConverter = new CurrencyUnitConverter();
        circuitBreaker = CircuitBreaker.ofDefaults("stripe-payment");
        adapter = new StripePaymentPortAdapter(
            stripeClient,
            currencyUnitConverter,
            circuitBreaker
        );
    }

    @Test
    @DisplayName(
        "Successfully creates PaymentIntent on Stripe and returns client secret"
    )
    void createPaymentIntent_Success_ReturnsPaymentIntentResult()
        throws Exception {
        PaymentIntent mockIntent = mock(PaymentIntent.class);
        when(mockIntent.getId()).thenReturn("pi_123456");
        when(mockIntent.getClientSecret()).thenReturn("pi_123456_secret_abc");
        when(mockIntent.getStatus()).thenReturn("requires_payment_method");

        when(
            paymentIntentService.create(
                any(PaymentIntentCreateParams.class),
                any(RequestOptions.class)
            )
        ).thenReturn(mockIntent);

        CreatePaymentIntentCommand command = new CreatePaymentIntentCommand(
            registrationId,
            workshopId,
            new BigDecimal("50.00"),
            "USD",
            idempotencyKey
        );

        PaymentIntentResult result = adapter.createPaymentIntent(command);

        assertThat(result).isNotNull();
        assertThat(result.paymentIntentId()).isEqualTo("pi_123456");
        assertThat(result.clientSecret()).isEqualTo("pi_123456_secret_abc");
        assertThat(result.status()).isEqualTo("requires_payment_method");

        ArgumentCaptor<PaymentIntentCreateParams> paramsCaptor =
            ArgumentCaptor.forClass(PaymentIntentCreateParams.class);
        ArgumentCaptor<RequestOptions> optionsCaptor = ArgumentCaptor.forClass(
            RequestOptions.class
        );

        verify(paymentIntentService).create(
            paramsCaptor.capture(),
            optionsCaptor.capture()
        );

        PaymentIntentCreateParams capturedParams = paramsCaptor.getValue();
        assertThat(capturedParams.getAmount()).isEqualTo(5000L);
        assertThat(capturedParams.getCurrency()).isEqualTo("usd");
        assertThat(capturedParams.getMetadata()).containsEntry(
            "registrationId",
            registrationId.toString()
        );
        assertThat(capturedParams.getMetadata()).containsEntry(
            "workshopId",
            workshopId.toString()
        );

        RequestOptions capturedOptions = optionsCaptor.getValue();
        assertThat(capturedOptions.getIdempotencyKey()).isEqualTo(
            idempotencyKey
        );
    }

    @Test
    @DisplayName("Translates StripeException into ServiceUnavailableException")
    void createPaymentIntent_StripeException_ThrowsServiceUnavailable()
        throws Exception {
        when(
            paymentIntentService.create(
                any(PaymentIntentCreateParams.class),
                any(RequestOptions.class)
            )
        ).thenThrow(new ApiConnectionException("Connection timeout to Stripe"));

        CreatePaymentIntentCommand command = new CreatePaymentIntentCommand(
            registrationId,
            workshopId,
            new BigDecimal("50.00"),
            "USD",
            idempotencyKey
        );

        assertThatThrownBy(() -> adapter.createPaymentIntent(command))
            .isInstanceOf(ServiceUnavailableException.class)
            .hasMessageContaining("Payment gateway service unavailable");
    }

    @Test
    @DisplayName(
        "When CircuitBreaker is OPEN, throws CallNotPermittedException without invoking Stripe"
    )
    void createPaymentIntent_CircuitBreakerOpen_ThrowsCallNotPermittedExceptionWithoutCallingStripe()
        throws Exception {
        circuitBreaker.transitionToOpenState();

        CreatePaymentIntentCommand command = new CreatePaymentIntentCommand(
            registrationId,
            workshopId,
            new BigDecimal("50.00"),
            "USD",
            idempotencyKey
        );

        assertThatThrownBy(() ->
            adapter.createPaymentIntent(command)
        ).isInstanceOf(CallNotPermittedException.class);

        verify(paymentIntentService, never()).create(
            any(PaymentIntentCreateParams.class),
            any(RequestOptions.class)
        );
    }
}
