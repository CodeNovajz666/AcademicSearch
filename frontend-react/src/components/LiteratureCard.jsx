import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ArrowUpRight, CheckCircle2, Star } from 'lucide-react'
import { api } from '../lib/api'
import { categoryLabel } from '../lib/categories'
import { normalizeText } from '../lib/textFormat'
import { useToast } from '../context/ToastContext'

function SimilarityMeter({ value }) {
  const pct = Math.max(0, Math.min(100, (value ?? 0) * 100))
  return (
    <div className="sim-meter">
      <div className="sim-track">
        <div className="sim-fill" style={{ width: `${pct}%` }} />
      </div>
      <span className="sim-num">{pct.toFixed(1)}%</span>
    </div>
  )
}

export default function LiteratureCard({ item, showScore = false, index = 0, showState = false }) {
  const lit = item.literature || item
  const navigate = useNavigate()
  const toast = useToast()
  const [starred, setStarred] = useState(Boolean(lit.starred))

  const meta = [lit.authors, lit.journal, lit.year].filter(Boolean).join(' · ')

  const toggleStar = async (e) => {
    e.stopPropagation()
    const next = !starred
    setStarred(next)
    try {
      await api.updateLiteratureState(lit.id, { starred: next })
    } catch (err) {
      setStarred(!next)
      toast(err.message, 'error')
    }
  }

  return (
    <article
      className="lit-card"
      style={{ animationDelay: `${Math.min(index, 8) * 40}ms` }}
      onClick={() => navigate(`/literature/${lit.id}`)}
    >
      <div className="lit-card-head">
        <h3 className="lit-title">{lit.title || '未命名文献'}</h3>
        {lit.processStatus === 'PROCESSING' && (
          <span className="chip chip-processing">解析中</span>
        )}
        {lit.isPublic === false && (
          <span className="chip chip-private" title="仅自己可见">私有</span>
        )}
        {lit.category && (
          <span className="chip chip-board">{categoryLabel(lit.category)}</span>
        )}
        {showState && lit.read && (
          <span className="chip chip-read" title="已读">
            <CheckCircle2 size={12} /> 已读
          </span>
        )}
        {showState && (
          <button
            type="button"
            className={`star-btn${starred ? ' on' : ''}`}
            title={starred ? '取消收藏' : '收藏'}
            aria-label={starred ? '取消收藏' : '收藏'}
            onClick={toggleStar}
          >
            <Star size={15} fill={starred ? 'currentColor' : 'none'} />
          </button>
        )}
        {showScore && <SimilarityMeter value={item.similarity} />}
      </div>
      {meta && <p className="lit-meta">{meta}</p>}
      {lit.abstractText && <p className="lit-abstract">{normalizeText(lit.abstractText)}</p>}
      {showScore && item.snippet && <p className="lit-snippet">{item.snippet}</p>}
      <div className="lit-foot">
        <span className="lit-doi">
          {lit.uploaderName ? `来自 ${lit.uploaderName} · ` : ''}
          {lit.doi ? `DOI · ${lit.doi}` : lit.fileName || ''}
        </span>
        <ArrowUpRight size={16} strokeWidth={1.8} className="lit-arrow" />
      </div>
    </article>
  )
}
