import { useEffect, useMemo, useRef, useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import * as pdfjsLib from 'pdfjs-dist'
import workerUrl from 'pdfjs-dist/build/pdf.worker.min.mjs?url'
import {
  ArrowLeft, BookOpenText, ChevronDown, ChevronRight, ChevronUp, Copy, Download, Flag, FlaskConical,
  Highlighter, Lightbulb, ListChecks, ListTree, Loader2, MessageSquarePlus, Microscope, NotebookPen,
  Palette, Pencil, Quote, ScrollText, Send, Sparkles, StickyNote, Trash2, X,
} from 'lucide-react'
import { api, agentChat } from '../lib/api'
import { normalizeText } from '../lib/textFormat'
import { downloadBlob } from '../lib/download'
import { NOTE_COLORS, NOTE_TYPES, colorValue, findCitation, normalizeNote, typeLabel } from '../lib/notes'
import { useToast } from '../context/ToastContext'
import { useConfirm } from '../context/ConfirmContext'

pdfjsLib.GlobalWorkerOptions.workerSrc = workerUrl

const SECTION_ANCHORS = [
  // flat：压缩文本兜底关键词（忽略空格/大小写的包含匹配），用于行首正则漏检的排版
  { key: 'abstract', patterns: [/^abstract\b/i, /^摘\s*要/], flat: ['abstract', '摘要'] },
  { key: 'methods', patterns: [/^\d+(\.\d+)*\.?\s+method/i, /^proposed method/i, /^approach/i, /^方法/, /^model architecture/i, /^\d+(\.\d+)*\.?\s+model/i] },
  { key: 'experiments', patterns: [/^\d+(\.\d+)*\.?\s+experiment/i, /^experiments/i, /^实验/, /^results/i] },
  { key: 'conclusions', patterns: [/^\d+(\.\d+)*\.?\s+conclusion/i, /^conclusions?\b/i, /^结\s*论/] },
]

const QUICK_QUESTIONS = [
  '这篇论文的核心创新点是什么？请引用原文证据说明',
  '提取论文中的具体方法，逐步讲清楚是怎么做的',
  '这篇论文的实验部分证明了什么？关键数字有哪些？',
  '论文的方法与之前的工作相比，改进在哪里？',
]

const AI_IDEAS_PROMPT = '请总结这篇论文的创新点和可改进之处，并引用原文证据说明'

const GENERIC_HEADINGS = /^(abstract|摘要|introduction|引言|related work|相关工作|background|preliminaries|预备知识|method(s)?|方法|approach|proposed method|model|实验|experiments?|experimental (setup|results|evaluation)|results|results and (discussion|analysis)|evaluation|discussion|conclusions?|结论|references|参考文献|acknowledg(e)?ments?|致谢|appendix|附录)\s*$/i
const NUMBERED_HEADING = /^(\d+(?:\.\d+)*)\.?\s+([A-Z][^\s].{1,80})$/

/** caret 定位兼容：Firefox 走 caretPositionFromPoint，其余走 caretRangeFromPoint */
function caretRangeFromPoint(x, y) {
  if (document.caretRangeFromPoint) return document.caretRangeFromPoint(x, y)
  if (document.caretPositionFromPoint) {
    const pos = document.caretPositionFromPoint(x, y)
    if (!pos) return null
    const r = document.createRange()
    r.setStart(pos.offsetNode, pos.offset)
    r.collapse(true)
    return r
  }
  return null
}

/** 本机临时 id（Date.now() 毫秒级）与后端 id（自增小整数）的区分：>1e11 视为本机数据 */
const isLocalId = (v) => typeof v === 'number' && v > 1e11

/** 笔记排序时间：入库笔记用 createdAt，本机旧数据用 id（Date.now 毫秒）兜底 */
const noteTime = (n) => (n.createdAt ? Date.parse(n.createdAt) : isLocalId(n.id) ? n.id : 0)

async function flattenOutline(outline, pdfDoc, level = 0, out = []) {
  for (const item of outline || []) {
    let page = null
    let pending = null
    try {
      if (Array.isArray(item.dest) && item.dest[0]) {
        // getPageIndex 是异步接口，必须 await，否则页码会变成 "[object Promise]N" 字符串
        page = (await pdfDoc.getPageIndex(item.dest[0])) + 1
      } else if (typeof item.dest === 'string') {
        pending = item.dest
      }
    } catch { /* 无法解析则不显示页码 */ }
    out.push({ title: (item.title || '').trim(), page, level, pending })
    if (item.items?.length) await flattenOutline(item.items, pdfDoc, level + 1, out)
  }
  return out
}

/** 无书签时按文本扫描生成目录：每页首个 "N 标题" 或通用章节行 */
async function scanTextToc(pdfDoc, maxPages = 60) {
  const toc = []
  const limit = Math.min(pdfDoc.numPages, maxPages)
  for (let p = 1; p <= limit; p++) {
    const page = await pdfDoc.getPage(p)
    const content = await page.getTextContent()
    const lines = []
    let line = ''
    for (const item of content.items) {
      if (item.str == null) continue
      line += item.str
      if (item.hasEOL) { lines.push(line.trim()); line = '' }
    }
    if (line.trim()) lines.push(line.trim())
    for (const raw of lines) {
      const text = raw.replace(/\s+/g, ' ').trim()
      const numbered = text.match(NUMBERED_HEADING)
      const generic = text.length <= 40 && GENERIC_HEADINGS.test(text)
      if (numbered) {
        toc.push({ title: `${numbered[1]} ${numbered[2]}`, page: p, level: (numbered[1].match(/\./g) || []).length })
        break
      }
      if (generic) {
        toc.push({ title: text, page: p, level: 0 })
        break
      }
    }
  }
  return toc
}

/**
 * 定位各知识分区起始页。三级策略，命中即止：
 * 1. PDF 书签标题匹配（页码最准，tocEntries 已解析好页码）；
 * 2. 逐页文本行首正则（标题独占一行时命中）；
 * 3. 摘要类的压缩文本包含匹配（兜底 "A B S T R A C T" 等字距拉开/被拆分的排版；
 *    method/results 等词正文太常见，不做兜底以免误跳第 1 页）。
 */
async function locateSections(pdfDoc, tocEntries = [], maxPages = 60) {
  const found = {}
  const limit = Math.min(pdfDoc.numPages, maxPages)

  for (const anchor of SECTION_ANCHORS) {
    const hit = tocEntries.find(
      (e) => e.page && anchor.patterns.some((re) => re.test((e.title || '').trim()))
    )
    if (hit) found[anchor.key] = hit.page
  }

  const pages = []
  for (let p = 1; p <= limit; p++) {
    const page = await pdfDoc.getPage(p)
    const items = (await page.getTextContent()).items.map((i) => i.str || '')
    pages.push({
      lines: items,
      flat: items.join('').toLowerCase().replace(/\s+/g, ''),
    })
  }
  for (const anchor of SECTION_ANCHORS) {
    if (found[anchor.key]) continue
    for (let p = 0; p < pages.length; p++) {
      const lineHit = pages[p].lines.some((l) => anchor.patterns.some((re) => re.test(l.trim())))
      const flatHit = anchor.flat?.some((kw) => pages[p].flat.includes(kw))
      if (lineHit || flatHit) { found[anchor.key] = p + 1; break }
    }
  }
  return found
}

export default function WorkspaceView() {
  const { id } = useParams()
  const [searchParams, setSearchParams] = useSearchParams()
  const toast = useToast()
  const confirm = useConfirm()

  const [lit, setLit] = useState(null)
  const [fatal, setFatal] = useState('')
  const [doc, setDoc] = useState(null)
  const [anchorPages, setAnchorPages] = useState({})
  const [pageNo, setPageNo] = useState(1)
  const [toc, setToc] = useState([])
  const [tocOpen, setTocOpen] = useState(false)
  const [jumpTo, setJumpTo] = useState('')

  // 对话状态
  const [messages, setMessages] = useState([])
  const [input, setInput] = useState('')
  const [streaming, setStreaming] = useState(false)
  const [conversationId, setConversationId] = useState(null)
  const abortRef = useRef(null)
  const chatEndRef = useRef(null)

  // 左下笔记插件：批注 / 引用标记 / 问题 / 记录，正文持久化到后端（本机只留颜色与创新点偏好）
  const [dockPanel, setDockPanel] = useState('')
  const [dockCollapsed, setDockCollapsed] = useState(false)
  const [noteDraft, setNoteDraft] = useState('')
  const [noteColor, setNoteColor] = useState('yellow')
  const [notes, setNotes] = useState([])
  const [ideas, setIdeas] = useState({ innovation: '', improvement: '' })
  // draftType：当前正在写的笔记类型（批注/问题/记录）；editingId：正在编辑的笔记；
  // markMode：引用标记模式，点击/框选原文中的引用即从该处引出箭头
  const [draftType, setDraftType] = useState('annotation')
  const [editingId, setEditingId] = useState(null)
  const [markMode, setMarkMode] = useState(false)
  // 选中文本 → 锚点批注：pendingAnchor 为待写入的锚点，selBubble 为选区旁的悬浮按钮，
  // openAnchor 为点开角标弹卡的批注 id，pulseId 为正在脉冲高亮的批注 id
  const [pendingAnchor, setPendingAnchor] = useState(null)
  const [selBubble, setSelBubble] = useState(null)
  const [openAnchor, setOpenAnchor] = useState(null)
  const [pulseId, setPulseId] = useState(null)

  // PDF 渲染 refs
  const pagesBoxRef = useRef(null)
  const pageRefs = useRef({})
  const rendered = useRef(new Set())
  const renderTasks = useRef(new Map())
  const textLayerRefs = useRef({})
  const textTasks = useRef(new Map())
  const noteInputRef = useRef(null)

  useEffect(() => () => {
    abortRef.current?.abort()
    renderTasks.current.forEach((t) => { try { t.cancel() } catch { /* noop */ } })
    textTasks.current.forEach((t) => { try { t.cancel() } catch { /* noop */ } })
  }, [])

  // 笔记加载：正文/锚点存于后端（ws_note 表），本机 localStorage 仅保留颜色与创新点；
  // 首次升级时把历史 localStorage 批注迁移入库，后端不可用时退回本机数据保证可读
  useEffect(() => {
    let cancelled = false
    const dockKey = `as.ws.${id}`
    setNotes([])
    setNoteColor('yellow')
    setIdeas({ innovation: '', improvement: '' })
    setDockPanel('')
    setDockCollapsed(false)
    setNoteDraft('')
    setDraftType('annotation')
    setEditingId(null)
    setMarkMode(false)
    setPendingAnchor(null)
    setSelBubble(null)
    setOpenAnchor(null)

    let saved = null
    try {
      const raw = localStorage.getItem(dockKey)
      saved = raw ? JSON.parse(raw) : null
    } catch { /* 隐私模式或数据损坏时保持空状态 */ }
    if (saved) {
      setNoteColor(saved.color || 'yellow')
      setIdeas({ innovation: saved.innovation || '', improvement: saved.improvement || '' })
      setDockCollapsed(saved.dockCollapsed === true)
    }

    api.listNotes(id).then((list) => {
      if (cancelled) return
      const remote = (Array.isArray(list) ? list : []).map(normalizeNote)
      const legacy = (Array.isArray(saved?.notes) ? saved.notes : [])
        .filter((n) => n && !remote.some((r) => String(r.id) === String(n.id)))
      if (!legacy.length) { setNotes(remote); return }
      // 旧批注补推入库（保留颜色与锚点几何），成功后清掉本机副本避免重复迁移
      Promise.all(legacy.map((n) => api.createNote({
        literatureId: Number(id),
        type: 'annotation',
        page: n.page ?? null,
        quote: n.quote || null,
        rects: Array.isArray(n.rects) ? n.rects : null,
        badge: n.badge || null,
        citeText: n.citeText || null,
        color: n.color || 'yellow',
        content: n.text || '',
      }).catch(() => null))).then((created) => {
        if (cancelled) return
        const migrated = created.filter(Boolean).map(normalizeNote)
        setNotes([...migrated, ...remote].sort((a, b) => noteTime(b) - noteTime(a)))
        try {
          localStorage.setItem(dockKey, JSON.stringify({ ...saved, notes: [] }))
        } catch { /* 存储不可用则下次进入重试迁移（按 id 去重，不会重复入库） */ }
      })
    }).catch(() => {
      if (cancelled) return
      const localNotes = Array.isArray(saved?.notes) ? saved.notes : []
      setNotes(localNotes.map(normalizeNote))
      if (localNotes.length) toast('笔记加载失败，已读取本机数据', 'error')
    })
    return () => { cancelled = true }
  }, [id])

  // 文献元数据 + PDF（路由复用组件实例，id 变化时必须整体重置，否则
  // 渲染缓存判定"已渲染"导致新 PDF 全部空白、旧会话串入新文献）
  useEffect(() => {
    let cancelled = false
    let pdfDoc = null
    abortRef.current?.abort()
    rendered.current = new Set()
    renderTasks.current.forEach((t) => { try { t.cancel() } catch { /* noop */ } })
    renderTasks.current.clear()
    textTasks.current.forEach((t) => { try { t.cancel() } catch { /* noop */ } })
    textTasks.current.clear()
    pageRefs.current = {}
    textLayerRefs.current = {}
    setDoc(null)
    setLit(null)
    setFatal('')
    setToc([])
    setAnchorPages({})
    setPageNo(1)
    setTocOpen(false)
    setMessages([])
    setConversationId(null)
    api.getLiterature(id).then((d) => { if (!cancelled) setLit(d) }).catch((e) => { if (!cancelled) setFatal(e.message) })
    ;(async () => {
      try {
        const blob = await api.getLiteratureFile(id)
        const data = await blob.arrayBuffer()
        pdfDoc = await pdfjsLib.getDocument({ data }).promise
        if (cancelled) { pdfDoc.destroy(); return }
        setDoc(pdfDoc)

        // 目录：优先 PDF 自带书签，为空则按文本扫描
        let entries = []
        try {
          const outline = await pdfDoc.getOutline()
          if (outline?.length) {
            entries = await flattenOutline(outline, pdfDoc)
            await Promise.all(entries.map(async (e) => {
              if (!e.pending) return
              try {
                const dest = await pdfDoc.getDestination(e.pending)
                if (Array.isArray(dest) && dest[0]) e.page = (await pdfDoc.getPageIndex(dest[0])) + 1
              } catch { /* 保留 null */ }
              e.pending = null
            }))
            entries = entries.filter((e) => e.title)
          }
        } catch { /* 书签解析失败走文本扫描 */ }
        if (!entries.length) entries = await scanTextToc(pdfDoc)
        setToc(entries)
        // 章节定位复用目录结果（书签页码最准），再做文本扫描兜底
        setAnchorPages(await locateSections(pdfDoc, entries))
      } catch (e) {
        if (!cancelled) setFatal(e.message || 'PDF 加载失败')
      }
    })()
    return () => { cancelled = true; if (pdfDoc) pdfDoc.destroy() }
  }, [id])

  // 懒渲染 + 页码跟踪（文档就绪后挂观察器）
  useEffect(() => {
    const box = pagesBoxRef.current
    if (!doc || !box) return undefined

    const renderPage = async (n, container) => {
      try {
        const page = await doc.getPage(n)
        // 用页面容器自身宽度定标：画布 / 文本层 / 批注高亮三者才能像素对齐
        // （旧逻辑用滚动区 clientWidth，含 padding，导致文本层与画布有 ~4px 漂移）
        const hostWidth = container.clientWidth || Math.max(box.clientWidth - 36, 300)
        const base = page.getViewport({ scale: 1 })
        const scale = Math.min(hostWidth / base.width, 1.6)
        const viewport = page.getViewport({ scale })
        const canvas = document.createElement('canvas')
        const dpr = Math.min(window.devicePixelRatio || 1, 2)
        canvas.width = Math.floor(viewport.width * dpr)
        canvas.height = Math.floor(viewport.height * dpr)
        canvas.style.width = '100%'
        container.style.aspectRatio = `${Math.floor(base.width)} / ${Math.floor(base.height)}`
        container.appendChild(canvas)
        const task = page.render({
          canvasContext: canvas.getContext('2d'),
          viewport,
          transform: dpr !== 1 ? [dpr, 0, 0, dpr, 0, 0] : undefined,
        })
        renderTasks.current.set(n, task)
        await task.promise
        // 文本层：透明文字覆盖在画布上，让原文可选中（锚点批注的前提）。
        // renderTextLayer 要求祖先带 --scale-factor，画布与文本层共用同一 viewport。
        const tl = textLayerRefs.current[n]
        if (tl) {
          tl.textContent = ''
          container.style.setProperty('--scale-factor', viewport.scale)
          container.style.setProperty('--total-scale-factor', viewport.scale)
          try {
            // pdfjs-dist 4.10 只暴露 TextLayer 类（renderTextLayer 已移除）。
            // textContentSource 用一次性 getTextContent()：sendWithStream 流式
            // 传输在部分环境下会挂起，普通对象路径由构造器自行包装，稳定可靠。
            const textContent = await page.getTextContent()
            const tlTask = new pdfjsLib.TextLayer({
              textContentSource: textContent,
              container: tl,
              viewport,
            })
            textTasks.current.set(n, tlTask)
            await tlTask.render()
          } catch { /* 文本层失败只影响选中，不影响阅读 */ }
          finally { textTasks.current.delete(n) }
        }
      } catch { /* 取消或渲染失败静默 */ }
      finally { renderTasks.current.delete(n) }
    }

    const observer = new IntersectionObserver((observed) => {
      for (const entry of observed) {
        if (!entry.isIntersecting) continue
        const n = entry.target.dataset.page
        if (n && !rendered.current.has(n)) {
          rendered.current.add(n)
          renderPage(Number(n), entry.target)
        }
        observer.unobserve(entry.target)
      }
    }, { rootMargin: '600px 0px' })
    box.querySelectorAll('[data-page]').forEach((el) => observer.observe(el))

    const onScroll = () => {
      const children = Array.from(box.querySelectorAll('[data-page]'))
      for (let i = children.length - 1; i >= 0; i--) {
        if (children[i].offsetTop - box.offsetTop <= box.scrollTop + 60) {
          setPageNo(Number(children[i].dataset.page))
          break
        }
      }
    }
    box.addEventListener('scroll', onScroll, { passive: true })
    return () => { observer.disconnect(); box.removeEventListener('scroll', onScroll) }
  }, [doc])

  const jumpPage = (n) => {
    const el = pageRefs.current[n]
    if (el) el.scrollIntoView({ behavior: 'smooth', block: 'start' })
    else toast('页码超出范围', 'error')
  }

  // 笔记插件页"定位原文"跳转：/workspace/:id?page=N → PDF 就绪后滚动定位并清掉查询参数，
  // 避免用户翻页后刷新又跳回同一页
  useEffect(() => {
    if (!doc) return
    const want = Number(searchParams.get('page'))
    if (want >= 1 && want <= doc.numPages) {
      const t = setTimeout(() => {
        jumpPage(want)
        setSearchParams({}, { replace: true })
      }, 400)
      return () => clearTimeout(t)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [doc])

  // 选中原文 → 悬浮「写批注」按钮；标记模式下点击/框选引用文献直接生成箭头标记。
  // 选区矩形换算成页面容器的百分比坐标存储，窗口缩放后高亮仍与画布对齐；
  // 同一行的碎矩形先合并。
  useEffect(() => {
    const box = pagesBoxRef.current
    if (!doc || !box) return undefined

    // 选区 → 页面百分比矩形（锚点批注与引用标记共用同一套换算）
    const selectionRects = (sel, pageEl) => {
      const range = sel.getRangeAt(0)
      const contRect = pageEl.getBoundingClientRect()
      const lines = []
      for (const r of range.getClientRects()) {
        if (r.width < 2 || r.height < 2) continue
        const p = {
          l: ((r.left - contRect.left) / contRect.width) * 100,
          t: ((r.top - contRect.top) / contRect.height) * 100,
          w: (r.width / contRect.width) * 100,
          h: (r.height / contRect.height) * 100,
        }
        if (p.t < -2 || p.t + p.h > 102) continue // 跨页选区只保留本页部分
        const last = lines[lines.length - 1]
        if (last && Math.abs(last.t - p.t) < Math.max(p.h, last.h) * 0.7) {
          const right = Math.max(last.l + last.w, p.l + p.w)
          last.l = Math.min(last.l, p.l)
          last.t = Math.min(last.t, p.t)
          last.w = right - last.l
          last.h = Math.max(last.h, p.h)
        } else lines.push(p)
      }
      return lines
    }

    // 标记模式 · 点击：caret 定位所在文本 span，识别引用标记（[12] / (Vaswani et al., 2017)）
    const markFromCaret = (x, y, pageEl) => {
      const range = caretRangeFromPoint(x, y)
      if (!range) { toast('未识别到引用，可框选引用文本后松开', 'error'); return }
      let span = range.startContainer?.nodeType === 3 ? range.startContainer.parentElement : range.startContainer
      // 爬到文本层的直接子元素（pdfjs 把每段文本包成 span）
      while (span && span.parentElement && !span.parentElement.classList?.contains('ws-textlayer')) {
        span = span.parentElement
      }
      if (!span || !span.parentElement?.classList?.contains('ws-textlayer')) return
      const direct = span.textContent || ''
      let cite = findCitation(direct)
      let rectEls = [span]
      if (!cite && span.nextElementSibling) {
        // pdfjs 可能把引用拆到相邻 span：拼接下一个再试，命中则合并两者矩形
        const joined = direct + (span.nextElementSibling.textContent || '')
        const joinedCite = findCitation(joined)
        if (joinedCite) { cite = joinedCite; rectEls = [span, span.nextElementSibling] }
      }
      if (!cite) { toast('未在该处识别到引用标记（如 [12]），可框选引用文本', 'error'); return }
      const contRect = pageEl.getBoundingClientRect()
      const rs = rectEls.map((el) => el.getBoundingClientRect())
      const left = Math.min(...rs.map((r) => r.left))
      const top = Math.min(...rs.map((r) => r.top))
      const right = Math.max(...rs.map((r) => r.right))
      const bottom = Math.max(...rs.map((r) => r.bottom))
      createCiteMark({
        page: Number(pageEl.dataset.page),
        citeText: cite,
        rects: [{
          l: ((left - contRect.left) / contRect.width) * 100,
          t: ((top - contRect.top) / contRect.height) * 100,
          w: ((right - left) / contRect.width) * 100,
          h: ((bottom - top) / contRect.height) * 100,
        }],
      })
    }

    const onPointerUp = (e) => {
      // 标记模式：框选引用 → 箭头；单击引用 → caret 识别 → 箭头。不弹「写批注」气泡。
      if (markMode) {
        if (e.target.closest?.('.ws-dock, .ws-mark-hint, .ws-anchor-pop')) return
        const sel = document.getSelection()
        const text = sel ? sel.toString().replace(/\s+/g, ' ').trim() : ''
        if (sel && !sel.isCollapsed && sel.rangeCount && text) {
          const node = sel.anchorNode?.nodeType === 3 ? sel.anchorNode.parentElement : sel.anchorNode
          const pageEl = node?.closest?.('.ws-textlayer')?.closest('[data-page]')
          if (pageEl) {
            // 先换算矩形再清选区（removeAllRanges 后 getClientRects 不保证可用）
            const rects = selectionRects(sel, pageEl).slice(0, 24)
            sel.removeAllRanges()
            if (rects.length) {
              createCiteMark({
                page: Number(pageEl.dataset.page),
                citeText: text.slice(0, 500),
                rects,
              })
            }
          }
          return
        }
        const pageEl = e.target.closest?.('[data-page]')
        if (pageEl && e.clientX != null) markFromCaret(e.clientX, e.clientY, pageEl)
        return
      }

      const sel = document.getSelection()
      const text = sel ? sel.toString().replace(/\s+/g, ' ').trim() : ''
      if (!sel || sel.isCollapsed || !text) { setSelBubble(null); return }
      const node = sel.anchorNode?.nodeType === 3 ? sel.anchorNode.parentElement : sel.anchorNode
      const pageEl = node?.closest?.('.ws-textlayer')?.closest('[data-page]')
      if (!pageEl || !sel.rangeCount) { setSelBubble(null); return }
      const range = sel.getRangeAt(0)
      const contRect = pageEl.getBoundingClientRect()
      const rangeRect = range.getBoundingClientRect()
      const rects = selectionRects(sel, pageEl)
      if (!rects.length) { setSelBubble(null); return }
      const end = rects[rects.length - 1]
      setSelBubble({
        x: Math.min(rangeRect.right, window.innerWidth - 130),
        y: rangeRect.top,
        page: Number(pageEl.dataset.page),
        quote: text.slice(0, 600),
        rects: rects.slice(0, 24),
        badge: { l: Math.min(end.l + end.w, 97), t: end.t + end.h / 2 },
      })
    }

    const onPointerDown = (e) => {
      if (!e.target.closest?.('.ws-sel-bubble')) setSelBubble(null)
      if (openAnchor && !e.target.closest?.('.ws-anchor-pop') && !e.target.closest?.('.ws-anchor-badge') && !e.target.closest?.('.ws-cite-flag')) {
        setOpenAnchor(null)
      }
    }
    const onKey = (e) => { if (e.key === 'Escape') { setSelBubble(null); setOpenAnchor(null); setMarkMode(false) } }
    const onScroll = () => setSelBubble(null)

    box.addEventListener('pointerup', onPointerUp)
    box.addEventListener('scroll', onScroll, { passive: true })
    document.addEventListener('pointerdown', onPointerDown)
    document.addEventListener('keydown', onKey)
    return () => {
      box.removeEventListener('pointerup', onPointerUp)
      box.removeEventListener('scroll', onScroll)
      document.removeEventListener('pointerdown', onPointerDown)
      document.removeEventListener('keydown', onKey)
    }
  }, [doc, openAnchor, markMode, noteColor])

  const downloadPdf = async () => {
    try {
      // 统一走 downloadBlob：挂载 <a>（Firefox 兼容）+ 回收 objectURL
      downloadBlob(await api.getLiteratureFile(id), lit?.fileName || 'paper.pdf')
    } catch (e) {
      toast(e.message, 'error')
    }
  }

  // 以纯函数方式更新最后一条 assistant 消息：直接 mutate state 会在
  // React StrictMode（开发模式 double-invoke updater）下造成流式文本双倍追加
  const patchLast = (patch) =>
    setMessages((m) => {
      if (!m.length) return m
      const last = m[m.length - 1]
      if (last?.role !== 'assistant') return m
      return [...m.slice(0, -1), { ...last, ...patch(last) }]
    })

  // 提问：带 literatureId，模型获得"正在精读该论文"上下文
  const ask = async (text) => {
    const question = (text ?? input).trim()
    if (!question || streaming) return
    setInput('')
    setMessages((m) => [...m, { role: 'user', content: question }])
    setStreaming(true)
    const assistant = { role: 'assistant', content: '', steps: [], refs: [] }
    setMessages((m) => [...m, assistant])

    const controller = new AbortController()
    abortRef.current = controller
    try {
      const ended = await agentChat({
        conversationId,
        question,
        literatureId: id,
        signal: controller.signal,
        onEvent: (event, data) => {
          if (event === 'start') setConversationId(data.conversationId)
          else if (event === 'step') patchLast((last) => ({ steps: [...last.steps, data] }))
          else if (event === 'token') patchLast((last) => ({ content: last.content + data.content }))
          else if (event === 'final') patchLast(() => ({ refs: data.references || [] }))
          else if (event === 'error') {
            patchLast((last) => ({
              content: last.content
                ? `${last.content}\n\n（执行中断：${data.message}）`
                : `执行失败：${data.message}`,
            }))
          }
        },
      })
      // 静默断流：没有 final/error 收尾就结束的流，提示用户回答可能不完整
      if (!ended) {
        patchLast((last) => ({
          content: last.content
            ? `${last.content}\n\n（连接中断，回答可能不完整）`
            : '（连接中断，请重试）',
        }))
      }
    } catch (e) {
      if (e.name !== 'AbortError') {
        patchLast((last) => ({
          content: last.content ? `${last.content}\n\n（执行失败：${e.message}）` : e.message,
        }))
      }
    } finally {
      setStreaming(false)
      abortRef.current = null
      chatEndRef.current?.scrollIntoView({ behavior: 'smooth' })
    }
  }

  useEffect(() => {
    chatEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages.length])

  const annotations = useMemo(() => ([
    { key: 'abstract', icon: BookOpenText, title: '摘要', body: normalizeText(lit?.abstractText) },
    { key: 'mainContent', icon: ScrollText, title: '主要内容', body: normalizeText(lit?.mainContent) },
    { key: 'methods', icon: Microscope, title: '方法', body: normalizeText(lit?.methods) },
    { key: 'experiments', icon: FlaskConical, title: '实验', body: normalizeText(lit?.experiments) },
    { key: 'conclusions', icon: ListChecks, title: '结论', body: normalizeText(lit?.conclusions) },
  ]), [lit])

  // 颜色 / 创新点 / 折叠状态仍存本机（非笔记正文）；批注/问题/记录已入库
  const persistDock = (patch) => {
    try {
      localStorage.setItem(
        `as.ws.${id}`,
        JSON.stringify({ ideas, color: noteColor, dockCollapsed, ...patch })
      )
    } catch { /* 存储不可用则仅保留内存状态 */ }
  }

  // 保存笔记：新建走 POST（乐观上屏后用真实 id 替换临时 id），编辑走 PUT。
  // 仅"批注"类型携带锚点；问题/记录不锚定页码，在笔记插件页集中查看。
  const saveNote = async () => {
    const text = noteDraft.trim()
    if (!text) return

    if (editingId) {
      const prev = notes
      const next = notes.map((n) => (n.id === editingId ? { ...n, text, color: noteColor, type: draftType } : n))
      setNotes(next)
      setNoteDraft('')
      setEditingId(null)
      setPendingAnchor(null)
      if (isLocalId(editingId)) { toast('本机笔记已更新'); return }
      try {
        await api.updateNote(editingId, { content: text, color: noteColor, type: draftType })
        toast('笔记已更新')
      } catch (e) {
        setNotes(prev)
        toast(e.message, 'error')
      }
      return
    }

    const anchor = draftType === 'annotation' ? pendingAnchor : null
    const temp = {
      id: -Date.now(),
      page: anchor ? anchor.page : (draftType === 'annotation' ? pageNo : null),
      text,
      color: noteColor,
      type: draftType,
      ...(anchor ? { quote: anchor.quote, rects: anchor.rects, badge: anchor.badge } : {}),
    }
    setNotes((cur) => [temp, ...cur])
    setNoteDraft('')
    setPendingAnchor(null)
    try {
      const savedNote = normalizeNote(await api.createNote({
        literatureId: Number(id),
        type: draftType,
        page: temp.page,
        quote: anchor?.quote || null,
        rects: anchor?.rects || null,
        badge: anchor?.badge || null,
        color: noteColor,
        content: text,
      }))
      setNotes((cur) => cur.map((n) => (n.id === temp.id ? savedNote : n)))
      toast(draftType === 'annotation'
        ? (anchor ? '批注已保存，锚点已写入原文' : '批注已保存')
        : `${typeLabel(draftType)}已保存，可在笔记插件页集中查看`)
    } catch (e) {
      setNotes((cur) => cur.filter((n) => n.id !== temp.id))
      toast(e.message, 'error')
    }
  }

  // 引用标记：框选/点击论文中的引用文献后立即入库，从引用处引出箭头指向页边旗标。
  // 先落库再上屏（拿到真实 id 才进入编辑态），箭头出现的同时聚焦批注输入框。
  const createCiteMark = async ({ page, citeText, rects }) => {
    try {
      const savedNote = normalizeNote(await api.createNote({
        literatureId: Number(id),
        type: 'annotation',
        page,
        quote: citeText.slice(0, 2000),
        rects,
        citeText: citeText.slice(0, 500),
        color: noteColor,
        content: '',
      }))
      setNotes((cur) => [savedNote, ...cur])
      setMarkMode(false)
      setDockCollapsed(false)
      setDockPanel('notes')
      setDraftType('annotation')
      setEditingId(savedNote.id)
      setNoteDraft('')
      setPulseId(savedNote.id)
      setTimeout(() => setPulseId((cur) => (cur === savedNote.id ? null : cur)), 1600)
      toast('已从引用处引出箭头标记，可补充批注内容')
    } catch (e) {
      toast(e.message, 'error')
    }
  }

  // 从批注列表/角标定位到原文锚点：跳页 + 高亮脉冲
  const jumpToNote = (n) => {
    if (!n.page) { toast('该笔记未锚定页码'); return }
    jumpPage(n.page)
    if (n.rects?.length) {
      setPulseId(n.id)
      setTimeout(() => setPulseId((cur) => (cur === n.id ? null : cur)), 1600)
    }
  }

  // 角标弹卡 → 引用到对话：把引文与批注一并交给模型解读
  const citeNote = (n) => {
    setOpenAnchor(null)
    ask(`请结合这段原文解读我的批注。原文：「${(n.quote || '').slice(0, 160)}」。我的批注：「${n.text}」`)
  }

  const copyCitation = (n) => {
    const text = `「${n.quote || n.citeText || ''}」（第 ${n.page ?? '—'} 页${n.text ? `，我的批注：${n.text}` : ''}）`
    if (navigator.clipboard?.writeText) {
      navigator.clipboard.writeText(text).then(() => toast('引用已复制'))
    } else toast('当前环境不支持复制', 'error')
  }

  const anchorSelection = () => {
    const b = selBubble
    if (!b) return
    setPendingAnchor({ page: b.page, quote: b.quote, rects: b.rects, badge: b.badge })
    setDockCollapsed(false)
    setDockPanel('notes')
    setDraftType('annotation')
    setEditingId(null)
    setNoteDraft('')
    setSelBubble(null)
    setTimeout(() => noteInputRef.current?.focus(), 60)
  }

  // 进入编辑态：把笔记内容装回输入框，保存时走 PUT（锚点几何不变）
  const startEdit = (n) => {
    setDockCollapsed(false)
    setDockPanel('notes')
    setEditingId(n.id)
    setDraftType(n.type || 'annotation')
    setNoteColor(n.color || 'yellow')
    setNoteDraft(n.text || '')
    setOpenAnchor(null)
    setSelBubble(null)
    setTimeout(() => noteInputRef.current?.focus(), 60)
  }

  const toggleMarkMode = () => {
    const next = !markMode
    setMarkMode(next)
    if (next) {
      setSelBubble(null)
      toast('标记模式：点击或框选论文中的引用（如 [12]），松开后从引用处引出箭头')
    }
  }

  const toggleDockCollapsed = (v) => {
    setDockCollapsed(v)
    persistDock({ dockCollapsed: v })
  }

  const removeNote = async (noteId) => {
    // 锚点批注含选区数据且不可恢复，删除前二次确认（与会话/文献删除同一交互标准）
    const ok = await confirm('删除后该笔记（含原文锚点高亮与引用箭头）将不可恢复。', {
      title: '删除笔记',
      confirmText: '删除',
      danger: true,
    })
    if (!ok) return
    const prev = notes
    setNotes((cur) => cur.filter((n) => n.id !== noteId))
    setOpenAnchor((cur) => (cur === noteId ? null : cur))
    setEditingId((cur) => (cur === noteId ? null : cur))
    if (isLocalId(noteId)) return // 离线兜底的本机数据，无后端记录可删
    try {
      await api.deleteNote(noteId)
    } catch (e) {
      setNotes(prev)
      toast(e.message, 'error')
    }
  }

  const chooseColor = (key) => {
    setNoteColor(key)
    persistDock({ color: key })
  }

  const saveIdeas = () => {
    persistDock({ ideas })
    toast('创新点与改进之处已保存')
  }

  const toggleDock = (panel) => setDockPanel((cur) => (cur === panel ? '' : panel))

  if (fatal) {
    return (
      <div className="reader-fatal">
        <X size={28} />
        <p>{fatal}</p>
        <Link to={`/literature/${id}`} className="btn-primary">返回详情</Link>
      </div>
    )
  }

  return (
    <div className="ws-page">
      <header className="reader-top">
        <Link to={`/literature/${id}`} className="back-link">
          <ArrowLeft size={15} /> 返回详情
        </Link>
        <div className="reader-title" title={lit?.title}>{lit?.title || '研究工作区'}</div>
        <div className="reader-pager">
          <button type="button" className={`btn-ghost pager-btn${tocOpen ? ' on' : ''}`} onClick={() => setTocOpen((v) => !v)}>
            <ListTree size={14} /> 目录
          </button>
          <button
            type="button"
            className="btn-ghost pager-btn"
            onClick={() => jumpPage(pageNo - 1)}
            disabled={pageNo <= 1}
            aria-label="上一页"
            title="上一页"
          >
            <ChevronUp size={14} />
          </button>
          <span>第 <b>{pageNo}</b> / {doc ? doc.numPages : '…'} 页</span>
          <button
            type="button"
            className="btn-ghost pager-btn"
            onClick={() => jumpPage(pageNo + 1)}
            disabled={!doc || pageNo >= doc.numPages}
            aria-label="下一页"
            title="下一页"
          >
            <ChevronDown size={14} />
          </button>
          <input
            className="pager-input"
            value={jumpTo}
            placeholder="跳转页码"
            inputMode="numeric"
            onChange={(e) => setJumpTo(e.target.value.replace(/\D/g, ''))}
            onKeyDown={(e) => {
              const n = Number(jumpTo)
              if (e.key === 'Enter' && n >= 1 && doc && n <= doc.numPages) { jumpPage(n); setJumpTo('') }
            }}
          />
          <button type="button" className="btn-ghost" onClick={downloadPdf}>
            <Download size={14} /> 下载
          </button>
        </div>
      </header>

      <div className="workspace-body">
        {/* 左：目录抽屉（默认收起，左侧直接展示论文原文） */}
        <nav className={`ws-toc${tocOpen ? ' is-open' : ''}`} aria-label="文档目录">
          <div className="ws-toc-head">
            <h3 className="side-heading">目录{toc.length ? '' : '（未识别到章节）'}</h3>
            <button type="button" className="ws-toc-close" onClick={() => setTocOpen(false)} aria-label="收起目录">
              <X size={14} />
            </button>
          </div>
          <div className="toc-list">
            {toc.map((item, idx) => (
              <button
                type="button"
                key={`${item.title}-${idx}`}
                className={`toc-item lvl-${Math.min(item.level ?? 0, 2)}`}
                onClick={() => { if (item.page) { jumpPage(item.page); setTocOpen(false) } }}
                disabled={!item.page}
              >
                <span className="toc-title">{item.title}</span>
                {item.page && <span className="toc-page">{item.page}</span>}
              </button>
            ))}
            {!toc.length && <p className="toc-empty">该 PDF 无书签且未扫描到明显章节标题。</p>}
          </div>
        </nav>

        {/* 左：论文原文 */}
        <main className="workspace-pdf" ref={pagesBoxRef}>
          {!doc && <div className="reader-loading"><Loader2 size={20} className="spin" /> 正在加载 PDF…</div>}
          {markMode && (
            <div className="ws-mark-hint">
              <Flag size={13} />
              <span>标记模式：点击或框选论文中的引用文献（如 [12]），松开后从引用处引出箭头</span>
              <button type="button" onClick={() => setMarkMode(false)}>退出</button>
            </div>
          )}
          {doc && Array.from({ length: doc.numPages }, (_, i) => {
            const pageNotes = notes.filter((n) => n.page === i + 1)
            const anchored = pageNotes.filter((n) => n.rects?.length && !n.citeText)
            const cited = pageNotes.filter((n) => n.rects?.length && n.citeText)
            // 同页多个引用标记：旗标纵向错开，避免箭头/旗标相互重叠
            let staggerT = -999
            const citeMarks = cited.map((n) => {
              const start = n.rects[n.rects.length - 1]
              const sy = start.t + start.h / 2
              let flagT = Math.max(5, Math.min(sy, 90))
              if (flagT - staggerT < 4) flagT = Math.min(staggerT + 4.5, 92)
              staggerT = flagT
              return { note: n, sx: Math.min(start.l + start.w, 96), sy, flagT }
            })
            return (
              <div
                key={i + 1}
                data-page={i + 1}
                ref={(el) => { pageRefs.current[i + 1] = el }}
                className={`reader-page${markMode ? ' is-marking' : ''}`}
              >
                <span className="page-label">{i + 1}</span>
                {pageNotes.length > 0 && (
                  <button
                    type="button"
                    className="page-note-pin"
                    style={{ '--pin': colorValue(pageNotes[0].color) }}
                    title={`第 ${i + 1} 页有 ${pageNotes.length} 条批注`}
                    onClick={() => { setDockPanel('notes'); jumpPage(i + 1) }}
                  >
                    <MessageSquarePlus size={11} /> {pageNotes.length}
                  </button>
                )}
                {/* 文本层：透明文字叠在画布上，让原文可选中（renderTextLayer 异步填充） */}
                <div className="ws-textlayer" ref={(el) => { textLayerRefs.current[i + 1] = el }} />
                {/* 锚点批注层：高亮引文 + 引出角标（编号按批注先后顺序） */}
                <div className="ws-anno-layer">
                  {anchored.map((n) => {
                    const no = notes.length - notes.findIndex((m) => m.id === n.id)
                    const below = n.badge.t < 22
                    const popL = Math.min(Math.max(n.badge.l, 20), 80)
                    return (
                      <div key={n.id} className="ws-anchor">
                        {n.rects.map((r, k) => (
                          <i
                            key={k}
                            className={`ws-hl${pulseId === n.id ? ' pulse' : ''}`}
                            style={{ left: `${r.l}%`, top: `${r.t}%`, width: `${r.w}%`, height: `${r.h}%`, '--hl': colorValue(n.color) }}
                          />
                        ))}
                        <button
                          type="button"
                          className={`ws-anchor-badge${openAnchor === n.id ? ' on' : ''}`}
                          style={{ left: `${n.badge.l}%`, top: `${n.badge.t}%`, '--pin': colorValue(n.color) }}
                          title="查看这条批注"
                          onClick={(e) => { e.stopPropagation(); setOpenAnchor(openAnchor === n.id ? null : n.id) }}
                        >
                          {no}
                        </button>
                        {openAnchor === n.id && (
                          <div
                            className={`ws-anchor-pop${below ? ' below' : ''}`}
                            style={{ left: `${popL}%`, top: `${n.badge.t}%` }}
                            onPointerDown={(e) => e.stopPropagation()}
                          >
                            <div className="pop-quote">
                              <Quote size={11} /> 原文 · 第 {n.page} 页
                              <p>{n.quote}</p>
                            </div>
                            <p className="pop-note">{n.text}</p>
                            <div className="pop-actions">
                              <button type="button" onClick={() => citeNote(n)} disabled={streaming}>
                                <Sparkles size={12} /> 让 AI 解读
                              </button>
                              <button type="button" onClick={() => copyCitation(n)}>
                                <Copy size={12} /> 复制引用
                              </button>
                              <button type="button" className="danger" onClick={() => removeNote(n.id)}>
                                <Trash2 size={12} /> 删除
                              </button>
                            </div>
                          </div>
                        )}
                      </div>
                    )
                  })}
                  {/* 引用标记：高亮引文 + 从引用处引出的箭头 + 页边旗标（编号按标记先后） */}
                  {citeMarks.map(({ note: n, sx, sy, flagT }) => {
                    const no = cited.length - cited.findIndex((m) => m.id === n.id)
                    const flagL = 96.5
                    const arrowL = Math.min(sx, flagL - 1.5)
                    const arrowW = Math.max(flagL - arrowL - 1, 1.2)
                    const below = flagT < 26
                    return (
                      <div key={n.id} className="ws-cite-mark">
                        {n.rects.map((r, k) => (
                          <i
                            key={k}
                            className={`ws-hl${pulseId === n.id ? ' pulse' : ''}`}
                            style={{ left: `${r.l}%`, top: `${r.t}%`, width: `${r.w}%`, height: `${r.h}%`, '--hl': colorValue(n.color) }}
                          />
                        ))}
                        <span
                          className="ws-cite-arrow"
                          style={{ left: `${arrowL}%`, top: `${sy}%`, width: `${arrowW}%`, '--mark': colorValue(n.color) }}
                        />
                        <button
                          type="button"
                          className={`ws-cite-flag${openAnchor === n.id ? ' on' : ''}`}
                          style={{ left: `${flagL}%`, top: `${flagT}%`, '--pin': colorValue(n.color) }}
                          title="查看这条引用标记"
                          onClick={(e) => { e.stopPropagation(); setOpenAnchor(openAnchor === n.id ? null : n.id) }}
                        >
                          <Flag size={10} /> {no}
                        </button>
                        {openAnchor === n.id && (
                          <div
                            className={`ws-anchor-pop ws-cite-pop${below ? ' below' : ''}`}
                            style={{ left: `${flagL}%`, top: `${flagT}%` }}
                            onPointerDown={(e) => e.stopPropagation()}
                          >
                            <div className="pop-quote">
                              <Flag size={11} /> 引用文献 · 第 {n.page} 页
                              <p>{n.citeText}</p>
                            </div>
                            {n.text
                              ? <p className="pop-note">{n.text}</p>
                              : <p className="pop-note is-empty">（尚未写批注，点击「写批注」补充）</p>}
                            <div className="pop-actions">
                              <button type="button" onClick={() => startEdit(n)}>
                                <Pencil size={12} /> 写批注
                              </button>
                              <button type="button" onClick={() => citeNote(n)} disabled={streaming || !n.text}>
                                <Sparkles size={12} /> 让 AI 解读
                              </button>
                              <button type="button" onClick={() => copyCitation(n)}>
                                <Copy size={12} /> 复制引用
                              </button>
                              <button type="button" className="danger" onClick={() => removeNote(n.id)}>
                                <Trash2 size={12} /> 删除
                              </button>
                            </div>
                          </div>
                        )}
                      </div>
                    )
                  })}
                </div>
              </div>
            )
          })}
        </main>

        {/* 右：批注 + 对话 */}
        <aside className="workspace-side">
          <section className="side-block ws-anno">
            <h3 className="side-heading">AI 知识批注（点击定位原文）</h3>
            {annotations.map(({ key, icon: Icon, title, body }) => (
              <div className={`anno-card${body ? '' : ' is-empty'}`} key={key}>
                <div
                  className={`anno-head${anchorPages[key] ? ' is-jumpable' : ''}`}
                  onClick={() => anchorPages[key] && jumpPage(anchorPages[key])}
                  role={anchorPages[key] ? 'button' : undefined}
                  tabIndex={anchorPages[key] ? 0 : undefined}
                  onKeyDown={(e) => {
                    if (anchorPages[key] && (e.key === 'Enter' || e.key === ' ')) {
                      e.preventDefault()
                      jumpPage(anchorPages[key])
                    }
                  }}
                  title={anchorPages[key] ? `跳转到第 ${anchorPages[key]} 页` : undefined}
                >
                  <Icon size={14} /> <b>{title}</b>
                  {anchorPages[key] && (
                    <span className="anno-jump">
                      第 {anchorPages[key]} 页 <ChevronRight size={12} />
                    </span>
                  )}
                </div>
                <p>{body ? `${body.slice(0, 140)}${body.length > 140 ? '…' : ''}` : '（暂无）'}</p>
              </div>
            ))}
          </section>

          <section className="ws-chat">
            <h3 className="side-heading">
              <Sparkles size={13} /> 与模型对话（已关联本文）
            </h3>

            {messages.length === 0 && (
              <div className="ws-quick">
                {QUICK_QUESTIONS.map((q) => (
                  <button key={q} type="button" className="quick-q" onClick={() => ask(q)} disabled={streaming}>
                    {q}
                  </button>
                ))}
              </div>
            )}

            <div className="ws-msgs">
              {messages.length === 0 && (
                <p className="ws-empty">向模型提问，它会用 <b>read_section</b> 检索本篇原文，引用证据为你讲清论文的方法与创新点。</p>
              )}
              {messages.map((m, i) => (
                <div className={`ws-msg ${m.role}`} key={i}>
                  {m.role === 'user' ? (
                    <div className="ws-bubble user">{m.content}</div>
                  ) : (
                    <div className="ws-bubble assistant">
                      {m.steps?.length > 0 && m.content === '' && streaming && (
                        <div className="ws-steps">
                          {m.steps.slice(-3).map((s, j) => (
                            <span key={j} className="ws-step">
                              {s.type === 'tool_call' ? `🔧 ${s.toolName || '工具调用'}` : s.type === 'thinking' ? `💭 ${s.summary ? `${s.summary.slice(0, 14)}…` : '思考中'}` : s.type === 'tool_result' ? `✓ ${s.toolName || '完成'}` : '…'}
                            </span>
                          ))}
                          <Loader2 size={12} className="spin" />
                        </div>
                      )}
                      <div className="ws-answer">{m.content || (streaming && m.steps?.length ? '' : '')}</div>
                      {m.refs?.length > 0 && m.content && (
                        <div className="ws-refs">
                          参考文献：{m.refs.map((r, k) => (
                            <button key={k} type="button" onClick={() => jumpPage(1)} title={r.title}>
                              {r.title?.slice(0, 24) || `#${r.id}`}
                            </button>
                          ))}
                        </div>
                      )}
                    </div>
                  )}
                </div>
              ))}
              <div ref={chatEndRef} />
            </div>

            <div className="ws-input-row">
              <input
                value={input}
                placeholder="例如：这个方法为什么比 baseline 好？"
                onChange={(e) => setInput(e.target.value)}
                onKeyDown={(e) => { if (e.key === 'Enter' && !e.nativeEvent.isComposing) ask() }}
                disabled={streaming}
              />
              <button type="button" className="btn-primary ws-send" onClick={() => ask()} disabled={streaming || !input.trim()}>
                {streaming ? <Loader2 size={15} className="spin" /> : <Send size={15} />}
              </button>
            </div>
          </section>
        </aside>

        {/* 左下：批注工具（可整体收起为圆钮；文本批注 / 颜色 / 创新点与改进） */}
        <div className="ws-dock">
          {selBubble && (
            <button
              type="button"
              className="ws-sel-bubble"
              style={{ left: selBubble.x, top: selBubble.y }}
              onPointerDown={(e) => e.preventDefault()}
              onClick={anchorSelection}
            >
              <Highlighter size={13} /> 写批注
            </button>
          )}

          {!dockCollapsed && dockPanel === 'notes' && (
            <div className="ws-dock-panel">
              <div className="dock-head">
                <b>
                  {typeLabel(draftType)}
                  {draftType === 'annotation' ? ` · 第 ${pendingAnchor?.page ?? pageNo} 页` : ''}
                </b>
                <button type="button" className="dock-close" onClick={() => setDockPanel('')} aria-label="关闭">
                  <X size={14} />
                </button>
              </div>
              <div className="dock-type-seg" role="tablist" aria-label="笔记类型">
                {NOTE_TYPES.map((t) => (
                  <button
                    key={t.key}
                    type="button"
                    className={draftType === t.key ? 'on' : ''}
                    onClick={() => setDraftType(t.key)}
                  >
                    {t.label}
                  </button>
                ))}
              </div>
              {draftType === 'annotation' && pendingAnchor && (
                <div className="dock-anchor-chip">
                  <Quote size={11} />
                  <span>
                    锚点已选中：「{pendingAnchor.quote.slice(0, 42)}{pendingAnchor.quote.length > 42 ? '…' : ''}」
                  </span>
                  <button type="button" onClick={() => setPendingAnchor(null)} title="清除锚点，改为整页批注">
                    <X size={11} />
                  </button>
                </div>
              )}
              <textarea
                ref={noteInputRef}
                rows={3}
                value={noteDraft}
                placeholder={
                  editingId ? '编辑这条笔记内容…'
                    : draftType === 'question' ? '记录阅读中的疑问，之后可在笔记插件页集中查看…'
                      : draftType === 'record' ? '记录想法、待办或补充资料…'
                        : pendingAnchor ? '针对选中的原文写下你的理解或疑问…'
                          : '记录对这一页的理解、疑问或原文证据…'
                }
                onChange={(e) => setNoteDraft(e.target.value)}
                onKeyDown={(e) => { if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) saveNote() }}
              />
              <div className="dock-actions">
                <div className="dock-swatches">
                  {NOTE_COLORS.map((c) => (
                    <button
                      key={c.key}
                      type="button"
                      className={`dock-color${noteColor === c.key ? ' on' : ''}`}
                      style={{ background: c.value }}
                      title={c.label}
                      aria-label={c.label}
                      onClick={() => chooseColor(c.key)}
                    />
                  ))}
                </div>
                <button type="button" className="btn-primary dock-save" onClick={saveNote} disabled={!noteDraft.trim()}>
                  {editingId ? '保存修改' : '保存笔记'}
                </button>
              </div>
              <div className="dock-note-list">
                {notes.length === 0 && (
                  <p className="dock-empty">
                    还没有笔记。选中论文原文可写「锚点批注」；用底栏「标记引用」可标记文献引出箭头；问题与记录不锚定页码，可在导航栏笔记插件页集中查看。
                  </p>
                )}
                {notes.map((n) => (
                  <div className="dock-note" key={n.id}>
                    <span className="dock-note-dot" style={{ background: colorValue(n.color) }} />
                    <div className="dock-note-body">
                      {(n.type !== 'annotation' || n.citeText) && (
                        <div className="dock-note-chips">
                          {n.type !== 'annotation' && (
                            <span className={`dock-note-chip t-${n.type}`}>{typeLabel(n.type)}</span>
                          )}
                          {n.citeText && (
                            <span className="dock-note-chip t-cite"><Flag size={9} /> 引用标记</span>
                          )}
                        </div>
                      )}
                      {n.quote && (
                        <p className="dock-note-quote">「{n.quote.slice(0, 60)}{n.quote.length > 60 ? '…' : ''}」</p>
                      )}
                      <p>{n.text || (n.citeText ? '（待补充批注）' : '')}</p>
                      <div className="dock-note-meta">
                        <button type="button" className="dock-note-page" onClick={() => jumpToNote(n)}>
                          {n.citeText ? '定位引用' : n.rects?.length ? '定位锚点' : n.page ? `第 ${n.page} 页` : '全局'}
                        </button>
                        <button type="button" className="dock-note-edit" title="编辑笔记" onClick={() => startEdit(n)}>
                          <Pencil size={12} />
                        </button>
                        <button type="button" className="dock-note-del" title="删除笔记" onClick={() => removeNote(n.id)}>
                          <Trash2 size={12} />
                        </button>
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}

          {!dockCollapsed && dockPanel === 'colors' && (
            <div className="ws-dock-panel">
              <div className="dock-head">
                <b>批注颜色</b>
                <button type="button" className="dock-close" onClick={() => setDockPanel('')} aria-label="关闭">
                  <X size={14} />
                </button>
              </div>
              <div className="dock-colors">
                {NOTE_COLORS.map((c) => (
                  <button
                    key={c.key}
                    type="button"
                    className={`dock-color${noteColor === c.key ? ' on' : ''}`}
                    style={{ background: c.value }}
                    title={c.label}
                    aria-label={c.label}
                    onClick={() => chooseColor(c.key)}
                  />
                ))}
              </div>
              <p className="dock-hint">
                当前颜色：{(NOTE_COLORS.find((c) => c.key === noteColor) || NOTE_COLORS[0]).label}
                。新批注和页面角标都会使用这个颜色。
              </p>
            </div>
          )}

          {!dockCollapsed && dockPanel === 'ideas' && (
            <div className="ws-dock-panel">
              <div className="dock-head">
                <b>创新点与改进之处</b>
                <button type="button" className="dock-close" onClick={() => setDockPanel('')} aria-label="关闭">
                  <X size={14} />
                </button>
              </div>
              <label className="dock-field-label" htmlFor="idea-innovation">创新点</label>
              <textarea
                id="idea-innovation"
                rows={3}
                value={ideas.innovation}
                placeholder="这篇论文新在哪里、相对已有工作突破了什么…"
                onChange={(e) => setIdeas({ ...ideas, innovation: e.target.value })}
              />
              <label className="dock-field-label" htmlFor="idea-improvement">改进之处</label>
              <textarea
                id="idea-improvement"
                rows={3}
                value={ideas.improvement}
                placeholder="局限、可改进方向、复现或落地难点…"
                onChange={(e) => setIdeas({ ...ideas, improvement: e.target.value })}
              />
              <div className="dock-actions">
                <button type="button" className="btn-ghost" onClick={() => ask(AI_IDEAS_PROMPT)} disabled={streaming}>
                  <Sparkles size={13} /> 让 AI 分析
                </button>
                <button type="button" className="btn-primary dock-save" onClick={saveIdeas}>保存</button>
              </div>
            </div>
          )}

          {!dockCollapsed && (
            <div className="ws-dock-bar">
              <button
                type="button"
                className={dockPanel === 'notes' ? 'on' : ''}
                onClick={() => toggleDock('notes')}
              >
                <NotebookPen size={14} /> 笔记
              </button>
              <button
                type="button"
                className={markMode ? 'on' : ''}
                onClick={toggleMarkMode}
                title="标记论文中的引用文献，从引用处引出箭头提示"
              >
                <Flag size={14} /> 标记引用
              </button>
              <button
                type="button"
                className={dockPanel === 'colors' ? 'on' : ''}
                onClick={() => toggleDock('colors')}
              >
                <Palette size={14} /> 颜色
              </button>
              <button
                type="button"
                className={dockPanel === 'ideas' ? 'on' : ''}
                onClick={() => toggleDock('ideas')}
              >
                <Lightbulb size={14} /> 创新点 / 改进
              </button>
              <button
                type="button"
                className="dock-fold"
                title="收起批注工具栏"
                aria-label="收起批注工具栏"
                onClick={() => { setDockPanel(''); toggleDockCollapsed(true) }}
              >
                <ChevronDown size={14} />
              </button>
            </div>
          )}

          {dockCollapsed && (
            <button
              type="button"
              className="ws-dock-fab"
              title="展开批注工具"
              aria-label="展开批注工具"
              onClick={() => toggleDockCollapsed(false)}
            >
              <StickyNote size={17} />
              {notes.length > 0 && <span className="fab-count">{notes.length}</span>}
            </button>
          )}
        </div>
      </div>
    </div>
  )
}
