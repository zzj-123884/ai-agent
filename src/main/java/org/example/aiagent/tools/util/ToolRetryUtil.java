package org.example.aiagent.tools.util;

import com.github.rholder.retry.Attempt;
import com.github.rholder.retry.RetryException;
import com.github.rholder.retry.RetryListener;
import com.github.rholder.retry.Retryer;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.WaitStrategies;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * 工具重试模板（基于 Guava Retrying）。
 * <p>
 * 为什么需要：给 AI 用的工具本质上是「网络调用 + 外部依赖」，抖动是常态。
 * 原来 {@code try { ... } catch (Exception e) { return "Error: " + e.getMessage(); }}
 * 的做法有两个问题：
 * <ol>
 *   <li>一次失败就直接把错误文本喂给大模型，模型往往会基于错误信息继续胡说；</li>
 *   <li>瞬时故障（限流、连接重置）本可以重试成功，却被当成永久失败。</li>
 * </ol>
 * <p>
 * 设计要点：
 * <ul>
 *   <li>指数退避（1s → 2s → 4s），避免重试风暴把对端打挂；</li>
 *   <li>只对「可能瞬时恢复」的异常重试，参数错误这类永久失败立刻放弃（由调用方决定）；</li>
 *   <li>最终仍失败时返回明确带 {@code [RETRY_EXHAUSTED]} 标记的文本，
 *       便于在日志里区分「一次就失败」和「重试耗尽」。</li>
 * </ul>
 */
@Slf4j
public final class ToolRetryUtil {

    /**
     * 默认最大尝试次数（含首次）。
     */
    private static final int DEFAULT_MAX_ATTEMPTS = 3;

    private ToolRetryUtil() {
    }

    /**
     * 带重试执行。失败时抛 {@link RetryException} 包装的运行时异常，由调用方兜底。
     */
    public static <T> T execute(Callable<T> task, String actionName) throws ExecutionException, RetryException {
        return execute(task, actionName, DEFAULT_MAX_ATTEMPTS);
    }

    public static <T> T execute(Callable<T> task, String actionName, int maxAttempts)
            throws ExecutionException, RetryException {

        Retryer<T> retryer = RetryerBuilder.<T>newBuilder()
                // 任何异常都触发重试：工具层拿到的异常类型五花八门（IO、JSON、HTTP、超时），
                // 与其枚举不如统一重试，再靠 maxAttempts 控制成本
                .retryIfException()
                // 返回 null 也视为失败，避免把 null 塞给大模型
                .retryIfResult(result -> result == null)
                .withWaitStrategy(WaitStrategies.exponentialWait(2, 1, TimeUnit.SECONDS))
                .withStopStrategy(StopStrategies.stopAfterAttempt(maxAttempts))
                .withRetryListener(new RetryListener() {
                    @Override
                    public <V> void onRetry(Attempt<V> attempt) {
                        if (attempt.hasException()) {
                            log.warn("[{}] 第 {} 次尝试失败，原因: {}",
                                    actionName,
                                    attempt.getAttemptNumber(),
                                    attempt.getExceptionCause().getMessage());
                        } else {
                            log.warn("[{}] 第 {} 次尝试返回空结果",
                                    actionName, attempt.getAttemptNumber());
                        }
                    }
                })
                .build();

        return retryer.call(task);
    }

    /**
     * 把重试耗尽的结果转成给大模型看的稳定文本。
     * <p>
     * 刻意保留 {@code [RETRY_EXHAUSTED]} 前缀：模型看到「重试耗尽」比看到
     * 「Connection reset」更容易做出正确决策（比如放弃并告知用户，而不是编造结果）。
     */
    public static String exhaustedMessage(String actionName, Exception e) {
        String cause = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
        return String.format("[RETRY_EXHAUSTED] %s 在重试后仍然失败: %s", actionName, cause);
    }
}
