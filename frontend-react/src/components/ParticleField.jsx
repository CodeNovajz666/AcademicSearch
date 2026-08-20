import { useEffect, useRef } from 'react'

const LINK_DIST = 130
const MOUSE_DIST = 170

export default function ParticleField() {
  const ref = useRef(null)

  useEffect(() => {
    const canvas = ref.current
    if (!canvas) return
    const ctx = canvas.getContext('2d')
    const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches
    const DPR = Math.min(window.devicePixelRatio || 1, 2)
    const mouse = { x: -9999, y: -9999 }
    let raf = 0
    let running = true
    let W = 0
    let H = 0
    let particles = []
    let accentRgb = [99, 91, 255]
    let alphaBase = 0.45

    const refreshColors = () => {
      const s = getComputedStyle(document.documentElement)
      const hex = s.getPropertyValue('--accent').trim()
      if (hex.startsWith('#')) {
        const n = parseInt(hex.slice(1), 16)
        accentRgb = [(n >> 16) & 255, (n >> 8) & 255, n & 255]
      }
      alphaBase = parseFloat(s.getPropertyValue('--particle-alpha')) || 0.45
    }

    const setup = () => {
      W = window.innerWidth
      H = window.innerHeight
      canvas.width = W * DPR
      canvas.height = H * DPR
      canvas.style.width = `${W}px`
      canvas.style.height = `${H}px`
      ctx.setTransform(DPR, 0, 0, DPR, 0, 0)
      const count = Math.min(110, Math.max(36, Math.floor((W * H) / 16000)))
      particles = Array.from({ length: count }, () => ({
        x: Math.random() * W,
        y: Math.random() * H,
        vx: (Math.random() - 0.5) * 0.34,
        vy: (Math.random() - 0.5) * 0.34,
        r: Math.random() * 1.4 + 0.8,
      }))
    }

    const draw = () => {
      const [r, g, b] = accentRgb
      ctx.clearRect(0, 0, W, H)
      for (let i = 0; i < particles.length; i++) {
        const p = particles[i]
        for (let j = i + 1; j < particles.length; j++) {
          const q = particles[j]
          const dx = p.x - q.x
          const dy = p.y - q.y
          if (Math.abs(dx) > LINK_DIST || Math.abs(dy) > LINK_DIST) continue
          const d = Math.hypot(dx, dy)
          if (d < LINK_DIST) {
            const a = (1 - d / LINK_DIST) * alphaBase * 0.5
            ctx.strokeStyle = `rgba(${r},${g},${b},${a})`
            ctx.lineWidth = 1
            ctx.beginPath()
            ctx.moveTo(p.x, p.y)
            ctx.lineTo(q.x, q.y)
            ctx.stroke()
          }
        }
        const mdx = p.x - mouse.x
        const mdy = p.y - mouse.y
        if (Math.abs(mdx) < MOUSE_DIST && Math.abs(mdy) < MOUSE_DIST) {
          const md = Math.hypot(mdx, mdy)
          if (md < MOUSE_DIST) {
            const a = (1 - md / MOUSE_DIST) * alphaBase * 0.7
            ctx.strokeStyle = `rgba(${r},${g},${b},${a})`
            ctx.lineWidth = 1
            ctx.beginPath()
            ctx.moveTo(p.x, p.y)
            ctx.lineTo(mouse.x, mouse.y)
            ctx.stroke()
          }
        }
        ctx.fillStyle = `rgba(${r},${g},${b},${alphaBase})`
        ctx.beginPath()
        ctx.arc(p.x, p.y, p.r, 0, Math.PI * 2)
        ctx.fill()
      }
    }

    const step = () => {
      if (!running) return
      for (const p of particles) {
        p.x += p.vx
        p.y += p.vy
        if (p.x < -20) p.x = W + 20
        else if (p.x > W + 20) p.x = -20
        if (p.y < -20) p.y = H + 20
        else if (p.y > H + 20) p.y = -20
      }
      draw()
      raf = requestAnimationFrame(step)
    }

    const onMouse = (e) => {
      mouse.x = e.clientX
      mouse.y = e.clientY
    }
    const onMouseOut = () => {
      mouse.x = -9999
      mouse.y = -9999
    }
    const onResize = () => {
      refreshColors()
      setup()
      if (reduced) draw()
    }
    const onVisibility = () => {
      if (document.hidden) {
        running = false
        cancelAnimationFrame(raf)
      } else if (!reduced) {
        running = true
        raf = requestAnimationFrame(step)
      }
    }
    const onThemeChange = () => {
      refreshColors()
      if (reduced) draw()
    }

    refreshColors()
    setup()
    if (reduced) {
      draw()
    } else {
      raf = requestAnimationFrame(step)
      window.addEventListener('mousemove', onMouse, { passive: true })
      window.addEventListener('mouseout', onMouseOut)
    }
    window.addEventListener('resize', onResize)
    window.addEventListener('theme:change', onThemeChange)
    document.addEventListener('visibilitychange', onVisibility)

    return () => {
      running = false
      cancelAnimationFrame(raf)
      window.removeEventListener('mousemove', onMouse)
      window.removeEventListener('mouseout', onMouseOut)
      window.removeEventListener('resize', onResize)
      window.removeEventListener('theme:change', onThemeChange)
      document.removeEventListener('visibilitychange', onVisibility)
    }
  }, [])

  return <canvas ref={ref} className="particle-field" aria-hidden="true" />
}
