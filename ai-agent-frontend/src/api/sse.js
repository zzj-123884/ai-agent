import { API_BASE } from './request'

/**
 * SSE 事件类型常量，与后端 org.example.aiagent.sse.SseEventType 一一对应。
 * 用常量而不是裸字符串：拼错时 JS 不会报错，只会静默走进 default 分支，很难排查。
 */
export const SseEventType = {
  STEP_START: 'STEP_START',
  THINK: 'THINK',
  TOOL_CALL: 'TOOL_CALL',
  TOOL_RESULT: 'TOOL_RESULT',
  ANSWER: 'ANSWER',
  DONE: 'DONE',
  STOPPED: 'STOPPED',
  ERROR: 'ERROR',
}

/**
 * 通过 EventSource 订阅 SSE 流。
 *
 * @param {string} path 接口路径，如 /ai/manus/chat
 * @param {Record<string, any>} params 查询参数
 * @param {{
 *   onEvent?: (event: { type: string, step: number|null, data: any, timestamp: number }) => void
 *   onMessage?: (data: string) => void
 *   onError?: (error: Event) => void
 *   onOpen?: () => void
 * }} handlers
 * @returns {{ close: () => void, taskId: string | null }}
 */
export function createSseConnection(path, params, handlers) {
  const query = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null) {
      query.append(key, String(value))
    }
  })

  const url = `${API_BASE}${path}?${query.toString()}`

  // 记录后端下发的 taskId。它来自首个事件，用于后续「停止生成」调用。
  let taskId = null
  let closed = false

  const eventSource = new EventSource(url)

  eventSource.onopen = () => {
    handlers.onOpen?.()
  }

  eventSource.onmessage = (event) => {
    if (event.data === undefined || event.data === null) return

    // 后端推送的是 JSON 事件。若解析失败说明是旧格式或代理篡改，
    // 退化为纯文本处理，保证不会因为一个解析异常导致整个聊天不可用。
    let parsed
    try {
      parsed = JSON.parse(event.data)
    } catch {
      handlers.onMessage?.(event.data)
      return
    }

    // 兼容两种结构：标准事件对象 {type, step, data} 与裸文本
    if (parsed && typeof parsed === 'object' && typeof parsed.type === 'string') {
      if (typeof parsed.taskId === 'string') {
        taskId = parsed.taskId
      }
      handlers.onEvent?.(parsed)
      // 同时回调 onMessage 以兼容只关心文本的旧调用方
      if (typeof parsed.data === 'string') {
        handlers.onMessage?.(parsed.data)
      }
      return
    }

    handlers.onMessage?.(event.data)
  }

  eventSource.onerror = () => {
    if (closed) return
    handlers.onError?.(new Error('SSE 连接已断开'))
    // 流结束后浏览器会触发 error，主动关闭避免无限重连
    eventSource.close()
    closed = true
  }

  return {
    close: () => {
      closed = true
      eventSource.close()
    },
    /** 供调用方在收到事件后读取最新的 taskId */
    get taskId() {
      return taskId
    },
  }
}

/**
 * 请求后端停止正在生成的任务。
 *
 * 先拿到 taskId 再调用；如果 taskId 为空说明任务尚未真正开始，直接忽略。
 *
 * @param {string | null} taskId
 * @returns {Promise<boolean>} 是否成功通知到服务端
 */
export async function stopAiTask(taskId) {
  if (!taskId) return false
  try {
    const response = await fetch(
      `${API_BASE}/ai/task/stop?taskId=${encodeURIComponent(taskId)}`,
      {
        method: 'POST',
        // 带上凭证，否则 session 登录态不会随请求发送，
        // 拦截器会直接返回未登录错误
        credentials: 'include',
      }
    )
    if (!response.ok) return false
    const body = await response.json()
    return body?.code === 0 && body?.data === true
  } catch {
    return false
  }
}

/**
 * 生成会话 ID。
 */
export function generateChatId() {
  return `chat_${Date.now()}_${Math.random().toString(36).slice(2, 10)}`
}
