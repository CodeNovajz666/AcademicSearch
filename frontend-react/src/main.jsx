import React from 'react'
import ReactDOM from 'react-dom/client'
import App from './App'
import ErrorBoundary from './components/ErrorBoundary'
import './styles/global.css'

let initialTheme = 'dark'
try {
  const saved = localStorage.getItem('as.theme')
  if (saved) initialTheme = saved
} catch { /* 存储不可用走默认主题 */ }
document.documentElement.dataset.theme = initialTheme

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <ErrorBoundary>
      <App />
    </ErrorBoundary>
  </React.StrictMode>
)
