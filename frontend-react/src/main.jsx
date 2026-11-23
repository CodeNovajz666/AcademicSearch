import React from 'react'
import ReactDOM from 'react-dom/client'
import App from './App'
<<<<<<< HEAD
import ErrorBoundary from './components/ErrorBoundary'
import './styles/global.css'

let initialTheme = 'dark'
try {
  const saved = localStorage.getItem('as.theme')
  if (saved) initialTheme = saved
} catch { /* 存储不可用走默认主题 */ }
=======
import './styles/global.css'

const initialTheme = localStorage.getItem('as.theme') || 'dark'
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4
document.documentElement.dataset.theme = initialTheme

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
<<<<<<< HEAD
    <ErrorBoundary>
      <App />
    </ErrorBoundary>
=======
    <App />
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4
  </React.StrictMode>
)
