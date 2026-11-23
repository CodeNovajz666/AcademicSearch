import { useEffect, useState } from 'react'
<<<<<<< HEAD
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router-dom'
import { FileSearch, Globe2, UploadCloud, UserRound } from 'lucide-react'
import { api } from '../lib/api'
import { useAuth } from '../context/AuthContext'
import { useToast } from '../context/ToastContext'
=======
import { Link, useSearchParams } from 'react-router-dom'
import { FileSearch, UploadCloud } from 'lucide-react'
import { api } from '../lib/api'
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4
import SearchHero from '../components/SearchHero'
import LiteratureCard from '../components/LiteratureCard'
import SkeletonCard from '../components/SkeletonCard'
import EmptyState from '../components/EmptyState'

export default function SearchView() {
  const [params] = useSearchParams()
  const q = params.get('q') || ''
<<<<<<< HEAD
  const { user } = useAuth()
  const toast = useToast()
  const navigate = useNavigate()
  const location = useLocation()
  const [results, setResults] = useState(null)
  const [error, setError] = useState('')
  const [scope, setScope] = useState('all')

  useEffect(() => {
    if (!q) return
    if (!user) {
      toast('请先登录后再使用检索')
      navigate('/login', { state: { from: location }, replace: true })
      return
    }
    let cancelled = false
    setResults(null)
    setError('')
    api
      .search(q, 10, scope)
      .then((r) => { if (!cancelled) setResults(r) })
      .catch((e) => { if (!cancelled) setError(e.message) })
    // 竞态防护：连续修改搜索词时丢弃过期响应，避免旧结果覆盖新关键词
    return () => { cancelled = true }
  }, [q, user, scope])
=======
  const [results, setResults] = useState(null)
  const [error, setError] = useState('')

  useEffect(() => {
    if (!q) return
    setResults(null)
    setError('')
    api
      .search(q, 10)
      .then(setResults)
      .catch((e) => setError(e.message))
  }, [q])
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4

  if (!q) {
    return (
      <div className="page">
        <div className="search-top">
          <SearchHero compact />
        </div>
        <EmptyState
          icon={FileSearch}
          title="输入关键词开始检索"
          desc="系统基于语义相似度匹配文献，支持自然语言问题。"
        />
      </div>
    )
  }

  return (
    <div className="page">
      <div className="search-top">
        <SearchHero compact initialQuery={q} />
      </div>

<<<<<<< HEAD
      <div className="scope-tabs scope-tabs-inline" role="tablist">
        <button
          type="button"
          role="tab"
          aria-selected={scope === 'all'}
          className={`scope-tab${scope === 'all' ? ' on' : ''}`}
          onClick={() => setScope('all')}
        >
          <Globe2 size={14} /> 总知识库
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={scope === 'mine'}
          className={`scope-tab${scope === 'mine' ? ' on' : ''}`}
          onClick={() => setScope('mine')}
        >
          <UserRound size={14} /> 我的知识库
        </button>
      </div>

=======
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4
      {results === null && !error && (
        <div className="lit-list" style={{ marginTop: 32 }}>
          {[0, 1, 2, 3].map((i) => (
            <SkeletonCard key={i} />
          ))}
        </div>
      )}

      {error && <EmptyState icon={FileSearch} title="检索失败" desc={error} />}

      {results && (
        <>
          <p className="result-count">
            「{q}」 · 找到 {results.length} 条相关文献
          </p>
          <div className="lit-list">
            {results.map((r, i) => (
              <LiteratureCard key={r.literature.id} item={r} showScore index={i} />
            ))}
          </div>
          {results.length === 0 && (
            <EmptyState
              icon={FileSearch}
              title="未找到相关文献"
              desc="换个关键词试试，或先上传文献丰富文献库。"
              action={
                <Link to="/upload" className="btn-primary">
                  <UploadCloud size={16} /> 上传文献
                </Link>
              }
            />
          )}
        </>
      )}
    </div>
  )
}
