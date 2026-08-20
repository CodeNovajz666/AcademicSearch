import { createContext, useContext, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api, tokenStore } from '../lib/api'

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [user, setUser] = useState(tokenStore.user)
  const navigate = useNavigate()

  useEffect(() => {
    const onExpired = () => {
      setUser(null)
      navigate('/login', { replace: true })
    }
    window.addEventListener('auth:expired', onExpired)
    return () => window.removeEventListener('auth:expired', onExpired)
  }, [navigate])

  const login = async (email, password) => {
    const data = await api.login(email, password)
    tokenStore.save(data)
    setUser(data.user)
    return data.user
  }

  const register = async (payload) => {
    await api.register(payload)
    return login(payload.email, payload.password)
  }

  const logout = () => {
    tokenStore.clear()
    setUser(null)
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
