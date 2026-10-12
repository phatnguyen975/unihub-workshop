package com.unihub.workshop.workers.ai_processing.inbound;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.unihub.workshop.workers.ai_processing.core.exceptions.AiSummarizationRetryExhaustedException;
import com.unihub.workshop.workers.ai_processing.core.exceptions.AiSummarizationTransientException;
import com.unihub.workshop.workers.ai_processing.core.models.BrochureSummarizationRequest;
import com.unihub.workshop.workers.ai_processing.core.ports.in.ProcessBrochureSummarizationPort;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiSummarizationListener {

    private final ProcessBrochureSummarizationPort summarizationPort;
    private final ObjectMapper objectMapper;

    @RabbitListener(
        queues = "${app.ai.queue:q.ai.summarization}",
        ackMode = "MANUAL"
    )
    public void onMessage(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        String routingKey = message
            .getMessageProperties()
            .getReceivedRoutingKey();

        try {
            AiSummarizationEventDto dto = objectMapper.readValue(
                message.getBody(),
                AiSummarizationEventDto.class
            );

            if (
                dto.jobId() == null ||
                dto.workshopId() == null ||
                dto.storageKey() == null
            ) {
                log.error(
                    "Rejecting invalid AiSummarizationEventDto: missing required fields on routingKey '{}'",
                    routingKey
                );
                channel.basicNack(deliveryTag, false, false);
                return;
            }

            BrochureSummarizationRequest request =
                new BrochureSummarizationRequest(
                    dto.jobId(),
                    dto.workshopId(),
                    dto.brochureId(),
                    dto.storageKey(),
                    dto.organizerId()
                );

            summarizationPort.process(request);
            channel.basicAck(deliveryTag, false);
        } catch (AiSummarizationTransientException e) {
            log.warn(
                "Transient error processing brochure summarization on routingKey '{}': {}. Requeuing message for retry.",
                routingKey,
                e.getMessage()
            );
            channel.basicNack(deliveryTag, false, true);
        } catch (AiSummarizationRetryExhaustedException e) {
            log.error(
                "Max retries exhausted for brochure summarization on routingKey '{}': {}. Rejecting message without requeue (routing to DLQ).",
                routingKey,
                e.getMessage()
            );
            channel.basicNack(deliveryTag, false, false);
        } catch (Exception e) {
            log.error(
                "Non-recoverable or fatal error processing brochure summarization on routingKey '{}': {}. Rejecting message without requeue.",
                routingKey,
                e.getMessage(),
                e
            );
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
