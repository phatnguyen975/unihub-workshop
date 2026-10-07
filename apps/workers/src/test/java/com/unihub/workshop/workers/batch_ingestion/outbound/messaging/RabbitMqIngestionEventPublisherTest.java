package com.unihub.workshop.workers.batch_ingestion.outbound.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

class RabbitMqIngestionEventPublisherTest {

    @Test
    void shouldPublishFileRejectedEvent() throws Exception {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        RabbitMqIngestionEventPublisher publisher =
            new RabbitMqIngestionEventPublisher(rabbitTemplate);

        UUID orgId = UUID.randomUUID();
        String fileKey = "inbox/it/file.csv";

        publisher.publishFileRejectedEvent(orgId, fileKey);

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);

        verify(rabbitTemplate).send(
            eq("unihub.events"),
            eq("notifications"),
            captor.capture()
        );

        Message message = captor.getValue();
        assertThat(message.getMessageProperties().getContentType()).isEqualTo(
            "application/json"
        );
        assertThat(
            (String) message.getMessageProperties().getHeader("__TypeId__")
        ).isEqualTo("FileRejectedEvent");
        String body = new String(message.getBody());
        assertThat(body).contains(orgId.toString());
        assertThat(body).contains(fileKey);
    }

    @Test
    void shouldPublishFileCompletedEvent() throws Exception {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        RabbitMqIngestionEventPublisher publisher =
            new RabbitMqIngestionEventPublisher(rabbitTemplate);

        UUID orgId = UUID.randomUUID();
        String fileKey = "inbox/it/file.csv";

        publisher.publishFileCompletedEvent(orgId, fileKey);

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);

        verify(rabbitTemplate).send(
            eq("unihub.events"),
            eq("notifications"),
            captor.capture()
        );

        Message message = captor.getValue();
        assertThat(message.getMessageProperties().getContentType()).isEqualTo(
            "application/json"
        );
        assertThat(
            (String) message.getMessageProperties().getHeader("__TypeId__")
        ).isEqualTo("FileCompletedEvent");
        String body = new String(message.getBody());
        assertThat(body).contains(orgId.toString());
        assertThat(body).contains(fileKey);
    }
}
