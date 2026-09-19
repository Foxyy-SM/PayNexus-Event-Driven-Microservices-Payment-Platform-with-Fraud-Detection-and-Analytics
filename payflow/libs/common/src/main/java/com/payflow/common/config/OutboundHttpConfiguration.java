package com.payflow.common.config;

import com.payflow.common.correlation.CorrelationIdFilter;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.time.Duration;

@Configuration
public class OutboundHttpConfiguration {
    public static final String PAYMENT_ID_HEADER = "X-Payment-Id";
    public static final String USER_ID_HEADER = "X-User-Id";

    @Bean
    RestClientCustomizer payflowRestClientCustomizer(
            @Value("${payflow.http.connect-timeout:PT1S}") Duration connectTimeout,
            @Value("${payflow.http.read-timeout:PT2S}") Duration readTimeout) {
        return builder -> {
            SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
            requestFactory.setConnectTimeout(connectTimeout);
            requestFactory.setReadTimeout(readTimeout);
            builder.requestFactory(requestFactory);
            builder.requestInterceptor((request, body, execution) -> {
                copyMdc(request, CorrelationIdFilter.ATTRIBUTE, CorrelationIdFilter.HEADER);
                copyMdc(request, "paymentId", PAYMENT_ID_HEADER);
                copyMdc(request, "userId", USER_ID_HEADER);
                return execution.execute(request, body);
            });
        };
    }

    private static void copyMdc(org.springframework.http.HttpRequest request, String mdcKey, String header) {
        String value = MDC.get(mdcKey);
        if (value != null && !value.isBlank() && !request.getHeaders().containsKey(header)) {
            request.getHeaders().set(header, value);
        }
    }
}
