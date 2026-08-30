package com.unihub.workshop.workers.batch_ingestion.outbound.messaging;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RabbitMqIngestionEventPublisherTest {

    @Test
    void shouldPublishFileRejectedEvent() {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        RabbitMqIngestionEventPublisher publisher = new RabbitMqIngestionEventPublisher(rabbitTemplate);
        
        UUID orgId = UUID.randomUUID();
        String fileKey = "inbox/it/file.csv";

        publisher.publishFileRejectedEvent(orgId, fileKey);

        ArgumentCaptor<RabbitMqIngestionEventPublisher.FileRejectedEvent> captor = 
            ArgumentCaptor.forClass(RabbitMqIngestionEventPublisher.FileRejectedEvent.class);

        verify(rabbitTemplate).convertAndSend(eq("unihub.events"), eq("notifications"), captor.capture());

        var event = captor.getValue();
        assertThat(event.organizationId()).isEqualTo(orgId);
        assertThat(event.sourceFileKey()).isEqualTo(fileKey);
    }
}
