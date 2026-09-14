package com.unihub.workshop.shared.config;

import io.github.bucket4j.distributed.proxy.ProxyManager;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@AutoConfiguration
public class RateLimitTestConfig {

    private interface ByteArrayProxyManager extends ProxyManager<byte[]> {}

    @Bean
    @Primary
    public ProxyManager<byte[]> mockBucket4jProxyManager() {
        return Mockito.mock(ByteArrayProxyManager.class);
    }
}
