import request from './request'

/**
 * 用户相关接口。
 *
 * 注意：所有请求都依赖 session cookie，request 实例已开启 withCredentials。
 */

export function register(payload) {
  return request.post('/user/register', payload)
}

export function login(payload) {
  return request.post('/user/login', payload)
}

export function logout() {
  return request.post('/user/logout')
}

/**
 * 获取当前登录用户。
 *
 * 未登录时后端返回 10100，这里转成 null 而不是抛错——
 * 调用方（路由守卫）需要的是「有没有登录」这个布尔判断，
 * 用异常表达会导致每次启动都打一堆错误日志。
 */
export async function getLoginUser() {
  try {
    return await request.get('/user/get/login')
  } catch (error) {
    if (error?.notLogin) {
      return null
    }
    throw error
  }
}
