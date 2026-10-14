package org.example.aiagent.demo.rag;


import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.expansion.MultiQueryExpander;
import org.springframework.stereotype.Component;

import java.util.List;

//扩展查询器demo
@Component
public class MultiQueryExpanderDemo {
    @Resource
    private ChatClient.Builder ChatClientBuilder;

    public List<Query> expand(String query){
        MultiQueryExpander queryExpander = MultiQueryExpander.builder()
                .chatClientBuilder(ChatClientBuilder)
                .numberOfQueries(3)
                .build();
        List<Query> queries=queryExpander.expand(new Query("谁是程序员鱼皮？"));
        return queries;
    }
}
