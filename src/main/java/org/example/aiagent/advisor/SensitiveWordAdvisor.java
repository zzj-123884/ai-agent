package org.example.aiagent.advisor;

import org.springframework.ai.chat.client.advisor.api.AdvisedRequest;
import org.springframework.ai.chat.client.advisor.api.AdvisedResponse;
import org.springframework.ai.chat.client.advisor.api.CallAroundAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAroundAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.core.Ordered;

import java.util.List;
import java.util.Set;

public class SensitiveWordAdvisor implements CallAroundAdvisor {

    // 只拦截明确有害的请求。疾病名、药名和正常健康咨询不在此列。
    private static final Set<String> FORBIDDEN_WORDS = Set.of(
            "制毒",
            "合成毒品",
            "伪造处方",
            "贩卖处方",
            "投毒",
            "下毒",
            "毒药配方",
            "自制毒药"
    );

    @Override
    public AdvisedResponse aroundCall(AdvisedRequest advisedRequest, CallAroundAdvisorChain chain) {
        // 1. 校验用户输入文本
        String userText = advisedRequest.userText();
        for (String word : FORBIDDEN_WORDS) {
            if (userText != null && userText.contains(word)) {
                // 方式一：直接短路拦截，不调用大模型，返回安全回复
                ChatResponse safeResponse = new ChatResponse(List.of(
                        new Generation(new AssistantMessage("您的提问包含敏感或违规内容，无法提供回答。"))
                ));
                return new AdvisedResponse(safeResponse, advisedRequest.adviseContext());

                // 方式二：或者直接抛出业务异常
                // throw new IllegalArgumentException("提问中包含敏感词: " + word);
            }
        }

        // 2. 放行请求，调用后续链路（包含调用大模型）
        AdvisedResponse response = chain.nextAroundCall(advisedRequest);

        // 3. （可选）后置拦截：校验大模型生成的输出内容
        String outputText = response.response().getResult().getOutput().getText();
        for (String word : FORBIDDEN_WORDS) {
            if (outputText != null && outputText.contains(word)) {
                ChatResponse fallbackResponse = new ChatResponse(List.of(
                        new Generation(new AssistantMessage("回答内容未能通过合规审查。"))
                ));
                return new AdvisedResponse(fallbackResponse, response.adviseContext());
            }
        }

        return response;
    }

    @Override
    public String getName() {
        return "SensitiveWordAdvisor";
    }

    @Override
    public int getOrder() {
        // 顺序排在权限校验之后
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}