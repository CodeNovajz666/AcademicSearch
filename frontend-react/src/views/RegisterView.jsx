<<<<<<< HEAD
import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { BookOpen, Eye, EyeOff, Loader2, RefreshCw } from 'lucide-react'
import { useAuth } from '../context/AuthContext'
import { api } from '../lib/api'
import { safeFromTarget } from '../lib/navigation'

function pwdScore(pwd) {
  let score = 0
  if (pwd.length >= 8) score++ // 与提交校验同口径（<8 直接拦截）
=======
import { useMemo, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { BookOpen, Eye, EyeOff, Loader2 } from 'lucide-react'
import { useAuth } from '../context/AuthContext'

function pwdScore(pwd) {
  let score = 0
  if (pwd.length >= 6) score++
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4
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
<<<<<<< HEAD
  const location = useLocation()
=======
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4
  const [email, setEmail] = useState('')
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [showPwd, setShowPwd] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
<<<<<<< HEAD
  const [captcha, setCaptcha] = useState(null)
  const [captchaCode, setCaptchaCode] = useState('')
=======
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4

  const strength = useMemo(() => pwdScore(password), [password])
  const usernameInvalid = username.length > 0 && (username.trim().length < 2 || username.length > 30)
  const confirmInvalid = confirm.length > 0 && confirm !== password

<<<<<<< HEAD
  const loadCaptcha = useCallback(async () => {
    try {
      setCaptcha(await api.getCaptcha())
    } catch {
      setCaptcha(null)
    }
    setCaptchaCode('')
  }, [])

  useEffect(() => {
    loadCaptcha()
  }, [loadCaptcha])

=======
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4
  const submit = async (e) => {
    e.preventDefault()
    if (busy) return
    if (username.trim().length < 2 || username.length > 30) {
      setError('用户名需为 2~30 个字符')
      return
    }
<<<<<<< HEAD
    if (password.length < 8) {
      setError('密码至少需要 8 位')
=======
    if (password.length < 6) {
      setError('密码至少需要 6 位')
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4
      return
    }
    if (password !== confirm) {
      setError('两次输入的密码不一致')
      return
    }
<<<<<<< HEAD
    if (!captcha || !captchaCode.trim()) {
      setError('请输入图形验证码')
      return
    }
    setBusy(true)
    setError('')
    try {
      await register({
        email,
        username: username.trim(),
        password,
        captchaId: captcha.captchaId,
        captchaCode: captchaCode.trim(),
      })
      // 注册成功回跳原页面（与登录行为一致；游客从检索页/工作区跳来时不丢上下文）
      navigate(safeFromTarget(location.state?.from) || '/', { replace: true })
    } catch (ex) {
      setError(ex.message)
      // 验证码一次性且连错作废，任何注册失败后都重新取一张
      loadCaptcha()
=======
    setBusy(true)
    setError('')
    try {
      await register({ email, username: username.trim(), password })
      navigate('/', { replace: true })
    } catch (ex) {
      setError(ex.message)
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4
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
<<<<<<< HEAD
              placeholder="至少 8 位，建议字母 + 数字"
=======
              placeholder="至少 6 位，建议字母 + 数字"
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4
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
<<<<<<< HEAD
        <div className="field">
          <label htmlFor="reg-captcha">图形验证码</label>
          <div className="captcha-row">
            <input
              id="reg-captcha"
              required
              value={captchaCode}
              maxLength={4}
              autoComplete="off"
              spellCheck={false}
              onChange={(e) => setCaptchaCode(e.target.value)}
              placeholder="输入右侧字符"
            />
            {captcha?.image ? (
              <img
                className="captcha-img"
                src={captcha.image}
                alt="图形验证码，点击刷新"
                title="看不清？点击刷新"
                onClick={loadCaptcha}
              />
            ) : (
              <button
                type="button"
                className="captcha-img captcha-retry"
                onClick={loadCaptcha}
                aria-label="加载验证码"
                title="加载验证码"
              >
                <RefreshCw size={16} strokeWidth={1.8} />
              </button>
            )}
          </div>
        </div>
=======
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4
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
