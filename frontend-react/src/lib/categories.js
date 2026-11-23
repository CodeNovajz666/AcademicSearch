/** 社区板块定义（与后端 LiteratureService.CATEGORIES 同源约定，扩展时两端同步）。 */
export const CATEGORIES = [
  { key: 'computer-science', label: '计算机科学', icon: 'cpu' },
  { key: 'engineering', label: '工学', icon: 'wrench' },
  { key: 'science', label: '理学', icon: 'atom' },
  { key: 'medicine', label: '医学', icon: 'stethoscope' },
  { key: 'social-science', label: '社会科学', icon: 'users' },
]

export const CATEGORY_MAP = Object.fromEntries(CATEGORIES.map((c) => [c.key, c]))

export function categoryLabel(key) {
  return CATEGORY_MAP[key]?.label || '未分类'
}
