package org.example.aiagent.agent.model;

import org.example.aiagent.advisor.MyLoggerAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

/**
 * 基于 DeepSeek 的超级智能体。
 * <p>
 * 注意：该类虽然标记了 {@code @Component}，但 Controller 里是 new 出来用的，
 * 目的是让每次请求都拥有独立的 messageList，避免多用户共用同一个实例时对话串味。
 * 这里的 {@code @Component} 仅用于让 Spring 启动时可注入，保留对现有构造签名的兼容。
 */
@Component
public class Manus_deepseek extends ToolCallAgent_deepseek {

    public Manus_deepseek(ToolCallback[] allTools, DeepSeekChatModel deepSeekChatModel) {
        super(allTools);
        this.setName("yuManus");
        String osName = System.getProperty("os.name", "unknown");
        boolean windows = osName.toLowerCase().contains("win");
        String shellRule = windows
                ? "Host OS is Windows. executeTerminalCommand runs cmd.exe. Use only cmd commands such as dir, type, del, copy, mkdir. Never use Unix commands such as ls, cat, rm, grep, or pwd."
                : "Host OS is " + osName + ". executeTerminalCommand runs /bin/sh. Use Unix shell commands such as ls, cat, rm, grep, and pwd.";
        String SYSTEM_PROMPT = """
                You are YuManus, an all-capable AI assistant, aimed at solving any task presented by the user.
                You have various tools at your disposal that you can call upon to efficiently complete complex requests.
                %s
                """.formatted(shellRule);
        this.setSystemPrompt(SYSTEM_PROMPT);
        String NEXT_STEP_PROMPT = """
                Based on user needs, proactively select the most appropriate tool or combination of tools.
                For complex tasks, you can break down the problem and use different tools step by step to solve it.
                After using each tool, clearly explain the execution results and suggest the next steps.
                If you want to stop the interaction at any point, use the `terminate` tool/function call.
                %s
                """.formatted(shellRule);
        this.setNextStepPrompt(NEXT_STEP_PROMPT);
        this.setMaxSteps(10);

        ChatClient chatClient = ChatClient.builder(deepSeekChatModel)
                .defaultAdvisors(new MyLoggerAdvisor())
                .build();
        this.setChatClient(chatClient);
    }
}