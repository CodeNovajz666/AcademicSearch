import { createContext, useContext, useEffect, useState } from 'react'

<<<<<<< HEAD
// 三种主题：深色（默认）→ 浅色 → 护眼（豆沙绿），循环切换
export const THEMES = ['dark', 'light', 'eye']

const ThemeContext = createContext(null)

export function ThemeProvider({ children }) {
  const [theme, setTheme] = useState(() => {
    try {
      const saved = localStorage.getItem('as.theme')
      return THEMES.includes(saved) ? saved : 'dark'
    } catch {
      return 'dark'
    }
  })

  useEffect(() => {
    document.documentElement.dataset.theme = theme
    // 隐私模式/配额满时 setItem 抛错：主题仍生效于当前会话，只是不持久化
    try {
      localStorage.setItem('as.theme', theme)
    } catch { /* ignore */ }
    window.dispatchEvent(new CustomEvent('theme:change'))
  }, [theme])

  const toggle = () => setTheme((t) => THEMES[(THEMES.indexOf(t) + 1) % THEMES.length])
=======
const ThemeContext = createContext(null)

export function ThemeProvider({ children }) {
  const [theme, setTheme] = useState(() => localStorage.getItem('as.theme') || 'dark')

  useEffect(() => {
    document.documentElement.dataset.theme = theme
    localStorage.setItem('as.theme', theme)
    window.dispatchEvent(new CustomEvent('theme:change'))
  }, [theme])

  const toggle = () => setTheme((t) => (t === 'dark' ? 'light' : 'dark'))
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4

  return <ThemeContext.Provider value={{ theme, toggle }}>{children}</ThemeContext.Provider>
}

export function useTheme() {
  return useContext(ThemeContext)
}
