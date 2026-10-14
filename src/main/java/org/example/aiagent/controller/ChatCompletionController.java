package org.example.aiagent.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import org.example.aiagent.app.LoveApp;
import org.example.aiagent.auth.AuthContext;
import org.example.aiagent.service.ApiKeyService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import jakarta.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * OpenAI 兼容的对话接口。调用方只需要一个 Bearer 令牌。
 */
@RestController
@RequestMapping("/v1")
public class ChatCompletionController {

    @Resource
    private LoveApp loveApp;

    @Resource
    private ApiKeyService apiKeyService;

    @Resource
    private ObjectMapper objectMapper;

    @PostMapping("/chat/completions")
    public ResponseEntity<?> completions(@RequestBody ChatCompletionRequest request) {
        String message = lastUserMessage(request);
        if (message == null || message.isBlank()) {
            return error(400, "messages 里需要一条 user 内容");
        }
        if (message.length() > 4000) {
            return error(400, "消息过长，请控制在 4000 字以内");
        }
        String model = request.getModel() == null || request.getModel().isBlank()
                ? "medical" : request.getModel();
        if (!"medical".equals(model) && !"medical-assistant".equals(model)) {
            return error(400, "model 目前只支持 medical");
        }

        String chatId = conversationId(request);
        Long apiKeyId = AuthContext.isApiKeyAuth() ? AuthContext.getApiKeyId() : null;
        String id = "chatcmpl-" + UUID.randomUUID().toString().replace("-", "");

        if (Boolean.TRUE.equals(request.getStream())) {
            Flux<String> body = loveApp.doChatByStream(message, chatId)
                    .map(chunk -> toSse(id, model, chunk))
                    .concatWithValues("data: [DONE]\n\n")
                    .doOnComplete(() -> charge(apiKeyId));
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_EVENT_STREAM)
                    .body(body);
        }

        String text = loveApp.doChat(message, chatId);
        charge(apiKeyId);
        Map<String, Object> messageBody = new LinkedHashMap<>();
        messageBody.put("role", "assistant");
        messageBody.put("content", text);
        Map<String, Object> choice = new LinkedHashMap<>();
        choice.put("index", 0);
        choice.put("message", messageBody);
        choice.put("finish_reason", "stop");
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", id);
        response.put("object", "chat.completion");
        response.put("model", model);
        response.put("choices", List.of(choice));
        return ResponseEntity.ok(response);
    }

    private String toSse(String id, String model, String content) {
        try {
            Map<String, Object> delta = new LinkedHashMap<>();
            delta.put("content", content);
            Map<String, Object> choice = new LinkedHashMap<>();
            choice.put("index", 0);
            choice.put("delta", delta);
            Map<String, Object> chunk = new LinkedHashMap<>();
            chunk.put("id", id);
            chunk.put("object", "chat.completion.chunk");
            chunk.put("model", model);
            chunk.put("choices", List.of(choice));
            return "data: " + objectMapper.writeValueAsString(chunk) + "\n\n";
        } catch (Exception e) {
            return "data: {\"error\":{\"message\":\"编码失败\"}}\n\n";
        }
    }

    private String lastUserMessage(ChatCompletionRequest request) {
        if (request == null || request.getMessages() == null) {
            return null;
        }
        String content = null;
        for (ChatMessage message : request.getMessages()) {
            if (message != null && "user".equals(message.getRole()) && message.getContent() != null) {
                content = message.getContent();
            }
        }
        return content;
    }

    private String conversationId(ChatCompletionRequest request) {
        String user = request.getUser();
        String suffix = user == null || user.isBlank() ? "default" : user.trim();
        Long keyId = AuthContext.getApiKeyId();
        Long userId = AuthContext.getUserId();
        String owner = keyId != null ? "key-" + keyId : "user-" + userId;
        return owner + ":" + suffix;
    }

    private void charge(Long apiKeyId) {
        if (apiKeyId != null) {
            apiKeyService.incrementUsage(apiKeyId);
        }
    }

    private ResponseEntity<Map<String, Object>> error(int status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", message);
        body.put("type", "invalid_request_error");
        return ResponseEntity.status(status).body(Map.of("error", body));
    }

    @Data
    public static class ChatCompletionRequest {
        private String model;
        private List<ChatMessage> messages;
        private Boolean stream;
        /** 同一 user 视为同一段对话，用来延续记忆。 */
        private String user;
    }

    @Data
    public static class ChatMessage {
        private String role;
        private String content;
    }
}
