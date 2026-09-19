package com.payflow.fraud.service;

import com.payflow.fraud.dto.ScoreRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
public class RiskExplanationService {
    private final String openAiKey;
    private final RestClient restClient;

    public RiskExplanationService(@Value("${payflow.ai.openai-key:}") String openAiKey,
                                  RestClient.Builder builder) {
        this.openAiKey = openAiKey;
        this.restClient = builder.baseUrl("https://api.openai.com").build();
    }

    public String explain(double score, String decision, List<String> rules, ScoreRequest request) {
        String fallback = template(score, decision, rules, request);
        if (openAiKey == null || openAiKey.isBlank()) {
            return fallback;
        }
        try {
            Map<?, ?> body = restClient.post()
                    .uri("/v1/chat/completions")
                    .headers(h -> {
                        h.setBearerAuth(openAiKey);
                        h.setContentType(MediaType.APPLICATION_JSON);
                    })
                    .body(Map.of(
                            "model", "gpt-4o-mini",
                            "messages", List.of(
                                    Map.of("role", "system", "content",
                                            "You explain payment fraud risk scores in one or two sentences for analysts."),
                                    Map.of("role", "user", "content", fallback)
                            )
                    ))
                    .retrieve()
                    .body(Map.class);
            if (body == null) {
                return fallback;
            }
            Object choices = body.get("choices");
            if (choices instanceof List<?> list && !list.isEmpty() && list.getFirst() instanceof Map<?, ?> choice) {
                Object message = choice.get("message");
                if (message instanceof Map<?, ?> msg && msg.get("content") instanceof String content) {
                    return content;
                }
            }
        } catch (Exception ignored) {
            return fallback;
        }
        return fallback;
    }

    private String template(double score, String decision, List<String> rules, ScoreRequest request) {
        String ruleText = rules.isEmpty() ? "no elevated rule hits" : String.join(", ", rules);
        return "Risk score " + String.format("%.2f", score) + " (" + decision + ") for amount "
                + request.amountMinor() + " minor units"
                + " because of " + ruleText
                + ". Unusually large amount, rapid transaction frequency and/or geographic anomaly may apply.";
    }
}
