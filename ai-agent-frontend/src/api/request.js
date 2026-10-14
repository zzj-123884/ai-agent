import axios from 'axios'

/**
 * 开发环境走 Vite 代理（/api -> http://localhost:8123），避免 SSE 跨域。
 * 生产环境由 nginx 反代 /api 到后端容器，同样是同源。
 */
export const API_BASE = '/api'

/**
 * 未登录错误码，与后端 ErrorCode.NOT_LOGIN_ERROR 保持一致。
 */
export const CODE_NOT_LOGIN = 10100

const request = axios.create({
  baseURL: API_BASE,
  timeout: 60000,
  // 携带 session cookie。不加这个，跨端口调试时登录态不会带上，
  // 所有需要登录的接口都会返回 10100。
  withCredentials: true,
})

/**
 * 响应拦截器。
 *
 * 后端已统一返回 { code, message, data }，这里把它「拆包」：
 *  - code === 0  -> 直接把 data 交给调用方，业务代码不必每次写 res.data
 *  - code !== 0  -> 抛出带 message 的错误，由调用方处理
 *
 * 同时为未登录错误打上 notLogin 标记，供路由守卫精确识别，
 * 而不用去解析错误文案（文案会改，错误码不会）。
 */
request.interceptors.response.use(
  (response) => {
    const body = response.data

    // 非标准响应（如直接返回文件流）原样透传
    if (!body || typeof body !== 'object' || !('code' in body)) {
      return body
    }

    if (body.code === 0) {
      return body.data
    }

    const error = new Error(body.message || '请求失败')
    error.code = body.code
    if (body.code === CODE_NOT_LOGIN) {
      error.notLogin = true
    }
    return Promise.reject(error)
  },
  (error) => {
    // 网络层错误（超时、断网、5xx）统一包装成带中文提示的 Error
    const message =
      error.code === 'ECONNABORTED'
        ? '请求超时，请稍后重试'
        : error.response?.status === 404
          ? '接口不存在'
          : '网络异常，请检查连接'
    const wrapped = new Error(message)
    wrapped.cause = error
    return Promise.reject(wrapped)
  }
)

export default request
