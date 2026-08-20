import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { Library as LibraryIcon, Plus } from 'lucide-react'
import { api } from '../lib/api'
import LiteratureCard from '../components/LiteratureCard'
import SkeletonCard from '../components/SkeletonCard'
import EmptyState from '../components/EmptyState'

const PAGE_SIZE = 8

export default function LibraryView() {
  const [all, setAll] = useState(null)
  const [count, setCount] = useState(PAGE_SIZE)
  const [loadingMore, setLoadingMore] = useState(false)
  const [error, setError] = useState('')
  const sentinelRef = useRef(null)

  useEffect(() => {
    api
      .listLiterature()
      .then((list) =>
        setAll(
          [...list].sort((a, b) => (b.createdAt || '').localeCompare(a.createdAt || ''))
        )
      )
      .catch((e) => setError(e.message))
  }, [])

  useEffect(() => {
    if (!all || count >= all.length) return
    const el = sentinelRef.current
    if (!el) return
    const io = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting && !loadingMore) {
          setLoadingMore(true)
          setTimeout(() => {
            setCount((c) => Math.min(c + PAGE_SIZE, all.length))
            setLoadingMore(false)
          }, 320)
        }
      },
      { rootMargin: '400px 0px' }
    )
    io.observe(el)
    return () => io.disconnect()
  }, [all, count, loadingMore])

  return (
    <div className="page">
      <header className="section-head">
        <div>
          <h2 className="section-title">文献库</h2>
          <p className="section-sub">
            {all ? `${all.length} 篇文献 · 向量索引已建立` : '加载中…'}
          </p>
        </div>
        <Link to="/upload" className="btn-primary">
          <Plus size={16} strokeWidth={2.2} /> 上传文献
        </Link>
      </header>

      {all === null && !error && (
        <div className="lit-list">
          {[0, 1, 2, 3, 4].map((i) => (
            <SkeletonCard key={i} />
          ))}
        </div>
      )}

      {error && <EmptyState icon={LibraryIcon} title="加载失败" desc={error} />}

      {all && all.length === 0 && (
        <EmptyState
          icon={LibraryIcon}
          title="文献库还是空的"
          desc="上传第一篇 PDF 文献，系统将自动解析全文并建立向量索引。"
          action={
            <Link to="/upload" className="btn-primary">
              上传文献
            </Link>
          }
        />
      )}

      {all && all.length > 0 && (
        <>
          <div className="lit-list">
            {all.slice(0, count).map((lit, i) => (
              <LiteratureCard key={lit.id} item={lit} index={i % PAGE_SIZE} />
            ))}
          </div>
          {loadingMore && (
            <div className="lit-list">
              {[0, 1].map((i) => (
                <SkeletonCard key={`more-${i}`} />
              ))}
            </div>
          )}
          <div ref={sentinelRef} className="sentinel" />
          {count >= all.length && <p className="list-end">— 已展示全部文献 —</p>}
        </>
      )}
    </div>
  )
}
