import request from './request'

/**
 * API Key 管理接口（开放平台接入与计费）。
 */

/**
 * 签发新的 API Key。
 *
 * 返回体中的 secretKey 是明文，仅此一次可见，务必提示用户立即保存。
 */
export function createApiKey(payload) {
  return request.post('/api-key/create', payload)
}

export function listApiKeys() {
  return request.get('/api-key/list')
}

export function updateApiKeyStatus(apiKeyId, status) {
  return request.post(
    `/api-key/status?apiKeyId=${encodeURIComponent(apiKeyId)}&status=${encodeURIComponent(status)}`
  )
}

export function deleteApiKey(apiKeyId) {
  return request.delete(`/api-key/${encodeURIComponent(apiKeyId)}`)
}
