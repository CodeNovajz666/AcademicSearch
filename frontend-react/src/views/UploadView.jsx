import { useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { FileText, FileUp, Globe2, Loader2, X } from 'lucide-react'
import { api } from '../lib/api'
import { CATEGORIES } from '../lib/categories'
import { useToast } from '../context/ToastContext'
import { useConfirm } from '../context/ConfirmContext'

const EMPTY_META = { title: '', authors: '', journal: '', year: '', doi: '' }
const MAX_FILE_SIZE = 50 * 1024 * 1024

export default function UploadView() {
  const [file, setFile] = useState(null)
  const [drag, setDrag] = useState(false)
  const [meta, setMeta] = useState(EMPTY_META)
  const [busy, setBusy] = useState(false)
  const [toCommunity, setToCommunity] = useState(false)
  const [category, setCategory] = useState('')
  const toast = useToast()
  const confirm = useConfirm()
  const navigate = useNavigate()
  const inputRef = useRef(null)

  const pick = (f) => {
    if (!f) return
    if (f.type !== 'application/pdf' || !f.name.toLowerCase().endsWith('.pdf')) {
      toast('仅支持 PDF 格式文件', 'error')
      return
    }
    if (f.size > MAX_FILE_SIZE) {
      toast('文件超过 50MB 上限，请压缩后重试', 'error')
      return
    }
    setFile(f)
  }

  const submit = async () => {
    if (!file || busy) return
    if (toCommunity && !category) {
      toast('贡献到社区时请选择一个板块', 'error')
      return
    }
    // 勾选贡献时上传即公开（全员可检索阅读），提交前二次确认
    if (toCommunity) {
      const ok = await confirm(
        '这篇文献将以社区公开方式上传：所有登录用户都可以在社区中检索和阅读它的全文与解析内容。',
        { title: '贡献到社区总知识库', confirmText: '上传并公开' }
      )
      if (!ok) return
    }
    setBusy(true)
    const fd = new FormData()
    fd.append('file', file)
    fd.append('isPublic', toCommunity ? 'true' : 'false')
    if (category) fd.append('category', category)
    Object.entries(meta).forEach(([k, v]) => {
      if (v) fd.append(k, v)
    })
    try {
      const lit = await api.uploadLiterature(fd)
      toast(toCommunity ? '上传成功，已贡献到社区总知识库' : '上传成功，文献保存在你的私有知识库')
      navigate(`/literature/${lit.id}`)
    } catch (e) {
      toast(e.message, 'error')
      setBusy(false)
    }
  }

  const set = (k) => (e) => setMeta((m) => ({ ...m, [k]: e.target.value }))

  return (
    <div className="upload-page">
      <header className="section-head">
        <div>
          <h2 className="section-title">上传文献</h2>
          <p className="section-sub">上传 PDF 后系统在后台自动提取元数据、建立向量索引并抽取知识要点</p>
        </div>
      </header>

      <div
        className={`drop-zone${drag ? ' is-drag' : ''}`}
        onClick={() => inputRef.current?.click()}
        onDragOver={(e) => {
          e.preventDefault()
          setDrag(true)
        }}
        onDragLeave={() => setDrag(false)}
        onDrop={(e) => {
          e.preventDefault()
          setDrag(false)
          pick(e.dataTransfer.files[0])
        }}
        role="button"
        aria-label="上传 PDF 文件"
      >
        <input
          ref={inputRef}
          type="file"
          accept="application/pdf,.pdf"
          hidden
          onChange={(e) => pick(e.target.files[0])}
        />
        <FileUp size={30} strokeWidth={1.4} />
        <p>点击或拖拽 PDF 文件到此处</p>
        <p className="hint">支持 .pdf 格式 · 最大 50MB</p>
        {file && (
          <span className="file-chip">
            <FileText size={15} strokeWidth={1.8} />
            {file.name}
            <button
              type="button"
              aria-label="移除文件"
              onClick={(e) => {
                e.stopPropagation()
                setFile(null)
                if (inputRef.current) inputRef.current.value = ''
              }}
            >
              <X size={14} />
            </button>
          </span>
        )}
      </div>

      <div className="upload-meta">
        <div className="field">
          <label htmlFor="up-title">标题</label>
          <input id="up-title" value={meta.title} onChange={set('title')} placeholder="可选，自动提取" />
        </div>
        <div className="field">
          <label htmlFor="up-authors">作者</label>
          <input id="up-authors" value={meta.authors} onChange={set('authors')} placeholder="逗号分隔" />
        </div>
        <div className="field">
          <label htmlFor="up-journal">期刊</label>
          <input id="up-journal" value={meta.journal} onChange={set('journal')} placeholder="可选" />
        </div>
        <div className="field">
          <label htmlFor="up-year">年份</label>
          <input id="up-year" value={meta.year} onChange={set('year')} placeholder="如 2025" inputMode="numeric" />
        </div>
        <div className="field field-full">
          <label htmlFor="up-doi">DOI</label>
          <input id="up-doi" value={meta.doi} onChange={set('doi')} placeholder="可选" />
        </div>
      </div>

      <label className="community-check">
        <input
          type="checkbox"
          checked={toCommunity}
          onChange={(e) => setToCommunity(e.target.checked)}
        />
        <Globe2 size={15} />
        <span>
          贡献到社区总知识库
          <small>勾选后所有登录用户都可检索和阅读这篇文献；不勾选则仅自己可见，随时可在详情页更改</small>
        </span>
      </label>

      {toCommunity && (
        <div className="field board-field">
          <label htmlFor="up-category">所属板块 <b className="req">*</b></label>
          <select id="up-category" value={category} onChange={(e) => setCategory(e.target.value)}>
            <option value="">请选择板块…</option>
            {CATEGORIES.map((c) => (
              <option key={c.key} value={c.key}>{c.label}</option>
            ))}
          </select>
        </div>
      )}

      <button type="button" className="btn-primary upload-submit" onClick={submit} disabled={!file || busy}>
        {busy ? (
          <>
            <Loader2 size={17} className="spin" /> 解析与索引中…
          </>
        ) : (
          '上传并建立索引'
        )}
      </button>
    </div>
  )
}
