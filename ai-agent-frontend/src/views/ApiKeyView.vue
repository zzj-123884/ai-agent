<template>
 <div class="apikey-page">
    <div class="apikey-panel">
      <header class="panel-head">
        <button class="back-btn" type="button" @click="$router.push('/')" aria-label="返回">←</button>
        <div class="head-text">
          <h1>API Key 管理</h1>
          <p class="head-sub">获取 Key 后即可用第三方应用接入 AI 服务，按调用次数计费</p>
        </div>
      </header>

      <button class="create-btn" type="button" @click="openDialog">获取 Key</button>

      <p v-if="errorMessage" class="error-msg">{{ errorMessage }}</p>

      <div v-if="dialogOpen" class="modal-mask" @click.self="onMaskClick">
        <div class="modal" role="dialog" aria-modal="true" aria-labelledby="key-dialog-title">
          <h2 id="key-dialog-title">{{ createdKey ? '请保存 Token' : '获取 Key' }}</h2>

          <template v-if="!createdKey">
            <label class="field">
              <span>名称</span>
              <input
                v-model.trim="newKeyName"
                class="name-input"
                type="text"
                placeholder="例如：我的小程序"
                maxlength="64"
                @keydown.enter="handleCreate"
              />
            </label>
            <label class="field">
              <span>配额</span>
              <input
                v-model.number="newKeyQuota"
                class="quota-input"
                type="number"
                min="1"
                placeholder="留空表示不限次数"
                @keydown.enter="handleCreate"
              />
            </label>
            <p v-if="dialogError" class="error-msg">{{ dialogError }}</p>
            <div class="modal-actions">
              <button class="act-btn" type="button" @click="closeDialog">取消</button>
              <button class="create-btn" type="button" :disabled="creating" @click="handleCreate">
                {{ creating ? '获取中…' : '确认获取' }}
              </button>
            </div>
          </template>

          <template v-else>
            <p class="created-title">关闭后无法再次查看，请先复制。</p>
            <div class="created-row">
              <code class="created-value">{{ createdKey.secretKey }}</code>
              <button class="copy-btn" type="button" @click="copySecret">复制</button>
            </div>
            <div class="modal-actions">
              <button class="create-btn" type="button" @click="closeDialog">我已保存</button>
            </div>
          </template>
        </div>
      </div>

      <!-- Key 列表 -->
      <section class="key-list">
        <p v-if="loading" class="list-hint">加载中…</p>
        <p v-else-if="keys.length === 0" class="list-hint">还没有 API Key，先获取一个吧</p>

        <article v-for="key in keys" :key="key.id" class="key-card">
          <div class="key-main">
            <div class="key-title">
              <span class="key-name">{{ key.name }}</span>
              <span class="key-status" :class="{ disabled: key.status !== 0 }">
                {{ key.status === 0 ? '正常' : '已禁用' }}
              </span>
            </div>
            <div class="key-meta">
              <span>已用 {{ key.usedQuota ?? 0 }}</span>
              <span>/</span>
              <span>{{ key.totalQuota == null ? '不限' : key.totalQuota }}</span>
            </div>
          </div>
          <div class="key-actions">
            <button class="act-btn" type="button" @click="toggleStatus(key)">
              {{ key.status === 0 ? '禁用' : '启用' }}
            </button>
            <button class="act-btn danger" type="button" @click="handleDelete(key)">删除</button>
          </div>
        </article>
      </section>

      <!-- 接入说明：用户最需要知道的就是这两个头怎么传 -->
      <section class="usage-box">
        <h2 class="usage-title">如何接入</h2>
        <p class="usage-desc">获取后得到一个 token，放在 Authorization 头里调用：</p>
        <pre class="usage-code">Authorization: Bearer sk-xxxxxxxx</pre>
        <p class="usage-desc">示例：</p>
        <pre class="usage-code">curl http://localhost:8123/api/v1/chat/completions \
  -H "Authorization: Bearer sk-xxx" \
  -H "Content-Type: application/json" \
  -d "{\"model\":\"medical\",\"messages\":[{\"role\":\"user\",\"content\":\"血糖高要注意什么\"}]}"</pre>
      </section>
    </div>
 </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { createApiKey, deleteApiKey, listApiKeys, updateApiKeyStatus } from '../api/apiKey'

const keys = ref([])
const loading = ref(false)
const creating = ref(false)
const errorMessage = ref('')
const newKeyName = ref('')
const newKeyQuota = ref(null)

// 新签发的 Key（含明文 secretKey），仅在本次会话中短暂展示
const dialogOpen = ref(false)
const dialogError = ref('')
const createdKey = ref(null)

function openDialog() {
  errorMessage.value = ''
  dialogError.value = ''
  createdKey.value = null
  newKeyName.value = ''
  newKeyQuota.value = null
  dialogOpen.value = true
}

function closeDialog() {
  if (creating.value) return
  dialogOpen.value = false
  createdKey.value = null
  dialogError.value = ''
}

function onMaskClick() {
  if (createdKey.value) return
  closeDialog()
}

onMounted(loadKeys)

async function loadKeys() {
  loading.value = true
  try {
    keys.value = (await listApiKeys()) || []
  } catch (error) {
    errorMessage.value = error?.message || '加载失败'
  } finally {
    loading.value = false
  }
}

async function handleCreate() {
  dialogError.value = ''
  if (!newKeyName.value) {
    dialogError.value = '请填写 Key 名称'
    return
  }

  creating.value = true
  try {
    createdKey.value = await createApiKey({
      name: newKeyName.value,
      totalQuota: newKeyQuota.value > 0 ? newKeyQuota.value : null,
    })
    newKeyName.value = ''
    newKeyQuota.value = null
    await loadKeys()
  } catch (error) {
    dialogError.value = error?.message || '获取失败'
  } finally {
    creating.value = false
  }
}

async function toggleStatus(key) {
  errorMessage.value = ''
  try {
    await updateApiKeyStatus(key.id, key.status === 0 ? 1 : 0)
    await loadKeys()
  } catch (error) {
    errorMessage.value = error?.message || '操作失败'
  }
}

async function handleDelete(key) {
  errorMessage.value = ''
  // 删除不可撤销，必须二次确认
  if (!window.confirm(`确定删除「${key.name}」？使用该 Key 的应用将立即失效。`)) {
    return
  }
  try {
    await deleteApiKey(key.id)
    await loadKeys()
  } catch (error) {
    errorMessage.value = error?.message || '删除失败'
  }
}

/**
 * 复制到剪贴板。
 *
 * navigator.clipboard 只在 HTTPS 或 localhost 下可用，
 * 因此保留 execCommand 作为降级方案，否则在 http 部署的测试环境里复制会静默失败。
 */
async function copySecret() {
  const text = createdKey.value?.secretKey
  if (!text) return
  try {
    if (navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(text)
    } else {
      const textarea = document.createElement('textarea')
      textarea.value = text
      textarea.style.position = 'fixed'
      textarea.style.opacity = '0'
      document.body.appendChild(textarea)
      textarea.select()
      document.execCommand('copy')
      document.body.removeChild(textarea)
    }
  } catch {
    errorMessage.value = '复制失败，请手动选中复制'
  }
}
</script>

<style scoped>
.apikey-page {
  display: flex;
  justify-content: center;
  flex: 1;
  padding: 24px;
}

.apikey-panel {
  width: 100%;
  max-width: 760px;
  padding: 28px 24px 32px;
  border: 1px solid rgba(30, 42, 38, 0.08);
  border-radius: 24px;
  background: var(--bg-panel);
  box-shadow: var(--shadow-sm);
}

.panel-head {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 24px;
}

.back-btn {
  width: 38px;
  height: 38px;
  flex-shrink: 0;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--bg-elevated);
  color: var(--text);
  cursor: pointer;
}

.back-btn:hover {
  border-color: var(--accent-cyan);
}

.head-text h1 {
  font-family: var(--font-display);
  font-size: 1.8rem;
  font-weight: 560;
  letter-spacing: -0.03em;
}

.head-sub {
  margin-top: 3px;
  font-size: 0.78rem;
  color: var(--text-muted);
}

.create-bar {
  display: none;
}

.modal-mask {
  position: fixed;
  inset: 0;
  z-index: 40;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: rgba(30, 42, 38, 0.35);
}

.modal {
  width: min(440px, 100%);
  padding: 22px 22px 18px;
  border-radius: 18px;
  background: var(--bg-panel);
  box-shadow: var(--shadow-md);
}

.modal h2 {
  font-family: var(--font-display);
  font-size: 1.45rem;
  font-weight: 560;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 14px;
  font-size: 0.82rem;
  color: var(--text-muted);
}

.modal .name-input,
.modal .quota-input {
  width: 100%;
  flex: none;
  height: 48px;
  min-height: 48px;
  padding: 0 14px;
  line-height: 48px;
}

.modal .quota-input {
  appearance: textfield;
  -moz-appearance: textfield;
}

.modal .quota-input::-webkit-outer-spin-button,
.modal .quota-input::-webkit-inner-spin-button {
  -webkit-appearance: none;
  margin: 0;
}

.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 18px;
}

.name-input,
.quota-input {
  height: 42px;
  padding: 0 12px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--bg-elevated);
  color: var(--text);
  outline: none;
}

.name-input {
  flex: 1;
  min-width: 180px;
}

.quota-input {
  width: 160px;
}

.name-input:focus,
.quota-input:focus {
  border-color: var(--accent-cyan);
}

.create-btn {
  height: 42px;
  padding: 0 20px;
  border: none;
  border-radius: var(--radius-sm);
  background: var(--accent-cyan);
  color: #fff;
  font-weight: 600;
  cursor: pointer;
}

.create-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.error-msg {
  margin-top: 12px;
  font-size: 0.82rem;
  color: var(--accent-rose);
}

.created-box {
  margin-top: 18px;
  padding: 16px;
  background: var(--accent-cyan-soft);
  border: 1px solid var(--accent-cyan);
  border-radius: var(--radius);
}

.created-title {
  font-size: 0.82rem;
  color: var(--accent-cyan);
  margin-bottom: 10px;
}

.created-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.created-label {
  font-size: 0.75rem;
  color: var(--text-muted);
}

.created-value {
  flex: 1;
  min-width: 200px;
  padding: 8px 10px;
  background: rgba(0, 0, 0, 0.35);
  border-radius: var(--radius-sm);
  font-family: var(--font-mono);
  font-size: 0.78rem;
  word-break: break-all;
}

.copy-btn {
  padding: 6px 14px;
  border: 1px solid var(--accent-cyan);
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--accent-cyan);
  font-size: 0.78rem;
  cursor: pointer;
}

.dismiss-btn {
  margin-top: 12px;
  padding: 0;
  border: none;
  background: none;
  color: var(--text-muted);
  font-size: 0.76rem;
  text-decoration: underline;
  cursor: pointer;
}

.key-list {
  margin-top: 26px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.list-hint {
  font-size: 0.84rem;
  color: var(--text-muted);
  text-align: center;
  padding: 24px 0;
}

.key-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 14px 16px;
  background: var(--bg-elevated);
  border: 1px solid var(--border);
  border-radius: var(--radius);
}

.key-title {
  display: flex;
  align-items: center;
  gap: 10px;
}

.key-name {
  font-size: 0.92rem;
  font-weight: 500;
}

.key-status {
  padding: 1px 7px;
  font-size: 0.68rem;
  color: var(--accent-cyan);
  background: var(--accent-cyan-soft);
  border-radius: 4px;
}

.key-status.disabled {
  color: var(--text-faint);
  background: var(--bg-elevated-hover);
}

.key-meta {
  margin-top: 5px;
  display: flex;
  gap: 6px;
  font-size: 0.76rem;
  color: var(--text-muted);
}

.key-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

.act-btn {
  padding: 6px 12px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--text);
  font-size: 0.78rem;
  cursor: pointer;
}

.act-btn:hover {
  border-color: var(--accent-cyan);
  color: var(--accent-cyan);
}

.act-btn.danger:hover {
  border-color: var(--accent-rose);
  color: var(--accent-rose);
}

.usage-box {
  margin-top: 32px;
  padding: 18px;
  background: var(--bg-elevated);
  border: 1px solid var(--border);
  border-radius: var(--radius);
}

.usage-title {
  font-size: 0.94rem;
  font-weight: 600;
  margin-bottom: 8px;
}

.usage-desc {
  font-size: 0.8rem;
  color: var(--text-muted);
  margin-bottom: 8px;
}

.usage-code {
  margin: 0 0 12px;
  padding: 12px;
  background: #e7efe9;
  border: 1px solid rgba(29, 107, 87, 0.16);
  border-radius: var(--radius-sm);
  font-family: var(--font-mono);
  font-size: 0.78rem;
  line-height: 1.65;
  color: #1e2a26;
  overflow-x: auto;
  white-space: pre-wrap;
  word-break: break-word;
}

@media (max-width: 640px) {
  .apikey-page {
    padding: 16px;
  }

  .key-card {
    flex-direction: column;
    align-items: stretch;
  }

  .key-actions {
    justify-content: flex-end;
  }
}
</style>
