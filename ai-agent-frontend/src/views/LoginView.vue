<template>
 <div class="auth-page">
    <div class="auth-card">
      <button class="back-btn" type="button" @click="goHome" aria-label="返回首页">←</button>

      <header class="auth-header">
        <h1>{{ isRegister ? '注册账号' : '登录' }}</h1>
        <p class="auth-sub">登录后即可使用 AI 医学助手与超级智能体</p>
      </header>

      <form class="auth-form" @submit.prevent="submit">
        <label class="field">
          <span class="field-label">账号</span>
          <input
            v-model.trim="form.userAccount"
            type="text"
            class="field-input"
            placeholder="4-32 位，字母或数字"
            autocomplete="username"
          />
        </label>

        <label class="field">
          <span class="field-label">密码</span>
          <input
            v-model="form.userPassword"
            type="password"
            class="field-input"
            placeholder="至少 8 位"
            autocomplete="current-password"
          />
        </label>

        <label v-if="isRegister" class="field">
          <span class="field-label">确认密码</span>
          <input
            v-model="form.checkPassword"
            type="password"
            class="field-input"
            placeholder="再次输入密码"
            autocomplete="new-password"
          />
        </label>

        <p v-if="errorMessage" class="error-msg">{{ errorMessage }}</p>
        <p v-if="successMessage" class="success-msg">{{ successMessage }}</p>

        <button class="submit-btn" type="submit" :disabled="submitting">
          {{ submitting ? '处理中…' : isRegister ? '注册' : '登录' }}
        </button>
      </form>

      <footer class="auth-footer">
        <button class="link-btn" type="button" @click="toggleMode">
          {{ isRegister ? '已有账号？去登录' : '没有账号？去注册' }}
        </button>
      </footer>
    </div>
 </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { login as loginApi, register as registerApi } from '../api/user'
import { resetAuthCache } from '../router'

const router = useRouter()

const isRegister = ref(false)
const submitting = ref(false)
const errorMessage = ref('')
const successMessage = ref('')

const form = reactive({
  userAccount: '',
  userPassword: '',
  checkPassword: '',
})

function toggleMode() {
  isRegister.value = !isRegister.value
  errorMessage.value = ''
  successMessage.value = ''
}

function goHome() {
  router.push('/')
}

/**
 * 前端做基础校验只为减少无意义的请求，
 * 真正的规则以服务端为准（前端校验可被绕过）。
 */
function validate() {
  if (!form.userAccount || form.userAccount.length < 4) {
    errorMessage.value = '账号至少 4 位'
    return false
  }
  if (!form.userPassword || form.userPassword.length < 8) {
    errorMessage.value = '密码至少 8 位'
    return false
  }
  if (isRegister.value && form.userPassword !== form.checkPassword) {
    errorMessage.value = '两次输入的密码不一致'
    return false
  }
  return true
}

async function submit() {
  errorMessage.value = ''
  successMessage.value = ''
  if (!validate()) return

  submitting.value = true
  try {
    if (isRegister.value) {
      await registerApi({
        userAccount: form.userAccount,
        userPassword: form.userPassword,
        checkPassword: form.checkPassword,
      })
      // 注册成功后自动切到登录态，省去用户再点一次
      isRegister.value = false
      successMessage.value = '注册成功，请登录'
      form.userPassword = ''
      form.checkPassword = ''
      return
    }

    await loginApi({
      userAccount: form.userAccount,
      userPassword: form.userPassword,
    })
    // 守卫只在第一次进站时向后端确认登录态。不清掉的话，
    // 这里跳首页会被仍为 false 的缓存立刻送回登录页。
    resetAuthCache()
    const redirect = router.currentRoute.value.query.redirect
    router.push(typeof redirect === 'string' ? redirect : '/')
  } catch (error) {
    errorMessage.value = error?.message || '操作失败，请稍后重试'
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.auth-page {
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 1;
  padding: 24px;
}

.auth-card {
  position: relative;
  width: 100%;
  max-width: 380px;
  padding: 32px 28px 24px;
  background: var(--bg-panel);
  border: 1px solid var(--border-strong);
  border-radius: var(--radius-lg);
  backdrop-filter: var(--glass);
  -webkit-backdrop-filter: var(--glass);
  box-shadow: var(--shadow-md);
}

.back-btn {
  position: absolute;
  top: 14px;
  left: 14px;
  width: 34px;
  height: 34px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--bg-elevated);
  color: var(--text);
  cursor: pointer;
}

.back-btn:hover {
  border-color: var(--accent-cyan);
}

.auth-header {
  margin-bottom: 22px;
  text-align: center;
}

.auth-header h1 {
  font-family: var(--font-display);
  font-size: 2rem;
  font-weight: 560;
  letter-spacing: -0.03em;
}

.auth-sub {
  margin-top: 6px;
  font-size: 0.8rem;
  color: var(--text-muted);
}

.auth-form {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.field-label {
  font-size: 0.78rem;
  color: var(--text-muted);
}

.field-input {
  height: 42px;
  padding: 0 12px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--bg-elev);
  color: var(--text);
  outline: none;
  transition: border-color var(--duration) var(--ease);
}

.field-input:focus {
  border-color: var(--accent-cyan);
}

.error-msg {
  margin: 0;
  font-size: 0.8rem;
  color: var(--accent-rose);
}

.success-msg {
  margin: 0;
  font-size: 0.8rem;
  color: var(--accent-cyan);
}

.submit-btn {
  height: 44px;
  margin-top: 4px;
  border: none;
  border-radius: var(--radius-sm);
  background: var(--accent-cyan);
  color: #fff;
  font-weight: 600;
  cursor: pointer;
  transition: filter var(--duration) var(--ease);
}

.submit-btn:hover:not(:disabled) {
  filter: brightness(1.08);
}

.submit-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.auth-footer {
  margin-top: 16px;
  text-align: center;
}

.link-btn {
  border: none;
  background: none;
  color: var(--text-muted);
  font-size: 0.8rem;
  cursor: pointer;
  text-decoration: underline;
}

.link-btn:hover {
  color: var(--accent-cyan);
}
</style>
