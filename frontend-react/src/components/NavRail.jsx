import { useEffect, useRef, useState } from 'react'
import { Link, NavLink } from 'react-router-dom'
import { BookOpen, Home, Library, LogOut, Moon, PlusCircle, Search, Sun } from 'lucide-react'
import { useTheme } from '../context/ThemeContext'
import { useAuth } from '../context/AuthContext'

const items = [
  { to: '/', icon: Home, label: '首页', end: true },
  { to: '/search', icon: Search, label: '检索' },
  { to: '/library', icon: Library, label: '文献库' },
  { to: '/upload', icon: PlusCircle, label: '上传' },
]

export default function NavRail() {
  const { theme, toggle } = useTheme()
  const { user, logout } = useAuth()
  const [faded, setFaded] = useState(false)
  const lastY = useRef(0)

  useEffect(() => {
    lastY.current = window.scrollY
    const onScroll = () => {
      const y = window.scrollY
      setFaded(y > lastY.current + 4 && y > 60)
      lastY.current = y
    }
    window.addEventListener('scroll', onScroll, { passive: true })
    return () => window.removeEventListener('scroll', onScroll)
  }, [])

  return (
    <nav className={`nav-rail${faded ? ' is-faded' : ''}`} aria-label="主导航">
      <Link to="/" className="rail-logo" aria-label="返回首页">
        <BookOpen size={19} strokeWidth={2} />
      </Link>
      <div className="rail-links">
        {items.map(({ to, icon: Icon, label, end }) => (
          <NavLink
            key={to}
            to={to}
            end={end}
            className={({ isActive }) => `rail-item${isActive ? ' is-active' : ''}`}
            aria-label={label}
          >
            <Icon size={20} strokeWidth={1.8} />
            <span className="rail-tip">{label}</span>
          </NavLink>
        ))}
      </div>
      <div className="rail-foot">
        <button
          type="button"
          className="rail-item rail-theme-btn"
          onClick={toggle}
          aria-label="切换深浅主题"
        >
          {theme === 'dark' ? <Sun size={19} strokeWidth={1.8} /> : <Moon size={19} strokeWidth={1.8} />}
          <span className="rail-tip">{theme === 'dark' ? '浅色模式' : '深色模式'}</span>
        </button>
        {user && (
          <button type="button" className="rail-item rail-logout-btn" onClick={logout} aria-label="退出登录">
            <LogOut size={19} strokeWidth={1.8} />
            <span className="rail-tip">退出登录</span>
          </button>
        )}
        {user && (
          <div className="rail-avatar" title={user.username || user.email}>
            {(user.username || user.email || 'U').slice(0, 1).toUpperCase()}
          </div>
        )}
      </div>
    </nav>
  )
}
