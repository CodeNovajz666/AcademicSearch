/** 从文献元数据生成常用引用格式，供一键复制。 */

const YEAR = (lit) => lit.year || 'n.d.'
const TITLE = (lit) => lit.title || '未命名文献'
const DOI = (lit) => lit.doi || ''

/** 作者字符串 → 姓氏数组。兼容 "A B, C D" / "A; B" / "A and B" 分隔。 */
function authorList(authors) {
  if (!authors) return []
  return String(authors)
    .split(/[,;，；]| and /)
    .map((s) => s.trim())
    .filter(Boolean)
}

/** "Zhipeng Wang" → "Wang, Z."；中文姓名原样保留 */
function toSurInitial(name) {
  const parts = name.split(/\s+/)
  if (parts.length === 1) return name
  const family = parts[parts.length - 1]
  const initials = parts.slice(0, -1).map((p) => `${p[0]}.`).join('')
  return `${family}, ${initials}`
}

/** BibTeX 字段转义：& % # _ 是 LaTeX/ BibTeX 特殊字符，不转义会导致 .bib 编译报错 */
function bibtexEscape(text) {
  return String(text).replace(/([&%#_])/g, '\\$1')
}

function bibKey(lit) {
  const first = authorList(lit.authors)[0] || 'unknown'
  const family = first.split(/\s+/).pop().replace(/[^\w]/g, '')
  return `${family}${YEAR(lit)}`
}

export function toBibtex(lit) {
  const fields = [
    `  title     = {${bibtexEscape(TITLE(lit))}}`,
    lit.authors ? `  author    = {${bibtexEscape(lit.authors)}}` : null,
    lit.journal ? `  journal   = {${bibtexEscape(lit.journal)}}` : null,
    `  year      = {${YEAR(lit)}}`,
    DOI(lit) ? `  doi       = {${DOI(lit)}}` : null,
  ].filter(Boolean)
  return `@article{${bibKey(lit)},\n${fields.join(',\n')}\n}`
}

export function toGBT7714(lit) {
  // [1] Author A B, Author C D. Title[J]. Journal, Year.
  const authors = authorList(lit.authors).map(toSurInitial)
  const authorPart = authors.length ? `${authors.join(', ')}. ` : ''
  const journalPart = lit.journal ? `${lit.journal}, ` : ''
  return `[1] ${authorPart}${TITLE(lit)}[J]. ${journalPart}${YEAR(lit)}.${DOI(lit) ? ` DOI: ${DOI(lit)}.` : ''}`
}

export function toAPA(lit) {
  const authors = authorList(lit.authors).map(toSurInitial)
  const authorPart = authors.length
    ? `${authors.join(', ').replace(/, ([^,]*)$/, ' & $1')} `
    : ''
  const sourcePart = lit.journal ? `. ${lit.journal}` : ''
  return `${authorPart}(${YEAR(lit)}). ${TITLE(lit)}${sourcePart}.${DOI(lit) ? ` https://doi.org/${DOI(lit)}` : ''}`
}
