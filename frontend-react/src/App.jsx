import { BrowserRouter, Link, Navigate, Outlet, Route, Routes, useLocation } from 'react-router-dom'
import { Lock } from 'lucide-react'
import { ThemeProvider } from './context/ThemeContext'
import { ToastProvider } from './context/ToastContext'
import { ConfirmProvider } from './context/ConfirmContext'
import { AuthProvider, useAuth } from './context/AuthContext'
import ParticleField from './components/ParticleField'
import NavRail from './components/NavRail'
import CopilotPanel from './components/CopilotPanel'
import EmptyState from './components/EmptyState'
import HomeView from './views/HomeView'
import SearchView from './views/SearchView'
import LibraryView from './views/LibraryView'
import CommunityView from './views/CommunityView'
import NotesView from './views/NotesView'
import SettingsView from './views/SettingsView'
import DetailView from './views/DetailView'
import WorkspaceView from './views/WorkspaceView'
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

/** 功能页守卫：游客可浏览应用外壳，进入功能页时显示登录引导卡（不硬跳转） */
function RequireLogin() {
  const { user } = useAuth()
  const location = useLocation()
  if (!user) {
    return (
      <div className="page">
        <EmptyState
          icon={Lock}
          title="登录后即可使用"
          desc="登录后即可管理自己的文献库、浏览社区公开文献、语义检索并使用 AI 研究副驾。"
          action={
            <Link to="/login" state={{ from: location }} className="btn-primary">
              去登录
            </Link>
          }
        />
      </div>
    )
  }
  return <Outlet />
}

function RedirectIfAuthed({ children }) {
  const { user } = useAuth()
  return user ? <Navigate to="/" replace /> : children
}

export default function App() {
  return (
    <ThemeProvider>
      <ToastProvider>
        <ConfirmProvider>
          <BrowserRouter>
          <AuthProvider>
            <ParticleField />
            <Routes>
              <Route path="/login" element={<RedirectIfAuthed><LoginView /></RedirectIfAuthed>} />
              <Route path="/register" element={<RedirectIfAuthed><RegisterView /></RedirectIfAuthed>} />
              <Route element={<AppShell />}>
                <Route path="/" element={<HomeView />} />
                <Route path="/search" element={<SearchView />} />
                <Route element={<RequireLogin />}>
                  <Route path="/library" element={<LibraryView />} />
                  <Route path="/notes" element={<NotesView />} />
                  <Route path="/community" element={<CommunityView />} />
                  <Route path="/settings" element={<SettingsView />} />
                  <Route path="/literature/:id" element={<DetailView />} />
                  <Route path="/upload" element={<UploadView />} />
                </Route>
              </Route>
              {/* 研究工作区：全屏布局，不套应用外壳（无导航栏与副驾面板）；
                  原阅读模式已并入，精读/批注/对话统一在此完成 */}
              <Route element={<RequireLogin />}>
                <Route path="/workspace/:id" element={<WorkspaceView />} />
              </Route>
              <Route path="*" element={<Navigate to="/" replace />} />
            </Routes>
          </AuthProvider>
          </BrowserRouter>
        </ConfirmProvider>
      </ToastProvider>
    </ThemeProvider>
  )
}
