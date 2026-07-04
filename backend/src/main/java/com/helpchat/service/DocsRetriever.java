package com.helpchat.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Very lightweight RAG: loads a markdown docs file per app, splits it into
 * sections by "## " headings, and scores sections by keyword overlap with
 * the user's question. Returns the top N sections as context.
 *
 * Docs are looked up in two places (first match wins):
 *   1. External folder ${helpchat.docs-dir} (default ./docs next to the jar) —
 *      lets clients add or edit docs files without rebuilding the service.
 *   2. The classpath (resources/docs/*.md bundled in the jar).
 *
 * PRODUCTION option: replace with OpenSearch hybrid (BM25 + vector)
 * retrieval, one index per appKey. This class is the only swap point.
 */
@Service
public class DocsRetriever {

    private final Map<String, List<String>> cache = new ConcurrentHashMap<>();

    @Value("${helpchat.docs-dir:./docs}")
    private String docsDir;

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
            String text = readDocsFile(docsFile);
            if (text == null) return List.of();
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

    /** External docs folder first (editable without rebuild), then classpath. */
    private String readDocsFile(String docsFile) throws Exception {
        // "docs/myapp.md" → <docs-dir>/myapp.md ; bare names work too
        String fileName = docsFile.startsWith("docs/") ? docsFile.substring(5) : docsFile;
        Path external = Path.of(docsDir, fileName);
        if (Files.isRegularFile(external)) {
            return Files.readString(external, StandardCharsets.UTF_8);
        }
        ClassPathResource cp = new ClassPathResource(docsFile);
        if (cp.exists()) {
            return new String(cp.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        }
        return null;
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
