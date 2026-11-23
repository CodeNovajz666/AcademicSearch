import { useCallback, useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  Globe2,
  LayoutGrid,
  Library as LibraryIcon,
  Plus,
  RefreshCw,
  Sparkles,
  UploadCloud,
} from 'lucide-react'
import { api } from '../lib/api'
import { CATEGORIES } from '../lib/categories'
import LiteratureCard from '../components/LiteratureCard'
import SkeletonCard from '../components/SkeletonCard'
import EmptyState from '../components/EmptyState'

export default function CommunityView() {
  const [recs, setRecs] = useState(null)
  const [recError, setRecError] = useState('')
  const [recLoading, setRecLoading] = useState(true)
  const [items, setItems] = useState(null)
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(0)
  const [hasMore, setHasMore] = useState(false)
  const [loadingMore, setLoadingMore] = useState(false)
  const [error, setError] = useState('')
  const [board, setBoard] = useState('')
  const sentinelRef = useRef(null)

  const PAGE_SIZE = 12

  const load = (p, { append = false } = {}) =>
    api
      .listLiterature(undefined, board, { page: p, size: PAGE_SIZE })
      .then((data) => {
        setItems((prev) => (append && prev ? [...prev, ...data.items] : data.items))
        setTotal(data.total)
        setPage(data.page)
        setHasMore((data.page + 1) * data.size < data.total)
      })
      .catch((e) => setError(e.message))

  // 切板块只重新拉社区列表（回到第一页），不重复计算推荐（推荐与板块无关）
  useEffect(() => {
    setItems(null)
    setError('')
    load(0)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [board])

  // 窗口重新聚焦时同步社区列表（回到第一页）：在详情页把文献设为私有/公开、
  // 或另一标签页有增删时，回到本页无需手动刷新即可反映最新可见性
  useEffect(() => {
    const resync = () => load(0).catch(() => {})
    window.addEventListener('focus', resync)
    return () => window.removeEventListener('focus', resync)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [board])

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

  const loadRecs = useCallback(() => {
    setRecLoading(true)
    setRecError('')
    api
      .recommendations(6)
      .then(setRecs)
      .catch((e) => setRecError(e.message))
      .finally(() => setRecLoading(false))
  }, [])

  useEffect(() => {
    loadRecs()
  }, [loadRecs])

  const recItems = recs?.items || []
  const basedOn = recs?.basedOn || []

  return (
    <div className="page">
      <header className="section-head">
        <div>
          <h2 className="section-title">社区知识库</h2>
          <p className="section-sub">
            {items
              ? `社区公开文献${total} 篇，按板块浏览或用语义检索精准查找`
              : '加载中…'}
          </p>
        </div>
        <Link to="/upload" className="btn-primary">
          <Plus size={16} strokeWidth={2.2} /> 上传文献
        </Link>
      </header>

      <section className="rec-block">
        <div className="rec-head">
          <div>
            <h3 className="rec-title">
              <Sparkles size={16} /> 为你推荐
            </h3>
            <p className="section-sub">基于你文献的内容，从社区中匹配主题相近的研究</p>
          </div>
          <button
            type="button"
            className="btn-ghost rec-refresh"
            onClick={loadRecs}
            disabled={recLoading}
          >
            <RefreshCw size={14} className={recLoading ? 'spin' : ''} /> 刷新推荐
          </button>
        </div>

        {basedOn.length > 0 && (
          <div className="rec-based">
            <span>依据</span>
            {basedOn.map((t, i) => (
              <span className="rec-chip" key={`${t}-${i}`} title={t}>
                {t || '未命名文献'}
              </span>
            ))}
          </div>
        )}

        {recLoading && (
          <div className="lit-list">
            {[0, 1].map((i) => (
              <SkeletonCard key={i} />
            ))}
          </div>
        )}

        {!recLoading && recError && (
          <p className="rec-note">推荐加载失败：{recError}</p>
        )}

        {!recLoading && !recError && recItems.length === 0 && (
          <div className="rec-empty">
            <p className="rec-note">
              {basedOn.length === 0
                ? '你的文献库还是空的，先上传并解析文献，系统才能据此从社区中推荐相近研究。'
                : '社区暂时没有与你研究方向相近的公开文献，可以先去社区浏览，或用检索页按关键词查找。'}
            </p>
            <Link to="/upload" className="btn-ghost">
              <UploadCloud size={15} /> 上传文献
            </Link>
          </div>
        )}

        {!recLoading && !recError && recItems.length > 0 && (
          <div className="lit-list">
            {recItems.map((item, i) => (
              <div className="rec-item" key={item.literature.id}>
                <p className="rec-reason">
                  <Sparkles size={12} />
                  与你文献《{item.basedOnTitle || '未命名文献'}》内容相近
                </p>
                <LiteratureCard item={item} showScore index={i} />
              </div>
            ))}
          </div>
        )}
      </section>

      <section>
        <div className="section-head">
          <h2 className="section-title">社区全部文献</h2>
          <Link to="/search" className="section-more">
            <Globe2 size={14} /> 语义检索
          </Link>
        </div>

        <div className="board-tabs">
          <button
            type="button"
            className={`board-tab${board === '' ? ' on' : ''}`}
            onClick={() => setBoard('')}
          >
            <LayoutGrid size={13} /> 全部
          </button>
          {CATEGORIES.map((c) => (
            <button
              key={c.key}
              type="button"
              className={`board-tab${board === c.key ? ' on' : ''}`}
              onClick={() => setBoard(c.key)}
            >
              {c.label}
            </button>
          ))}
        </div>

        {items === null && !error && (
          <div className="lit-list">
            {[0, 1, 2].map((i) => (
              <SkeletonCard key={i} />
            ))}
          </div>
        )}

        {error && <EmptyState icon={LibraryIcon} title="加载失败" desc={error} />}

        {items && items.length === 0 && (
          <EmptyState
            icon={LibraryIcon}
            title="社区还没有公开文献"
            desc="上传文献时勾选「贡献到社区」即可公开，其他成员就能检索到并收到相关推荐。"
            action={
              <Link to="/upload" className="btn-primary">
                上传并公开
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
            {!hasMore && <p className="list-end">— 已展示全部社区文献 —</p>}
          </>
        )}
      </section>
    </div>
  )
}
