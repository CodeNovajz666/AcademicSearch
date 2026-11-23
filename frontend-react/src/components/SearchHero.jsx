import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ArrowRight, Search } from 'lucide-react'

/** 候选池：每次从 20 个热门研究方向中随机推送 3 个 */
const SUGGESTIONS = [
  '大语言模型最新进展',
  '检索增强生成 RAG',
  '多模态学习',
  'Transformer 架构改进',
  '大模型推理加速',
  '模型蒸馏与量化',
  'RLHF 与人类反馈对齐',
  '思维链推理 CoT',
  '知识图谱与大模型结合',
  '小样本学习 Few-shot',
  'AI 智能体任务规划',
  '长上下文建模',
  '幻觉抑制与事实性',
  '多智能体协作',
  '向量数据库与近似检索',
  '文本嵌入与语义相似度',
  'AI for Science 科学发现',
  '代码生成大模型',
  '模型安全与可解释性',
  '具身智能与机器人学习',
]

/** 不放回随机抽取 n 个候选。 */
function sampleSuggestions(count) {
  const pool = [...SUGGESTIONS]
  const picks = []
  while (picks.length < count && pool.length > 0) {
    picks.push(pool.splice(Math.floor(Math.random() * pool.length), 1)[0])
  }
  return picks
}

export default function SearchHero({ initialQuery = '', compact = false }) {
  const [q, setQ] = useState(initialQuery)
  // 惰性初始化：只在挂载时抽一次，输入重渲染不会重洗候选
  const [picks] = useState(() => sampleSuggestions(3))
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
            向文献库提问<span className="hero-dot"></span>
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
          {picks.map((s) => (
            <button key={s} type="button" onClick={() => setQ(s)}>
              {s}
            </button>
          ))}
        </div>
      )}
    </div>
  )
}
