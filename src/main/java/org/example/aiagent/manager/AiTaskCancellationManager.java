package org.example.aiagent.manager;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 运行中 AI 任务的取消注册表。
 * <p>
 * 为什么需要它：用户点「停止」时，浏览器关闭 EventSource 只是断开了 TCP 连接，
 * 后端那个正在跑 ReAct 循环、反复调用大模型的线程**毫不知情**，会一直跑到 maxSteps 耗尽。
 * 每次大模型调用都是真金白银，用户不想要的结果不该继续烧 token。
 * <p>
 * 设计取舍：
 * <ul>
 *   <li>用 {@link AtomicBoolean} 而非中断线程 —— 模型 SDK 的阻塞调用不一定响应 interrupt，
 *       而「标志位 + 循环里主动检查」是最可靠、最可控的做法。</li>
 *   <li>key 用 taskId 而非 sessionId —— 一个会话可能先后跑多个任务，
 *       用会话 id 会误停到新任务。</li>
 *   <li>内存存储 —— 单机部署足够；多实例部署时需换成 Redis 标志位，
 *       因为取消请求可能落到没有跑该任务的实例上。</li>
 * </ul>
 */
@Slf4j
@Component
public class AiTaskCancellationManager {

    private final Map<String, AtomicBoolean> cancelFlags = new ConcurrentHashMap<>();

    /**
     * 注册一个可取消任务。任务开始前调用。
     *
     * @param taskId 任务唯一标识
     */
    public void register(String taskId) {
        cancelFlags.put(taskId, new AtomicBoolean(false));
        log.debug("注册可取消任务: {}, 当前运行中任务数: {}", taskId, cancelFlags.size());
    }

    /**
     * 检查任务是否已被取消。在耗时循环的每一轮开头调用。
     *
     * @param taskId 任务唯一标识
     * @return true 表示应立刻停止执行
     */
    public boolean isCancelled(String taskId) {
        AtomicBoolean flag = cancelFlags.get(taskId);
        return flag != null && flag.get();
    }

    /**
     * 取消任务。由停止接口调用。
     * <p>
     * 注意：这里只置标志位就返回，不等待任务真正结束 ——
     * 接口要立即响应，真正的收敛交给任务线程自己在下一轮检查时完成。
     *
     * @param taskId 任务唯一标识
     * @return true 表示确实有运行中的任务被标记
     */
    public boolean cancel(String taskId) {
        AtomicBoolean flag = cancelFlags.get(taskId);
        if (flag == null) {
            log.debug("取消任务失败，任务不存在或已结束: {}", taskId);
            return false;
        }
        flag.set(true);
        log.info("已请求取消任务: {}", taskId);
        return true;
    }

    /**
     * 任务结束后清理，避免 map 无限增长导致内存泄漏。
     */
    public void unregister(String taskId) {
        cancelFlags.remove(taskId);
        log.debug("注销任务: {}, 当前运行中任务数: {}", taskId, cancelFlags.size());
    }

    /**
     * 当前运行中的任务数量，供监控 / 健康检查使用。
     */
    public int runningTaskCount() {
        return cancelFlags.size();
    }
}
