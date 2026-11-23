import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { ArrowDownWideNarrow, Library as LibraryIcon, Plus, Star } from 'lucide-react'
import { api } from '../lib/api'
import LiteratureCard from '../components/LiteratureCard'
import SkeletonCard from '../components/SkeletonCard'
import EmptyState from '../components/EmptyState'

const PAGE_SIZE = 12

// 排序映射为服务端参数（真分页后排序在数据库完成）
const SORTERS = {
  recent: { label: '最近入库', sortBy: 'createdAt', order: 'desc' },
  title: { label: '标题 A-Z', sortBy: 'title', order: 'asc' },
  year: { label: '年份新→旧', sortBy: 'year', order: 'desc' },
}

export default function LibraryView() {
  const [items, setItems] = useState(null)
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(0)
  const [hasMore, setHasMore] = useState(false)
  const [loadingMore, setLoadingMore] = useState(false)
  const [error, setError] = useState('')
  const [sortBy, setSortBy] = useState('recent')
  const [onlyStarred, setOnlyStarred] = useState(false)
  const sentinelRef = useRef(null)

  const load = (p, { append = false } = {}) => {
    const s = SORTERS[sortBy]
    return api
      .listLiterature('mine', null, { page: p, size: PAGE_SIZE, sortBy: s.sortBy, order: s.order, starred: onlyStarred })
      .then((data) => {
        setItems((prev) => (append && prev ? [...prev, ...data.items] : data.items))
        setTotal(data.total)
        setPage(data.page)
        setHasMore((data.page + 1) * data.size < data.total)
      })
      .catch((e) => setError(e.message))
  }

  // 排序/筛选变化回到第一页
  useEffect(() => {
    setItems(null)
    setError('')
    load(0)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [onlyStarred, sortBy])

  // 无限滚动加载下一页（服务端分页）
  useEffect(() => {
    if (!hasMore) return
    const el = sentinelRef.current
    if (!el) return
    const io = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting && !loadingMore) {
          setLoadingMore(true)
          load(page + 1, { append: true }).finally(() => setLoadingMore(false))
        }
      },
      { rootMargin: '400px 0px' }
    )
    io.observe(el)
    return () => io.disconnect()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [hasMore, page, loadingMore])

  return (
    <div className="page">
      <header className="section-head">
        <div>
          <h2 className="section-title">我的文献库</h2>
          <p className="section-sub">
            {items ? `我的文献 · ${total} 篇 · 仅自己可见，可随时公开到社区` : '加载中…'}
          </p>
        </div>
        <Link to="/upload" className="btn-primary">
          <Plus size={16} strokeWidth={2.2} /> 上传文献
        </Link>
      </header>

      {items && items.length > 0 && (
        <div className="lib-toolbar">
          <button
            type="button"
            className={`filter-chip${onlyStarred ? ' on' : ''}`}
            onClick={() => setOnlyStarred((v) => !v)}
          >
            <Star size={14} fill={onlyStarred ? 'currentColor' : 'none'} /> 只看收藏
          </button>
          <label className="sort-box">
            <ArrowDownWideNarrow size={14} />
            <select value={sortBy} onChange={(e) => setSortBy(e.target.value)}>
              {Object.entries(SORTERS).map(([k, v]) => (
                <option key={k} value={k}>{v.label}</option>
              ))}
            </select>
          </label>
        </div>
      )}

      {items === null && !error && (
        <div className="lit-list">
          {[0, 1, 2, 3, 4].map((i) => (
            <SkeletonCard key={i} />
          ))}
        </div>
      )}

      {error && <EmptyState icon={LibraryIcon} title="加载失败" desc={error} />}

      {items && items.length === 0 && (
        <EmptyState
          icon={LibraryIcon}
          title={onlyStarred ? '还没有收藏的文献' : '我的文献库还是空的'}
          desc={
            onlyStarred
              ? '在文献卡片或详情页点击星标即可收藏，方便后续精读。'
              : '上传第一篇 PDF，默认仅自己可见；需要分享时可在详情页公开到社区，供他人检索与推荐。'
          }
          action={
            <Link to="/upload" className="btn-primary">
              上传文献
            </Link>
          }
        />
      )}

      {items && items.length > 0 && (
        <>
          <div className="lit-list">
            {items.map((lit, i) => (
              <LiteratureCard key={lit.id} item={lit} index={i % PAGE_SIZE} showState />
            ))}
          </div>
          {loadingMore && (
            <div className="lit-list">
              {[0, 1].map((i) => (
                <SkeletonCard key={`more-${i}`} />
              ))}
            </div>
          )}
          {hasMore && <div ref={sentinelRef} className="sentinel" />}
          {!hasMore && <p className="list-end">— 已展示全部文献 —</p>}
        </>
      )}
    </div>
  )
}
