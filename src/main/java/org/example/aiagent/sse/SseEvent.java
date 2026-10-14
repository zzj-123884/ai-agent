package org.example.aiagent.sse;

import lombok.Data;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * SSE 统一事件协议。
 * <p>
 * 背景：原实现直接 {@code emitter.send(String)}，前端拿到的是一坨纯文本，
 * 无法区分「这是思考过程」还是「这是最终回答」，也无法承载工具调用等结构化信息。
 * <p>
 * 现在所有 SSE 数据都以 JSON 形式推送，前端按 {@code type} 分发渲染：
 * <pre>
 * {"type":"THINK","step":1,"data":"我需要先搜索…","timestamp":1730000000000}
 * </pre>
 * 前端只需监听 message 事件并 JSON.parse，无需为每种类型注册命名事件，
 * 这样 EventSource 的 onmessage 单入口即可处理全部类型。
 */
@Data
public class SseEvent implements Serializable {

    /**
     * 事件类型，见 {@link SseEventType}。
     */
    private String type;

    /**
     * 所属步骤序号（从 1 开始），非步骤类事件为 null。
     */
    private Integer step;

    /**
     * 任务唯一标识。前端用它调用 /ai/task/stop 停止生成。
     * 每个事件都带上而不是只在首个事件带，是因为前端可能中途才建立监听，
     * 反复携带的成本极低（几十字节）而丢失它的代价是整个停止功能失效。
     */
    private String taskId;

    /**
     * 事件载荷：文本内容，或工具调用的结构化描述。
     */
    private Object data;

    /**
     * 服务端产生时间戳，前端可用于计算耗时、排查卡顿。
     */
    private Long timestamp;

    public SseEvent() {
    }

    public SseEvent(String type, Integer step, Object data) {
        this.type = type;
        this.step = step;
        this.data = data;
        this.timestamp = System.currentTimeMillis();
    }

    public static SseEvent of(SseEventType type, Integer step, Object data) {
        return new SseEvent(type.name(), step, data);
    }

    /**
     * 带上 taskId 的构造入口。
     */
    public static SseEvent of(SseEventType type, Integer step, Object data, String taskId) {
        SseEvent event = new SseEvent(type.name(), step, data);
        event.setTaskId(taskId);
        return event;
    }

    public static SseEvent think(Integer step, String text) {
        return of(SseEventType.THINK, step, text);
    }

    public static SseEvent answer(String text) {
        return of(SseEventType.ANSWER, null, text);
    }

    public static SseEvent toolCall(Integer step, String toolName, String arguments) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tool", toolName);
        payload.put("arguments", arguments);
        return of(SseEventType.TOOL_CALL, step, payload);
    }

    public static SseEvent toolResult(Integer step, String toolName, String result) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tool", toolName);
        payload.put("result", result);
        return of(SseEventType.TOOL_RESULT, step, payload);
    }

    public static SseEvent stepStart(Integer step) {
        return of(SseEventType.STEP_START, step, null);
    }

    public static SseEvent done(String summary) {
        return of(SseEventType.DONE, null, summary);
    }

    /**
     * 用户主动停止。前端据此把气泡标记为「已停止」而非「出错」。
     */
    public static SseEvent stopped(String message) {
        return of(SseEventType.STOPPED, null, message);
    }

    public static SseEvent error(String message) {
        return of(SseEventType.ERROR, null, message);
    }
}
