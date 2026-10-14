<template>
  <ChatRoom
    title="AI 医学助手"
    subtitle="MedlinePlus 知识库 · SSE 实时对话"
    theme="med"
    :chat-id="chatId"
    :messages="messages"
    :loading="loading"
    empty-hint="描述症状或健康问题，助手会先检索 MedlinePlus 再回答"
    placeholder="例如：血糖高要注意什么…"
    @back="goHome"
    @send="handleSend"
    @stop="handleStop"
  />
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import ChatRoom from '../components/ChatRoom.vue'
import { createSseConnection, stopAiTask, generateChatId } from '../api/sse'

const router = useRouter()
const chatId = ref('')
const messages = ref([])
const loading = ref(false)

// 保存整个连接句柄（而非仅 close 函数），停止时需要读 taskId
let connection = null

// 当前回答气泡的下标。流式分片要追加到同一个气泡，
// 用下标而非「取最后一条」：中间会插入错误提示等消息，取最后一条会追加错位置。
let currentAnswerIndex = -1

onMounted(() => {
  chatId.value = generateChatId()
})

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
 * 先通知后端置取消标志，再断开连接——否则连接一断就丢失了 taskId。
 */
async function handleStop() {
  const taskId = connection?.taskId
  if (taskId) {
    await stopAiTask(taskId)
  }
  connection?.close()
  connection = null

  if (messages.value[currentAnswerIndex]) {
    messages.value[currentAnswerIndex].streaming = false
    messages.value[currentAnswerIndex].stopped = true
  }
  loading.value = false
}

function handleSend(text) {
  if (loading.value) return

  messages.value.push({ role: 'user', content: text })
  messages.value.push({ role: 'ai', kind: 'answer', content: '', streaming: true })
  currentAnswerIndex = messages.value.length - 1
  loading.value = true

  connection?.close()
  connection = createSseConnection(
    '/ai/love_app/chat/sse',
    { message: text, chatId: chatId.value },
    {
      // 恋爱大师后端是 Flux<String> 纯文本流，不走 SseEvent 协议，
      // 因此这里用 onMessage 而不是 onEvent
      onMessage: (data) => {
        if (data === '[DONE]') {
          finishStream()
          return
        }
        const target = messages.value[currentAnswerIndex]
        if (target) {
          target.content += data
        }
      },
      onError: () => {
        const target = messages.value[currentAnswerIndex]
        if (target && !target.content) {
          target.content = '连接已断开或服务暂不可用，请稍后再试。'
          target.kind = 'error'
        }
        finishStream()
      },
    }
  )
}

function finishStream() {
  const target = messages.value[currentAnswerIndex]
  if (target) {
    target.streaming = false
  }
  loading.value = false
  connection?.close()
  connection = null
}
</script>
