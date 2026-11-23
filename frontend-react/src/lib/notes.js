/**
 * 笔记插件共享工具：批注色板、笔记类型、后端数据归一化与引用标记识别。
 * 工作区（WorkspaceView）与笔记插件页（NotesView）共用，保证类型/颜色约定只有一份。
 */

/** 批注标记色板：选中颜色同时用于批注列表圆点、页面角标与引用箭头 */
export const NOTE_COLORS = [
  { key: 'yellow', label: '黄色', value: '#f5c542' },
  { key: 'green', label: '绿色', value: '#3ecf8e' },
  { key: 'blue', label: '蓝色', value: '#5b8def' },
  { key: 'pink', label: '粉色', value: '#ef6fa8' },
]

export const colorValue = (key) => (NOTE_COLORS.find((c) => c.key === key) || NOTE_COLORS[0]).value

/** 笔记类型：批注 / 问题 / 记录（与后端 NoteService.NOTE_TYPES 一致） */
export const NOTE_TYPES = [
  { key: 'annotation', label: '批注' },
  { key: 'question', label: '问题' },
  { key: 'record', label: '记录' },
]

export const typeLabel = (key) =>
  (NOTE_TYPES.find((t) => t.key === key) || NOTE_TYPES[0]).label

/** 后端笔记对象 → 前端渲染形态：字段补默认值，rects/badge 透传 */
export const normalizeNote = (n = {}) => ({
  id: n.id,
  page: n.page ?? null,
  text: n.content ?? n.text ?? '',
  color: n.color || 'yellow',
  type: n.type || 'annotation',
  createdAt: n.createdAt || null,
  ...(n.quote ? { quote: n.quote } : {}),
  ...(Array.isArray(n.rects) && n.rects.length ? { rects: n.rects } : {}),
  ...(n.badge ? { badge: n.badge } : {}),
  ...(n.citeText ? { citeText: n.citeText } : {}),
})

/**
 * 引用标记识别：论文里常见的两种引用写法。
 * - 数字上标/方括号：[12]、[3,7]、[4-9]、[12–15]
 * - 作者-年份： (Vaswani et al., 2017)、(Smith and Jones, 2020a)
 */
export const CITE_BRACKET_RE = /\[\d+(?:\s*[-–,，]\s*\d+)*\]/
export const CITE_AUTHOR_YEAR_RE = /\([^()\n]{0,120}?\b(?:19|20)\d{2}[a-z]?\b[^()\n]{0,20}?\)/

/** 返回文本中命中的引用片段（先方括号后作者-年份），未命中返回 null */
export const findCitation = (text) => {
  if (!text) return null
  const m = text.match(CITE_BRACKET_RE) || text.match(CITE_AUTHOR_YEAR_RE)
  return m ? m[0] : null
}
