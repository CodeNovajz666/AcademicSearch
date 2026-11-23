import { useEffect, useRef, useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import {
  BrainCircuit, Check, ChevronDown, CornerDownRight, Download, FileText, History, Pencil, Play,
  Plus, SendHorizontal, Sparkles, Square, Trash2,
} from 'lucide-react'
import { api } from '../lib/api'
import { downloadBlob } from '../lib/download'
import { useAuth } from '../context/AuthContext'
import { useToast } from '../context/ToastContext'
import { useConfirm } from '../context/ConfirmContext'

const WELCOME =
  '你好，我是 AI 研究副驾。我身后是一个 Agent 团队，可为你：\n-  检索文献库，回答研究问题\n- 精读论文，提炼方法与结论\n- 中英文互译、结构化总结\n每条回答附带可溯源的参考文献与完整执行轨迹。'

const AGENT_LABEL = {
  orchestrator: '协调者',
  search: '检索',
  reader: '精读',
  analyst: '分析',
}

const TOOL_LABEL = {
  semantic_search: '语义检索',
  list_literatures: '文献列表',
  get_literature: '文献详情',
  read_section: '分段精读',
  dispatch_search: '派发检索任务',
  dispatch_read: '派发精读任务',
  dispatch_analyze: '派发分析任务',
  translate_text: '中英翻译',
  summarize_literature: '论文总结',
  finalize: '信息收集完成',
}

function inline(text) {
  return text
    .split(/(\*\*[^*]+\*\*|`[^`]+`)/g)
    .filter(Boolean)
    .map((part, i) => {
      if (part.startsWith('**') && part.endsWith('**')) {
        return <strong key={i}>{part.slice(2, -2)}</strong>
      }
      if (part.startsWith('`') && part.endsWith('`')) {
        return <code key={i}>{part.slice(1, -1)}</code>
      }
      return <span key={i}>{part}</span>
    })
}

/** 轻量 Markdown：标题 / 列表 / 加粗 / 行内代码 */
function MdText({ text }) {
  const blocks = []
  let list = []
  const flush = (key) => {
    if (list.length) {
      blocks.push(
        <ul key={`ul-${key}`}>
          {list.map((item, j) => (
            <li key={j}>{inline(item)}</li>
          ))}
        </ul>
      )
      list = []
    }
  }
  text.split('\n').forEach((line, i) => {
    const heading = line.match(/^#{1,6}\s+(.*)/)
    const bullet = line.match(/^\s*[-*]\s+(.*)/)
    if (bullet) {
      list.push(bullet[1])
    } else {
      flush(i)
      if (heading) {
        blocks.push(<h5 key={i}>{inline(heading[1])}</h5>)
      } else if (line.trim()) {
        blocks.push(<p key={i}>{inline(line)}</p>)
      }
    }
  })
  flush('end')
  return blocks
}

function stepText(step) {
  if (step.type === 'agent_start') return `开始：${step.summary}`
  if (step.type === 'thinking') return `思考：${step.summary}`
  if (step.type === 'tool_call')
    return `${AGENT_LABEL[step.agent] || step.agent} 调用 ${TOOL_LABEL[step.toolName] || step.toolName}`
  const seconds = step.durationMs != null ? `（${(step.durationMs / 1000).toFixed(1)}s）` : ''
  return `${TOOL_LABEL[step.toolName] || step.toolName} 完成${seconds}`
}

const STEP_ICONS = {
  agent_start: Play,
  thinking: BrainCircuit,
  tool_call: CornerDownRight,
  tool_result: Check,
}

/** 轨迹行：思考步骤默认收起为两行，点击展开看完整思维链 */
function StepRow({ step }) {
  const [expanded, setExpanded] = useState(false)
  const Icon = STEP_ICONS[step.type] || CornerDownRight

  if (step.type === 'thinking') {
    return (
      <div className={`cp-step cp-step-thinking${expanded ? ' expanded' : ''}`}>
        <span className="cp-step-ic"><Icon size={10} strokeWidth={2} /></span>
        <div className="cp-step-main">
          <button
            type="button"
            className="cp-thinking"
            onClick={() => setExpanded((v) => !v)}
            title={expanded ? '收起思考' : '展开完整思考'}
          >
            <span className="cp-thinking-label">思考</span>
            <span className="cp-thinking-text">{step.summary || '（模型未输出思考内容）'}</span>
            <ChevronDown size={12} strokeWidth={2} className="cp-thinking-chev" />
          </button>
        </div>
      </div>
    )
  }

  return (
    <div className={`cp-step cp-step-${step.type}`}>
      <span className="cp-step-ic"><Icon size={10} strokeWidth={2} /></span>
      <div className="cp-step-main">
        <span className="cp-step-tx">{stepText(step)}</span>
        {step.type === 'tool_call' && step.summary && (
          <code className="cp-step-args" title={step.summary}>{step.summary}</code>
        )}
      </div>
    </div>
  )
}

function statsText(stats) {
  if (!stats) return ''
  const parts = []
  if (stats.steps != null) parts.push(`${stats.steps} 步`)
  if (stats.toolCalls != null) parts.push(`${stats.toolCalls} 次工具调用`)
  if (stats.durationMs != null) parts.push(`${(stats.durationMs / 1000).toFixed(1)}s`)
  if (stats.totalTokens != null) parts.push(`${stats.totalTokens} tokens`)
  return parts.join(' · ')
}

function StatsBar({ stats }) {
  const text = statsText(stats)
  if (!text) return null
  return <div className="cp-stats">{text}</div>
}

function fmtTime(value) {
  if (!value) return ''
  return String(value).slice(5, 16)
}

export default function CopilotPanel() {
  const { user } = useAuth()
  const toast = useToast()
  const confirm = useConfirm()
  const navigate = useNavigate()
  const location = useLocation()
  const [messages, setMessages] = useState([{ role: 'ai', text: WELCOME, refs: [], steps: [], open: false }])
  const [conversationId, setConversationId] = useState(null)
  const [input, setInput] = useState('')
  const [thinking, setThinking] = useState(false)
  const [historyOpen, setHistoryOpen] = useState(false)
  const [conversations, setConversations] = useState([])
  const [editingId, setEditingId] = useState(null)
  const [editTitle, setEditTitle] = useState('')
  const bodyRef = useRef(null)
  const abortRef = useRef(null)

  useEffect(() => {
    const el = bodyRef.current
    if (el) el.scrollTop = el.scrollHeight
  }, [messages, thinking])

  // 组件卸载（如 auth:expired 跳转登录页）时中止进行中的 SSE 流，
  // 避免后台继续传输并对已卸载组件 setState
  useEffect(() => () => { abortRef.current?.abort() }, [])

  const updateLast = (fn) =>
    setMessages((m) => {
      if (!m.length) return m
      const copy = [...m]
      copy[copy.length - 1] = fn(copy[copy.length - 1])
      return copy
    })

  /** 游客操作守卫：提示登录并跳转，登录后回到当前页 */
  const requireLogin = (feature) => {
    if (user) return true
    toast(`请先登录后再使用${feature}`)
    navigate('/login', { state: { from: location }, replace: true })
    return false
  }

  const send = async () => {
    const question = input.trim()
    if (!question || thinking) return
    if (!requireLogin('AI 问答')) return
    setMessages((m) => [
      ...m,
      { role: 'user', text: question, refs: [] },
      { role: 'ai', text: '', refs: [], steps: [], open: true, streaming: true },
    ])
    setInput('')
    setThinking(true)
    setHistoryOpen(false)

    const controller = new AbortController()
    abortRef.current = controller

    try {
      const ended = await api.agentChat({
        conversationId,
        question,
        signal: controller.signal,
        onEvent: (event, data) => {
          if (event === 'start') {
            setConversationId(data.conversationId)
          } else if (event === 'step') {
            updateLast((msg) => ({ ...msg, steps: [...msg.steps, data] }))
          } else if (event === 'token') {
            updateLast((msg) => ({ ...msg, text: msg.text + (data.content || '') }))
          } else if (event === 'final') {
            setConversationId(data.conversationId)
            updateLast((msg) => ({
              ...msg,
              text: msg.text || '（未生成回答）',
              refs: data.references || [],
              stats: data.stats || null,
              streaming: false,
              open: false,
            }))
          } else if (event === 'error') {
            updateLast((msg) => ({
              ...msg,
              text: `执行失败：${data.message}`,
              streaming: false,
              open: false,
            }))
          }
        },
      })
      // 静默断流：流正常结束但没有 final/error 收尾（网关超时/后端崩溃），
      // 必须复位 streaming 并提示，否则光标永远闪烁
      if (!ended) {
        updateLast((msg) => ({
          ...msg,
          text: msg.text ? `${msg.text}\n\n（连接中断，回答可能不完整）` : '（连接中断，请重试）',
          streaming: false,
          open: false,
        }))
      }
    } catch (e) {
      if (e.name === 'AbortError') {
        updateLast((msg) => ({
          ...msg,
          text: msg.text || '（已停止）',
          streaming: false,
          open: false,
        }))
      } else {
        updateLast((msg) => ({
          ...msg,
          text: `${msg.text ? msg.text + '\n\n' : ''}执行失败：${e.message}`,
          streaming: false,
          open: false,
        }))
      }
    } finally {
      setThinking(false)
      abortRef.current = null
    }
  }

  const stop = () => abortRef.current?.abort()

  const newChat = () => {
    setConversationId(null)
    setHistoryOpen(false)
    setMessages([{ role: 'ai', text: WELCOME, refs: [], steps: [], open: false }])
  }

  const toggleHistory = async () => {
    if (!requireLogin('历史会话')) return
    const next = !historyOpen
    setHistoryOpen(next)
    if (next) {
      try {
        setConversations(await api.agentConversations())
      } catch {
        setConversations([])
      }
    }
  }

  const loadConversation = async (id) => {
    try {
      const history = await api.agentMessages(id)
      setConversationId(id)
      setHistoryOpen(false)
      setMessages(
        history.map((message) => ({
          role: message.role === 'user' ? 'user' : 'ai',
          text: message.content || '',
          refs: message.role === 'assistant' ? message.references || [] : [],
          stats: message.role === 'assistant' ? message.stats || null : null,
          steps: message.steps || [],
          open: false,
        }))
      )
    } catch (e) {
      setHistoryOpen(false)
      setMessages((m) => [...m, { role: 'ai', text: `加载会话失败：${e.message}`, refs: [] }])
    }
  }

  const removeConversation = async (id) => {
    const ok = await confirm('删除后该会话的全部消息与执行轨迹将不可恢复。', {
      title: '删除会话',
      confirmText: '删除',
      danger: true,
    })
    if (!ok) return
    try {
      await api.agentDeleteConversation(id)
      setConversations((list) => list.filter((c) => c.id !== id))
      if (id === conversationId) newChat()
    } catch (e) {
      toast(`删除失败：${e.message}`, 'error')
    }
  }

  const commitRename = async (id) => {
    const title = editTitle.trim()
    setEditingId(null)
    if (!title) return
    if (conversations.find((c) => c.id === id)?.title === title) return
    try {
      const updated = await api.agentRenameConversation(id, title)
      setConversations((list) => list.map((c) => (c.id === id ? updated : c)))
    } catch (e) {
      toast(`重命名失败：${e.message}`, 'error')
    }
  }

  const exportMarkdown = () => {
    const hasContent = messages.some(
      (m) => m.role === 'user' || m.steps?.length > 0 || m.refs?.length > 0
    )
    if (!hasContent) return
    const title =
      conversations.find((c) => c.id === conversationId)?.title ||
      `研究对话 ${new Date().toLocaleDateString('zh-CN')}`
    const lines = [`# ${title}`, '', `> 导出自 AI 研究副驾 · ${new Date().toLocaleString('zh-CN')}`, '']
    messages.forEach((msg) => {
      if (msg.role === 'user') {
        lines.push('## 提问', '', msg.text, '')
      } else if (msg.text && msg.text !== WELCOME) {
        lines.push('## 回答', '', msg.text, '')
        if (msg.steps?.length) {
          lines.push('<details>', '<summary>Agent 执行轨迹</summary>', '')
          msg.steps.forEach((step) => lines.push(`- ${stepText(step)}`))
          lines.push('', '</details>', '')
        }
        if (msg.refs?.length) {
          lines.push('**参考文献**', '')
          msg.refs.forEach((lit) => lines.push(`- ${lit.title}`))
          lines.push('')
        }
        const stats = statsText(msg.stats)
        if (stats) lines.push('---', `*${stats}*`, '')
      }
    })
    const blob = new Blob([lines.join('\n')], { type: 'text/markdown;charset=utf-8' })
    // 统一走 downloadBlob：挂载 <a>（Firefox 兼容）+ 回收 objectURL
    downloadBlob(blob, `${title.replace(/[\\/:*?"<>|]/g, '_')}.md`)
  }

  return (
    <aside className="copilot" aria-label="AI 副驾面板">
      <header className="copilot-head">
        <span className="copilot-badge">
          <Sparkles size={18} strokeWidth={1.8} />
        </span>
        <div className="copilot-head-text">
          <h3>AI 研究副驾</h3>
          <p>
            <i className="pulse-dot" />
            Agent 团队 · 检索 / 翻译 / 总结
          </p>
        </div>
        <div className="copilot-actions">
          <button type="button" onClick={exportMarkdown} aria-label="导出会话" title="导出为 Markdown">
            <Download size={16} strokeWidth={1.8} />
          </button>
          <button type="button" onClick={toggleHistory} aria-label="历史会话" title="历史会话">
            <History size={16} strokeWidth={1.8} />
          </button>
          <button type="button" onClick={newChat} aria-label="新会话" title="新会话">
            <Plus size={16} strokeWidth={1.8} />
          </button>
        </div>
      </header>

      {historyOpen && (
        <div className="cp-history">
          {conversations.length === 0 ? (
            <p className="cp-history-empty">暂无历史会话</p>
          ) : (
            conversations.map((conversation) => (
              <div
                key={conversation.id}
                className={`cp-history-item ${conversation.id === conversationId ? 'active' : ''}`}
              >
                {editingId === conversation.id ? (
                  <input
                    className="cp-rename-input"
                    value={editTitle}
                    autoFocus
                    maxLength={60}
                    onChange={(e) => setEditTitle(e.target.value)}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter') commitRename(conversation.id)
                      if (e.key === 'Escape') setEditingId(null)
                    }}
                    onBlur={() => commitRename(conversation.id)}
                    aria-label="会话标题"
                  />
                ) : (
                  <>
                    <button
                      type="button"
                      className="cp-history-load"
                      onClick={() => loadConversation(conversation.id)}
                    >
                      <span>{conversation.title || '未命名会话'}</span>
                      <time>{fmtTime(conversation.updatedAt)}</time>
                    </button>
                    <div className="cp-history-ops">
                      <button
                        type="button"
                        className="cp-history-edit"
                        onClick={() => {
                          setEditingId(conversation.id)
                          setEditTitle(conversation.title || '')
                        }}
                        aria-label="重命名会话"
                        title="重命名"
                      >
                        <Pencil size={13} strokeWidth={1.8} />
                      </button>
                      <button
                        type="button"
                        className="cp-history-del"
                        onClick={() => removeConversation(conversation.id)}
                        aria-label="删除会话"
                        title="删除"
                      >
                        <Trash2 size={14} strokeWidth={1.8} />
                      </button>
                    </div>
                  </>
                )}
              </div>
            ))
          )}
        </div>
      )}

      <div className="copilot-body" ref={bodyRef}>
        {messages.map((msg, i) => (
          <div key={i} className={`cp-msg ${msg.role === 'user' ? 'cp-user' : 'cp-ai'}`}>
            {msg.role === 'user' ? (
              msg.text
            ) : (
              <>
                {msg.steps?.length > 0 && (
                  <div className="cp-trace">
                    <button
                      type="button"
                      className="cp-trace-toggle"
                      onClick={() =>
                        setMessages((m) =>
                          m.map((item, j) => (j === i ? { ...item, open: !item.open } : item))
                        )
                      }
                    >
                      {msg.open ? '收起思维链路' : `思维链路 · ${msg.steps.length} 步`}
                    </button>
                    {msg.open && (
                      <div className="cp-steps">
                        {msg.steps.map((step, j) => (
                          <StepRow key={j} step={step} />
                        ))}
                      </div>
                    )}
                  </div>
                )}
                {msg.text && (
                  <div className="cp-answer">
                    <MdText text={msg.text} />
                    {msg.streaming && <span className="cp-cursor" />}
                  </div>
                )}
                {!msg.streaming && <StatsBar stats={msg.stats} />}
                {msg.refs?.length > 0 && (
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
              </>
            )}
          </div>
        ))}
        {thinking && messages[messages.length - 1]?.steps?.length === 0 && !messages[messages.length - 1]?.text && (
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
          placeholder="提出研究问题，Agent 团队为你研究…"
          disabled={thinking}
          aria-label="输入问题"
        />
        {thinking ? (
          <button type="button" className="copilot-send stop" onClick={stop} aria-label="停止生成">
            <Square size={15} strokeWidth={2} />
          </button>
        ) : (
          <button
            type="button"
            className="copilot-send"
            onClick={send}
            disabled={!input.trim()}
            aria-label="发送问题"
          >
            <SendHorizontal size={17} strokeWidth={2} />
          </button>
        )}
      </footer>
    </aside>
  )
}
