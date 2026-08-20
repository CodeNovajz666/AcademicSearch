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
  return fetch(`/api${path}`, {
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
  ragQuery: (question, topK = 5) =>
    request('/rag/query', { method: 'POST', body: { question, topK } }),
}
