import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { ArrowLeft, FileWarning, Trash2 } from 'lucide-react'
import { api } from '../lib/api'
import EmptyState from '../components/EmptyState'
import { useToast } from '../context/ToastContext'

export default function DetailView() {
  const { id } = useParams()
  const navigate = useNavigate()
  const toast = useToast()
  const [lit, setLit] = useState(null)
  const [error, setError] = useState('')
  const [confirming, setConfirming] = useState(false)

  useEffect(() => {
    api
      .getLiterature(id)
      .then(setLit)
      .catch((e) => setError(e.message))
  }, [id])

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
            {lit.journal && <span className="chip">{lit.journal}</span>}
            {lit.year && <span className="chip">{lit.year}</span>}
            {lit.doi && <span className="chip">DOI · {lit.doi}</span>}
          </div>
          {lit.authors && <p className="detail-authors">{lit.authors}</p>}

          {lit.abstractText && (
            <section className="detail-section">
              <h3>摘要</h3>
              <p>{lit.abstractText}</p>
            </section>
          )}

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
