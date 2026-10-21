import { animate, AnimatePresence, motion, useInView, useReducedMotion } from 'motion/react'
import { lazy, Suspense, useEffect, useMemo, useRef, useState } from 'react'
import { type Application, APPLICATIONS, byIndustry, companiesFor, DOCUMENTED_RESULTS, INDUSTRIES, type Industry, liveRoute, type Method, METHODS } from '../applications/catalog'
import { Reveal, spotlight } from '../components/Reveal'
import { navigate } from '../navigation'

const HeroScene = lazy(() => import('../components/HeroScene'))

function canUseWebGL() {
  try { const canvas = document.createElement('canvas'); return !!(canvas.getContext('webgl2') || canvas.getContext('webgl')) } catch { return false }
}

function HeroFallback() {
  const points = useMemo(() => Array.from({ length: 34 }, (_, i) => ({ x: (i * 137.5) % 100, y: ((i * 61.8) % 70) + 15 })), [])
  return <svg className="hero-fallback" viewBox="0 0 100 100" preserveAspectRatio="xMidYMid slice" aria-hidden>
    {points.map((p, i) => { const q = points[(i * 7 + 3) % points.length]; return <line key={`l${i}`} x1={p.x} y1={p.y} x2={q.x} y2={q.y} stroke="#4f6dff" strokeOpacity=".25" strokeWidth=".12" /> })}
    {points.map((p, i) => <circle key={i} cx={p.x} cy={p.y} r=".45" fill="#c9d6ff" fillOpacity=".7" />)}
  </svg>
}

function CountUp({ value, prefix, suffix }: { value: number; prefix: string; suffix: string }) {
  const ref = useRef<HTMLElement>(null)
  const inView = useInView(ref, { once: true, margin: '-40px' })
  const reduce = useReducedMotion()
  const decimals = (String(value).split('.')[1] ?? '').length
  const [shown, setShown] = useState(reduce ? value : 0)
  useEffect(() => {
    if (!inView || reduce) return
    const controls = animate(0, value, { duration: 1.6, ease: [.2, .8, .2, 1], onUpdate: setShown })
    return () => controls.stop()
  }, [inView, reduce, value])
  return <strong ref={ref}>{prefix}{shown.toFixed(decimals)}{suffix}</strong>
}

const ICONS: Record<Industry, string> = { logistics: '⇄', energy: '◉', retail: '▤', manufacturing: '⚙', finance: '◈', airlines: '✈', healthcare: '✚', public: '⌂', construction: '▲', telecom: '⟟', agriculture: '❋', forestry: '⧖' }
const TILES: { industry: Industry; wide?: boolean; dark?: boolean }[] = [
  { industry: 'logistics', wide: true, dark: true }, { industry: 'energy', wide: true },
  { industry: 'retail' }, { industry: 'manufacturing' }, { industry: 'finance' }, { industry: 'airlines' },
  { industry: 'construction', wide: true, dark: true }, { industry: 'healthcare' }, { industry: 'public' },
  { industry: 'telecom' }, { industry: 'agriculture' }, { industry: 'forestry', wide: true },
]

function tileApps(industry: Industry, wide?: boolean) {
  const apps = byIndustry(industry)
  const typical = apps.filter(app => !app.documented)
  const documented = apps.filter(app => app.documented)
  const size = wide ? 6 : 3
  return [...typical.slice(0, size - Math.min(1, documented.length)), ...documented.slice(0, 1)].slice(0, size)
}

function IndustryTile({ industry, wide, dark }: { industry: Industry; wide?: boolean; dark?: boolean }) {
  const apps = tileApps(industry, wide)
  const methods = [...new Set(apps.flatMap(app => app.methods))].slice(0, 5)
  return <div className={`bento-tile spotlight ${wide ? 'wide' : ''} ${dark ? 'dark' : ''}`} tabIndex={0}>
    <span className="industry-icon" aria-hidden>{ICONS[industry]}</span>
    <h3>{INDUSTRIES[industry].label}</h3>
    <p className="tagline">{INDUSTRIES[industry].tagline}</p>
    <ul className="bento-list">{apps.map(app => <li key={app.id}><b>{app.title}</b> <span>({companiesFor(app, industry).slice(0, 3).join(', ')})</span></li>)}</ul>
    <div className="bento-meta">{methods.map(m => <span key={m} className="pill">{METHODS[m].label}</span>)}</div>
    {wide && <span className="bento-glyph" aria-hidden>{ICONS[industry]}</span>}
  </div>
}

function AppCard({ app, industry }: { app: Application; industry: Industry | null }) {
  const route = liveRoute(app)
  return <motion.article layout className="app-card spotlight" initial={{ opacity: 0, scale: .97 }} animate={{ opacity: 1, scale: 1 }} exit={{ opacity: 0, scale: .97 }} transition={{ duration: .3, ease: [.2, .8, .2, 1] }}>
    <span className="eyebrow">{app.industries.map(i => INDUSTRIES[i].label).join(' · ')}</span>
    <h4>{app.title}</h4>
    <p>{app.problem}</p>
    <p className="payoff">{app.payoff}</p>
    <p className="companies">({companiesFor(app, industry).join(', ')})</p>
    <div className="foot">
      {app.documented && <span className="pill documented">Documented result</span>}
      <span className="pill source">{app.source}</span>
      {route && <button className="link-button try" onClick={() => navigate(route)}>Try it live →</button>}
    </div>
  </motion.article>
}

function Navigator() {
  const [industry, setIndustry] = useState<Industry | null>(null)
  const [method, setMethod] = useState<Method | null>(null)
  const [query, setQuery] = useState('')
  const [all, setAll] = useState(false)
  const methods = useMemo(() => (Object.keys(METHODS) as Method[]).filter(m => APPLICATIONS.some(app => app.methods.includes(m))), [])
  const filtered = useMemo(() => {
    const words = query.toLowerCase().split(/\s+/).filter(Boolean)
    return APPLICATIONS.filter(app => (!industry || app.industries.includes(industry)) && (!method || app.methods.includes(method)) && words.every(word => `${app.title} ${app.problem} ${app.payoff} ${app.companies.join(' ')} ${app.source}`.toLowerCase().includes(word)))
  }, [industry, method, query])
  const shown = all ? filtered : filtered.slice(0, 9)
  return <div className="navigator">
    <div className="navigator-bar">
      <div className="navigator-search"><span aria-hidden>⌕</span><input value={query} onChange={e => setQuery(e.target.value)} placeholder="Search a problem, company or method…" aria-label="Search applications" /><span className="navigator-count">{filtered.length} of {APPLICATIONS.length}</span></div>
      <div className="chip-row"><button className={`filter-chip ${industry === null ? 'active' : ''}`} onClick={() => setIndustry(null)}>All industries</button>{(Object.keys(INDUSTRIES) as Industry[]).map(item => <button key={item} className={`filter-chip ${industry === item ? 'active' : ''}`} onClick={() => setIndustry(industry === item ? null : item)}>{INDUSTRIES[item].label}</button>)}</div>
      <div className="chip-row"><button className={`filter-chip ${method === null ? 'active' : ''}`} onClick={() => setMethod(null)}>All methods</button>{methods.map(item => <button key={item} className={`filter-chip ${method === item ? 'active' : ''}`} onClick={() => setMethod(method === item ? null : item)}>{METHODS[item].route && <span className="live-dot" aria-label="live solver" />}{METHODS[item].label}</button>)}</div>
    </div>
    <motion.div layout className="app-grid" onPointerMove={spotlight}>
      <AnimatePresence mode="popLayout">{shown.map(app => <AppCard key={app.id} app={app} industry={industry} />)}</AnimatePresence>
    </motion.div>
    {filtered.length > shown.length && <div style={{ textAlign: 'center' }}><button className="button secondary" onClick={() => setAll(true)}>Show all {filtered.length}</button></div>}
    <p className="disclaimer"><span className="live-dot" aria-hidden />marks methods with a live solver in this portal. Companies in brackets are examples of organisations that face the problem, not claims of use. “Documented result” marks real-life cases reported in Taha, <i>Operations Research: An Introduction</i>, 10th ed.</p>
  </div>
}

export function Home() {
  const [webgl] = useState(() => canUseWebGL())
  const reduce = useReducedMotion()
  const fallback = <HeroFallback />
  return <div className="home-main">
    <section className="home-hero">
      <div className="home-hero-canvas" aria-hidden>{webgl && !reduce ? <Suspense fallback={fallback}><HeroScene /></Suspense> : fallback}</div>
      <div className="page-width">
        <motion.div className="home-hero-copy" initial={reduce ? false : { opacity: 0, y: 24 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 1, ease: [.2, .8, .2, 1] }}>
          <span className="eyebrow">Operations research for business</span>
          <h1>Right decisions in real operations, <span className="gradient-text">backed by mathematics.</span></h1>
          <p className="lead">Routes, fleets, production, stock, staff and projects, using Operations Research models and solved to a proven optimum. You get the cheapest plan and the proof that it is the cheapest.</p>
          <div className="cta-row"><button className="button light" onClick={() => navigate('/production')}>Run the production planner</button><button className="button ghost" onClick={() => navigate('/study')}>Explore the models</button></div>
        </motion.div>
      </div>
      <span className="hero-scroll" aria-hidden>Scroll</span>
    </section>

    <section className="proof-band">
      <div className="page-width">
        <Reveal><span className="section-kicker">Documented results</span></Reveal>
        <Reveal delay={.05}><div className="proof-grid">{DOCUMENTED_RESULTS.map(item => <div key={`${item.who}-${item.label}`}><CountUp value={item.value} prefix={item.prefix} suffix={item.suffix} /><span>{item.label}</span><small>{item.who}</small></div>)}</div></Reveal>
        <p className="proof-note">Real-life applications reported in Taha, <i>Operations Research: An Introduction</i>, 10th edition, chapters 2–18 and Chapter 26.</p>
      </div>
    </section>

    <section className="home-section">
      <div className="page-width">
        <Reveal><span className="section-kicker">Where it pays</span><h2 className="section-title">Every industry runs on decisions that can be computed.</h2><p className="section-lead">A handful of models, from linear programming and transportation to networks, integer and dynamic programming, inventory and queues, covers the decisions that move margin in each sector.</p></Reveal>
        <div className="bento" onPointerMove={spotlight}>{TILES.map((tile, i) => <Reveal key={tile.industry} delay={(i % 4) * .06} className={tile.wide ? 'bento-cell wide' : 'bento-cell'}><IndustryTile {...tile} /></Reveal>)}</div>
      </div>
    </section>

    <section className="home-section tight steps-section">
      <div className="page-width">
        <Reveal><span className="section-kicker">How it works</span><h2 className="section-title">From your data to a plan you can defend.</h2></Reveal>
        <div className="steps-row">
          {[['01', 'Describe the operation', 'Demand, capacities, costs and rules you already track. No new data collection.'],
            ['02', 'Solve it exactly', 'Simplex, branch and bound and network algorithms, implemented in this portal.'],
            ['03', 'Act with proof', 'A lower bound equal to the plan cost shows that no cheaper plan exists. Every step can be replayed.']].map(([n, title, text], i) =>
            <Reveal key={n} delay={i * .08} className="step-card"><span className="step-number gradient-text">{n}</span><h3>{title}</h3><p>{text}</p></Reveal>)}
        </div>
      </div>
    </section>

    <section className="home-section tight">
      <div className="page-width">
        <Reveal><span className="section-kicker">Application navigator</span><h2 className="section-title">Find the model behind your problem.</h2><p className="section-lead">{APPLICATIONS.length} operations research applications across industries and methods. Filter by industry or method, then open the live solver where one exists.</p></Reveal>
        <Navigator />
      </div>
    </section>

    <section className="cta-band">
      <div className="page-width">
        <Reveal><h2>Your next decision can be computed, not guessed.</h2><p>Start with the Dubai freight planner, or open a classroom model and watch the algorithm reach its optimum step by step.</p><div className="cta-row"><button className="button light" onClick={() => navigate('/production')}>Run the production planner</button><button className="button ghost" onClick={() => navigate('/study')}>Explore the models</button></div></Reveal>
      </div>
    </section>
  </div>
}
