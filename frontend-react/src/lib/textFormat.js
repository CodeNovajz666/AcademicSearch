/**
 * 展示层文本规范化：清理 PDF 抽取产物、统一中英文混排排版。
 * 只影响渲染，不改动数据库存储内容。
 *
 * 规则（保守，避免误伤正文）：
 * 1. 去掉 NBSP / 零宽字符，所有换行与连续空白折叠为单个空格
 * 2. 中文与拉丁字母/数字之间补一个空格（盘古之白）
 * 3. 标点前的多余空格移除；开括号后的多余空格移除
 * 4. 半角逗号后若紧跟字母/中文，补一个空格（"a,b" → "a, b"；数字不处理，保护 "1,000"）
 */
export function normalizeText(input) {
  if (typeof input !== 'string') return input ?? ''
  const s = input
    .replace(/[\u00a0\u200b\u200c\u200d\ufeff]/g, '')
    .replace(/\s+/g, ' ')
    .trim()
  if (!s) return s
  return s
    .replace(/([\u4e00-\u9fff\u3400-\u4dbf])([A-Za-z0-9])/g, '$1 $2')
    .replace(/([A-Za-z0-9%)])([\u4e00-\u9fff\u3400-\u4dbf])/g, '$1 $2')
    .replace(/\s+([,.;:!?%)\]}，。；：！？）】」》])/g, '$1')
    .replace(/([(\[{（【「《])\s+/g, '$1')
    .replace(/,([A-Za-z\u4e00-\u9fff])/g, ', $1')
}
