package org.example.aiagent.rag;


import jakarta.annotation.Resource;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class MedicalVectorStoreConfig {

    @Resource
    private MedicalDocumentLoader medicalDocumentLoader;

    @Resource
    private MyKeywordEnricher myKeywordEnricher;
//    @Bean
    VectorStore  medicalVectorStore(EmbeddingModel dashscopeEmbeddingModel){
        SimpleVectorStore simpleVectorStore = SimpleVectorStore.builder(dashscopeEmbeddingModel).build();
        List<Document> documentList =medicalDocumentLoader.loadMarkdowns();
        List<Document> enrichedDocuments=myKeywordEnricher.enrichDocuments(documentList);

        simpleVectorStore.add(documentList);
        return simpleVectorStore;
    }
}
