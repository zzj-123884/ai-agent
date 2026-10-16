package org.example.aiagent.rag;

//import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
//import com.alibaba.cloud.ai.dashscope.rag.DashScopeDocumentRetriever;
//import com.alibaba.cloud.ai.dashscope.rag.DashScopeDocumentRetrieverOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;


//自定义基于阿里云服务的rag增强顾问
//@Component
@Slf4j
public class MedicalRagCloudAdvisorConfig {

    @Value("${spring.ai.dashscope.api-key}")
    private String dashscopeApiKey;

//    @Bean
//    public Advisor medicalRagCloudAdvisor(){
//
//        DashScopeApi dashScopeApi =new DashScopeApi(dashscopeApiKey);
//        DocumentRetriever dashScopeDocumentRetriever = new DashScopeDocumentRetriever(dashScopeApi,
//                DashScopeDocumentRetrieverOptions.builder()
//                        .withIndexName("医学助手")
//                        .build());
//        return RetrievalAugmentationAdvisor.builder()
//                .documentRetriever(dashScopeDocumentRetriever)
//                .build();
//    }
}
