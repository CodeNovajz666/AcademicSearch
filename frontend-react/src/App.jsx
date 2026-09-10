import { BrowserRouter, Navigate, Outlet, Route, Routes } from 'react-router-dom'
import { ThemeProvider } from './context/ThemeContext'
import { ToastProvider } from './context/ToastContext'
import { AuthProvider, useAuth } from './context/AuthContext'
import ParticleField from './components/ParticleField'
import NavRail from './components/NavRail'
import CopilotPanel from './components/CopilotPanel'
import HomeView from './views/HomeView'
import SearchView from './views/SearchView'
import LibraryView from './views/LibraryView'
import DetailView from './views/DetailView'
import UploadView from './views/UploadView'
import LoginView from './views/LoginView'
import RegisterView from './views/RegisterView'

function AppShell() {
  return (
    <div className="shell">
      <NavRail />
      <div className="workspace">
        <div className="workspace-inner">
          <main className="content">
            <Outlet />
          </main>
          <div className="copilot-wrap">
            <CopilotPanel />
          </div>
        </div>
      </div>
    </div>
  )
}

/** 登录守卫：未登录访问任何功能页一律跳转登录 */
function RequireAuth() {
  const { user } = useAuth()
  if (!user) return <Navigate to="/login" replace />
  return <AppShell />
}

function RedirectIfAuthed({ children }) {
  const { user } = useAuth()
  return user ? <Navigate to="/" replace /> : children
}

export default function App() {
  return (
    <ThemeProvider>
      <ToastProvider>
        <BrowserRouter>
          <AuthProvider>
            <ParticleField />
            <Routes>
              <Route path="/login" element={<RedirectIfAuthed><LoginView /></RedirectIfAuthed>} />
              <Route path="/register" element={<RedirectIfAuthed><RegisterView /></RedirectIfAuthed>} />
              <Route element={<RequireAuth />}>
                <Route path="/" element={<HomeView />} />
                <Route path="/search" element={<SearchView />} />
                <Route path="/library" element={<LibraryView />} />
                <Route path="/literature/:id" element={<DetailView />} />
                <Route path="/upload" element={<UploadView />} />
              </Route>
              <Route path="*" element={<Navigate to="/" replace />} />
            </Routes>
          </AuthProvider>
        </BrowserRouter>
      </ToastProvider>
    </ThemeProvider>
  )
}
