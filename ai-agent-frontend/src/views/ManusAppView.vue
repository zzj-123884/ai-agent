<template>
  <ChatRoom
    title="AI 超级智能体"
    subtitle="多工具协作 · SSE 实时推理"
    theme="cyan"
    :messages="messages"
    :loading="loading"
    empty-hint="描述你的任务，超级智能体将逐步完成"
    placeholder="输入任务或问题…"
    @back="goHome"
    @send="handleSend"
    @stop="handleStop"
  />
</template>

<script setup>
import { onBeforeUnmount, ref } from 'vue'
import { useRouter } from 'vue-router'
import ChatRoom from '../components/ChatRoom.vue'
import { createSseConnection, stopAiTask, SseEventType } from '../api/sse'

const router = useRouter()
const messages = ref([])
const loading = ref(false)

// SSE 连接句柄。保存整对象而非只保存 close 函数，
// 因为停止时需要读取它上面挂的 taskId。
let connection = null

onBeforeUnmount(() => {
  connection?.close()
})

function goHome() {
  connection?.close()
  router.push('/')
}

/**
 * 用户点击「停止」。
 *
 * 顺序很关键：先通知后端置取消标志，再关闭 SSE 连接。
 * 反过来的话连接一断就失去了 taskId 来源，后端任务会继续烧 token。
 */
async function handleStop() {
  const taskId = connection?.taskId
  if (!taskId) {
    pushNotice('任务连接尚未就绪，请稍后重试停止。', 'error')
    return
  }
  const stopped = await stopAiTask(taskId)
  if (!stopped) {
    pushNotice('服务端未确认停止，任务仍在运行，请重试。', 'error')
    return
  }
  connection?.close()
  connection = null

  // 本地立即收敛 UI，不等后端回事件——用户点了停止就该马上看到反馈
  const last = messages.value[messages.value.length - 1]
  if (last?.streaming) {
    last.streaming = false
  }
  loading.value = false
  pushNotice('已停止生成')
}

function handleSend(text) {
  if (loading.value) return

  messages.value.push({ role: 'user', content: text })
  loading.value = true

  connection?.close()
  connection = createSseConnection(
    '/ai/manus/chat',
    { message: text },
    {
      onEvent: handleEvent,
      onError: () => {
        pushNotice('连接已断开或服务暂不可用，请稍后再试。', 'error')
        finishStream()
      },
    }
  )
}

/**
 * 按事件类型分发渲染。
 *
 * 这是本次前端改造的核心：后端把「思考」「工具调用」「工具结果」「回答」分开推送，
 * 前端不再是一坨纯文本，而是差异化的卡片与气泡。
 */
function handleEvent(event) {
  switch (event.type) {
    case SseEventType.STEP_START:
      messages.value.push({
        role: 'ai',
        kind: 'step',
        content: `第 ${event.step} 步`,
      })
      break

    case SseEventType.THINK:
      pushOrAppendThink(event.data)
      break

    case SseEventType.TOOL_CALL:
      messages.value.push({
        role: 'ai',
        kind: 'tool',
        toolState: 'call',
        tool: event.data?.tool || '未知工具',
        content: formatPayload(event.data?.arguments),
      })
      break

    case SseEventType.TOOL_RESULT:
      messages.value.push({
        role: 'ai',
        kind: 'tool',
        toolState: 'result',
        tool: event.data?.tool || '未知工具',
        content: formatPayload(event.data?.result),
      })
      break

    case SseEventType.ANSWER:
      pushOrAppendAnswer(event.data)
      break

    case SseEventType.DONE:
      finishStream()
      break

    case SseEventType.STOPPED:
      markStopped(event.data)
      break

    case SseEventType.ERROR:
      pushNotice(String(event.data ?? '执行出错'), 'error')
      finishStream()
      break

    default:
      // 未知类型不丢弃，降级为普通回答，避免后端新增事件时前端静默吞消息
      if (typeof event.data === 'string') {
        pushOrAppendAnswer(event.data)
      }
  }
}

/**
 * 思考内容：同一步骤内可能多次推送，追加到上一条 think 消息而不是新建。
 * 否则每一步会散落成好几条灰色小块，视觉上很碎。
 */
function pushOrAppendThink(text) {
  if (!text) return
  const last = messages.value[messages.value.length - 1]
  if (last?.kind === 'think') {
    last.content += `\n${text}`
    return
  }
  messages.value.push({
    role: 'ai',
    kind: 'think',
    content: text,
  })
}

/**
 * 最终回答：追加到上一条 answer 气泡，实现流式逐字效果。
 */
function pushOrAppendAnswer(text) {
  if (!text) return
  const last = messages.value[messages.value.length - 1]
  if (last?.kind !== 'answer' || last.role !== 'ai' || last.finished) {
    messages.value.push({
      role: 'ai',
      kind: 'answer',
      content: text,
      streaming: true,
    })
    return
  }
  last.content += text
  last.streaming = true
}

function pushNotice(text, kind = 'notice') {
  messages.value.push({
    role: 'ai',
    kind: kind === 'error' ? 'error' : 'answer',
    content: text,
  })
}

function markStopped(text) {
  const last = messages.value[messages.value.length - 1]
  if (last?.role === 'ai' && last.kind === 'answer') {
    last.streaming = false
    last.stopped = true
  } else {
    pushNotice(text || '已停止生成')
  }
  finishStream()
}

function finishStream() {
  // 收尾：关掉所有还挂着「输入中」光标的气泡，避免永远闪烁
  messages.value.forEach((msg) => {
    if (msg.streaming) {
      msg.streaming = false
      msg.finished = true
    }
  })
  loading.value = false
  connection?.close()
  connection = null
}

/**
 * 工具参数/结果可能是对象也可能是字符串，统一格式化后展示。
 */
function formatPayload(payload) {
  if (payload === undefined || payload === null) return ''
  if (typeof payload === 'string') return payload
  try {
    return JSON.stringify(payload, null, 2)
  } catch {
    return String(payload)
  }
}
</script>
