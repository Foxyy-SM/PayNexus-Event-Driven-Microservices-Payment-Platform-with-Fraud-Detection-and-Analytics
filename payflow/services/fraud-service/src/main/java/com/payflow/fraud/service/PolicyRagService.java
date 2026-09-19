package com.payflow.fraud.service;

import com.payflow.fraud.dto.PolicyAskResponse;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class PolicyRagService {
    private final List<PolicyDoc> docs;

    public PolicyRagService() {
        this.docs = load();
    }

    public PolicyAskResponse ask(String question) {
        if (question == null || question.isBlank()) {
            return new PolicyAskResponse("Ask a fraud-policy question.", List.of());
        }
        List<Scored> ranked = docs.stream()
                .map(doc -> new Scored(doc, overlap(question, doc.text())))
                .sorted(Comparator.comparingDouble(Scored::score).reversed())
                .limit(2)
                .filter(s -> s.score() > 0)
                .toList();
        if (ranked.isEmpty()) {
            return new PolicyAskResponse("No matching internal fraud policy was found.", List.of());
        }
        StringBuilder answer = new StringBuilder("Retrieved from internal fraud policy documents:\n\n");
        ranked.forEach(s -> answer.append("### ").append(s.doc().name()).append("\n")
                .append(s.doc().text()).append("\n\n"));
        return new PolicyAskResponse(answer.toString().trim(),
                ranked.stream().map(s -> s.doc().name()).toList());
    }

    private double overlap(String question, String text) {
        List<String> q = tokens(question);
        List<String> t = tokens(text);
        if (q.isEmpty()) {
            return 0;
        }
        long hits = q.stream().filter(t::contains).count();
        return (double) hits / q.size();
    }

    private List<String> tokens(String value) {
        return Arrays.stream(value.toLowerCase(Locale.ROOT).split("[^a-z0-9]+"))
                .filter(s -> s.length() > 2)
                .toList();
    }

    private List<PolicyDoc> load() {
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver()
                    .getResources("classpath:policies/*.md");
            List<PolicyDoc> loaded = new ArrayList<>();
            for (Resource resource : resources) {
                String name = resource.getFilename() == null ? "policy" : resource.getFilename();
                String text = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                loaded.add(new PolicyDoc(name, text));
            }
            return loaded;
        } catch (Exception ex) {
            return List.of();
        }
    }

    private record PolicyDoc(String name, String text) {
    }

    private record Scored(PolicyDoc doc, double score) {
    }
}
