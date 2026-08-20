import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ArrowRight, Search } from 'lucide-react'

const SUGGESTIONS = ['大语言模型最新进展', '检索增强生成 RAG', '多模态学习']

export default function SearchHero({ initialQuery = '', compact = false }) {
  const [q, setQ] = useState(initialQuery)
  const navigate = useNavigate()

  const submit = (e) => {
    e?.preventDefault()
    const t = q.trim()
    if (t) navigate(`/search?q=${encodeURIComponent(t)}`)
  }

  return (
    <div className={`hero${compact ? ' hero-compact' : ''}`}>
      {!compact && (
        <>
          <p className="hero-eyebrow">Semantic Scholar</p>
          <h1 className="hero-title">
            向文献库提问<span className="hero-dot">。</span>
          </h1>
          <p className="hero-sub">语义向量检索 × 检索增强生成，每个回答皆可溯源</p>
        </>
      )}
      <div className="halo-wrap">
        <div className="hero-halo" aria-hidden="true" />
        <form className="hero-search" onSubmit={submit} role="search">
          <Search size={compact ? 19 : 22} strokeWidth={1.8} className="hero-search-icon" />
          <input
            value={q}
            onChange={(e) => setQ(e.target.value)}
            placeholder="输入研究问题、关键词或主题…"
            aria-label="检索文献"
          />
          <button
            type="submit"
            className="hero-go"
            disabled={!q.trim()}
            aria-label="开始检索"
          >
            <ArrowRight size={compact ? 18 : 20} strokeWidth={2} />
          </button>
        </form>
      </div>
      {!compact && (
        <div className="hero-chips">
          {SUGGESTIONS.map((s) => (
            <button key={s} type="button" onClick={() => setQ(s)}>
              {s}
            </button>
          ))}
        </div>
      )}
    </div>
  )
}
