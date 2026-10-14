package org.example.aiagent.chatmemory;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;


//@Component
public class RedisChatMemory implements ChatMemory {
    record ChatMessageDto(String type, String content) implements Serializable {}

    private static final String PREFIX = "chat:memory:";
    private final RedisTemplate<String, Object> redisTemplate;
    private final int maxMessages = 20; // 滑动窗口大小（保留最近20条）

    public RedisChatMemory(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }
    @Override
    public void add(String conversationId, List<Message> messages) {
        String key = PREFIX + conversationId;
        for (Message message : messages) {
            ChatMessageDto dto = new ChatMessageDto(
                    message.getMessageType().name(),
                    message.getText()
            );
            redisTemplate.opsForList().rightPush(key, dto);
        }
        // 限制窗口长度并设置过期时间
        redisTemplate.opsForList().trim(key, -maxMessages, -1);
        redisTemplate.expire(key, Duration.ofDays(7));
    }
    @Override
    public List<Message> get(String conversationId) {
        // 默认获取滑窗大小范围内的最新消息
        return get(conversationId, this.maxMessages);
    }

//    @Override
    public List<Message> get(String conversationId, int lastN) {
        if (lastN <= 0) {
            return Collections.emptyList();
        }

        String key = PREFIX + conversationId;
        // -lastN 到 -1 表示获取列表尾部（最新）的 lastN 条数据
        List<Object> list = redisTemplate.opsForList().range(key, -lastN, -1);
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }

        return list.stream()
                .map(item -> (ChatMessageDto) item)
                .map(this::toMessage)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    public void clear(String conversationId) {
        redisTemplate.delete(PREFIX + conversationId);
    }

    private Message toMessage(ChatMessageDto dto) {
        return switch (dto.type()) {
            case "USER" -> new UserMessage(dto.content());
            case "ASSISTANT" -> new AssistantMessage(dto.content());
            case "SYSTEM" -> new SystemMessage(dto.content());
            default -> null;
        };
    }
}
