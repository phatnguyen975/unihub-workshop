package com.unihub.workshop.shared.realtime.config;

import com.unihub.workshop.shared.realtime.infrastructure.RedisRealtimePublisherAdapter;
import com.unihub.workshop.shared.realtime.infrastructure.RedisRealtimeSubscriber;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * Spring configuration binding Redis message listener container to realtime streaming channels.
 */
@Configuration
public class RealtimeRedisConfig {

    @Bean
    @ConditionalOnBean(RedisConnectionFactory.class)
    public RedisMessageListenerContainer realtimeRedisMessageListenerContainer(
        RedisConnectionFactory connectionFactory,
        RedisRealtimeSubscriber subscriber
    ) {
        RedisMessageListenerContainer container =
            new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(
            subscriber,
            new PatternTopic(
                RedisRealtimePublisherAdapter.USER_CHANNEL_PREFIX + "*"
            )
        );
        container.addMessageListener(
            subscriber,
            new PatternTopic(
                RedisRealtimePublisherAdapter.WORKSHOP_CHANNEL_PREFIX + "*"
            )
        );
        return container;
    }
}
