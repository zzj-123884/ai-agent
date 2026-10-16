package org.example.aiagent.app;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.example.aiagent.advisor.MyLoggerAdvisor;
import org.example.aiagent.advisor.SensitiveWordAdvisor;
import org.example.aiagent.chatmemory.FileBasedChatMemory;
import org.example.aiagent.chatmemory.RedisChatMemory;
import org.example.aiagent.rag.MedicalRagAdvisorFactory;
import org.example.aiagent.rag.MedicalKnowledgeStore;
import org.example.aiagent.rag.QueryRewriter;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.MessageAggregator;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY;
import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_RETRIEVE_SIZE_KEY;



@Component
@Slf4j
public class MedicalAssistant {
    private final ChatClient chatClient;
    private final MedicalKnowledgeStore medicalKnowledgeStore;
    private static final String SYSTEM_PROMPT = "你是医学健康助手，只回答健康、疾病、症状、检查、用药、预防和就医相关的问题。" +
            "编程、情感、娱乐、旅行、作业等与医学无关的问题，直接回复：我只能回答医学健康相关的问题。" +
            "不要借检索资料把无关问题绕成医学回答。" +
            "对医学问题，优先依据给出的 MedlinePlus 资料用中文解释，回答末尾列出资料标题和链接。" +
            "资料不够时直接说明知识库没有匹配条目，不要编造出处。" +
            "不做诊断、不开药。出现胸痛、呼吸困难、大出血、意识不清时，告诉用户立即急诊。";
    //    @Resource
//    private VectorStore medicalVectorStore;

//    @Resource
//    private Advisor medicalRagCloudAdvisor;

    @Resource
    private QueryRewriter queryRewriter;

    @Resource
    private ToolCallback[] allTools;


//    初始化ai客户端
    public MedicalAssistant(DeepSeekChatModel deepSeekChatModel, MedicalKnowledgeStore medicalKnowledgeStore) {
        this.medicalKnowledgeStore = medicalKnowledgeStore;
//        初始化基于文件的对话记忆
//        String fileDir=System.getProperty("user.dir")+"/tmp/chat-memory";
//        ChatMemory chatMemory=new FileBasedChatMemory(fileDir);



        //基于内存的对话记忆
//        ChatMemory chatMemory =new InMemoryChatMemory();
//        ChatMemory chatMemory =new RedisChatMemory()
        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .build();
        chatClient=ChatClient.builder(deepSeekChatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
//                        自定义拦截器
                        new MyLoggerAdvisor(),
                        new SensitiveWordAdvisor()
                )
                .build();
    }

    public String doChat(String message, String chatId) {
        ChatResponse response = chatClient
                .prompt()
                .system(systemPromptFor(message))
                .user(message)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                .call()
                .chatResponse();
        String content = response.getResult().getOutput().getText();
//        log.info("content: {}", content);
        return content;
    }

    public Flux<String> doChatByStream(String message, String chatId) {
        return  chatClient
                .prompt()
                .system(systemPromptFor(message))
                .user(message)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                .stream()
                .content();
    }

    private String systemPromptFor(String message) {
        String context = medicalKnowledgeStore.retrieve(message);
        if (context.isBlank()) {
            return SYSTEM_PROMPT + "本次知识库没有检索到相关资料。请说明没有匹配到 MedlinePlus 条目。";
        }
        return SYSTEM_PROMPT + "\n以下是检索到的资料：\n" + context;
    }

    record HealthReport(String title, List<String> suggestions){}
//    演示结构化输出
    public HealthReport doChatwithReporter(String message, String chatId) {
        HealthReport HealthReport = chatClient
                .prompt()
                .system(SYSTEM_PROMPT+"每次对话后都要生成健康建议,标题为{用户名}的健康建议,内容建议列表")
                .user(message)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                .call()
                .entity(HealthReport.class);
//        String content = response.getResult().getOutput().getText();
        log.info("HealthReport: {}", HealthReport);
        return HealthReport;
    }


//    支持调用工具
    public String doChatWithTools(String message, String chatId) {
        ChatResponse response = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))

                .advisors(new MyLoggerAdvisor())
//                .tools(allTools)
                .call()
                .chatResponse();
        String content = response.getResult().getOutput().getText();
//        log.info("content: {}", content);
        String finishReason = response.getResult().getMetadata().getFinishReason();
        System.out.println("Finish Reason: " + finishReason);
        return content;
    }



//    和rag知识库进行对话
    public String doChatWithRag(String message ,String chatId){
//        查询重写
        String rewrittenMessage= queryRewriter.doQueryRewriter(message);

        ChatResponse chatResponse=chatClient
                .prompt()
                .user(rewrittenMessage)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                .advisors(new MyLoggerAdvisor())
//                应用本地rag知识库问答
//                .advisors(new QuestionAnswerAdvisor(medicalVectorStore))
//              应用rag检索增强服务(基于云知识库)
//                .advisors(medicalRagCloudAdvisor)
//                .advisors(MedicalRagAdvisorFactory.createMedicalRagAdvisor(
//                        medicalVectorStore,"单身"
//                ))
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }

//    ai调用mcp服务
//    @Resource
//    private ToolCallbackProvider toolCallbackProvider;

    public String doChatWithMcp(String message, String chatId) {
        ChatResponse response = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))

                .advisors(new MyLoggerAdvisor())
//                .tools(toolCallbackProvider)
                .call()
                .chatResponse();
        String content = response.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }

}
