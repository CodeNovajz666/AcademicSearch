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
