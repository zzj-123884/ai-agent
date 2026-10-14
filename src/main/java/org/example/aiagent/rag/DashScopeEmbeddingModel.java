package org.example.aiagent.rag;

import com.alibaba.dashscope.embeddings.TextEmbedding;
import com.alibaba.dashscope.embeddings.TextEmbeddingParam;
import com.alibaba.dashscope.embeddings.TextEmbeddingResult;
import com.alibaba.dashscope.embeddings.TextEmbeddingResultItem;
import com.alibaba.dashscope.rerank.TextReRank;
import com.alibaba.dashscope.rerank.TextReRankOutput;
import com.alibaba.dashscope.rerank.TextReRankParam;
import com.alibaba.dashscope.rerank.TextReRankResult;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 通义 qwen3.7-text-embedding-flash。中英文在同一向量空间，适合用中文问题检索英文 MedlinePlus。
 */
@Component
public class DashScopeEmbeddingModel implements EmbeddingModel {

    static final int DIMENSIONS = 1024;

    private static final String MODEL = "qwen3.7-text-embedding-flash";

    private static final int BATCH_SIZE = 20;

    private final String apiKey;

    public DashScopeEmbeddingModel(@Value("${DASHSCOPE_API_KEY:}") String apiKey) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    public boolean hasApiKey() {
        return !apiKey.isBlank();
    }

    /**
     * 用 qwen3.7-text-rerank 按问题对候选段落重新打分，返回文档下标，从高到低。
     */
    public List<Integer> rerank(String query, List<String> documents, int topN) {
        if (!hasApiKey() || documents.isEmpty()) {
            return List.of();
        }
        try {
            TextReRankParam param = TextReRankParam.builder()
                    .apiKey(apiKey)
                    .model("qwen3.7-text-rerank")
                    .query(query)
                    .documents(documents)
                    .topN(Math.min(topN, documents.size()))
                    .returnDocuments(false)
                    .build();
            TextReRankResult result = new TextReRank().call(param);
            if (result.getOutput() == null || result.getOutput().getResults() == null) {
                return List.of();
            }
            List<Integer> order = new ArrayList<>();
            for (TextReRankOutput.Result item : result.getOutput().getResults()) {
                if (item.getIndex() != null) {
                    order.add(item.getIndex());
                }
            }
            return order;
        } catch (Exception e) {
            throw new IllegalStateException("调用通义重排接口失败", e);
        }
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<String> inputs = request.getInstructions();
        List<Embedding> embeddings = new ArrayList<>(inputs.size());
        for (int start = 0; start < inputs.size(); start += BATCH_SIZE) {
            int end = Math.min(start + BATCH_SIZE, inputs.size());
            embeddings.addAll(embedBatch(inputs.subList(start, end), start));
        }
        embeddings.sort(Comparator.comparing(Embedding::getIndex));
        return new EmbeddingResponse(embeddings);
    }

    @Override
    public float[] embed(Document document) {
        return embed(document.getText());
    }

    @Override
    public int dimensions() {
        return DIMENSIONS;
    }

    private List<Embedding> embedBatch(List<String> texts, int indexOffset) {
        try {
            TextEmbeddingParam param = TextEmbeddingParam.builder()
                    .apiKey(apiKey)
                    .model(MODEL)
                    .texts(texts)
                    .dimension(DIMENSIONS)
                    .build();
            TextEmbeddingResult result = new TextEmbedding().call(param);
            if (result.getOutput() == null || result.getOutput().getEmbeddings() == null) {
                throw new IllegalStateException("通义向量接口没有返回向量");
            }
            List<Embedding> embeddings = new ArrayList<>();
            List<TextEmbeddingResultItem> items = result.getOutput().getEmbeddings();
            for (int i = 0; i < items.size(); i++) {
                TextEmbeddingResultItem item = items.get(i);
                int index = item.getTextIndex() == null ? indexOffset + i : indexOffset + item.getTextIndex();
                embeddings.add(new Embedding(toFloats(item.getEmbedding()), index));
            }
            return embeddings;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("调用通义向量接口失败", e);
        }
    }

    private float[] toFloats(List<Double> values) {
        float[] vector = new float[values.size()];
        for (int i = 0; i < values.size(); i++) {
            vector[i] = values.get(i).floatValue();
        }
        return vector;
    }
}
