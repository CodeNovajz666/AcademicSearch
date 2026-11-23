import { createContext, useCallback, useContext, useEffect, useState } from 'react'
import { AlertTriangle } from 'lucide-react'

/**
 * 全局确认对话框：confirm(message, options) 返回 Promise<boolean>，
 * await 得到用户选择。用于不可恢复或扩大暴露面的关键操作（删除会话、公开到社区等）。
 * options: { title, confirmText, cancelText, danger }
 */
const ConfirmContext = createContext(null)

export function ConfirmProvider({ children }) {
  const [dialog, setDialog] = useState(null)

  const confirm = useCallback(
    (message, { title = '请确认操作', confirmText = '确认', cancelText = '取消', danger = false } = {}) =>
      new Promise((resolve) => setDialog({ message, title, confirmText, cancelText, danger, resolve })),
    []
  )

  const settle = (result) => {
    if (!dialog) return
    dialog.resolve(result)
    setDialog(null)
  }

  // Escape = 取消；遮罩点击 = 取消（见 overlay onClick）
  useEffect(() => {
    if (!dialog) return undefined
    const onKey = (e) => {
      if (e.key === 'Escape') settle(false)
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [dialog])

  return (
    <ConfirmContext.Provider value={confirm}>
      {children}
      {dialog && (
        <div className="confirm-overlay" role="presentation" onClick={() => settle(false)}>
          <div
            className="confirm-card"
            role="alertdialog"
            aria-modal="true"
            aria-label={dialog.title}
            onClick={(e) => e.stopPropagation()}
          >
            <div className="confirm-head">
              {dialog.danger && <AlertTriangle size={17} className="confirm-warn" />}
              <b>{dialog.title}</b>
            </div>
            <p className="confirm-msg">{dialog.message}</p>
            <div className="confirm-actions">
              <button type="button" className="btn-ghost" onClick={() => settle(false)}>
                {dialog.cancelText}
              </button>
              <button
                type="button"
                autoFocus
                className={dialog.danger ? 'btn-danger' : 'btn-primary'}
                onClick={() => settle(true)}
              >
                {dialog.confirmText}
              </button>
            </div>
          </div>
        </div>
      )}
    </ConfirmContext.Provider>
  )
}

export function useConfirm() {
  return useContext(ConfirmContext)
}
