package com.unihub.workshop.payments.infrastructure.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.stripe.model.Event;
import com.stripe.net.Webhook;
import com.unihub.workshop.shared.exception.BadRequestException;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StripeWebhookVerifierTest {

    private static final String SECRET = "whsec_test_secret_key_12345";
    private StripeWebhookVerifier verifier;

    private static final String SAMPLE_PAYLOAD = """
    {
      "id": "evt_test_123",
      "object": "event",
      "type": "customer.subscription.created",
      "data": {
        "object": {
          "id": "sub_123"
        }
      }
    }
    """;

    @BeforeEach
    void setUp() {
        verifier = new StripeWebhookVerifier(SECRET);
    }

    private String generateSignatureHeader(
        String payload,
        String secret,
        long timestamp
    ) throws Exception {
        String payloadToSign = timestamp + "." + payload;
        String signature = Webhook.Util.computeHmacSha256(
            secret,
            payloadToSign
        );
        return "t=" + timestamp + ",v1=" + signature;
    }

    @Test
    @DisplayName(
        "FR-18, BR-05: Valid HMAC-SHA256 signature returns parsed Stripe Event"
    )
    void verifyAndConstructEvent_ValidSignature_ReturnsEvent()
        throws Exception {
        long now = Instant.now().getEpochSecond();
        String signatureHeader = generateSignatureHeader(
            SAMPLE_PAYLOAD,
            SECRET,
            now
        );

        Event event = verifier.verifyAndConstructEvent(
            SAMPLE_PAYLOAD,
            signatureHeader
        );

        assertThat(event).isNotNull();
        assertThat(event.getId()).isEqualTo("evt_test_123");
        assertThat(event.getType()).isEqualTo("customer.subscription.created");
    }

    @Test
    @DisplayName(
        "FR-18: Missing Stripe-Signature header throws BadRequestException (400)"
    )
    void verifyAndConstructEvent_MissingSignature_ThrowsBadRequestException() {
        assertThatThrownBy(() ->
            verifier.verifyAndConstructEvent(SAMPLE_PAYLOAD, null)
        )
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("Missing Stripe-Signature header");

        assertThatThrownBy(() ->
            verifier.verifyAndConstructEvent(SAMPLE_PAYLOAD, "   ")
        )
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("Missing Stripe-Signature header");
    }

    @Test
    @DisplayName(
        "FR-18: Malformed Stripe-Signature header throws BadRequestException (400)"
    )
    void verifyAndConstructEvent_MalformedSignature_ThrowsBadRequestException() {
        assertThatThrownBy(() ->
            verifier.verifyAndConstructEvent(
                SAMPLE_PAYLOAD,
                "malformed_signature_no_timestamp"
            )
        )
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("Invalid webhook signature");
    }

    @Test
    @DisplayName(
        "FR-18: Signature generated with wrong secret throws BadRequestException (400)"
    )
    void verifyAndConstructEvent_WrongSecret_ThrowsBadRequestException()
        throws Exception {
        long now = Instant.now().getEpochSecond();
        String wrongSecret = "whsec_wrong_secret_99999";
        String signatureHeader = generateSignatureHeader(
            SAMPLE_PAYLOAD,
            wrongSecret,
            now
        );

        assertThatThrownBy(() ->
            verifier.verifyAndConstructEvent(SAMPLE_PAYLOAD, signatureHeader)
        )
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("Invalid webhook signature");
    }

    @Test
    @DisplayName(
        "FR-18: Expired timestamp (> 5 minutes old) throws BadRequestException (400)"
    )
    void verifyAndConstructEvent_ExpiredTimestamp_ThrowsBadRequestException()
        throws Exception {
        long expiredTime = Instant.now().minusSeconds(600).getEpochSecond(); // 10 minutes ago
        String signatureHeader = generateSignatureHeader(
            SAMPLE_PAYLOAD,
            SECRET,
            expiredTime
        );

        assertThatThrownBy(() ->
            verifier.verifyAndConstructEvent(SAMPLE_PAYLOAD, signatureHeader)
        )
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("Invalid webhook signature");
    }

    @Test
    @DisplayName("Null payload throws BadRequestException (400)")
    void verifyAndConstructEvent_NullPayload_ThrowsBadRequestException() {
        assertThatThrownBy(() ->
            verifier.verifyAndConstructEvent(null, "t=123,v1=abc")
        )
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("Missing webhook payload");
    }
}
