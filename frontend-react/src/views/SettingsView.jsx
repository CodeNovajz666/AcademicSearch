import { useEffect, useState } from 'react'
import { Bot, KeyRound, Save, Server, Sparkles, Trash2 } from 'lucide-react'
import { api } from '../lib/api'
import { useToast } from '../context/ToastContext'

export default function SettingsView() {
  const toast = useToast()
  const [form, setForm] = useState({ apiKey: '', chatModel: '', subagentModel: '' })
  const [usingServerKey, setUsingServerKey] = useState(true)
  const [hasApiKey, setHasApiKey] = useState(false)
  const [maskedKey, setMaskedKey] = useState('')
  const [clearingKey, setClearingKey] = useState(false)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    api
      .getSettings()
      .then((data) => {
        // GET 只返回掩码：输入框保持空值，占位符提示"已保存，留空保持不变"
        setForm({
          apiKey: '',
          chatModel: data.chatModel || '',
          subagentModel: data.subagentModel || '',
        })
        setHasApiKey(Boolean(data.hasApiKey))
        setMaskedKey(data.apiKey || '')
        setUsingServerKey(data.usingServerKey)
      })
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false))
  }, [])

  const change = (key) => (e) => {
    const value = e.target.value
    setForm((f) => ({ ...f, [key]: value }))
    // 重新输入新 Key 时取消"清除"意图
    if (key === 'apiKey' && value.trim()) setClearingKey(false)
  }

  const save = async (e) => {
    e.preventDefault()
    setSaving(true)
    try {
      const payload = { chatModel: form.chatModel, subagentModel: form.subagentModel }
      if (form.apiKey.trim()) payload.apiKey = form.apiKey.trim()
      if (clearingKey) payload.clearApiKey = true
      const data = await api.saveSettings(payload)
      // 保存成功后回到"已保存掩码"态：输入框清空，不回显任何明文
      setForm((f) => ({ ...f, apiKey: '' }))
      setHasApiKey(Boolean(data.hasApiKey))
      setMaskedKey(data.apiKey || '')
      setClearingKey(false)
      setUsingServerKey(data.usingServerKey)
      toast('设置已保存，下一次对话生效')
    } catch (err) {
      toast(err.message, 'error')
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="page">
      <header className="section-head">
        <div>
          <h2 className="section-title">设置</h2>
          <p className="section-sub">配置智能体使用的 API Key 与模型，仅对你的账号生效</p>
        </div>
      </header>

      {loading && <p className="settings-help">加载中…</p>}
      {!loading && error && <p className="settings-error">设置加载失败：{error}</p>}

      {!loading && !error && (
        <form className="settings-card" onSubmit={save}>
          <div className="settings-status-row">
            <span className={`settings-badge${usingServerKey ? '' : ' is-own'}`}>
              {usingServerKey ? <Server size={13} /> : <KeyRound size={13} />}
              {usingServerKey ? '未配置个人 Key · AI 研究副驾不可用' : '当前使用我的 API Key'}
            </span>
            {hasApiKey && (
              <span className="settings-badge is-own">
                <KeyRound size={13} /> 已保存：{maskedKey}
              </span>
            )}
          </div>

          <label className="settings-field">
            <span className="settings-label">
              <KeyRound size={14} /> DashScope API Key
            </span>
            <input
              type="password"
              value={form.apiKey}
              onChange={change('apiKey')}
              placeholder={hasApiKey ? '已保存（留空保持不变，输入新值即替换）' : 'sk-…（使用AI研究副驾必填）'}
              autoComplete="new-password"
              spellCheck={false}
              disabled={clearingKey}
            />
            {hasApiKey && (
              <button
                type="button"
                className="btn-ghost"
                style={{ marginTop: 8, fontSize: 13 }}
                onClick={() => setClearingKey((v) => !v)}
              >
                <Trash2 size={14} />
                {clearingKey ? '取消清除（保存后生效）' : '移除已保存的 Key，回退服务器默认'}
              </button>
            )}
            <small className="settings-help">
              模型交互强制使用你自己的 Key（协调者、子智能体与工具调用均按你的 Key 计费），
              未配置时无法与 AI 副驾对话。完整 Key 仅保存在服务端，本页只显示掩码、不可回读。
            </small>
          </label>

          <div className="settings-grid">
            <label className="settings-field">
              <span className="settings-label">
                <Bot size={14} /> 协调者模型
              </span>
              <input
                type="text"
                value={form.chatModel}
                onChange={change('chatModel')}
                placeholder="qwen3.8-flash（服务器默认）"
              />
            </label>
            <label className="settings-field">
              <span className="settings-label">
                <Sparkles size={14} /> 子智能体模型
              </span>
              <input
                type="text"
                value={form.subagentModel}
                onChange={change('subagentModel')}
                placeholder="qwen3.8-flash（服务器默认）"
              />
            </label>
          </div>

          <div className="settings-actions">
            <span className="settings-help">保存后下一次对话生效。AI 副驾必须配置个人 API Key；协调者/子智能体模型留空则回退服务器默认。</span>
            <button type="submit" className="btn-primary" disabled={saving}>
              <Save size={15} /> {saving ? '保存中…' : '保存设置'}
            </button>
          </div>
        </form>
      )}

      <p className="settings-warn">
        安全提示：API Key 保存在服务端数据库，本页仅显示掩码。请勿在公共设备上保存长期有效的 Key，
        并定期在 DashScope 控制台轮换。
      </p>
    </div>
  )
}
