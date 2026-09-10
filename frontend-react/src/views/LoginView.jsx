import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { BookOpen, Eye, EyeOff, Loader2 } from 'lucide-react'
import { useAuth } from '../context/AuthContext'

const REMEMBER_KEY = 'as.remember-email'

export default function LoginView() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [email, setEmail] = useState(() => localStorage.getItem(REMEMBER_KEY) || '')
  const [password, setPassword] = useState('')
  const [remember, setRemember] = useState(() => Boolean(localStorage.getItem(REMEMBER_KEY)))
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
      if (remember) localStorage.setItem(REMEMBER_KEY, trimmed)
      else localStorage.removeItem(REMEMBER_KEY)
      navigate(location.state?.from?.pathname || '/', { replace: true })
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
          <Link to="/register">注册</Link>
        </p>
      </form>
    </div>
  )
}
