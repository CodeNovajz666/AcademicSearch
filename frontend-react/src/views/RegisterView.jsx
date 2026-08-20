import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { BookOpen, Loader2 } from 'lucide-react'
import { useAuth } from '../context/AuthContext'

export default function RegisterView() {
  const { register } = useAuth()
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  const submit = async (e) => {
    e.preventDefault()
    if (busy) return
    if (password.length < 6) {
      setError('密码至少需要 6 位')
      return
    }
    setBusy(true)
    setError('')
    try {
      await register({ email, username, password })
      navigate('/', { replace: true })
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
        <h1>创建账号</h1>
        <p className="auth-sub">开始构建你的智能文献库</p>
        <div className="field">
          <label htmlFor="reg-username">用户名</label>
          <input
            id="reg-username"
            required
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            placeholder="你的名字"
          />
        </div>
        <div className="field">
          <label htmlFor="reg-email">邮箱</label>
          <input
            id="reg-email"
            type="email"
            required
            autoComplete="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder="you@example.com"
          />
        </div>
        <div className="field">
          <label htmlFor="reg-password">密码</label>
          <input
            id="reg-password"
            type="password"
            required
            autoComplete="new-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            placeholder="至少 6 位"
          />
        </div>
        {error && <p className="form-error">{error}</p>}
        <button type="submit" className="btn-primary auth-submit" disabled={busy}>
          {busy ? <Loader2 size={17} className="spin" /> : '注册'}
        </button>
        <p className="auth-switch">
          已有账号？
          <Link to="/login">登录</Link>
        </p>
      </form>
    </div>
  )
}
