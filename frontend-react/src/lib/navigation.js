/**
 * 校验登录/注册后的回跳目标为站内地址，防开放重定向。
 * 接受 React Router location 对象（{pathname, search, hash}）或站内路径字符串；
 * 非站内路径（//evil.com、http:// 等）或非法值返回 null，调用方回退首页。
 */
export function safeFromTarget(from) {
  if (!from) return null
  if (typeof from === 'object' && typeof from.pathname === 'string') {
    const { pathname, search = '', hash = '' } = from
    return pathname.startsWith('/') && !pathname.startsWith('//')
      ? `${pathname}${search}${hash}`
      : null
  }
  if (typeof from === 'string') {
    return from.startsWith('/') && !from.startsWith('//') ? from : null
  }
  return null
}
