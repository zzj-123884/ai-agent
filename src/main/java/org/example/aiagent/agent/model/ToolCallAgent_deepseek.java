package org.example.aiagent.agent.model;

import cn.hutool.core.collection.CollUtil;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;


import java.util.List;
import java.util.stream.Collectors;

@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public class ToolCallAgent_deepseek extends ReActAgent {


    private final ToolCallback[] availableTools;


    private ChatResponse toolCallChatResponse;


    private final ToolCallingManager toolCallingManager;


    private final ChatOptions chatOptions;

    public ToolCallAgent_deepseek(ToolCallback[] availableTools) {
        super();
        this.availableTools = availableTools;
        this.toolCallingManager = ToolCallingManager.builder().build();

        this.chatOptions = DeepSeekChatOptions.builder()
                .temperature(0.7)
                .maxTokens(2000)
                .internalToolExecutionEnabled(false)
                .build();
    }

    @Override
    public boolean think() {

        if (getNextStepPrompt() != null && !getNextStepPrompt().isEmpty()) {
            UserMessage userMessage = new UserMessage(getNextStepPrompt());
            getMessageList().add(userMessage);
        }

        List<Message> messageList = getMessageList();
        Prompt prompt = new Prompt(messageList, chatOptions);

        try {

            ChatResponse chatResponse = getChatClient()
                    .prompt(prompt)
                    .system(getSystemPrompt())
                    .toolCallbacks(availableTools)
                    .call()
                    .chatResponse();

            this.toolCallChatResponse = chatResponse;

            AssistantMessage assistantMessage =
                    chatResponse.getResult().getOutput();

            String result = assistantMessage.getText();

            List<AssistantMessage.ToolCall> toolCallList =
                    assistantMessage.getToolCalls();

            log.info("{} 的思考: {}", getName(), result);
            log.info("{} 选择了 {} 个工具来使用",
                    getName(), toolCallList.size());

            // 无工具调用时模型文本就是最终回答；有工具调用时才是规划说明。
            if (toolCallList.isEmpty()) {
                emitAnswer(result);
            } else {
                emitThink(result);
            }

            String toolCallInfo = toolCallList.stream()
                    .map(toolCall -> String.format(
                            "工具名称：%s，参数：%s",
                            toolCall.name(),
                            toolCall.arguments()))
                    .collect(Collectors.joining("\n"));

            log.info(toolCallInfo);

            // 结构化推送工具调用，前端可渲染成「正在调用 xxx」的卡片
            emitToolCalls(toolCallList);

            if (toolCallList.isEmpty()) {
                getMessageList().add(assistantMessage);
                return false;
            }

            return true;

        } catch (Exception e) {
            log.error("{} 的思考过程遇到了问题", getName(), e);
            throw new IllegalStateException("智能体思考失败", e);
        }
    }

    @Override
    public String act() {
        if (!toolCallChatResponse.hasToolCalls()) {
            return "没有工具调用";
        }

        Prompt prompt = new Prompt(getMessageList(), chatOptions);
        ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, toolCallChatResponse);

        setMessageList(toolExecutionResult.conversationHistory());

        ToolResponseMessage toolResponseMessage = (ToolResponseMessage) CollUtil.getLast(toolExecutionResult.conversationHistory());

        // 逐个推送工具结果，而不是拼成一个大字符串：
        // 前端可以把每个工具的执行结果折叠展示，避免长文本淹没最终回答
        toolResponseMessage.getResponses().forEach(response ->
                emitToolResult(response.name(), String.valueOf(response.responseData())));

        String results = toolResponseMessage.getResponses().stream()
                .map(response -> "工具 " + response.name() + " 完成了它的任务！结果: " + response.responseData())
                .collect(Collectors.joining("\n"));

        boolean terminateToolCalled = toolResponseMessage.getResponses().stream()
                .anyMatch(response -> "doTerminate".equals(response.name()));
        if (terminateToolCalled) {
            setState(AgentState.FINISHED);
        }
        log.info(results);
        return results;
    }
}
