export default function SkeletonCard() {
  return (
    <div className="lit-card skeleton-card" aria-hidden="true">
      <div className="sk sk-title" />
      <div className="sk sk-meta" />
      <div className="sk sk-line w90" />
      <div className="sk sk-line w75" />
      <div className="sk sk-line w55" />
    </div>
  )
}
