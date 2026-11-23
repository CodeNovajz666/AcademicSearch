import { createContext, useContext, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api, tokenStore } from '../lib/api'
import { useToast } from './ToastContext'

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [user, setUser] = useState(tokenStore.user)
  const navigate = useNavigate()
  const toast = useToast()

  useEffect(() => {
    const onExpired = () => {
      setUser(null)
      toast('登录已过期，请重新登录')
      navigate('/login', { replace: true })
    }
    window.addEventListener('auth:expired', onExpired)
    return () => window.removeEventListener('auth:expired', onExpired)
  }, [navigate, toast])

<<<<<<< HEAD
  // 启动时校验本地会话：localStorage 里的 user 只是缓存，token 可能已过期或被密钥轮换作废。
  // 不校验会出现"界面显示已登录，一发消息才 401 被登出"的幽灵态；
  // 校验期间 access 失效会先走静默刷新，刷新也失败才清理并回到登录页。
  // 仅鉴权失败才清空：网络瞬断保留缓存态，避免用户被打回登录引导卡
  useEffect(() => {
    if (!tokenStore.access) return
    api.me().catch((e) => {
      if (e?.message?.includes('登录已过期')) setUser(null)
    })
  }, [])

=======
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4
  const login = async (email, password) => {
    const data = await api.login(email, password)
    tokenStore.save(data)
    setUser(data.user)
    toast(`欢迎回来，${data.user.username || data.user.email}`)
    return data.user
  }

  const register = async (payload) => {
    await api.register(payload)
    return login(payload.email, payload.password)
  }

  const logout = () => {
<<<<<<< HEAD
    // 先读走 refresh token 发起服务端吊销（盗号止损），再清理本地状态
    api.logout()
=======
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4
    tokenStore.clear()
    setUser(null)
    toast('已退出登录')
    navigate('/login', { replace: true })
  }

  return (
    <AuthContext.Provider value={{ user, login, register, logout }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  return useContext(AuthContext)
}
