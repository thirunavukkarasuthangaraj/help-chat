package com.helpchat.service;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Very lightweight RAG: loads a markdown docs file per app, splits it into
 * sections by "## " headings, and scores sections by keyword overlap with
 * the user's question. Returns the top N sections as context.
 *
 * PRODUCTION: replace with OpenSearch hybrid (BM25 + vector) retrieval,
 * one index per appKey. This class is the only thing you need to swap.
 */
@Service
public class DocsRetriever {

    private final Map<String, List<String>> cache = new ConcurrentHashMap<>();

    public String retrieve(String docsFile, String question, int topN) {
        List<String> chunks = cache.computeIfAbsent(docsFile, this::loadChunks);
        if (chunks.isEmpty()) return "";

        Set<String> qTokens = tokenize(question);
        return chunks.stream()
                .map(c -> Map.entry(c, score(qTokens, c)))
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(topN)
                .filter(e -> e.getValue() > 0)
                .map(Map.Entry::getKey)
                .reduce("", (a, b) -> a + "\n\n---\n\n" + b);
    }

    private List<String> loadChunks(String docsFile) {
        try {
            String text = new String(
                    new ClassPathResource(docsFile).getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8);
            List<String> chunks = new ArrayList<>();
            for (String section : text.split("(?m)^## ")) {
                String s = section.strip();
                if (!s.isEmpty()) chunks.add("## " + s);
            }
            return chunks;
        } catch (Exception e) {
            return List.of();
        }
    }

    private Set<String> tokenize(String text) {
        Set<String> tokens = new HashSet<>();
        for (String t : text.toLowerCase().split("[^a-z0-9]+")) {
            if (t.length() > 2) tokens.add(t);
        }
        return tokens;
    }

    private double score(Set<String> qTokens, String chunk) {
        Set<String> cTokens = tokenize(chunk);
        long overlap = qTokens.stream().filter(cTokens::contains).count();
        return (double) overlap;
    }
}
