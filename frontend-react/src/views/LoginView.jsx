import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { BookOpen, Eye, EyeOff, Loader2 } from 'lucide-react'
import { useAuth } from '../context/AuthContext'
import { safeFromTarget } from '../lib/navigation'

const REMEMBER_KEY = 'as.remember-email'

function readRememberedEmail() {
  try {
    return localStorage.getItem(REMEMBER_KEY) || ''
  } catch {
    return ''
  }
}

export default function LoginView() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [email, setEmail] = useState(readRememberedEmail)
  const [password, setPassword] = useState('')
  const [remember, setRemember] = useState(() => Boolean(readRememberedEmail()))
  const [showPwd, setShowPwd] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  const submit = async (e) => {
    e.preventDefault()
    if (busy) return
    setBusy(true)
    setError('')
    const trimmed = email.trim()
    try {
      await login(trimmed, password)
      try {
        if (remember) localStorage.setItem(REMEMBER_KEY, trimmed)
        else localStorage.removeItem(REMEMBER_KEY)
      } catch { /* 存储不可用时仅影响"记住邮箱" */ }
      navigate(safeFromTarget(location.state?.from) || '/', { replace: true })
    } catch (ex) {
      setError(ex.message)
      setBusy(false)
    }
  }

  return (
    <div className="auth-page">
      <form className="auth-card" onSubmit={submit}>
        <div className="auth-logo">
          <BookOpen size={22} strokeWidth={2} />
        </div>
        <h1>欢迎回来</h1>
        <p className="auth-sub">登录以继续你的文献研究</p>
        <div className="field">
          <label htmlFor="login-email">邮箱</label>
          <input
            id="login-email"
            type="email"
            required
            autoComplete="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder="you@example.com"
          />
        </div>
        <div className="field">
          <label htmlFor="login-password">密码</label>
          <div className="pwd-wrap">
            <input
              id="login-password"
              type={showPwd ? 'text' : 'password'}
              required
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="输入密码"
            />
            <button
              type="button"
              className="pwd-toggle"
              onClick={() => setShowPwd((v) => !v)}
              aria-label={showPwd ? '隐藏密码' : '显示密码'}
              title={showPwd ? '隐藏密码' : '显示密码'}
            >
              {showPwd ? <EyeOff size={16} strokeWidth={1.8} /> : <Eye size={16} strokeWidth={1.8} />}
            </button>
          </div>
        </div>
        <label className="auth-remember">
          <input
            type="checkbox"
            checked={remember}
            onChange={(e) => setRemember(e.target.checked)}
          />
          <span>记住邮箱</span>
        </label>
        {error && <p className="form-error">{error}</p>}
        <button type="submit" className="btn-primary auth-submit" disabled={busy}>
          {busy ? <Loader2 size={17} className="spin" /> : '登录'}
        </button>
        <p className="auth-switch">
          还没有账号？
          {/* 带上回跳 state：从功能页被引导到登录的游客，注册完成后回到原页面 */}
          <Link to="/register" state={location.state}>注册</Link>
        </p>
      </form>
    </div>
  )
}
