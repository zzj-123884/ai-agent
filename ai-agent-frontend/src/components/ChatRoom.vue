<template>
  <div class="chat-page">
    <div class="chat-room" :class="themeClass">
      <header class="chat-header">
        <button class="back-btn" type="button" @click="$emit('back')" aria-label="返回首页">
          ←
        </button>
        <div class="header-info">
          <h1>{{ title }}</h1>
          <p v-if="subtitle" class="subtitle">{{ subtitle }}</p>
        </div>
        <div v-if="chatId" class="chat-id" :title="chatId">
          <span class="chat-id-label">会话</span>
          <span class="chat-id-value">{{ shortChatId }}</span>
        </div>
      </header>

      <main ref="messageListRef" class="message-list">
        <div v-if="messages.length === 0" class="empty-state">
          <div class="empty-icon" aria-hidden="true">{{ theme === 'med' ? '✚' : '◈' }}</div>
          <p class="empty-hint">{{ emptyHint }}</p>
        </div>

        <div
          v-for="(msg, index) in messages"
          :key="index"
          class="message-row"
          :class="[msg.role, msg.kind || 'answer']"
        >
          <div class="avatar" aria-hidden="true">
            {{ msg.role === 'user' ? '我' : 'AI' }}
          </div>

          <!-- 步骤分隔条：把多个 Step 在视觉上切开，避免糊成一大团 -->
          <div v-if="msg.kind === 'step'" class="step-divider">{{ msg.content }}</div>

          <!-- 工具调用卡片：名字 + 参数 / 结果，可折叠 -->
          <div v-else-if="msg.kind === 'tool'" class="tool-card">
            <div class="tool-head">
              <span class="tool-badge">{{ msg.toolState === 'call' ? '调用' : '结果' }}</span>
              <span class="tool-name">{{ msg.tool }}</span>
            </div>
            <pre class="tool-body">{{ msg.content }}</pre>
          </div>

          <!-- 思考块：灰色斜体小字，与正式回答区分开 -->
          <div v-else-if="msg.kind === 'think'" class="bubble think-bubble">
            <span class="think-label">思考</span>
            <pre class="bubble-text">{{ msg.content }}</pre>
            <span v-if="msg.streaming" class="typing-cursor" aria-hidden="true">|</span>
          </div>

          <!-- 普通对话气泡（用户提问与最终回答） -->
          <div v-else class="bubble" :class="{ 'error-bubble': msg.kind === 'error' }">
            <pre class="bubble-text">{{ msg.content }}</pre>
            <span v-if="msg.streaming" class="typing-cursor" aria-hidden="true">|</span>
            <span v-if="msg.stopped" class="stopped-tag">已停止</span>
          </div>
        </div>
      </main>

      <footer class="chat-input-area">
        <div class="input-shell">
          <textarea
            ref="textareaRef"
            v-model="inputText"
            class="chat-input"
            :placeholder="placeholder"
            :disabled="loading"
            rows="1"
            @input="autoResize"
            @keydown.enter.exact.prevent="handleSend"
          />
          <!-- 生成中时把发送按钮换成停止按钮：同一个位置，用户不需要找 -->
          <button
            v-if="loading"
            class="send-btn stop-btn"
            type="button"
            @click="$emit('stop')"
          >
            停止
          </button>
          <button
            v-else
            class="send-btn"
            type="button"
            :disabled="!inputText.trim()"
            @click="handleSend"
          >
            发送
          </button>
        </div>
        <p class="input-tip">Enter 发送 · Shift+Enter 换行</p>
      </footer>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, ref, watch } from 'vue'

const props = defineProps({
  title: {
    type: String,
    required: true,
  },
  subtitle: {
    type: String,
    default: '',
  },
  chatId: {
    type: String,
    default: '',
  },
  theme: {
    type: String,
    default: 'med',
  },
  emptyHint: {
    type: String,
    default: '开始你的第一轮对话吧',
  },
  placeholder: {
    type: String,
    default: '输入消息，按 Enter 发送…',
  },
  messages: {
    type: Array,
    default: () => [],
  },
  loading: {
    type: Boolean,
    default: false,
  },
})

const emit = defineEmits(['back', 'send', 'stop'])

const inputText = ref('')
const messageListRef = ref(null)
const textareaRef = ref(null)

const themeClass = computed(() => `theme-${props.theme}`)
const shortChatId = computed(() => {
  if (!props.chatId) return ''
  return props.chatId.length > 16
    ? `${props.chatId.slice(0, 8)}…${props.chatId.slice(-4)}`
    : props.chatId
})

watch(
  () => props.messages,
  async () => {
    await nextTick()
    const el = messageListRef.value
    if (el) {
      el.scrollTop = el.scrollHeight
    }
  },
  { deep: true }
)

function autoResize() {
  const el = textareaRef.value
  if (!el) return
  el.style.height = 'auto'
  el.style.height = `${Math.min(el.scrollHeight, 140)}px`
}

function handleSend() {
  const text = inputText.value.trim()
  if (!text || props.loading) return
  emit('send', text)
  inputText.value = ''
  nextTick(() => {
    if (textareaRef.value) {
      textareaRef.value.style.height = 'auto'
    }
  })
}
</script>

<style scoped>
.chat-page {
  display: flex;
  justify-content: center;
  flex: 1;
  min-height: 0;
  width: 100%;
  padding:
    var(--safe-top)
    max(0px, var(--safe-right))
    0
    max(0px, var(--safe-left));
}

.chat-room {
  display: flex;
  flex-direction: column;
  width: 100%;
  max-width: var(--chat-max);
  height: 100%;
  min-height: 0;
  overflow: hidden;
  background: var(--bg-panel);
  border-left: 1px solid transparent;
  border-right: 1px solid transparent;
}

.theme-med {
  --theme-accent: var(--accent-neon);
  --theme-soft: var(--accent-gold-soft);
  --theme-glow: rgba(15, 118, 110, 0.12);
}

.theme-cyan {
  --theme-accent: var(--accent-ink);
  --theme-soft: rgba(47, 77, 104, 0.1);
  --theme-glow: rgba(47, 77, 104, 0.12);
}

/* —— Header —— */
.chat-header {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: var(--header-h);
  padding: 12px clamp(12px, 3vw, 20px);
  border-bottom: 1px solid var(--border);
  background: var(--bg-panel);
  backdrop-filter: var(--glass);
  -webkit-backdrop-filter: var(--glass);
  z-index: 2;
}

.back-btn {
  width: 40px;
  height: 40px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--bg-elevated);
  color: var(--text);
  cursor: pointer;
  font-size: 1.1rem;
  line-height: 1;
  transition:
    background var(--duration) var(--ease),
    border-color var(--duration) var(--ease),
    transform var(--duration) var(--ease);
}

.back-btn:hover {
  border-color: var(--theme-accent);
  background: var(--theme-soft);
}

.back-btn:active {
  transform: scale(0.96);
}

.header-info {
  flex: 1;
  min-width: 0;
}

.header-info h1 {
  font-family: var(--font-display);
  font-size: clamp(1.15rem, 2.8vw, 1.35rem);
  font-weight: 560;
  letter-spacing: 0.01em;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.subtitle {
  margin-top: 2px;
  font-size: clamp(0.72rem, 2vw, 0.8rem);
  color: var(--text-muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.chat-id {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 2px;
  padding: 6px 10px;
  border-radius: var(--radius-sm);
  background: var(--theme-soft);
  max-width: 140px;
}

.chat-id-label {
  font-size: 0.65rem;
  color: var(--text-muted);
  letter-spacing: 0.06em;
}

.chat-id-value {
  color: var(--theme-accent);
  font-size: 0.72rem;
  font-family: var(--font-mono);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 100%;
}

/* —— Messages —— */
.message-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
  background:
    linear-gradient(180deg, rgba(29, 107, 87, 0.05), transparent 120px),
    var(--bg-deep);
  padding: clamp(16px, 3vw, 24px) clamp(12px, 3vw, 20px);
  display: flex;
  flex-direction: column;
  gap: clamp(14px, 2.5vw, 18px);
  -webkit-overflow-scrolling: touch;
  overscroll-behavior: contain;
}

.empty-state {
  margin: auto;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 24px;
  text-align: center;
}

.empty-icon {
  width: 56px;
  height: 56px;
  border-radius: 16px;
  display: grid;
  place-items: center;
  font-size: 1.4rem;
  background: var(--theme-soft);
  color: var(--theme-accent);
  opacity: 0.9;
}

.empty-hint {
  color: var(--text-muted);
  font-size: clamp(0.88rem, 2.5vw, 0.98rem);
  max-width: 18em;
  line-height: 1.5;
}

.message-row {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  max-width: min(88%, 640px);
  animation: msg-in 0.35s var(--ease) both;
}

.message-row.user {
  align-self: flex-end;
  flex-direction: row-reverse;
}

.message-row.ai {
  align-self: flex-start;
}

.avatar {
  flex-shrink: 0;
  width: 34px;
  height: 34px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  font-size: 0.7rem;
  font-weight: 600;
}

.message-row.user .avatar {
  background: var(--user-bubble);
  color: var(--accent-cyan);
}

.message-row.ai .avatar {
  background: var(--theme-soft);
  color: var(--theme-accent);
}

.bubble {
  position: relative;
  padding: 11px 14px;
  border-radius: var(--radius);
  line-height: 1.55;
  word-break: break-word;
  font-size: clamp(0.9rem, 2.4vw, 0.95rem);
}

.message-row.user .bubble {
  background: var(--user-bubble);
  border-bottom-right-radius: 4px;
  color: var(--text);
}

.message-row.ai .bubble {
  background: var(--ai-bubble);
  border: 1px solid var(--border);
  border-bottom-left-radius: 4px;
}

.bubble-text {
  margin: 0;
  font-family: inherit;
  white-space: pre-wrap;
  word-break: break-word;
  overflow-wrap: anywhere;
}

.typing-cursor {
  display: inline-block;
  margin-left: 2px;
  color: var(--theme-accent);
  animation: blink 1s step-end infinite;
}

/* 步骤分隔条：一条细横线 + 小字，把每一步隔开 */
.step-divider {
  align-self: center;
  margin: 4px auto;
  padding: 3px 14px;
  font-size: 0.72rem;
  letter-spacing: 0.06em;
  color: var(--text-faint);
  background: var(--bg-elevated);
  border: 1px solid var(--border);
  border-radius: 999px;
}

/* 思考块：与正式回答区分——更小、灰色、左侧竖线 */
.think-bubble {
  background: transparent !important;
  border: none !important;
  border-left: 2px solid var(--border-strong) !important;
  border-radius: 0 !important;
  padding: 4px 0 4px 12px !important;
  font-size: 0.86rem;
  color: var(--text-muted);
  font-style: italic;
}

.think-label {
  display: inline-block;
  margin-right: 6px;
  padding: 1px 6px;
  font-size: 0.68rem;
  font-style: normal;
  letter-spacing: 0.05em;
  color: var(--text-muted);
  background: var(--bg-elevated);
  border: 1px solid var(--border);
  border-radius: 4px;
  vertical-align: middle;
}

/* 工具调用卡片 */
.tool-card {
  flex: 1;
  min-width: 0;
  padding: 10px 12px;
  background: var(--bg-elevated);
  border: 1px solid var(--border);
  border-left: 3px solid var(--theme-accent);
  border-radius: var(--radius-sm);
}

.tool-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.tool-badge {
  padding: 1px 7px;
  font-size: 0.68rem;
  letter-spacing: 0.05em;
  color: var(--theme-accent);
  background: var(--theme-soft);
  border-radius: 4px;
}

.tool-name {
  font-family: var(--font-mono);
  font-size: 0.8rem;
  color: var(--text);
}

.tool-body {
  margin: 0;
  max-height: 180px;
  overflow: auto;
  font-family: var(--font-mono);
  font-size: 0.75rem;
  line-height: 1.5;
  color: var(--text-muted);
  white-space: pre-wrap;
  word-break: break-word;
}

/* 错误气泡 */
.error-bubble {
  border-color: var(--accent-rose) !important;
  color: var(--accent-rose);
}

/* 「已停止」标记 */
.stopped-tag {
  display: inline-block;
  margin-left: 8px;
  padding: 1px 6px;
  font-size: 0.68rem;
  color: var(--text-faint);
  border: 1px solid var(--border);
  border-radius: 4px;
}

/* 停止按钮：与发送按钮同位，用暗红区分危险操作 */
.stop-btn {
  background: var(--accent-rose) !important;
}

@keyframes blink {
  50% {
    opacity: 0;
  }
}

@keyframes msg-in {
  from {
    opacity: 0;
    transform: translateY(8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* —— Input —— */
.chat-input-area {
  flex-shrink: 0;
  padding: 12px clamp(12px, 3vw, 20px) 12px;
  border-top: 1px solid var(--border);
  background: var(--bg-panel);
  backdrop-filter: var(--glass);
  -webkit-backdrop-filter: var(--glass);
}

.input-shell {
  display: flex;
  align-items: flex-end;
  gap: 10px;
}

.chat-input {
  flex: 1;
  min-height: 46px;
  max-height: 140px;
  padding: 12px 14px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--bg-elevated);
  color: var(--text);
  resize: none;
  outline: none;
  line-height: 1.45;
  transition: border-color var(--duration) var(--ease), background var(--duration) var(--ease);
}

.chat-input::placeholder {
  color: var(--text-faint);
}

.chat-input:focus {
  border-color: var(--theme-accent);
  background: var(--bg-elevated-hover);
}

.chat-input:disabled {
  opacity: 0.55;
}

.send-btn {
  flex-shrink: 0;
  min-width: 76px;
  height: 46px;
  padding: 0 18px;
  border: none;
  border-radius: var(--radius);
  background: var(--theme-accent);
  color: #fff;
  font-weight: 600;
  font-size: 0.92rem;
  cursor: pointer;
  transition:
    opacity var(--duration) var(--ease),
    transform var(--duration) var(--ease),
    filter var(--duration) var(--ease);
}

.send-btn:hover:not(:disabled) {
  filter: brightness(1.08);
  transform: translateY(-1px);
}

.send-btn:active:not(:disabled) {
  transform: scale(0.98);
}

.send-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.input-tip {
  margin-top: 8px;
  text-align: center;
  font-size: 0.7rem;
  color: var(--text-faint);
}

/* —— Tablet —— */
@media (min-width: 768px) {
  .chat-page {
    padding-top: max(10px, var(--safe-top));
    padding-bottom: 10px;
  }

  .chat-room {
    height: 100%;
    max-height: none;
    margin: 0;
    border: 1px solid var(--border-strong);
    border-radius: var(--radius-lg);
    overflow: hidden;
    box-shadow: var(--shadow-md);
    background: var(--bg-panel);
  }

  .message-row {
    max-width: 78%;
  }

  .avatar {
    width: 36px;
    height: 36px;
  }
}

/* —— Desktop —— */
@media (min-width: 1024px) {
  .chat-room {
    max-width: var(--chat-max);
  }

  .message-list {
    padding-left: 28px;
    padding-right: 28px;
  }

  .bubble {
    padding: 12px 16px;
  }
}

/* —— Mobile tight —— */
@media (max-width: 640px) {
  .chat-id {
    display: none;
  }

  .message-row {
    max-width: 92%;
  }

  .input-tip {
    display: none;
  }

  .send-btn {
    min-width: 68px;
    padding: 0 14px;
  }
}

@media (max-width: 380px) {
  .avatar {
    width: 30px;
    height: 30px;
    font-size: 0.65rem;
  }

  .message-row {
    gap: 8px;
  }
}

/* Touch: slightly larger tap targets */
@media (hover: none) and (pointer: coarse) {
  .back-btn,
  .send-btn {
    min-height: 44px;
  }

  .send-btn {
    height: 48px;
  }

  .chat-input {
    min-height: 48px;
  }
}
</style>
