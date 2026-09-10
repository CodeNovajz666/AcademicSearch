import { useMemo, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { BookOpen, Eye, EyeOff, Loader2 } from 'lucide-react'
import { useAuth } from '../context/AuthContext'

function pwdScore(pwd) {
  let score = 0
  if (pwd.length >= 6) score++
  if (pwd.length >= 10) score++
  if (/[a-zA-Z]/.test(pwd) && /\d/.test(pwd)) score++
  if (/[a-z]/.test(pwd) && /[A-Z]/.test(pwd)) score++
  if (/[^a-zA-Z0-9]/.test(pwd)) score++
  return Math.min(score, 3)
}

const STRENGTH_LABEL = ['', '弱', '中', '强']
const STRENGTH_CLASS = ['', 'weak', 'mid', 'strong']

export default function RegisterView() {
  const { register } = useAuth()
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [showPwd, setShowPwd] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  const strength = useMemo(() => pwdScore(password), [password])
  const usernameInvalid = username.length > 0 && (username.trim().length < 2 || username.length > 30)
  const confirmInvalid = confirm.length > 0 && confirm !== password

  const submit = async (e) => {
    e.preventDefault()
    if (busy) return
    if (username.trim().length < 2 || username.length > 30) {
      setError('用户名需为 2~30 个字符')
      return
    }
    if (password.length < 6) {
      setError('密码至少需要 6 位')
      return
    }
    if (password !== confirm) {
      setError('两次输入的密码不一致')
      return
    }
    setBusy(true)
    setError('')
    try {
      await register({ email, username: username.trim(), password })
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
            maxLength={30}
            onChange={(e) => setUsername(e.target.value)}
            placeholder="2~30 个字符"
          />
          {usernameInvalid && <p className="field-hint">用户名需为 2~30 个字符</p>}
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
          <div className="pwd-wrap">
            <input
              id="reg-password"
              type={showPwd ? 'text' : 'password'}
              required
              autoComplete="new-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="至少 6 位，建议字母 + 数字"
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
          {password && (
            <div className={`pwd-strength ${STRENGTH_CLASS[strength]}`}>
              <i />
              <i />
              <i />
              <span>{STRENGTH_LABEL[strength]}</span>
            </div>
          )}
        </div>
        <div className="field">
          <label htmlFor="reg-confirm">确认密码</label>
          <input
            id="reg-confirm"
            type={showPwd ? 'text' : 'password'}
            required
            autoComplete="new-password"
            value={confirm}
            onChange={(e) => setConfirm(e.target.value)}
            placeholder="再次输入密码"
          />
          {confirmInvalid && <p className="field-hint">两次输入的密码不一致</p>}
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
