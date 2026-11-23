import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { ArrowRight, FileSearch, LogIn, Sparkles, UploadCloud, UserPlus } from 'lucide-react'
import { api } from '../lib/api'
import { useAuth } from '../context/AuthContext'
import SearchHero from '../components/SearchHero'
import LiteratureCard from '../components/LiteratureCard'
import SkeletonCard from '../components/SkeletonCard'
import EmptyState from '../components/EmptyState'

export default function HomeView() {
  const { user } = useAuth()
  const [recent, setRecent] = useState(null)
  const [total, setTotal] = useState(0)
  const [error, setError] = useState('')

  useEffect(() => {
    if (!user) return
    // 最近入库 = 我的文献库的最新几篇（与"查看全部"指向的 /library 同一数据源）；
    // 不能用社区列表（scope=all 只含公开文献，自己刚上传的私有文献不会出现）。
    // 接口返回分页结构 { items, total, page, size }，默认按 createdAt 倒序，直接取首页 4 篇。
    api
      .listLiterature('mine', null, { page: 0, size: 4 })
      .then((data) => {
        setRecent(data.items)
        setTotal(data.total)
      })
      .catch((e) => setError(e.message))
  }, [user])

  if (!user) {
    return (
      <div className="page">
        <section className="home-hero">
          <SearchHero />
        </section>
        <EmptyState
          icon={Sparkles}
          title="登录后开启你的智能文献库"
          desc="上传 PDF 自动解析并向量化，语义检索研究文献，AI 副驾的每个回答皆可溯源。"
          action={
            <div className="guest-actions">
              <Link to="/login" className="btn-primary">
                <LogIn size={16} strokeWidth={2} /> 立即登录
              </Link>
              <Link to="/register" className="btn-ghost">
                <UserPlus size={16} strokeWidth={2} /> 注册新账号
              </Link>
            </div>
          }
        />
      </div>
    )
  }

  return (
    <div className="page">
      <section className="home-hero">
        <SearchHero />
        {recent && total > 0 && (
          <p className="home-stat">
            我的文献库已收录 {total} 篇 · 语义检索 · 检索增强问答
          </p>
        )}
      </section>

      {recent === null && !error && (
        <section className="home-recent">
          <header className="section-head">
            <h2 className="section-title">最近入库</h2>
          </header>
          <div className="lit-list">
            {[0, 1, 2].map((i) => (
              <SkeletonCard key={i} />
            ))}
          </div>
        </section>
      )}

      {/* 接口失败必须给出可见提示：静默留白会被误认为"文献库内容丢了" */}
      {recent === null && error && (
        <section className="home-recent">
          <header className="section-head">
            <h2 className="section-title">最近入库</h2>
          </header>
          <EmptyState icon={FileSearch} title="最近入库加载失败" desc={error} />
        </section>
      )}

      {recent && recent.length > 0 && (
        <section className="home-recent">
          <header className="section-head">
            <h2 className="section-title">最近入库</h2>
            <Link to="/library" className="section-more">
              查看全部 <ArrowRight size={14} />
            </Link>
          </header>
          <div className="lit-list">
            {recent.slice(0, 4).map((lit, i) => (
              <LiteratureCard key={lit.id} item={lit} index={i} />
            ))}
          </div>
        </section>
      )}

      {recent?.length === 0 && (
        <EmptyState
          icon={FileSearch}
          title="文献库还是空的"
          desc="上传第一篇 PDF 文献，系统将自动解析全文并建立向量索引。"
          action={
            <Link to="/upload" className="btn-primary">
              <UploadCloud size={16} /> 上传文献
            </Link>
          }
        />
      )}
    </div>
  )
}
