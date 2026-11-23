import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import {
  ArrowLeft, BookOpenText, Check, CheckCircle2, Copy, Download, ExternalLink, Eye, EyeOff,
  FileWarning, FlaskConical, ListChecks, Loader2, Microscope, NotebookPen, Sparkles,
  Star, Trash2, TriangleAlert,
} from 'lucide-react'
import { api } from '../lib/api'
import { toBibtex, toGBT7714, toAPA } from '../lib/citation'
import { normalizeText } from '../lib/textFormat'
import { downloadBlob } from '../lib/download'
import EmptyState from '../components/EmptyState'
import { useAuth } from '../context/AuthContext'
import { useToast } from '../context/ToastContext'
import { useConfirm } from '../context/ConfirmContext'

const POLL_INTERVAL = 3000

const CITATIONS = [
  { key: 'bibtex', label: 'BibTeX（LaTeX）', gen: toBibtex },
  { key: 'gbt', label: 'GB/T 7714（中文期刊）', gen: toGBT7714 },
  { key: 'apa', label: 'APA', gen: toAPA },
]

export default function DetailView() {
  const { id } = useParams()
  const navigate = useNavigate()
  const toast = useToast()
  const confirm = useConfirm()
  const [lit, setLit] = useState(null)
  const [error, setError] = useState('')
  const [confirming, setConfirming] = useState(false)
  const [pdfUrl, setPdfUrl] = useState('')
  const [pdfLoading, setPdfLoading] = useState(false)
  const [noteDraft, setNoteDraft] = useState('')
  const [noteSaving, setNoteSaving] = useState(false)
  const pdfBlobRef = useRef(null)

  useEffect(() => {
    api
      .getLiterature(id)
      .then((data) => {
        setLit(data)
        setNoteDraft(data.note || '')
      })
      .catch((e) => setError(e.message))
  }, [id])

  // 后台解析未完成时轮询详情，直到 READY / FAILED。
  // 依赖只留解析状态：轮询响应更新 lit 引用不会销毁重建 interval；
  // 上限 10 分钟——后端卡住时不再无限轮询，用户可手动刷新或重新解析
  useEffect(() => {
    if (!lit || lit.processStatus !== 'PROCESSING') return undefined
    const startedAt = Date.now()
    const timer = setInterval(async () => {
      if (Date.now() - startedAt > 10 * 60 * 1000) {
        clearInterval(timer)
        return
      }
      try {
        const fresh = await api.getLiterature(id)
        setLit((prev) => (prev && prev.processStatus === 'PROCESSING' ? fresh : prev))
      } catch {
        /* 轮询失败静默重试 */
      }
    }, POLL_INTERVAL)
    return () => clearInterval(timer)
  }, [lit?.processStatus, id])

  // 组件卸载或重新加载时释放 blob，避免内存泄漏
  useEffect(() => () => {
    if (pdfBlobRef.current) URL.revokeObjectURL(pdfBlobRef.current)
  }, [])

  const loadPdf = async () => {
    if (pdfUrl || pdfLoading) return
    setPdfLoading(true)
    try {
      const blob = await api.getLiteratureFile(id)
      const url = URL.createObjectURL(blob)
      pdfBlobRef.current = url
      setPdfUrl(url)
    } catch (e) {
      toast(e.message, 'error')
    } finally {
      setPdfLoading(false)
    }
  }

  const downloadPdf = async () => {
    try {
      // 统一走 downloadBlob：挂载 <a>（Firefox 兼容）+ 回收 objectURL（此前每次下载泄漏一个）
      downloadBlob(await api.getLiteratureFile(id), lit?.fileName || 'literature.pdf')
    } catch (e) {
      toast(e.message, 'error')
    }
  }

  const patchState = async (payload, okMsg) => {
    try {
      const fresh = await api.updateLiteratureState(id, payload)
      setLit((prev) => ({ ...prev, starred: fresh.starred, read: fresh.read, note: fresh.note }))
      if (okMsg) toast(okMsg)
    } catch (e) {
      toast(e.message, 'error')
    }
  }

  const toggleStar = () => {
    if (!lit) return
    patchState({ starred: !lit.starred }, !lit.starred ? '已加入收藏' : '已取消收藏')
  }

  const toggleRead = () => {
    if (!lit) return
    patchState({ read: !lit.read })
  }

  const saveNote = async () => {
    setNoteSaving(true)
    try {
      await patchState({ note: noteDraft }, '笔记已保存')
    } finally {
      setNoteSaving(false)
    }
  }

  const copyText = async (text) => {
    try {
      await navigator.clipboard.writeText(text)
      toast('已复制到剪贴板')
    } catch {
      toast('复制失败，请手动选择复制', 'error')
    }
  }

  const { user } = useAuth()
  const isOwner = lit && user && lit.userId === user.id

  const toggleVisibility = async () => {
    const next = !lit.isPublic
    // 公开把全文与解析内容暴露给所有登录用户，属于扩大暴露面的动作，先确认；
    // 设回私有是收敛暴露面，直接执行
    if (next) {
      const ok = await confirm(
        '公开后所有登录用户都可以在社区中检索和阅读这篇文献的全文与解析内容。',
        { title: '公开到社区总知识库', confirmText: '公开' }
      )
      if (!ok) return
    }
    try {
      const fresh = await api.updateLiteratureVisibility(id, next)
      setLit((prev) => ({ ...prev, isPublic: fresh.isPublic }))
      toast(next ? '已公开到社区总知识库' : '已设为私有')
    } catch (e) {
      toast(e.message, 'error')
    }
  }

  const remove = async () => {
    try {
      await api.deleteLiterature(id)
      toast('文献已删除')
      navigate('/')
    } catch (e) {
      toast(e.message, 'error')
      setConfirming(false)
    }
  }

  const knowledge = [
    { icon: BookOpenText, title: '主要内容', body: lit?.mainContent },
    { icon: Microscope, title: '研究方法与操作', body: lit?.methods },
    { icon: FlaskConical, title: '实验与结果', body: lit?.experiments },
    { icon: ListChecks, title: '结论', body: lit?.conclusions },
  ]

  return (
    <div className="page page-detail">
      <button type="button" className="back-link" onClick={() => navigate(-1)}>
        <ArrowLeft size={15} /> 返回
      </button>

      {error && <EmptyState icon={FileWarning} title="无法加载文献" desc={error} />}

      {lit && (
        <article className="detail-card">
          <h1 className="detail-title">{lit.title || '未命名文献'}</h1>
          <div className="detail-chips">
            <span className={`chip ${lit.isPublic === false ? 'chip-private' : 'chip-public'}`}>
              {lit.isPublic === false ? '私有 · 仅自己可见' : '已公开到社区'}
            </span>
            {lit.journal && <span className="chip">{lit.journal}</span>}
            {lit.year && <span className="chip">{lit.year}</span>}
            {lit.doi && <span className="chip">DOI · {lit.doi}</span>}
          </div>
          {lit.authors && <p className="detail-authors">{lit.authors}</p>}

          <div className="detail-actions">
            <Link to={`/workspace/${lit.id}`} className="btn-primary" style={{ textDecoration: 'none' }}>
              <Sparkles size={15} /> 研究工作区
            </Link>
            <button type="button" className="btn-ghost" onClick={loadPdf} disabled={pdfLoading}>
              {pdfLoading ? <Loader2 size={15} className="spin" /> : <BookOpenText size={15} />}
              {pdfUrl ? '原文预览中' : pdfLoading ? '加载原文…' : '预览原文'}
            </button>
            <button type="button" className="btn-ghost" onClick={downloadPdf}>
              <Download size={15} /> 下载 PDF
            </button>
            <button
              type="button"
              className={`btn-ghost${lit.starred ? ' is-starred' : ''}`}
              onClick={toggleStar}
              title={lit.starred ? '取消收藏' : '收藏'}
            >
              <Star size={15} fill={lit.starred ? 'currentColor' : 'none'} />
              {lit.starred ? '已收藏' : '收藏'}
            </button>
            <button
              type="button"
              className={`btn-ghost${lit.read ? ' is-read' : ''}`}
              onClick={toggleRead}
              title={lit.read ? '标记为未读' : '标记为已读'}
            >
              {lit.read ? <CheckCircle2 size={15} /> : <BookOpenText size={15} style={{ opacity: 0.45 }} />}
              {lit.read ? '已读' : '未读'}
            </button>
            {isOwner && (
              <button
                type="button"
                className="btn-ghost"
                onClick={toggleVisibility}
                title={lit.isPublic === false ? '公开到社区总知识库' : '设为私有'}
              >
                {/* 按钮显示"点击后要执行的动作"：私有→公开到社区，公开→设为私有 */}
                {lit.isPublic === false ? <Eye size={15} /> : <EyeOff size={15} />}
                {lit.isPublic === false ? '公开到社区' : '设为私有'}
              </button>
            )}
          </div>

          {pdfUrl && (
            <div className="pdf-preview">
              <div className="pdf-preview-head">
                <span>原文预览 · {lit.fileName}</span>
                <button type="button" className="btn-ghost" onClick={() => window.open(pdfUrl, '_blank')}>
                  <ExternalLink size={14} /> 新窗口打开
                </button>
              </div>
              <iframe src={pdfUrl} title="原文 PDF 预览" className="pdf-frame" />
            </div>
          )}

          {lit.processStatus === 'PROCESSING' && (
            <div className="detail-banner is-processing">
              <Loader2 size={16} className="spin" />
              正在解析全文：构建向量索引 + AI 抽取知识要点（主要内容/方法/实验/结论），完成后自动刷新…
            </div>
          )}
          {lit.processStatus === 'FAILED' && (
            <div className="detail-banner is-failed">
              <TriangleAlert size={16} />
              后台解析失败，文献已保存但知识要点缺失，建议删除后重新上传。
            </div>
          )}

          {lit.abstractText && (
            <section className="detail-section">
              <h3>摘要</h3>
              <p>{normalizeText(lit.abstractText)}</p>
            </section>
          )}

          {lit.processStatus !== 'PROCESSING' &&
            knowledge.map(({ icon: Icon, title, body }) =>
              body ? (
                <section className="detail-section" key={title}>
                  <h3>
                    <Icon size={15} /> {title}
                  </h3>
                  <p>{body}</p>
                </section>
              ) : null
            )}

          {lit.processStatus === 'READY' &&
            !lit.mainContent && !lit.methods && !lit.experiments && !lit.conclusions && (
              <div className="detail-banner is-failed">
                <TriangleAlert size={16} />
                知识要点抽取无结果（原文可能为扫描件或非论文文本），全文与向量检索不受影响。
              </div>
            )}

          <section className="detail-section">
            <h3>引用格式</h3>
            <div className="cite-list">
              {CITATIONS.map(({ key, label, gen }) => (
                <div className="cite-row" key={key}>
                  <div className="cite-meta">
                    <span className="cite-label">{label}</span>
                    <button type="button" className="btn-ghost cite-copy" onClick={() => copyText(gen(lit))}>
                      <Copy size={13} /> 复制
                    </button>
                  </div>
                  <pre className="cite-text">{gen(lit)}</pre>
                </div>
              ))}
            </div>
          </section>

          <section className="detail-section">
            <h3>
              <NotebookPen size={15} /> 阅读笔记
              <span className="note-hint">仅自己可见</span>
            </h3>
            <textarea
              className="note-editor"
              value={noteDraft}
              placeholder="记录阅读心得、待验证的问题、与自己课题的关联……"
              rows={5}
              onChange={(e) => setNoteDraft(e.target.value)}
            />
            <div className="note-actions">
              <button
                type="button"
                className="btn-primary"
                disabled={noteSaving || noteDraft === (lit.note || '')}
                onClick={saveNote}
              >
                {noteSaving ? <Loader2 size={14} className="spin" /> : <Check size={14} />} 保存笔记
              </button>
            </div>
          </section>

          <footer className="detail-foot">
            <span className="detail-file">
              源文件 · {lit.fileName} · 入库于 {lit.createdAt || '未知时间'}
            </span>
            {!confirming ? (
              <button type="button" className="btn-danger-ghost" onClick={() => setConfirming(true)}>
                <Trash2 size={15} /> 删除文献
              </button>
            ) : (
              <span className="confirm-inline">
                确认删除？此操作不可恢复
                <button type="button" className="btn-danger" onClick={remove}>
                  删除
                </button>
                <button type="button" className="btn-ghost" onClick={() => setConfirming(false)}>
                  取消
                </button>
              </span>
            )}
          </footer>
        </article>
      )}
    </div>
  )
}
