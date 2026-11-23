const TOKEN_KEY = 'as.access'
const REFRESH_KEY = 'as.refresh'
const USER_KEY = 'as.user'

<<<<<<< HEAD
// 存储不可用（隐私模式/配额满/企业策略禁用）时降级为内存态：
// localStorage.setItem 直接抛错会让登录流程以无关的 TypeError 失败
const memoryStore = new Map()

function safeGet(key) {
  try {
    return localStorage.getItem(key)
  } catch {
    return memoryStore.get(key) ?? null
  }
}

function safeSet(key, value) {
  try {
    localStorage.setItem(key, value)
  } catch {
    memoryStore.set(key, value)
  }
}

function safeRemove(key) {
  try {
    localStorage.removeItem(key)
  } catch {
    memoryStore.delete(key)
  }
}

export const tokenStore = {
  get access() {
    return safeGet(TOKEN_KEY)
  },
  get refresh() {
    return safeGet(REFRESH_KEY)
  },
  get user() {
    try {
      return JSON.parse(safeGet(USER_KEY))
=======
export const tokenStore = {
  get access() {
    return localStorage.getItem(TOKEN_KEY)
  },
  get refresh() {
    return localStorage.getItem(REFRESH_KEY)
  },
  get user() {
    try {
      return JSON.parse(localStorage.getItem(USER_KEY))
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4
    } catch {
      return null
    }
  },
  save({ accessToken, refreshToken, user }) {
<<<<<<< HEAD
    if (accessToken) safeSet(TOKEN_KEY, accessToken)
    if (refreshToken) safeSet(REFRESH_KEY, refreshToken)
    if (user) safeSet(USER_KEY, JSON.stringify(user))
    // 新的登录态意味着可以再次广播过期事件
    expiredNotified = false
  },
  clear() {
    ;[TOKEN_KEY, REFRESH_KEY, USER_KEY].forEach(safeRemove)
  },
}

// 认证端点（登录/注册/刷新/验证码）不携带旧令牌：用户带着过期令牌回来重新登录时，
// 登录请求若附上过期 Authorization 头会被服务端拒绝，造成"永远登录不上"的死锁。
// /auth/me 需要令牌换当前用户，不在此列。
function isAuthEndpoint(path) {
  return path.startsWith('/auth/') && !path.startsWith('/auth/me')
}

async function rawRequest(path, { method = 'GET', body, isForm = false, signal, timeoutMs = 30000 } = {}) {
  const headers = {}
  const token = tokenStore.access
  if (token && !isAuthEndpoint(path)) headers.Authorization = `Bearer ${token}`
  if (body && !isForm) headers['Content-Type'] = 'application/json'
  // /agent-api 开头走 Vite 代理直连 Python Agent，其余一律补 /api 前缀走 Java 后端
  const url = path.startsWith('/agent-api')
    ? path
    : `/api${path.startsWith('/') ? path : `/${path}`}`
  return fetch(url, {
    method,
    headers,
    body: isForm ? body : body ? JSON.stringify(body) : undefined,
    // 默认 30s 超时防止请求永久挂起（后端黑洞时按钮永远转圈）；
    // 慢请求（上传/原文下载）由调用方放宽 timeoutMs，SSE 用调用方传入的 signal
    signal: signal ?? AbortSignal.timeout(timeoutMs),
  })
}

// auth:expired 在复位前只广播一次：并发多个请求同时 401 且刷新失败时，
// 避免连环弹出"登录已过期"并多次跳转。tokenStore.save（新登录态）负责复位
let expiredNotified = false

function notifyAuthExpired() {
  if (expiredNotified) return
  expiredNotified = true
  window.dispatchEvent(new CustomEvent('auth:expired'))
}

// 单飞刷新：并发多个 401 时只发一次刷新请求，共享同一个进行中的 Promise。
// 返回 { ok } 或 { ok: false, retryable }：
// retryable=true 表示刷新端点 5xx/网络抖动（服务端故障 ≠ 令牌失效），调用方不应强制登出
let refreshInFlight = null

async function tryRefresh() {
  const refreshToken = tokenStore.refresh
  if (!refreshToken) return { ok: false, retryable: false }
  if (refreshInFlight) return refreshInFlight
  refreshInFlight = (async () => {
    try {
      const res = await fetch('/api/auth/refresh', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken }),
        signal: AbortSignal.timeout(15000),
      })
      if (res.status >= 500) return { ok: false, retryable: true }
      if (!res.ok) return { ok: false, retryable: false }
      const payload = await res.json()
      if (payload.code !== 200) return { ok: false, retryable: false }
      tokenStore.save(payload.data)
      return { ok: true }
    } catch {
      return { ok: false, retryable: true }
    } finally {
      refreshInFlight = null
    }
  })()
  return refreshInFlight
}

/** 401 刷新失败后的统一处置：不可重试 → 清理登出；可重试（服务端故障）→ 保留登录态抛错 */
async function handleRefreshFailure() {
  const r = await tryRefresh()
  if (r.ok) return true
  if (!r.retryable) {
    tokenStore.clear()
    notifyAuthExpired()
    throw new Error('登录已过期，请重新登录')
  }
  throw new Error('网络服务暂时不可用，请稍后重试')
=======
    if (accessToken) localStorage.setItem(TOKEN_KEY, accessToken)
    if (refreshToken) localStorage.setItem(REFRESH_KEY, refreshToken)
    if (user) localStorage.setItem(USER_KEY, JSON.stringify(user))
  },
  clear() {
    ;[TOKEN_KEY, REFRESH_KEY, USER_KEY].forEach((k) => localStorage.removeItem(k))
  },
}

async function rawRequest(path, { method = 'GET', body, isForm = false } = {}) {
  const headers = {}
  const token = tokenStore.access
  if (token) headers.Authorization = `Bearer ${token}`
  if (body && !isForm) headers['Content-Type'] = 'application/json'
  // 以 / 开头的绝对路径直连（如 /agent-api → Python Agent 服务），其余走 Java 后端
  return fetch(path.startsWith('/') ? path : `/api${path}`, {
    method,
    headers,
    body: isForm ? body : body ? JSON.stringify(body) : undefined,
  })
}

async function tryRefresh() {
  const refreshToken = tokenStore.refresh
  if (!refreshToken) return false
  try {
    const res = await fetch('/api/auth/refresh', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken }),
    })
    if (!res.ok) return false
    const payload = await res.json()
    if (payload.code !== 200) return false
    tokenStore.save(payload.data)
    return true
  } catch {
    return false
  }
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4
}

export async function request(path, options, _retried = false) {
  const res = await rawRequest(path, options)
<<<<<<< HEAD
  // 认证端点的 401 是真实业务错误（如"邮箱或密码错误"），
  // 绝不能触发刷新重试——否则错误信息会被"登录已过期"掩盖
  if (res.status === 401 && !_retried && !isAuthEndpoint(path)) {
    if (await handleRefreshFailure()) return request(path, options, true)
=======
  if (res.status === 401 && !_retried) {
    if (await tryRefresh()) return request(path, options, true)
    tokenStore.clear()
    window.dispatchEvent(new CustomEvent('auth:expired'))
    throw new Error('登录已过期，请重新登录')
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4
  }
  const payload = await res.json().catch(() => null)
  if (!res.ok || !payload || payload.code !== 200) {
    throw new Error(payload?.message || `请求失败（${res.status}）`)
  }
  return payload.data
}

<<<<<<< HEAD
/** 二进制响应（如原文 PDF）。错误仍按 JSON 解析，401 同样自动续期重试。 */
async function requestBlob(path, _retried = false) {
  const res = await rawRequest(path, { timeoutMs: 120000 })
  if (res.status === 401 && !_retried) {
    if (await handleRefreshFailure()) return requestBlob(path, true)
  }
  if (!res.ok) {
    const payload = await res.json().catch(() => null)
    throw new Error(payload?.message || `请求失败（${res.status}）`)
  }
  // 用 arrayBuffer 而非 res.blob()：Chrome 的 blob() 会把大文件落盘到 blob 存储，
  // C 盘空间紧张时写入失败，fetch 直接抛 "Failed to fetch"（后端实际已完整返回）
  const buf = await res.arrayBuffer()
  return new Blob([buf], { type: res.headers.get('content-type') || 'application/pdf' })
}

async function agentChatRaw(conversationId, question, literatureId, signal) {
=======
async function agentChatRaw(conversationId, question, signal) {
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4
  const headers = { 'Content-Type': 'application/json' }
  const token = tokenStore.access
  if (token) headers.Authorization = `Bearer ${token}`
  return fetch('/agent-api/agent/chat', {
    method: 'POST',
    headers,
<<<<<<< HEAD
    body: JSON.stringify({ conversationId, question, literatureId }),
=======
    body: JSON.stringify({ conversationId, question }),
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4
    signal,
  })
}

/**
 * Agent 流式对话（SSE）。
 * 事件：start（会话 id）→ step*（执行轨迹）→ token*（流式回答）→ final | error
<<<<<<< HEAD
 * literatureId：研究工作区精读的文献 id，服务端据此注入"优先引用该论文原文"上下文。
 */
export async function agentChat({ conversationId, question, literatureId, onEvent, signal }) {
  let res = await agentChatRaw(conversationId, question, literatureId, signal)
  if (res.status === 401) {
    // 刷新失败（refresh 过期/丢失）→ 清令牌广播 auth:expired；
    // 刷新端点 5xx/网络抖动 → 保留登录态抛可重试错误，不把用户登出
    if (await handleRefreshFailure()) {
      res = await agentChatRaw(conversationId, question, literatureId, signal)
      if (res.status === 401) {
        tokenStore.clear()
        notifyAuthExpired()
        throw new Error('登录已过期，请重新登录')
      }
    }
=======
 */
export async function agentChat({ conversationId, question, onEvent, signal }) {
  let res = await agentChatRaw(conversationId, question, signal)
  if (res.status === 401 && (await tryRefresh())) {
    res = await agentChatRaw(conversationId, question, signal)
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4
  }
  if (!res.ok || !res.body) {
    let message = `请求失败（${res.status}）`
    try {
      const payload = await res.json()
      if (payload?.message) message = payload.message
    } catch {}
    throw new Error(message)
  }

  const reader = res.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
<<<<<<< HEAD
  // 是否收到了 final/error 收尾事件：静默断流（网关超时/后端崩溃）时
  // 流正常结束但没有 final，调用方据此把消息从"生成中"复位并提示
  let ended = false

  // 按 SSE 规范：一个事件块可含多行 data，各行内容以换行拼接；仅剥掉首个空格
=======

>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4
  const dispatch = (block) => {
    let event = 'message'
    let data = ''
    for (const line of block.split('\n')) {
      if (line.startsWith('event:')) event = line.slice(6).trim()
<<<<<<< HEAD
      else if (line.startsWith('data:')) {
        const chunk = line.slice(5)
        data += (data ? '\n' : '') + (chunk.startsWith(' ') ? chunk.slice(1) : chunk)
      }
    }
    if (!data) return
    if (event === 'final' || event === 'error') ended = true
=======
      else if (line.startsWith('data:')) data += line.slice(5).trim()
    }
    if (!data) return
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4
    try {
      onEvent(event, JSON.parse(data))
    } catch {
      onEvent(event, data)
    }
  }

  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
<<<<<<< HEAD
    // 按 SSE 规范事件边界兼容 LF 与 CRLF 行尾（部分网关会改写行尾），先统一去 CR
    let m
    while ((m = buffer.match(/\r?\n\r?\n/))) {
      const block = buffer.slice(0, m.index)
      buffer = buffer.slice(m.index + m[0].length)
      dispatch(block.replace(/\r/g, ''))
    }
  }
  if (buffer.trim()) dispatch(buffer.replace(/\r/g, ''))
  decoder.decode()
  return ended
=======
    let idx
    while ((idx = buffer.indexOf('\n\n')) >= 0) {
      const block = buffer.slice(0, idx)
      buffer = buffer.slice(idx + 2)
      dispatch(block)
    }
  }
  if (buffer.trim()) dispatch(buffer)
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4
}

export const api = {
  login: (email, password) =>
    request('/auth/login', { method: 'POST', body: { email, password } }),
  register: (payload) =>
    request('/auth/register', { method: 'POST', body: payload }),
<<<<<<< HEAD
  getCaptcha: () => request('/auth/captcha'),
  me: () => request('/auth/me'),
  // 登出：让服务端吊销 refresh 令牌（尽力而为，失败不影响本地清理）。
  // 直接 fetch 而不走 request()：此时不应触发刷新/广播逻辑，结果也无需等待
  logout: () => {
    const refreshToken = tokenStore.refresh
    if (!refreshToken) return Promise.resolve()
    return fetch('/api/auth/logout', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken }),
      keepalive: true,
    }).catch(() => {})
  },
  getSettings: () => request('/settings'),
  saveSettings: (payload) => request('/settings', { method: 'PUT', body: payload }),
  // 分页列表：scope=mine 私库 / 默认总库；opts: { page, size, sortBy, order, starred, board }
  listLiterature: (scope, board, { page = 0, size = 12, sortBy, order, starred } = {}) => {
    const params = []
    if (scope === 'mine') params.push('scope=mine')
    if (board) params.push(`board=${encodeURIComponent(board)}`)
    if (sortBy) params.push(`sortBy=${encodeURIComponent(sortBy)}`)
    if (order) params.push(`order=${encodeURIComponent(order)}`)
    if (starred) params.push('starred=true')
    params.push(`page=${page}`)
    params.push(`size=${size}`)
    return request(`/literature?${params.join('&')}`)
  },
  getLiterature: (id) => request(`/literature/${id}`),
  updateLiteratureState: (id, payload) =>
    request(`/literature/${id}/state`, { method: 'PUT', body: payload }),
  updateLiteratureVisibility: (id, isPublic) =>
    request(`/literature/${id}/visibility`, { method: 'PUT', body: { isPublic } }),
  getLiteratureFile: (id) => requestBlob(`/literature/${id}/file`),
  deleteLiterature: (id) => request(`/literature/${id}`, { method: 'DELETE' }),
  // 笔记插件：工作区批注（含引用标记）/问题/记录
  listNotes: (literatureId) =>
    request(literatureId != null ? `/notes?literatureId=${literatureId}` : '/notes'),
  createNote: (payload) => request('/notes', { method: 'POST', body: payload }),
  updateNote: (noteId, payload) => request(`/notes/${noteId}`, { method: 'PUT', body: payload }),
  deleteNote: (noteId) => request(`/notes/${noteId}`, { method: 'DELETE' }),
  recommendations: (topK = 6) => request(`/literature/recommendations?topK=${topK}`),
  search: (query, topK = 10, scope) =>
    request(`/literature/search?query=${encodeURIComponent(query)}&topK=${topK}${scope === 'mine' ? '&scope=mine' : ''}`),
  uploadLiterature: (formData) =>
    request('/literature/upload', { method: 'POST', body: formData, isForm: true, timeoutMs: 120000 }),
=======
  me: () => request('/auth/me'),
  listLiterature: () => request('/literature'),
  getLiterature: (id) => request(`/literature/${id}`),
  deleteLiterature: (id) => request(`/literature/${id}`, { method: 'DELETE' }),
  search: (query, topK = 10) =>
    request(`/literature/search?query=${encodeURIComponent(query)}&topK=${topK}`),
  uploadLiterature: (formData) =>
    request('/literature/upload', { method: 'POST', body: formData, isForm: true }),
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4
  agentChat,
  agentConversations: () => request('/agent-api/agent/conversations'),
  agentMessages: (conversationId) =>
    request(`/agent-api/agent/conversations/${conversationId}/messages`),
  agentDeleteConversation: (conversationId) =>
    request(`/agent-api/agent/conversations/${conversationId}`, { method: 'DELETE' }),
  agentRenameConversation: (conversationId, title) =>
    request(`/agent-api/agent/conversations/${conversationId}`, { method: 'PATCH', body: { title } }),
}
