package org.example.aiagent.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * MedlinePlus 检索：英文关键词改写、BM25 与向量混合召回、gte-rerank 重排。
 * 向量存在本地 JSON，用内存余弦相似度，不使用 pgvector。
 */
@Component
@Slf4j
public class MedicalKnowledgeStore {

    private static final int CHUNK_CHARS = 900;

    private static final int RECALL_K = 20;

    private static final int RRF_K = 60;

    private final DashScopeEmbeddingModel embeddingModel;

    private final DeepSeekChatModel deepSeekChatModel;

    private final ResourcePatternResolver resourcePatternResolver;

    private final File indexFile;

    private final boolean rebuild;

    private final int topK;

    private final double similarityThreshold;

    private final Object gate = new Object();

    private volatile VectorStore vectorStore;

    private volatile Bm25Index bm25;

    private volatile boolean ready;

    public MedicalKnowledgeStore(
            DashScopeEmbeddingModel embeddingModel,
            DeepSeekChatModel deepSeekChatModel,
            ResourcePatternResolver resourcePatternResolver,
            @Value("${app.medical-rag.index-path:data/medical-vector-store-v4.json}") String indexPath,
            @Value("${app.medical-rag.rebuild:false}") boolean rebuild,
            @Value("${app.medical-rag.top-k:4}") int topK,
            @Value("${app.medical-rag.similarity-threshold:0.2}") double similarityThreshold) {
        this.embeddingModel = embeddingModel;
        this.deepSeekChatModel = deepSeekChatModel;
        this.resourcePatternResolver = resourcePatternResolver;
        this.indexFile = new File(indexPath);
        this.rebuild = rebuild;
        this.topK = topK;
        this.similarityThreshold = similarityThreshold;
    }

    /**
     * 返回可放进系统提示词的资料文本。索引还没建好或没有命中时返回空字符串。
     */
    public String retrieve(String question) {
        if (question == null || question.isBlank()) {
            return "";
        }
        ensureReady();
        if (!ready) {
            return "";
        }
        SearchText searchText = toEnglishQuery(question);
        log.info("向量问句: {} | BM25关键词: {}", searchText.sentence(), searchText.keywords());

        List<Document> vectorHits = vectorStore.similaritySearch(SearchRequest.builder()
                .query(searchText.sentence())
                .topK(RECALL_K)
                .similarityThreshold(similarityThreshold)
                .build());
        List<Document> keywordHits = bm25.search(searchText.keywords(), RECALL_K);
        List<Document> fused = fuse(vectorHits, keywordHits, RECALL_K);
        if (fused.isEmpty()) {
            return "";
        }
        List<Document> ranked = rerank(question, fused);
        Map<String, String> uniqueByTitle = new LinkedHashMap<>();
        for (Document hit : ranked) {
            String title = String.valueOf(hit.getMetadata().getOrDefault("title", hit.getId()));
            uniqueByTitle.putIfAbsent(title, hit.getText());
            if (uniqueByTitle.size() >= topK) {
                break;
            }
        }
        return String.join("\n\n----\n\n", uniqueByTitle.values());
    }

    private SearchText toEnglishQuery(String question) {
        if (!containsCjk(question)) {
            return new SearchText(question, question);
        }
        try {
            String rewritten = deepSeekChatModel.call(new Prompt(new UserMessage("""
                    Rewrite the health question for MedlinePlus retrieval.
                    Output exactly two lines:
                    SENTENCE: one English question that keeps negation, the patient, and the conditions
                    KEYWORDS: 6 to 18 English search keywords
                    Question: %s
                    """.formatted(question)))).getResult().getOutput().getText();
            SearchText parsed = parseSearchText(rewritten);
            if (parsed == null) {
                return new SearchText(question, question);
            }
            return parsed;
        } catch (Exception e) {
            log.warn("检索词改写失败，改用原问题", e);
            return new SearchText(question, question);
        }
    }

    private SearchText parseSearchText(String rewritten) {
        if (rewritten == null || rewritten.isBlank()) {
            return null;
        }
        String sentence = "";
        String keywords = "";
        for (String line : rewritten.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.regionMatches(true, 0, "SENTENCE:", 0, "SENTENCE:".length())) {
                sentence = trimmed.substring("SENTENCE:".length()).trim();
            } else if (trimmed.regionMatches(true, 0, "KEYWORDS:", 0, "KEYWORDS:".length())) {
                keywords = trimmed.substring("KEYWORDS:".length()).trim();
            }
        }
        if (sentence.isBlank() || keywords.isBlank()) {
            return null;
        }
        return new SearchText(sentence, keywords);
    }

    private record SearchText(String sentence, String keywords) {
    }

    private List<Document> rerank(String question, List<Document> candidates) {
        List<String> texts = new ArrayList<>();
        for (Document candidate : candidates) {
            String text = candidate.getText();
            texts.add(text.length() > 1500 ? text.substring(0, 1500) : text);
        }
        try {
            List<Integer> order = embeddingModel.rerank(question, texts, candidates.size());
            if (order.isEmpty()) {
                return candidates;
            }
            List<Document> ranked = new ArrayList<>();
            for (Integer index : order) {
                if (index >= 0 && index < candidates.size()) {
                    ranked.add(candidates.get(index));
                }
            }
            return ranked.isEmpty() ? candidates : ranked;
        } catch (Exception e) {
            log.warn("重排失败，改用混合召回顺序", e);
            return candidates;
        }
    }

    private List<Document> fuse(List<Document> vectorHits, List<Document> keywordHits, int limit) {
        Map<String, Document> byId = new LinkedHashMap<>();
        Map<String, Double> scores = new HashMap<>();
        accumulate(vectorHits, byId, scores);
        accumulate(keywordHits, byId, scores);
        return scores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue(Comparator.reverseOrder()))
                .limit(limit)
                .map(entry -> byId.get(entry.getKey()))
                .toList();
    }

    private void accumulate(List<Document> ranked, Map<String, Document> byId, Map<String, Double> scores) {
        if (ranked == null) {
            return;
        }
        for (int i = 0; i < ranked.size(); i++) {
            Document document = ranked.get(i);
            byId.putIfAbsent(document.getId(), document);
            scores.merge(document.getId(), 1.0 / (RRF_K + i + 1), Double::sum);
        }
    }

    private void ensureReady() {
        if (ready) {
            return;
        }
        synchronized (gate) {
            if (ready) {
                return;
            }
            List<Document> loaded = loadChunks();
            bm25 = new Bm25Index(loaded);
            SimpleVectorStore store = SimpleVectorStore.builder(embeddingModel).build();
            if (!rebuild && indexFile.isFile() && indexFile.length() > 0) {
                store.load(indexFile);
                vectorStore = store;
                ready = true;
                log.info("已加载本地医学向量索引: {}", indexFile.getAbsolutePath());
                return;
            }
            if (!embeddingModel.hasApiKey()) {
                log.error("未配置 DASHSCOPE_API_KEY，无法为 MedlinePlus 文档建立向量索引");
                return;
            }
            log.info("开始为 {} 个文档片段建立向量索引，第一次会调用通义向量接口", loaded.size());
            store.add(loaded);
            File parent = indexFile.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            store.save(indexFile);
            vectorStore = store;
            ready = true;
            log.info("医学向量索引已写入 {}", indexFile.getAbsolutePath());
        }
    }

    private List<Document> loadChunks() {
        List<Document> documents = new ArrayList<>();
        try {
            Resource[] resources = resourcePatternResolver.getResources("classpath:document/medlineplus/*.md");
            for (Resource resource : resources) {
                String filename = resource.getFilename() == null ? "unknown.md" : resource.getFilename();
                String raw = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                documents.addAll(chunkFile(filename, raw));
            }
        } catch (Exception e) {
            throw new IllegalStateException("读取 MedlinePlus 文档失败", e);
        }
        return documents;
    }

    private List<Document> chunkFile(String filename, String raw) {
        String title = filename;
        String url = "";
        String[] lines = raw.split("\n", -1);
        if (lines.length > 0 && lines[0].startsWith("# ")) {
            title = lines[0].substring(2).trim();
        }
        for (String line : lines) {
            if (line.startsWith("Page: ")) {
                url = line.substring("Page: ".length()).trim();
                break;
            }
        }
        int sourceAt = raw.indexOf("## Source");
        String body = sourceAt > 0 ? raw.substring(0, sourceAt) : raw;
        body = body.replaceFirst("(?s)^# .*?\\n", "").trim();
        if (body.isBlank()) {
            return List.of();
        }

        List<String> sections = splitSections(body);
        List<Document> documents = new ArrayList<>();
        int index = 0;
        for (String section : sections) {
            String piece = section.trim();
            if (piece.length() < 80) {
                continue;
            }
            String text = "标题：" + title + "\n链接：" + url + "\n\n" + piece;
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("title", title);
            metadata.put("url", url);
            metadata.put("filename", filename);
            String id = UUID.nameUUIDFromBytes((filename + "#" + index).getBytes(StandardCharsets.UTF_8)).toString();
            documents.add(new Document(id, text, metadata));
            index++;
        }
        return documents;
    }

    /**
     * 按小节标题切开。过长的小节再按段落截断，并带上标题。
     */
    private List<String> splitSections(String body) {
        String[] blocks = body.split("\\n\\s*\\n");
        List<String> sections = new ArrayList<>();
        String heading = "";
        StringBuilder current = new StringBuilder();
        for (String block : blocks) {
            String trimmed = block.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (isHeading(trimmed) && current.length() > 0) {
                sections.add((heading + current).trim());
                current.setLength(0);
                heading = trimmed + "\n\n";
                continue;
            }
            if (isHeading(trimmed) && current.length() == 0) {
                heading = trimmed + "\n\n";
                continue;
            }
            if (current.length() > 0) {
                current.append("\n\n");
            }
            current.append(trimmed);
            if (current.length() >= CHUNK_CHARS) {
                sections.add((heading + current).trim());
                current.setLength(0);
            }
        }
        if (current.length() > 0) {
            sections.add((heading + current).trim());
        }
        if (sections.isEmpty()) {
            sections.add(body);
        }
        return sections;
    }

    private boolean isHeading(String block) {
        if (block.contains("\n") || block.startsWith("- ") || block.startsWith("#")) {
            return false;
        }
        if (block.length() > 120) {
            return false;
        }
        return block.endsWith("?") || (!block.contains(". ") && block.length() < 80);
    }

    private boolean containsCjk(String text) {
        return text.codePoints().anyMatch(cp -> cp >= 0x4E00 && cp <= 0x9FFF);
    }

    /**
     * 内存 BM25，专门匹配药名、缩写这类向量容易冲淡的词。
     */
    private static final class Bm25Index {

        private static final double K1 = 1.2;

        private static final double B = 0.75;

        private final List<Document> documents;

        private final List<Map<String, Integer>> termFreqs;

        private final Map<String, Integer> documentFreq = new HashMap<>();

        private final double avgLength;

        private Bm25Index(List<Document> documents) {
            this.documents = documents;
            this.termFreqs = new ArrayList<>(documents.size());
            int total = 0;
            for (Document document : documents) {
                Map<String, Integer> freq = new HashMap<>();
                for (String token : tokenize(document.getText())) {
                    freq.merge(token, 1, Integer::sum);
                }
                termFreqs.add(freq);
                total += freq.values().stream().mapToInt(Integer::intValue).sum();
                for (String token : freq.keySet()) {
                    documentFreq.merge(token, 1, Integer::sum);
                }
            }
            this.avgLength = documents.isEmpty() ? 0 : (double) total / documents.size();
        }

        private List<Document> search(String query, int limit) {
            List<String> queryTokens = tokenize(query);
            if (queryTokens.isEmpty() || documents.isEmpty()) {
                return List.of();
            }
            int docCount = documents.size();
            List<Scored> scored = new ArrayList<>();
            for (int i = 0; i < docCount; i++) {
                double score = 0;
                int length = termFreqs.get(i).values().stream().mapToInt(Integer::intValue).sum();
                for (String token : queryTokens) {
                    int tf = termFreqs.get(i).getOrDefault(token, 0);
                    if (tf == 0) {
                        continue;
                    }
                    int df = documentFreq.getOrDefault(token, 0);
                    double idf = Math.log(1 + (docCount - df + 0.5) / (df + 0.5));
                    double denom = tf + K1 * (1 - B + B * length / avgLength);
                    score += idf * (tf * (K1 + 1)) / denom;
                }
                if (score > 0) {
                    scored.add(new Scored(documents.get(i), score));
                }
            }
            scored.sort(Comparator.comparingDouble(Scored::score).reversed());
            List<Document> hits = new ArrayList<>();
            for (int i = 0; i < Math.min(limit, scored.size()); i++) {
                hits.add(scored.get(i).document());
            }
            return hits;
        }

        private static List<String> tokenize(String text) {
            String lower = text.toLowerCase(Locale.ROOT);
            List<String> tokens = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            for (int i = 0; i < lower.length(); i++) {
                char ch = lower.charAt(i);
                if (Character.isLetterOrDigit(ch)) {
                    current.append(ch);
                } else if (current.length() > 1) {
                    tokens.add(current.toString());
                    current.setLength(0);
                } else {
                    current.setLength(0);
                }
            }
            if (current.length() > 1) {
                tokens.add(current.toString());
            }
            return tokens;
        }

        private record Scored(Document document, double score) {
        }
    }
}
