import { AnimatePresence, motion } from 'motion/react'
import { useState } from 'react'
import { methodsForPath } from '../applications/catalog'
import { ApplicationsTrigger } from './ApplicationsSheet'

export function BusinessBrief() {
  const [open, setOpen] = useState(false)
  return <section className="brief" aria-label="Business brief">
    <div className="brief-line">
      <span><b>Business task:</b> deliver every Dubai freight order at the lowest total cost, with a mathematical proof that no cheaper plan exists.</span>
      <button className="link-button" onClick={() => setOpen(!open)} aria-expanded={open}>{open ? 'Hide the brief' : 'Read the brief'}</button>
      <ApplicationsTrigger methods={methodsForPath('/production')} title="Where network flow and integer models pay off" />
    </div>
    <AnimatePresence initial={false}>
      {open && <motion.div className="brief-panel" initial={{ height: 0, opacity: 0 }} animate={{ height: 'auto', opacity: 1 }} exit={{ height: 0, opacity: 0 }} transition={{ duration: .45, ease: [.2, .8, .2, 1] }}>
        <div className="brief-grid">
          <div><span className="eyebrow">Challenge</span><h4>Freight between three hubs</h4><ul><li>Jebel Ali Port, Dubai South / DWC, DXB Cargo Village</li><li>Tonnes to move between every pair, ready at one time</li><li>Each truck trip and each tonne handled costs money</li></ul></div>
          <div><span className="eyebrow">Decisions</span><h4>What the planner chooses</h4><ul><li>Direct route or transfer through a hub</li><li>How many 20 t, 10 t and 5 t trucks</li><li>Which shipments share a truck</li><li>When each trip and terminal job runs</li></ul></div>
          <div><span className="eyebrow">Constraints</span><h4>What it must respect</h4><ul><li>Truck usable load and fleet limits</li><li>Terminal processing capacity and queues</li><li>Planning horizon and delivery deadline</li><li>Optional rental at a per-trip price</li></ul></div>
          <div><span className="eyebrow">Payoff</span><h4>Measured on the example</h4><span className="brief-number">−4.10%</span><ul><li>3,958 AED vs 4,127.20 AED direct-only</li><li>Optimality gap 0: proven cheapest</li><li>Ready-to-run trip and terminal schedule</li></ul></div>
        </div>
      </motion.div>}
    </AnimatePresence>
  </section>
}
