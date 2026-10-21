import { AnimatePresence, motion, useReducedMotion } from 'motion/react'
import { useMemo, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { type Application, byMethod, companiesFor, INDUSTRIES, type Industry, liveRoute, type Method, METHODS } from '../applications/catalog'
import { navigate } from '../navigation'
import { useFocusTrap } from './useFocusTrap'

export function ApplicationsTrigger({ methods, title }: { methods: Method[]; title?: string }) {
  const [open, setOpen] = useState(false)
  const apps = useMemo(() => byMethod(...methods), [methods.join()])
  if (!apps.length) return null
  return <>
    <button className="uses-trigger" onClick={() => setOpen(true)} aria-haspopup="dialog">
      <span className="pulse-dot" aria-hidden />
      <span><span className="count">{apps.length}</span> real-world uses</span>
    </button>
    {createPortal(<AnimatePresence>{open && <ApplicationsSheet apps={apps} methods={methods} title={title} onClose={() => setOpen(false)} />}</AnimatePresence>, document.body)}
  </>
}

function ApplicationsSheet({ apps, methods, title, onClose }: { apps: Application[]; methods: Method[]; title?: string; onClose: () => void }) {
  const ref = useRef<HTMLDivElement>(null)
  const reduce = useReducedMotion()
  const [industry, setIndustry] = useState<Industry | null>(null)
  const [openId, setOpenId] = useState<string | null>(null)
  useFocusTrap(ref, true, onClose)
  const industries = useMemo(() => [...new Set(apps.flatMap(app => app.industries))], [apps])
  const shown = industry ? apps.filter(app => app.industries.includes(industry)) : apps
  const documented = apps.filter(app => app.documented).length
  return <>
    <motion.div className="sheet-backdrop" onClick={onClose} initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} transition={{ duration: .25 }} />
    <motion.aside ref={ref} className="sheet" role="dialog" aria-modal="true" aria-label="Real-world applications"
      initial={reduce ? { opacity: 0 } : { x: '100%' }} animate={reduce ? { opacity: 1 } : { x: 0 }} exit={reduce ? { opacity: 0 } : { x: '100%' }}
      transition={{ type: 'spring', stiffness: 320, damping: 36 }}>
      <div className="sheet-head">
        <button className="sheet-close" onClick={onClose} aria-label="Close" data-autofocus>✕</button>
        <span className="eyebrow">{methods.map(m => METHODS[m].label).join(' · ')}</span>
        <h3>{title ?? 'Where this model pays off'}</h3>
        <p>{apps.length} <i>Operations Research</i> applications{documented ? `, ${documented} with documented results` : ''}.</p>
        {industries.length > 1 && <div className="chip-row">
          <button className={`filter-chip ${industry === null ? 'active' : ''}`} onClick={() => setIndustry(null)}>All</button>
          {industries.map(item => <button key={item} className={`filter-chip ${industry === item ? 'active' : ''}`} onClick={() => setIndustry(item)}>{INDUSTRIES[item].label}</button>)}
        </div>}
      </div>
      <div className="sheet-body">
        {shown.map(app => <UseRow key={app.id} app={app} industry={industry} open={openId === app.id} onToggle={() => setOpenId(openId === app.id ? null : app.id)} onNavigate={onClose} />)}
      </div>
    </motion.aside>
  </>
}

export function UseRow({ app, industry, open, onToggle, onNavigate }: { app: Application; industry: Industry | null; open: boolean; onToggle: () => void; onNavigate?: () => void }) {
  const route = liveRoute(app)
  const here = route === window.location.pathname
  return <div className={`use-row ${open ? 'open' : ''}`}>
    <button onClick={onToggle} aria-expanded={open}>
      <span><b>{app.title}</b><small>{app.industries.map(i => INDUSTRIES[i].label).join(' · ')}</small></span>
      <span className="chev" aria-hidden>＋</span>
    </button>
    <AnimatePresence initial={false}>
      {open && <motion.div className="use-detail" initial={{ height: 0, opacity: 0 }} animate={{ height: 'auto', opacity: 1 }} exit={{ height: 0, opacity: 0 }} transition={{ duration: .3, ease: [.2, .8, .2, 1] }}>
        <div className="use-detail-inner">
          <p>{app.problem}</p>
          <p className="payoff">{app.payoff}</p>
          <p className="companies">{app.documented ? 'Case: ' : 'Could benefit: '}{companiesFor(app, industry).join(', ')}</p>
          <div className="actions">
            {app.documented && <span className="pill documented">Documented result</span>}
            <span className="pill source">{app.source}</span>
            {route && !here && <button className="link-button" onClick={() => { onNavigate?.(); navigate(route) }}>Try it live →</button>}
          </div>
        </div>
      </motion.div>}
    </AnimatePresence>
  </div>
}
