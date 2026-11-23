import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  Check, Flag, HelpCircle, Loader2, Notebook, NotebookPen, Pencil, Quote, Search, StickyNote, Trash2, X,
} from 'lucide-react'
import { api } from '../lib/api'
import { colorValue, typeLabel } from '../lib/notes'
import { useToast } from '../context/ToastContext'
import { useConfirm } from '../context/ConfirmContext'
import EmptyState from '../components/EmptyState'

// 类型筛选项：引用标记是批注的子类（带 citeText），单独作为筛选维度便于查看
const FILTERS = [
  { key: 'all', label: '全部' },
  { key: 'annotation', label: '批注' },
  { key: 'cite', label: '引用标记' },
  { key: 'question', label: '问题' },
  { key: 'record', label: '记录' },
]

const matchFilter = (n, filter) => {
  if (filter === 'all') return true
  if (filter === 'cite') return Boolean(n.citeText)
  return (n.type || 'annotation') === filter
}

const formatDate = (iso) => {
  const d = iso ? new Date(iso) : null
  return d && !Number.isNaN(d.getTime())
    ? d.toLocaleString('zh-CN', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' })
    : ''
}

/**
 * 笔记插件页：跨文献集中查看与管理用户笔记。
 * 内容包括工作区的原文批注、引用标记（箭头旗标）、问题与记录；
 * 支持筛选/搜索/行内编辑/删除，并可跳回工作区定位到锚点页。
 */
export default function NotesView() {
  const toast = useToast()
  const confirm = useConfirm()
  const [notes, setNotes] = useState(null)
  const [error, setError] = useState('')
  const [filter, setFilter] = useState('all')
  const [keyword, setKeyword] = useState('')
  // 行内编辑：editingId 与草稿文本，保存走 PUT
  const [editingId, setEditingId] = useState(null)
  const [editDraft, setEditDraft] = useState('')

  useEffect(() => {
    api.listNotes()
      .then((list) => setNotes(Array.isArray(list) ? list : []))
      .catch((e) => setError(e.message))
  }, [])

  const visible = useMemo(() => {
    if (!notes) return []
    const kw = keyword.trim().toLowerCase()
    return notes.filter((n) => {
      if (!matchFilter(n, filter)) return false
      if (!kw) return true
      const hay = `${n.content || ''} ${n.quote || ''} ${n.citeText || ''}`.toLowerCase()
      return hay.includes(kw)
    })
  }, [notes, filter, keyword])

  // 按文献分组，组内已按时间倒序（后端 id desc）
  const groups = useMemo(() => {
    const map = new Map()
    for (const n of visible) {
      const key = n.literatureId ?? 0
      if (!map.has(key)) {
        map.set(key, {
          literatureId: n.literatureId,
          title: n.literatureTitle || `文献 #${n.literatureId ?? '—'}`,
          notes: [],
        })
      }
      map.get(key).notes.push(n)
    }
    return [...map.values()]
  }, [visible])

  const startEdit = (n) => {
    setEditingId(n.id)
    setEditDraft(n.content || '')
  }

  const saveEdit = async (n) => {
    const text = editDraft.trim()
    if (!text) return
    const prev = notes
    setNotes((cur) => cur.map((m) => (m.id === n.id ? { ...m, content: text } : m)))
    setEditingId(null)
    try {
      await api.updateNote(n.id, { content: text })
      toast('笔记已更新')
    } catch (e) {
      setNotes(prev)
      toast(e.message, 'error')
    }
  }

  const removeNote = async (n) => {
    const ok = await confirm('删除后该笔记（含原文锚点）将不可恢复。', {
      title: '删除笔记',
      confirmText: '删除',
      danger: true,
    })
    if (!ok) return
    const prev = notes
    setNotes((cur) => cur.filter((m) => m.id !== n.id))
    try {
      await api.deleteNote(n.id)
      toast('笔记已删除')
    } catch (e) {
      setNotes(prev)
      toast(e.message, 'error')
    }
  }

  if (error) {
    return (
      <div className="page">
        <EmptyState icon={NotebookPen} title="加载失败" desc={error} />
      </div>
    )
  }

  return (
    <div className="page notes-page">
      <header className="section-head">
        <div>
          <h2 className="section-title">我的笔记</h2>
          <p className="section-sub">
            {notes
              ? `工作区批注 · 引用标记 · 问题与记录，共 ${notes.length} 条`
              : '加载中…'}
          </p>
        </div>
      </header>

      <div className="lib-toolbar notes-toolbar">
        <div className="board-tabs">
          {FILTERS.map((f) => (
            <button
              key={f.key}
              type="button"
              className={`filter-chip${filter === f.key ? ' on' : ''}`}
              onClick={() => setFilter(f.key)}
            >
              {f.label}
            </button>
          ))}
        </div>
        <label className="notes-search">
          <Search size={14} />
          <input
            value={keyword}
            placeholder="搜索笔记 / 引文内容…"
            onChange={(e) => setKeyword(e.target.value)}
          />
        </label>
      </div>

      {notes === null && (
        <div className="notes-loading"><Loader2 size={18} className="spin" /> 正在加载笔记…</div>
      )}

      {notes !== null && notes.length === 0 && (
        <EmptyState
          icon={Notebook}
          title="还没有笔记"
          desc="进入文献研究工作区：选中原文可写批注，用「标记引用」可标记文献并引出箭头，也可记录问题与想法。"
        />
      )}

      {notes !== null && notes.length > 0 && visible.length === 0 && (
        <EmptyState
          icon={Search}
          title="没有匹配的笔记"
          desc="换个筛选类型或关键词试试。"
        />
      )}

      {groups.map((g) => (
        <section className="notes-group" key={`${g.literatureId}`}>
          <header className="notes-group-head">
            <StickyNote size={15} />
            <Link to={`/literature/${g.literatureId}`} className="notes-group-title" title={g.title}>
              {g.title}
            </Link>
            <span className="notes-group-count">{g.notes.length} 条</span>
            <Link to={`/workspace/${g.literatureId}`} className="notes-group-open">
              打开工作区 →
            </Link>
          </header>
          <div className="notes-list">
            {g.notes.map((n) => (
              <article className={`note-card t-${n.type || 'annotation'}${n.citeText ? ' is-cite' : ''}`} key={n.id}>
                <span className="note-dot" style={{ background: colorValue(n.color) }} />
                <div className="note-main">
                  <div className="note-chips">
                    <span className={`note-chip t-${n.type || 'annotation'}`}>
                      {n.citeText ? <Flag size={10} /> : n.type === 'question' ? <HelpCircle size={10} /> : n.type === 'record' ? <NotebookPen size={10} /> : <Quote size={10} />}
                      {n.citeText ? '引用标记' : typeLabel(n.type)}
                    </span>
                    {n.page ? <span className="note-chip plain">第 {n.page} 页</span> : <span className="note-chip plain">全局</span>}
                    {n.quote && <span className="note-chip quote">「{n.quote.slice(0, 36)}{n.quote.length > 36 ? '…' : ''}」</span>}
                  </div>

                  {editingId === n.id ? (
                    <div className="note-edit">
                      <textarea
                        rows={3}
                        value={editDraft}
                        onChange={(e) => setEditDraft(e.target.value)}
                        onKeyDown={(e) => { if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) saveEdit(n) }}
                        autoFocus
                      />
                      <div className="note-edit-actions">
                        <button type="button" className="btn-primary" onClick={() => saveEdit(n)} disabled={!editDraft.trim()}>
                          <Check size={13} /> 保存
                        </button>
                        <button type="button" className="btn-ghost" onClick={() => setEditingId(null)}>
                          <X size={13} /> 取消
                        </button>
                      </div>
                    </div>
                  ) : (
                    <p className="note-content">{n.content || '（待补充批注内容）'}</p>
                  )}

                  <footer className="note-foot">
                    <span className="note-date">{formatDate(n.createdAt)}</span>
                    <div className="note-actions">
                      {n.page ? (
                        <Link to={`/workspace/${n.literatureId}?page=${n.page}`} className="note-loc">
                          定位原文
                        </Link>
                      ) : (
                        <Link to={`/workspace/${n.literatureId}`} className="note-loc">
                          前往文献
                        </Link>
                      )}
                      {editingId !== n.id && (
                        <button type="button" className="note-btn" title="编辑" onClick={() => startEdit(n)}>
                          <Pencil size={13} />
                        </button>
                      )}
                      <button type="button" className="note-btn danger" title="删除" onClick={() => removeNote(n)}>
                        <Trash2 size={13} />
                      </button>
                    </div>
                  </footer>
                </div>
              </article>
            ))}
          </div>
        </section>
      ))}
    </div>
  )
}
