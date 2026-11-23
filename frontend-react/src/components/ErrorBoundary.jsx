import { Component } from 'react'

/**
 * 全局错误边界：任何渲染异常（PDF 解析、后端返回异常数据结构等）
 * 不再卸载整棵 React 树白屏，而是显示可恢复的兜底页。
 */
class ErrorBoundary extends Component {
  constructor(props) {
    super(props)
    this.state = { error: null }
  }

  static getDerivedStateFromError(error) {
    return { error }
  }

  componentDidCatch(error, info) {
    console.error('页面渲染异常：', error, info?.componentStack)
  }

  render() {
    if (!this.state.error) return this.props.children
    return (
      <div
        style={{
          minHeight: '100vh',
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          gap: 16,
          padding: 24,
          fontFamily: 'system-ui, sans-serif',
          textAlign: 'center',
        }}
      >
        <h2 style={{ margin: 0 }}>页面出现异常</h2>
        <p style={{ margin: 0, opacity: 0.7, maxWidth: 520 }}>
          {String(this.state.error?.message || this.state.error)}
        </p>
        <div style={{ display: 'flex', gap: 12 }}>
          <button type="button" onClick={() => this.setState({ error: null })}>
            返回重试
          </button>
          <button type="button" onClick={() => window.location.reload()}>
            刷新页面
          </button>
        </div>
      </div>
    )
  }
}

export default ErrorBoundary
