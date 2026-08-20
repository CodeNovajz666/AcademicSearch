import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { ArrowRight, FileSearch, UploadCloud } from 'lucide-react'
import { api } from '../lib/api'
import SearchHero from '../components/SearchHero'
import LiteratureCard from '../components/LiteratureCard'
import SkeletonCard from '../components/SkeletonCard'
import EmptyState from '../components/EmptyState'

export default function HomeView() {
  const [recent, setRecent] = useState(null)
  const [error, setError] = useState('')

  useEffect(() => {
    api
      .listLiterature()
      .then((list) =>
        setRecent(
          [...list].sort((a, b) => (b.createdAt || '').localeCompare(a.createdAt || ''))
        )
      )
      .catch((e) => setError(e.message))
  }, [])

  return (
    <div className="page">
      <section className="home-hero">
        <SearchHero />
        {recent && recent.length > 0 && (
          <p className="home-stat">
            已收录 {recent.length} 篇文献 · 语义检索 · 检索增强问答
          </p>
        )}
      </section>

      {recent === null && !error && (
        <section className="home-recent">
          <header className="section-head">
            <h2 className="section-title">最近入库</h2>
          </header>
          <div className="lit-list">
            {[0, 1, 2].map((i) => (
              <SkeletonCard key={i} />
            ))}
          </div>
        </section>
      )}

      {recent && recent.length > 0 && (
        <section className="home-recent">
          <header className="section-head">
            <h2 className="section-title">最近入库</h2>
            <Link to="/library" className="section-more">
              查看全部 <ArrowRight size={14} />
            </Link>
          </header>
          <div className="lit-list">
            {recent.slice(0, 4).map((lit, i) => (
              <LiteratureCard key={lit.id} item={lit} index={i} />
            ))}
          </div>
        </section>
      )}

      {recent?.length === 0 && (
        <EmptyState
          icon={FileSearch}
          title="文献库还是空的"
          desc="上传第一篇 PDF 文献，系统将自动解析全文并建立向量索引。"
          action={
            <Link to="/upload" className="btn-primary">
              <UploadCloud size={16} /> 上传文献
            </Link>
          }
        />
      )}
    </div>
  )
}
