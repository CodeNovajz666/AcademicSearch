const TOKEN_KEY = 'as.access'
const REFRESH_KEY = 'as.refresh'
const USER_KEY = 'as.user'

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
    } catch {
      return null
    }
  },
  save({ accessToken, refreshToken, user }) {
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
}

export async function request(path, options, _retried = false) {
  const res = await rawRequest(path, options)
  if (res.status === 401 && !_retried) {
    if (await tryRefresh()) return request(path, options, true)
    tokenStore.clear()
    window.dispatchEvent(new CustomEvent('auth:expired'))
    throw new Error('登录已过期，请重新登录')
  }
  const payload = await res.json().catch(() => null)
  if (!res.ok || !payload || payload.code !== 200) {
    throw new Error(payload?.message || `请求失败（${res.status}）`)
  }
  return payload.data
}

async function agentChatRaw(conversationId, question, signal) {
  const headers = { 'Content-Type': 'application/json' }
  const token = tokenStore.access
  if (token) headers.Authorization = `Bearer ${token}`
  return fetch('/agent-api/agent/chat', {
    method: 'POST',
    headers,
    body: JSON.stringify({ conversationId, question }),
    signal,
  })
}

/**
 * Agent 流式对话（SSE）。
 * 事件：start（会话 id）→ step*（执行轨迹）→ token*（流式回答）→ final | error
 */
export async function agentChat({ conversationId, question, onEvent, signal }) {
  let res = await agentChatRaw(conversationId, question, signal)
  if (res.status === 401 && (await tryRefresh())) {
    res = await agentChatRaw(conversationId, question, signal)
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

  const dispatch = (block) => {
    let event = 'message'
    let data = ''
    for (const line of block.split('\n')) {
      if (line.startsWith('event:')) event = line.slice(6).trim()
      else if (line.startsWith('data:')) data += line.slice(5).trim()
    }
    if (!data) return
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
    let idx
    while ((idx = buffer.indexOf('\n\n')) >= 0) {
      const block = buffer.slice(0, idx)
      buffer = buffer.slice(idx + 2)
      dispatch(block)
    }
  }
  if (buffer.trim()) dispatch(buffer)
}

export const api = {
  login: (email, password) =>
    request('/auth/login', { method: 'POST', body: { email, password } }),
  register: (payload) =>
    request('/auth/register', { method: 'POST', body: payload }),
  me: () => request('/auth/me'),
  listLiterature: () => request('/literature'),
  getLiterature: (id) => request(`/literature/${id}`),
  deleteLiterature: (id) => request(`/literature/${id}`, { method: 'DELETE' }),
  search: (query, topK = 10) =>
    request(`/literature/search?query=${encodeURIComponent(query)}&topK=${topK}`),
  uploadLiterature: (formData) =>
    request('/literature/upload', { method: 'POST', body: formData, isForm: true }),
  agentChat,
  agentConversations: () => request('/agent-api/agent/conversations'),
  agentMessages: (conversationId) =>
    request(`/agent-api/agent/conversations/${conversationId}/messages`),
  agentDeleteConversation: (conversationId) =>
    request(`/agent-api/agent/conversations/${conversationId}`, { method: 'DELETE' }),
  agentRenameConversation: (conversationId, title) =>
    request(`/agent-api/agent/conversations/${conversationId}`, { method: 'PATCH', body: { title } }),
}
