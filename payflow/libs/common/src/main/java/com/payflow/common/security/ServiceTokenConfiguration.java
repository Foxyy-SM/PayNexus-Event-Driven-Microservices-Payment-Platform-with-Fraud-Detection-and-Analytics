package com.payflow.common.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(ServiceTokenProperties.class)
public class ServiceTokenConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "payflow.security.client", name = "token-uri")
    public ServiceTokenProvider serviceTokenProvider(
            RestClient.Builder builder,
            ServiceTokenProperties properties) {
        return new ServiceTokenProvider(builder, properties);
    }
}
