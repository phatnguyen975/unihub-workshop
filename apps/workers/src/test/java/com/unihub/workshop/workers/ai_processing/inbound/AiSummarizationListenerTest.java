package com.unihub.workshop.workers.ai_processing.inbound;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.unihub.workshop.workers.ai_processing.core.models.BrochureSummarizationRequest;
import com.unihub.workshop.workers.ai_processing.core.ports.in.ProcessBrochureSummarizationPort;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

class AiSummarizationListenerTest {

    private ProcessBrochureSummarizationPort summarizationPort;
    private ObjectMapper objectMapper;
    private Channel channel;
    private AiSummarizationListener listener;

    private static final UUID JOB_ID = UUID.randomUUID();
    private static final UUID WORKSHOP_ID = UUID.randomUUID();
    private static final UUID BROCHURE_ID = UUID.randomUUID();
    private static final UUID ORGANIZER_ID = UUID.randomUUID();
    private static final String STORAGE_KEY = "workshops/w1/brochures/b1.pdf";

    @BeforeEach
    void setUp() {
        summarizationPort = mock(ProcessBrochureSummarizationPort.class);
        objectMapper = new ObjectMapper().findAndRegisterModules();
        channel = mock(Channel.class);
        listener = new AiSummarizationListener(summarizationPort, objectMapper);
    }

    private Message createMessage(byte[] body) {
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(42L);
        properties.setReceivedRoutingKey("ai.summarization");
        return new Message(body, properties);
    }

    @Test
    @DisplayName("Should successfully consume event, call port and ACK message")
    void shouldConsumeValidEventAndAck() throws Exception {
        AiSummarizationEventDto dto = new AiSummarizationEventDto(
            JOB_ID,
            WORKSHOP_ID,
            BROCHURE_ID,
            STORAGE_KEY,
            ORGANIZER_ID,
            Instant.now()
        );
        byte[] body = objectMapper.writeValueAsBytes(dto);
        Message message = createMessage(body);

        listener.onMessage(message, channel);

        verify(summarizationPort).process(
            new BrochureSummarizationRequest(
                JOB_ID,
                WORKSHOP_ID,
                BROCHURE_ID,
                STORAGE_KEY,
                ORGANIZER_ID
            )
        );
        verify(channel).basicAck(42L, false);
    }

    @Test
    @DisplayName(
        "Should NACK without requeue when message is missing required fields"
    )
    void shouldNackWhenMessageMissingFields() throws Exception {
        AiSummarizationEventDto invalidDto = new AiSummarizationEventDto(
            null,
            WORKSHOP_ID,
            BROCHURE_ID,
            STORAGE_KEY,
            ORGANIZER_ID,
            Instant.now()
        );
        byte[] body = objectMapper.writeValueAsBytes(invalidDto);
        Message message = createMessage(body);

        listener.onMessage(message, channel);

        verifyNoInteractions(summarizationPort);
        verify(channel).basicNack(42L, false, false);
    }

    @Test
    @DisplayName("Should NACK without requeue when port throws an exception")
    void shouldNackWhenPortThrowsException() throws Exception {
        AiSummarizationEventDto dto = new AiSummarizationEventDto(
            JOB_ID,
            WORKSHOP_ID,
            BROCHURE_ID,
            STORAGE_KEY,
            ORGANIZER_ID,
            Instant.now()
        );
        byte[] body = objectMapper.writeValueAsBytes(dto);
        Message message = createMessage(body);

        doThrow(new RuntimeException("MinIO connection failed"))
            .when(summarizationPort)
            .process(any(BrochureSummarizationRequest.class));

        listener.onMessage(message, channel);

        verify(channel).basicNack(42L, false, false);
    }
}
