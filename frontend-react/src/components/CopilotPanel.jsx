import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { FileText, SendHorizontal, Sparkles } from 'lucide-react'
import { api } from '../lib/api'

const WELCOME =
  '你好，我是学术副驾。\n基于文献库回答你的研究问题，每条回答都附带可溯源的参考文献。'

export default function CopilotPanel() {
  const [messages, setMessages] = useState([{ role: 'ai', text: WELCOME, refs: [] }])
  const [input, setInput] = useState('')
  const [thinking, setThinking] = useState(false)
  const bodyRef = useRef(null)

  useEffect(() => {
    const el = bodyRef.current
    if (el) el.scrollTop = el.scrollHeight
  }, [messages, thinking])

  const send = async () => {
    const q = input.trim()
    if (!q || thinking) return
    setMessages((m) => [...m, { role: 'user', text: q, refs: [] }])
    setInput('')
    setThinking(true)
    try {
      const data = await api.ragQuery(q, 5)
      setMessages((m) => [
        ...m,
        { role: 'ai', text: data.answer || '（未生成回答）', refs: data.references || [] },
      ])
    } catch (e) {
      setMessages((m) => [
        ...m,
        { role: 'ai', text: `回答生成失败：${e.message}`, refs: [] },
      ])
    } finally {
      setThinking(false)
    }
  }

  return (
    <aside className="copilot" aria-label="AI 副驾面板">
      <header className="copilot-head">
        <span className="copilot-badge">
          <Sparkles size={18} strokeWidth={1.8} />
        </span>
        <div>
          <h3>AI 副驾</h3>
          <p>
            <i className="pulse-dot" />
            语义检索 · 可溯源回答
          </p>
        </div>
      </header>

      <div className="copilot-body" ref={bodyRef}>
        {messages.map((msg, i) => (
          <div key={i} className={`cp-msg ${msg.role === 'user' ? 'cp-user' : 'cp-ai'}`}>
            {msg.text}
            {msg.role === 'ai' && msg.refs?.length > 0 && (
              <div className="cp-refs">
                <span className="cp-refs-label">参考文献 · {msg.refs.length}</span>
                {msg.refs.map((lit) => (
                  <Link key={lit.id} to={`/literature/${lit.id}`} className="ref-chip">
                    <FileText size={13} strokeWidth={1.8} />
                    <span>{lit.title}</span>
                  </Link>
                ))}
              </div>
            )}
          </div>
        ))}
        {thinking && (
          <div className="typing" aria-label="正在思考">
            <i />
            <i />
            <i />
          </div>
        )}
      </div>

      <footer className="copilot-input">
        <input
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && send()}
          placeholder="向文献库提问…"
          disabled={thinking}
          aria-label="输入问题"
        />
        <button
          type="button"
          className="copilot-send"
          onClick={send}
          disabled={!input.trim() || thinking}
          aria-label="发送问题"
        >
          <SendHorizontal size={17} strokeWidth={2} />
        </button>
      </footer>
    </aside>
  )
}
