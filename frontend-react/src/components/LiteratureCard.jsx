import { useNavigate } from 'react-router-dom'
import { ArrowUpRight } from 'lucide-react'

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

export default function LiteratureCard({ item, showScore = false, index = 0 }) {
  const lit = item.literature || item
  const navigate = useNavigate()

  const meta = [lit.authors, lit.journal, lit.year].filter(Boolean).join(' · ')

  return (
    <article
      className="lit-card"
      style={{ animationDelay: `${Math.min(index, 8) * 40}ms` }}
      onClick={() => navigate(`/literature/${lit.id}`)}
    >
      <div className="lit-card-head">
        <h3 className="lit-title">{lit.title || '未命名文献'}</h3>
        {showScore && <SimilarityMeter value={item.similarity} />}
      </div>
      {meta && <p className="lit-meta">{meta}</p>}
      {lit.abstractText && <p className="lit-abstract">{lit.abstractText}</p>}
      {showScore && item.snippet && <p className="lit-snippet">{item.snippet}</p>}
      <div className="lit-foot">
        <span className="lit-doi">{lit.doi ? `DOI · ${lit.doi}` : lit.fileName || ''}</span>
        <ArrowUpRight size={16} strokeWidth={1.8} className="lit-arrow" />
      </div>
    </article>
  )
}
