import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'
import MedicalView from '../views/MedicalView.vue'
import ManusAppView from '../views/ManusAppView.vue'
import LoginView from '../views/LoginView.vue'
import ApiKeyView from '../views/ApiKeyView.vue'
import { applySeo, injectJsonLd } from '../utils/seo'
import { getLoginUser } from '../api/user'

const SITE_NAME = 'AI Agent'

const routes = [
  {
    path: '/',
    name: 'home',
    component: HomeView,
    meta: {
      title: `${SITE_NAME} 应用中心`,
      description:
        'AI Agent 应用中心：医学助手与超级智能体，支持实时流式对话。',
      keywords: 'AI Agent,人工智能,智能体,SSE,流式对话,医学助手,超级智能体,Vue3',
      jsonLd: {
        '@context': 'https://schema.org',
        '@type': 'WebSite',
        name: SITE_NAME,
        description: 'AI Agent 应用中心 — 医学助手与超级智能体',
        potentialAction: {
          '@type': 'SearchAction',
          target: '/?q={search_term_string}',
          'query-input': 'required name=search_term_string',
        },
      },
    },
  },
  {
    path: '/medical',
    name: 'medical',
    component: MedicalView,
    meta: {
      title: `AI 医学助手 | ${SITE_NAME}`,
      description:
        'AI 医学助手：基于 MedlinePlus 知识库的健康咨询，SSE 实时流式回复，并附带资料来源。',
      keywords: 'AI医学助手,健康咨询,MedlinePlus,SSE,流式对话',
      jsonLd: {
        '@context': 'https://schema.org',
        '@type': 'WebApplication',
        name: 'AI 医学助手',
        applicationCategory: 'HealthApplication',
        operatingSystem: 'Web',
        description: '基于 MedlinePlus 知识库的健康咨询，支持实时流式对话。',
      },
    },
  },
  {
    path: '/manus-app',
    name: 'manus-app',
    component: ManusAppView,
    meta: {
      title: `AI 超级智能体 | ${SITE_NAME}`,
      description:
        'AI 超级智能体：多工具协作、分步推理、任务执行，SSE 实时展示每个 Step 的执行结果。',
      keywords: 'AI超级智能体,Agent,工具调用,分步推理,SSE,任务执行,Manus',
      jsonLd: {
        '@context': 'https://schema.org',
        '@type': 'WebApplication',
        name: 'AI 超级智能体',
        applicationCategory: 'BusinessApplication',
        operatingSystem: 'Web',
        description: '多工具协作的超级智能体，分步展示推理与执行过程。',
      },
    },
  },
  {
    path: '/login',
    name: 'login',
    component: LoginView,
    // 公开页：路由守卫会跳过对它的登录检查，否则会无限重定向
    meta: { public: true, title: `登录 | ${SITE_NAME}` },
  },
  {
    path: '/api-key',
    name: 'api-key',
    component: ApiKeyView,
    meta: {
      title: `API Key 管理 | ${SITE_NAME}`,
      description: '管理用于第三方接入的 API Key，支持配额控制与启用禁用。',
    },
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior() {
    return { top: 0 }
  },
})

router.afterEach((to) => {
  const meta = to.meta || {}
  applySeo({
    title: meta.title,
    description: meta.description,
    keywords: meta.keywords,
  })
  if (meta.jsonLd) {
    injectJsonLd(meta.jsonLd)
  }
})

/**
 * 登录守卫。
 *
 * 设计要点：
 *  - 后端才是真正的防线（拦截器），这里只是避免用户进入一个必然报错的页面；
 *  - 登录态只在首次导航时向后端确认一次，结果缓存起来，
 *    否则每次路由跳转都发请求，既慢又浪费；
 *  - 未登录时带上 redirect 参数，登录后可回到原本想去的页面。
 */
let loginChecked = false
let loggedIn = false

router.beforeEach(async (to) => {
  if (to.meta?.public) return true

  if (!loginChecked) {
    try {
      loggedIn = Boolean(await getLoginUser())
    } catch {
      loggedIn = false
    }
    loginChecked = true
  }

  if (!loggedIn) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  return true
})

/**
 * 登录/登出后重置缓存，让下一次导航重新探测登录态。
 */
export function resetAuthCache() {
  loginChecked = false
  loggedIn = false
}

export default router
