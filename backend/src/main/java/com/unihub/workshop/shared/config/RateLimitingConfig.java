package com.unihub.workshop.shared.config;

import io.github.bucket4j.redis.lettuce.Bucket4jLettuce;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import io.lettuce.core.api.StatefulRedisConnection;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
public class RateLimitingConfig {

    @Bean(destroyMethod = "shutdown")
    public RedisClient rateLimitingRedisClient(
            @Value("${spring.data.redis.url:}") String redisUrl,
            @Value("${spring.data.redis.host:localhost}") String redisHost,
            @Value("${spring.data.redis.port:6379}") int redisPort,
            @Value("${spring.data.redis.database:0}") int redisDatabase,
            @Value("${spring.data.redis.password:}") String redisPassword) {
        RedisURI redisUri = resolveRedisUri(redisUrl, redisHost, redisPort, redisDatabase, redisPassword);
        return RedisClient.create(redisUri);
    }

    @Bean(destroyMethod = "close")
    public StatefulRedisConnection<String, byte[]> rateLimitingRedisConnection(RedisClient rateLimitingRedisClient) {
        RedisCodec<String, byte[]> codec = RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE);
        return rateLimitingRedisClient.connect(codec);
    }

    @Bean
    public io.github.bucket4j.distributed.proxy.ProxyManager<String> rateLimitingProxyManager(
            StatefulRedisConnection<String, byte[]> rateLimitingRedisConnection) {
        return Bucket4jLettuce.casBasedBuilder(rateLimitingRedisConnection).build();
    }

    private RedisURI resolveRedisUri(String redisUrl, String redisHost, int redisPort, int redisDatabase,
            String redisPassword) {
        if (StringUtils.hasText(redisUrl)) {
            return RedisURI.create(redisUrl);
        }

        RedisURI.Builder builder = RedisURI.builder()
                .withHost(redisHost)
                .withPort(redisPort)
                .withDatabase(redisDatabase);

        if (StringUtils.hasText(redisPassword)) {
            builder.withPassword(redisPassword.toCharArray());
        }

        return builder.build();
    }
}
