import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { FileSearch, UploadCloud } from 'lucide-react'
import { api } from '../lib/api'
import SearchHero from '../components/SearchHero'
import LiteratureCard from '../components/LiteratureCard'
import SkeletonCard from '../components/SkeletonCard'
import EmptyState from '../components/EmptyState'

export default function SearchView() {
  const [params] = useSearchParams()
  const q = params.get('q') || ''
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
