/**
 * 统一 blob 下载：<a> 必须先挂载 DOM 再 click（Firefox 不触发未挂载元素的下载），
 * 完成后延迟回收 objectURL（给浏览器留出启动下载的时间，避免内存泄漏）。
 */
export function downloadBlob(blob, filename) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  link.remove()
  setTimeout(() => URL.revokeObjectURL(url), 5000)
}
