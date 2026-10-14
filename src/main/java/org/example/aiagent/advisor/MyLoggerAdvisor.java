package org.example.aiagent.advisor;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.advisor.api.*;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.MessageAggregator;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.core.Ordered;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;



import org.springframework.ai.chat.client.advisor.api.AdvisedRequest;
import org.springframework.ai.chat.client.advisor.api.AdvisedResponse;
import org.springframework.ai.chat.client.advisor.api.CallAroundAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAroundAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAroundAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAroundAdvisorChain;

/**
 * A simple logger advisor that logs the request and response messages.
 *
 * @author Christian Tzolov
 */
@Slf4j
public class MyLoggerAdvisor implements CallAroundAdvisor, StreamAroundAdvisor {

    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 9;
    }

    private AdvisedRequest before(AdvisedRequest request) {
        log.info("AI Resquest: {}",request.userText());
        return request;
    }

    private void observeAfter(AdvisedResponse advisedResponse) {
        log.info("AI Response: {}", advisedResponse.response().getResult().getOutput().getText());
    }

    @Override
    public AdvisedResponse aroundCall(AdvisedRequest advisedRequest, CallAroundAdvisorChain chain) {

        advisedRequest = before(advisedRequest);

        AdvisedResponse advisedResponse = chain.nextAroundCall(advisedRequest);

        observeAfter(advisedResponse);

        return advisedResponse;
    }

    @Override
    public Flux<AdvisedResponse> aroundStream(AdvisedRequest advisedRequest, StreamAroundAdvisorChain chain) {
        advisedRequest = before(advisedRequest);

        Flux<AdvisedResponse> advisedResponses = chain.nextAroundStream(advisedRequest);

        // 暂存流式过程中的 adviseContext
        Map<String, Object> context = new ConcurrentHashMap<>(advisedRequest.adviseContext());

        // 提取 ChatResponse 流并更新上下文
        Flux<ChatResponse> chatResponses = advisedResponses
                .doOnNext(ar -> {
                    if (ar.adviseContext() != null) {
                        context.putAll(ar.adviseContext());
                    }
                })
                .map(AdvisedResponse::response);

        // 调用 aggregate 方法并在流结束时触发 observeAfter 回调
        Flux<ChatResponse> aggregatedFlux = new MessageAggregator().aggregate(chatResponses, aggregatedResponse -> {
            this.observeAfter(new AdvisedResponse(aggregatedResponse, context));
        });

        // 重新将 ChatResponse 包装为 AdvisedResponse 返回
        return aggregatedFlux.map(response -> new AdvisedResponse(response, context));
    }
}
