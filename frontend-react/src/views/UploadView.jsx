import { useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { FileText, FileUp, Loader2, X } from 'lucide-react'
import { api } from '../lib/api'
import { useToast } from '../context/ToastContext'

const EMPTY_META = { title: '', authors: '', journal: '', year: '', doi: '' }
const MAX_FILE_SIZE = 50 * 1024 * 1024

export default function UploadView() {
  const [file, setFile] = useState(null)
  const [drag, setDrag] = useState(false)
  const [meta, setMeta] = useState(EMPTY_META)
  const [busy, setBusy] = useState(false)
  const toast = useToast()
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
    setBusy(true)
    const fd = new FormData()
    fd.append('file', file)
    Object.entries(meta).forEach(([k, v]) => {
      if (v) fd.append(k, v)
    })
    try {
      const lit = await api.uploadLiterature(fd)
      toast('上传成功，向量索引已建立')
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
          <p className="section-sub">上传 PDF 后系统自动提取元数据、切分文本并建立向量索引</p>
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
        <div className="field">
          <label htmlFor="up-doi">DOI</label>
          <input id="up-doi" value={meta.doi} onChange={set('doi')} placeholder="可选" />
        </div>
      </div>

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
