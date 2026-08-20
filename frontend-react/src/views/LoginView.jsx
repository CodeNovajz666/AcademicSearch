import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { BookOpen, Loader2 } from 'lucide-react'
import { useAuth } from '../context/AuthContext'

export default function LoginView() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  const submit = async (e) => {
    e.preventDefault()
    if (busy) return
    setBusy(true)
    setError('')
    try {
      await login(email, password)
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
          <input
            id="login-password"
            type="password"
            required
            autoComplete="current-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            placeholder="输入密码"
          />
        </div>
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
