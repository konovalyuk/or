import { AnimatePresence, motion } from 'motion/react'
import { type KeyboardEvent as ReactKeyboardEvent, useEffect, useMemo, useRef, useState } from 'react'
import { APPLICATIONS, type Application, INDUSTRIES, liveRoute, METHODS } from '../applications/catalog'
import { navigate, OPEN_PALETTE_EVENT } from '../navigation'
import { useFocusTrap } from './useFocusTrap'

const PAGES = [
  { title: 'Home', hint: 'Industries and all applications', path: '/' },
  { title: 'Production planner', hint: 'Dubai freight network · proven optimum', path: '/production' },
  { title: 'Models', hint: 'Transportation, LP and network models', path: '/study' },
  { title: 'Transportation model', hint: 'Vogel, MODI, step by step', path: '/transportation' },
  { title: 'Linear programming', hint: 'Graphical and simplex', path: '/linear-programming' },
  { title: 'Minimal spanning tree', hint: 'Network models · 6.2', path: '/network/spanning-tree' },
  { title: 'Shortest route', hint: 'Dijkstra and Floyd · 6.3', path: '/network/shortest-route' },
  { title: 'Maximal flow', hint: 'Labeling algorithm · 6.4', path: '/network/max-flow' },
  { title: 'Critical path', hint: 'CPM and PERT · 6.5', path: '/network/critical-path' },
]

type Item = { kind: 'page'; title: string; hint: string; path: string } | { kind: 'app'; title: string; hint: string; app: Application }

function haystack(app: Application) {
  return [app.title, app.problem, app.payoff, app.source, ...app.companies, ...app.industries.map(i => INDUSTRIES[i].label), ...app.methods.map(m => METHODS[m].label)].join(' ').toLowerCase()
}

export function CommandPalette() {
  const [open, setOpen] = useState(false)
  useEffect(() => {
    const onKey = (event: KeyboardEvent) => { if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') { event.preventDefault(); setOpen(value => !value) } }
    const onOpen = () => setOpen(true)
    window.addEventListener('keydown', onKey)
    window.addEventListener(OPEN_PALETTE_EVENT, onOpen)
    return () => { window.removeEventListener('keydown', onKey); window.removeEventListener(OPEN_PALETTE_EVENT, onOpen) }
  }, [])
  return <AnimatePresence>{open && <Palette onClose={() => setOpen(false)} />}</AnimatePresence>
}

function Palette({ onClose }: { onClose: () => void }) {
  const ref = useRef<HTMLDivElement>(null)
  const listRef = useRef<HTMLDivElement>(null)
  const [query, setQuery] = useState('')
  const [active, setActive] = useState(0)
  useFocusTrap(ref, true, onClose)
  const index = useMemo(() => APPLICATIONS.map(app => ({ app, text: haystack(app) })), [])
  const items: Item[] = useMemo(() => {
    const words = query.toLowerCase().split(/\s+/).filter(Boolean)
    const match = (text: string) => words.every(word => text.includes(word))
    const pages = PAGES.filter(page => match(`${page.title} ${page.hint}`.toLowerCase())).map(page => ({ kind: 'page' as const, ...page }))
    const apps = index.filter(entry => match(entry.text)).map(({ app }) => ({ kind: 'app' as const, title: app.title, hint: app.industries.map(i => INDUSTRIES[i].label).join(' · '), app }))
    return [...pages, ...apps]
  }, [query, index])
  useEffect(() => setActive(0), [query])
  useEffect(() => { listRef.current?.querySelector('.palette-item.active')?.scrollIntoView({ block: 'nearest' }) }, [active])
  const current = items[active]
  function choose(item: Item | undefined) {
    if (!item) return
    const target = item.kind === 'page' ? item.path : liveRoute(item.app)
    if (!target) return
    onClose()
    navigate(target)
  }
  function onKey(event: ReactKeyboardEvent) {
    if (event.key === 'ArrowDown') { event.preventDefault(); setActive(i => Math.min(items.length - 1, i + 1)) }
    else if (event.key === 'ArrowUp') { event.preventDefault(); setActive(i => Math.max(0, i - 1)) }
    else if (event.key === 'Enter') { event.preventDefault(); choose(current) }
  }
  const firstApp = items.findIndex(item => item.kind === 'app')
  return <div className="palette-wrap">
    <motion.div className="sheet-backdrop" onClick={onClose} initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} transition={{ duration: .2 }} />
    <motion.div ref={ref} className="palette" role="dialog" aria-modal="true" aria-label="Search models and applications" onKeyDown={onKey}
      initial={{ opacity: 0, scale: .96, y: -8 }} animate={{ opacity: 1, scale: 1, y: 0 }} exit={{ opacity: 0, scale: .97, y: -6 }} transition={{ type: 'spring', stiffness: 420, damping: 32 }}>
      <div className="palette-input"><span aria-hidden>⌕</span><input data-autofocus value={query} onChange={e => setQuery(e.target.value)} placeholder="Search models, industries, companies…" aria-label="Search" /><kbd>Esc</kbd></div>
      {items.length === 0 ? <div className="palette-empty">Nothing matches “{query}”.</div> : <div className="palette-grid">
        <div className="palette-list" ref={listRef} role="listbox">
          {items.map((item, i) => <div key={item.kind === 'page' ? item.path : item.app.id}>
            {i === 0 && item.kind === 'page' && <div className="palette-group">Pages</div>}
            {i === firstApp && <div className="palette-group">Applications</div>}
            <button role="option" aria-selected={i === active} className={`palette-item ${i === active ? 'active' : ''}`} onMouseMove={() => setActive(i)} onClick={() => choose(item)}><span>{item.title}</span><small>{item.hint}</small></button>
          </div>)}
        </div>
        <div className="palette-preview">{current && (current.kind === 'page' ? <><span className="eyebrow">Page</span><h4>{current.title}</h4><p>{current.hint}</p><p><button className="button primary" onClick={() => choose(current)}>Open</button></p></> : <>
          <span className="eyebrow">{current.app.methods.map(m => METHODS[m].label).join(' · ')}</span>
          <h4>{current.app.title}</h4>
          <p>{current.app.problem}</p>
          <p style={{ color: 'var(--ink)', fontWeight: 600 }}>{current.app.payoff}</p>
          <p style={{ color: 'var(--muted)' }}>{current.app.documented ? 'Case: ' : 'Could benefit: '}{current.app.companies.join(', ')}</p>
          <div className="chip-row">{current.app.documented && <span className="pill documented">Documented result</span>}<span className="pill source">Taha · {current.app.source}</span></div>
          {liveRoute(current.app) && <p><button className="button primary" onClick={() => choose(current)}>Try it live →</button></p>}
        </>)}</div>
      </div>}
      <div className="palette-foot"><span>↑↓ navigate</span><span>↵ open</span><span>⌘K / Ctrl K toggle</span></div>
    </motion.div>
  </div>
}
