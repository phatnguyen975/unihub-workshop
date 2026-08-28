package com.unihub.workshop.shared.config;

import io.github.bucket4j.distributed.proxy.ProxyManager;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@AutoConfiguration
public class RateLimitTestConfig {

    @Bean
    @Primary
    @SuppressWarnings("unchecked")
    public ProxyManager<byte[]> mockBucket4jProxyManager() {
        return Mockito.mock(ProxyManager.class);
    }
}
