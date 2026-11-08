import { useEffect, useMemo, useState } from 'react'
import { motion, useReducedMotion } from 'motion/react'
import { BusinessBrief } from './components/BusinessBrief'
import { CountUp } from './components/CountUp'
import { loadRoutePlanExample, type PlanResult, type RoutePlanRequest, type RoutePlanResponse, type TerminalJob } from './routePlanApi'

const scenarioNames: Record<string, string> = {
  directOnly: 'Direct trucks only',
  optimizedNetwork: 'Optimized network',
  ownedFleetOnly: 'Owned fleet only',
  ownedFleetWithRental: 'Owned fleet + rental',
}

const statusTitles: Record<PlanResult['status'], string> = {
  OPTIMAL: 'Global optimum proven',
  FEASIBLE_NOT_PROVEN: 'Feasible plan, optimality not proven',
  INFEASIBLE: 'No feasible plan',
  NO_SOLUTION_FOUND: 'No plan found within the search limit',
  MODEL_TOO_LARGE: 'Model exceeds the configured size limits',
}

const jobColors: Record<TerminalJob['kind'], string> = { ORIGIN: '#2563eb', TRANSIT: '#d97706', DESTINATION: '#16a34a' }

function fmt(value: number | null | undefined, digits = 2) { return value == null ? '—' : Number(value).toFixed(digits) }

function clock(readyTime: string, hours: number | null | undefined) {
  if (hours == null) return '—'
  const [h, m] = readyTime.split(':').map(Number)
  const total = Math.round((h * 60 + m) + hours * 60)
  const day = Math.floor(total / 1440)
  const minutes = ((total % 1440) + 1440) % 1440
  const text = `${String(Math.floor(minutes / 60)).padStart(2, '0')}:${String(minutes % 60).padStart(2, '0')}`
  return day > 0 ? `${text} (+${day}d)` : text
}

type Dataset = 'dubai' | 'novaPoshta' | 'custom'

const MIN_CITIES = 2
const MAX_CITIES = 4

function clampCityCount(value: number) {
  if (!Number.isFinite(value)) return MIN_CITIES
  return Math.min(MAX_CITIES, Math.max(MIN_CITIES, Math.floor(value)))
}

function matrixWith(matrix: number[][], row: number, column: number, value: number) {
  return matrix.map((line, i) => line.map((cell, j) => i === row && j === column ? value : cell))
}

function squareMatrix(current: number[][], size: number) {
  return Array.from({ length: size }, (_, i) => Array.from({ length: size }, (_, j) => i === j ? 0 : current[i]?.[j] ?? 0))
}

function resizeNetwork(problem: RoutePlanRequest, size: number) {
  const count = clampCityCount(size)
  const lastCapacity = problem.terminalCapacity[problem.terminalCapacity.length - 1] ?? 1
  const lastHandling = problem.terminalHandlingCost[problem.terminalHandlingCost.length - 1] ?? 0
  return {
    locations: Array.from({ length: count }, (_, i) => problem.locations[i] ?? `City ${i + 1}`),
    demandMatrix: squareMatrix(problem.demandMatrix, count),
    distanceMatrix: squareMatrix(problem.distanceMatrix, count),
    terminalCapacity: Array.from({ length: count }, (_, i) => problem.terminalCapacity[i] ?? lastCapacity),
    terminalHandlingCost: Array.from({ length: count }, (_, i) => problem.terminalHandlingCost[i] ?? lastHandling),
  }
}

const novaPoshtaLocations = ['Poltava', 'Kharkiv', 'Dnipro', 'Kremenchuk']

const novaPoshtaDemand = [
  [0, 9, 12, 14],
  [76, 0, 55, 10],
  [46, 86, 0, 15],
  [55, 31, 54, 0],
]

const novaPoshtaDistance = [
  [0, 149, 185, 108],
  [152, 0, 225, 257],
  [185, 223, 0, 184],
  [108, 257, 184, 0],
]

function novaPoshtaProblem(current: RoutePlanRequest): RoutePlanRequest {
  return {
    ...current,
    locations: [...novaPoshtaLocations],
    demandMatrix: novaPoshtaDemand.map(row => [...row]),
    distanceMatrix: novaPoshtaDistance.map(row => [...row]),
    terminalCapacity: [25, 85, 90, 20],
    terminalHandlingCost: [28, 57, 57, 28],
    vehicleTypes: [
      { name: '20 t', nominalCapacity: 20, tariffPerKm: 15.64, rentalCostPerTrip: 0 },
      { name: '10 t', nominalCapacity: 10, tariffPerKm: 10.8, rentalCostPerTrip: 0 },
      { name: '5 t', nominalCapacity: 5, tariffPerKm: 5.23, rentalCostPerTrip: 0 },
    ],
    loadingFactor: 0.8,
    speedKmPerHour: 60,
    readyTime: '20:00',
    planningHorizonHours: 36,
    deliveryDeadline: null,
    ownedVehicles: null,
    rentalEnabled: false,
    rentalLimits: null,
    directOnly: false,
    consolidation: true,
    routeActivationCost: null,
    vehicleActivationCost: null,
    discountBands: null,
    currency: 'UAH',
  }
}

function clockText(seconds: number) {
  return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`
}

function WaitingPanel({ seconds }: { seconds: number }) {
  return <div className="wait-panel">
    <span className="wait-mark" aria-hidden><span /></span>
    <div><strong>Proving the optimum.</strong><p aria-live="polite">This usually takes a few minutes.</p></div>
    <b className="wait-clock">{clockText(seconds)}</b>
  </div>
}

function Unit({ children }: { children: string }) {
  return <small className="th-unit">{children}</small>
}

function NumberInput({ value, onChange, min = 0, step = 'any' }: { value: number; onChange: (value: number) => void; min?: number; step?: string }) {
  return <input className="matrix-input" type="number" min={min} step={step} value={value} onChange={event => onChange(Number(event.target.value))} />
}

function OptionalNumberInput({ value, onChange }: { value: number | null; onChange: (value: number | null) => void }) {
  return <input className="matrix-input" type="number" min="0" step="1" placeholder="unlimited" value={value ?? ''}
    onChange={event => onChange(event.target.value === '' ? null : Math.max(0, Math.floor(Number(event.target.value))))} />
}

function MatrixEditor({ title, unit, locations, values, onChange }: { title: string; unit: string; locations: string[]; values: number[][]; onChange: (row: number, column: number, value: number) => void }) {
  return <div className="table-scroll"><h4>{title}</h4><table className="matrix-table input-table"><thead><tr><th>From / To ({unit})</th>{locations.map((city, i) => <th key={i}>{city}</th>)}</tr></thead>
    <tbody>{values.map((row, i) => <tr key={i}><th>{locations[i]}</th>{row.map((value, j) => <td key={j}>{i === j ? <span className="planner-diagonal">—</span> : <NumberInput value={value} onChange={next => onChange(i, j, next)} />}</td>)}</tr>)}</tbody></table></div>
}

const GRAPH = { width: 860, height: 470 }

function circlePositions(count: number) {
  const cx = GRAPH.width / 2
  const cy = GRAPH.height / 2 + 15
  const r = Math.min(GRAPH.width, GRAPH.height) * 0.36
  return Array.from({ length: count }, (_, i) => {
    const angle = -Math.PI / 2 + (2 * Math.PI * i) / count
    return { x: cx + r * Math.cos(angle) * 1.35, y: cy + r * Math.sin(angle), outward: { x: Math.cos(angle), y: Math.sin(angle) } }
  })
}

function FlowGraph({ plan, labels }: { plan: PlanResult; labels: string[] }) {
  const arcs = useMemo(() => {
    const byArc = new Map<string, { from: number; to: number; tonnage: number; trips: number; transit: boolean }>()
    for (const trip of plan.trips) {
      const key = `${trip.from}-${trip.to}`
      const current = byArc.get(key) ?? { from: trip.from, to: trip.to, tonnage: 0, trips: 0, transit: false }
      current.tonnage += trip.loadedTonnage
      current.trips += 1
      current.transit ||= trip.loads.some(load => load.origin !== trip.from || load.destination !== trip.to)
      byArc.set(key, current)
    }
    return [...byArc.values()]
  }, [plan])
  const points = circlePositions(labels.length)
  const radius = 26
  return <div className="graph-card"><h4>Freight flows on the network</h4>
    <svg className="planner-graph" viewBox={`0 0 ${GRAPH.width} ${GRAPH.height}`} role="img" aria-label="Freight flows">
      <defs>{['#0f766e', '#d97706'].map(color => <marker key={color} id={`planner-arrow-${color.slice(1)}`} markerWidth="5" markerHeight="5" refX="4" refY="2.5" orient="auto" markerUnits="strokeWidth"><path d="M0,0 L0,5 L5,2.5 z" fill={color} /></marker>)}</defs>
      {arcs.map((arc, index) => {
        const a = points[arc.from], b = points[arc.to]
        const dx = b.x - a.x, dy = b.y - a.y, length = Math.hypot(dx, dy) || 1
        const nx = -dy / length, ny = dx / length
        const bend = 46
        const cx = (a.x + b.x) / 2 + nx * bend, cy = (a.y + b.y) / 2 + ny * bend
        const start = { x: a.x + ((cx - a.x) / Math.hypot(cx - a.x, cy - a.y)) * radius, y: a.y + ((cy - a.y) / Math.hypot(cx - a.x, cy - a.y)) * radius }
        const width = Math.min(9, 2 + arc.tonnage / 4)
        const gap = radius + width * 4
        const end = { x: b.x + ((cx - b.x) / Math.hypot(cx - b.x, cy - b.y)) * gap, y: b.y + ((cy - b.y) / Math.hypot(cx - b.x, cy - b.y)) * gap }
        const color = arc.transit ? '#d97706' : '#0f766e'
        const d = `M${start.x},${start.y} Q${cx},${cy} ${end.x},${end.y}`
        const delay = { animationDelay: `${index * 0.08}s` }
        return <g key={`${arc.from}-${arc.to}`}>
          <path className="flow-arc" style={delay} pathLength={1} d={d} fill="none" stroke={color} strokeWidth={width} strokeLinecap="round" markerEnd={`url(#planner-arrow-${color.slice(1)})`} />
          <path className="flow-cargo" style={delay} d={d} fill="none" stroke="#ffffff" strokeOpacity=".85" strokeWidth={Math.max(1.4, width * 0.38)} strokeLinecap="round" />
          <text x={(a.x + b.x) / 2 + nx * (bend / 2 + 20)} y={(a.y + b.y) / 2 + ny * (bend / 2 + 20) + 4} textAnchor="middle" className="graph-edge-label planner-edge-label">{fmt(arc.tonnage, 1)} t · {arc.trips} trip{arc.trips === 1 ? '' : 's'}</text>
        </g>
      })}
      {labels.map((label, i) => {
        const point = points[i]
        const load = plan.terminalLoads.find(terminal => terminal.city === i)
        const transit = !!load && load.transitTonnage > 1e-9
        const below = point.outward.y >= -0.2
        const captionY = below ? point.y + radius + 20 : point.y - radius - 26
        return <g key={label}>
          <circle cx={point.x} cy={point.y} r={radius} fill={transit ? '#fef3c7' : '#dbeafe'} stroke={transit ? '#b45309' : '#1d4ed8'} strokeWidth="2.5" />
          <text x={point.x} y={point.y + 5} textAnchor="middle" className="graph-node-label">{i + 1}</text>
          <text x={point.x} y={captionY} textAnchor="middle" className="planner-node-caption">{label}</text>
          <text x={point.x} y={captionY + 15} textAnchor="middle" className="planner-node-sub">{fmt(load?.totalTonnage ?? 0, 0)} t handled{transit ? ` · ${fmt(load!.transitTonnage, 0)} t transit` : ''}</text>
        </g>
      })}
    </svg>
    <p className="graph-legend">Arrow width = loaded tonnage · teal = direct cargo only · amber = carries transshipped cargo · amber terminal = transit hub</p></div>
}

function Timeline({ plan, labels, readyTime }: { plan: PlanResult; labels: string[]; readyTime: string }) {
  const rows = [
    ...labels.map((label, city) => ({ label: `Terminal · ${label}`, bars: plan.terminalJobs.filter(job => job.city === city).map(job => ({ start: job.startHour, end: job.endHour, color: jobColors[job.kind], title: `${job.kind.toLowerCase()} · ${labels[job.city]} · ${fmt(job.tonnage)} t · ${clock(readyTime, job.startHour)}–${clock(readyTime, job.endHour)}` })) })),
    ...plan.trips.map((trip, n) => ({ label: `Trip ${n + 1} · ${labels[trip.from]} → ${labels[trip.to]}`, bars: [{ start: trip.departureHour, end: trip.arrivalHour, color: trip.rented ? '#7c3aed' : '#475569', title: `${trip.vehicleName} · ${fmt(trip.loadedTonnage)} t · ${clock(readyTime, trip.departureHour)}–${clock(readyTime, trip.arrivalHour)}` }] })),
  ]
  const end = Math.max(1, plan.completionHour ?? 0, ...rows.flatMap(row => row.bars.map(bar => bar.end)))
  const span = Math.ceil(end * 2) / 2
  const labelWidth = 290
  const width = 980
  const rowHeight = 26
  const height = rows.length * rowHeight + 36
  const x = (hours: number) => labelWidth + (hours / span) * (width - labelWidth - 20)
  const ticks = Array.from({ length: Math.floor(span / 0.5) + 1 }, (_, i) => i * 0.5)
  return <div className="table-scroll"><svg className="planner-timeline" viewBox={`0 0 ${width} ${height}`} role="img" aria-label="Delivery schedule">
    {ticks.map(tick => <g key={tick}><line x1={x(tick)} x2={x(tick)} y1={20} y2={height} stroke="#e2e8f0" /><text x={x(tick)} y={14} textAnchor="middle" className="planner-tick">{clock(readyTime, tick)}</text></g>)}
    {rows.map((row, i) => <g key={row.label}>
      <text x={8} y={36 + i * rowHeight + 13} className="planner-row-label">{row.label}</text>
      {row.bars.map((bar, j) => <rect key={j} x={x(bar.start)} y={36 + i * rowHeight + 2} width={Math.max(2, x(bar.end) - x(bar.start))} height={rowHeight - 8} rx="4" fill={bar.color}><title>{bar.title}</title></rect>)}
    </g>)}
  </svg><p className="graph-legend">Terminal processing: blue = outbound (origin), amber = transit, green = inbound (destination) · trips: grey = owned, violet = rented</p></div>
}

function BoundsConvergence({ lower, upper, gapPercent, currency }: { lower: number; upper: number; gapPercent: number | null; currency: string }) {
  const reduce = useReducedMotion()
  const proven = gapPercent != null && gapPercent <= 1e-6
  const spread = proven ? 0 : Math.min(38, 6 + (gapPercent ?? 10) * 6)
  const ease = [.2, .8, .2, 1] as const
  const move = (from: number, to: number) => ({ initial: reduce ? false as const : { left: `${from}%` }, animate: { left: `${to}%` }, transition: { duration: 1.2, ease } })
  return <div className={`bounds ${proven ? 'proven' : ''}`} aria-label={`Lower bound ${fmt(lower)} ${currency}, plan cost ${fmt(upper)} ${currency}`}>
    <div className="bounds-track"><motion.div className="bounds-fill" initial={reduce ? false : { left: '4%', right: '4%' }} animate={{ left: `${50 - spread}%`, right: `${50 - spread}%` }} transition={{ duration: 1.2, ease }} /></div>
    <motion.div className="bounds-marker lower" {...move(4, 50 - spread)}><span>Lower bound</span><b>{fmt(lower)}</b></motion.div>
    <motion.div className="bounds-marker upper" {...move(96, 50 + spread)}><span>Plan cost</span><b>{fmt(upper)}</b></motion.div>
    {proven && <motion.div className="bounds-seal" initial={reduce ? false : { opacity: 0, scale: .6 }} animate={{ opacity: 1, scale: 1 }} transition={{ delay: reduce ? 0 : 1.15, type: 'spring', stiffness: 260, damping: 16 }}><span aria-hidden>✓</span>Proven optimal · gap 0</motion.div>}
  </div>
}

function ProofCard({ plan, currency }: { plan: PlanResult; currency: string }) {
  return <section className="card result-card"><div className="result-banner"><div><span className="eyebrow">OPTIMALITY CERTIFICATE</span><h3>{statusTitles[plan.status]}</h3><p>{plan.message}</p></div>{plan.totalCost == null ? <strong>—</strong> : <CountUp value={plan.totalCost} decimals={2} suffix={` ${currency}`} />}</div>
    {plan.totalCost != null && plan.lowerBound != null && <BoundsConvergence lower={plan.lowerBound} upper={plan.totalCost} gapPercent={plan.gapPercent} currency={currency} />}
    <div className="metric-grid"><div><span>Plan cost (upper bound)</span><b>{fmt(plan.totalCost)} {currency}</b></div><div><span>Lower bound</span><b>{fmt(plan.lowerBound)} {currency}</b></div><div><span>Gap</span><b>{plan.gapPercent == null ? '—' : `${fmt(plan.gapPercent, 4)} %`}</b></div><div><span>Search nodes</span><b>{plan.searchNodes}</b></div><div><span>Method</span><b className="planner-small">{plan.method}</b></div></div>
    {plan.proof.length > 0 && <ol className="planner-proof">{plan.proof.map((line, i) => <li key={i}>{line}</li>)}</ol>}
    {plan.warnings.length > 0 && <div className="notice warning">{plan.warnings.join(' ')}</div>}
  </section>
}

function CostCard({ plan, currency, readyTime }: { plan: PlanResult; currency: string; readyTime: string }) {
  return <section className="card"><div className="section-heading"><div><span className="eyebrow">COST STRUCTURE</span><h3>Where the money goes</h3><p>Transport is charged per loaded one-way trip (tariff × km); handling is charged at every terminal the cargo visits.</p></div></div>
    <div className="metric-grid"><div><span>Transport</span><b>{fmt(plan.transportCost)} {currency}</b></div><div><span>Terminal handling</span><b>{fmt(plan.terminalCost)} {currency}</b></div><div><span>Rental</span><b>{fmt(plan.rentalCost)} {currency}</b></div>{plan.activationCost > 0 && <div><span>Activation</span><b>{fmt(plan.activationCost)} {currency}</b></div>}<div><span>Total</span><b>{fmt(plan.totalCost)} {currency}</b></div><div><span>Cost per tonne</span><b>{fmt(plan.costPerTon)} {currency}/t</b></div><div><span>Tonnage</span><b>{fmt(plan.totalTonnage)} t</b></div><div><span>Trips / truck-km</span><b>{plan.tripCount} / {fmt(plan.totalTruckKm, 1)} km</b></div><div><span>Transshipped</span><b>{fmt(plan.transshipmentTonnage)} t</b></div><div><span>All delivered by</span><b>{clock(readyTime, plan.completionHour)}</b></div></div>
  </section>
}

function ScenarioCard({ response }: { response: RoutePlanResponse }) {
  const entries = Object.entries(response.scenarios)
  return <section className="card"><div className="section-heading"><div><span className="eyebrow">SCENARIO COMPARISON</span><h3>Direct trucks versus the optimized network</h3><p>The same demand is planned once with direct trucks only and once with transshipment and consolidation allowed.</p></div>
    {response.savingsVsDirect != null && <span className="status ok savings">Saves <CountUp value={response.savingsVsDirect} decimals={2} suffix={` ${response.currency}`} /> ({fmt(response.savingsPercent)} %)</span>}</div>
    <div className="scenario-grid">{entries.map(([name, scenario]) => <article className="scenario-card" key={name}><span>{scenarioNames[name] ?? name}</span><strong>{fmt(scenario.totalCost)} {response.currency}</strong><small>{statusTitles[scenario.status]}</small><small>Transport {fmt(scenario.transportCost)} · handling {fmt(scenario.terminalCost)}{scenario.rentalCost > 0 ? ` · rental ${fmt(scenario.rentalCost)}` : ''}</small><small>{scenario.tripCount} trips · {fmt(scenario.costPerTon)} {response.currency}/t</small></article>)}</div>
  </section>
}

function PlanTables({ plan, labels, currency, readyTime }: { plan: PlanResult; labels: string[]; currency: string; readyTime: string }) {
  return <section className="card"><div className="section-heading"><div><span className="eyebrow">FINAL LOGISTICS PLAN</span><h3>Routes, trucks and terminals</h3><p>Every tonne of demand is assigned to a path, every leg to a truck, and every visit to a terminal processing window.</p></div></div>
    <FlowGraph plan={plan} labels={labels} />
    <h4 className="planner-subhead">Selected routes</h4>
    <div className="table-scroll"><table className="route-table plan-table"><thead><tr><th>Shipment</th><th>Path</th><th className="num">Tonnage<Unit>t</Unit></th><th className="num">Distance<Unit>km</Unit></th><th>First departure</th><th>Delivered</th><th className="num">Handling<Unit>{currency}</Unit></th></tr></thead>
      <tbody>{plan.routes.map((route, i) => <tr key={i}><td>{labels[route.origin]} → {labels[route.destination]}</td><td>{route.path.map(city => labels[city]).join(' → ')}{route.path.length > 2 && <span className="planner-tag">transshipment</span>}</td><td className="num">{fmt(route.tonnage)}</td><td className="num">{fmt(route.distance, 1)}</td><td className="num-text">{clock(readyTime, route.firstDepartureHour)}</td><td className="num-text">{clock(readyTime, route.deliveredHour)}</td><td className="num">{fmt(route.handlingCost)}</td></tr>)}</tbody></table></div>
    <h4 className="planner-subhead">Truck trips and consolidated loads</h4>
    <div className="table-scroll"><table className="route-table plan-table"><thead><tr><th className="num">#</th><th>Leg</th><th>Vehicle</th><th className="num">Load / usable<Unit>t</Unit></th><th className="num">Utilization<Unit>%</Unit></th><th>Cargo on board</th><th>Departure → arrival</th><th className="num">Cost<Unit>{currency}</Unit></th></tr></thead>
      <tbody>{plan.trips.map((trip, n) => <tr key={trip.tripIndex}><td className="num">{n + 1}</td><td>{labels[trip.from]} → {labels[trip.to]}</td><td className="nowrap">{trip.vehicleName} <span className="planner-muted">{trip.rented ? 'rented' : 'owned'}</span></td><td className="num">{fmt(trip.loadedTonnage)} / {fmt(trip.usableCapacity)}</td><td className="num">{fmt(trip.usableCapacity > 0 ? trip.loadedTonnage / trip.usableCapacity * 100 : 0, 1)}</td>
        <td>{trip.loads.map((load, i) => <div key={i} className="planner-load">{fmt(load.tonnage)} t of {labels[load.origin]} → {labels[load.destination]}</div>)}</td>
        <td className="num-text">{clock(readyTime, trip.departureHour)} → {clock(readyTime, trip.arrivalHour)}</td><td className="num">{fmt(trip.transportCost + trip.rentalCost)}</td></tr>)}</tbody></table></div>
    <h4 className="planner-subhead">Terminal workload</h4>
    <div className="table-scroll"><table className="route-table plan-table compact"><thead><tr><th>Terminal</th><th className="num">Outbound<Unit>t</Unit></th><th className="num">Transit<Unit>t</Unit></th><th className="num">Inbound<Unit>t</Unit></th><th className="num">Total handled<Unit>t</Unit></th><th className="num">Capacity<Unit>t/h</Unit></th><th className="num">Busy<Unit>min</Unit></th><th className="num">Peak rate<Unit>t/h</Unit></th><th className="num">Utilization<Unit>% of horizon</Unit></th><th>Window</th><th className="num">Handling cost<Unit>{currency}</Unit></th></tr></thead>
      <tbody>{plan.terminalLoads.map(load => <tr key={load.city}><td className="nowrap">{labels[load.city]}</td><td className="num">{fmt(load.outboundTonnage)}</td><td className="num">{fmt(load.transitTonnage)}</td><td className="num">{fmt(load.inboundTonnage)}</td><td className="num"><b>{fmt(load.totalTonnage)}</b></td><td className="num">{fmt(load.capacityPerHour, 0)}</td><td className="num">{fmt(load.busyHours * 60, 1)}</td><td className="num">{fmt(load.peakRatePerHour, 1)}</td><td className="num">{fmt(load.utilizationPercent, 1)}</td><td className="num-text">{load.firstStartHour == null ? '—' : `${clock(readyTime, load.firstStartHour)}–${clock(readyTime, load.lastEndHour)}`}</td><td className="num"><b>{fmt(load.cost)}</b><span className="planner-formula">{fmt(load.totalTonnage)} t × {fmt(load.handlingCostPerTon)}</span></td></tr>)}</tbody>
      <tfoot><tr><th>Total</th><td colSpan={3} /><td className="num"><b>{fmt(plan.terminalLoads.reduce((sum, load) => sum + load.totalTonnage, 0))}</b></td><td colSpan={5} /><td className="num"><b>{fmt(plan.terminalCost)}</b></td></tr></tfoot></table></div>
    <h4 className="planner-subhead">Delivery schedule</h4>
    <Timeline plan={plan} labels={labels} readyTime={readyTime} />
  </section>
}

const SOLVE_TIMEOUT_MS = 10 * 60 * 1000

async function solveRoutePlan(problem: RoutePlanRequest): Promise<RoutePlanResponse> {
  const controller = new AbortController()
  const timeout = window.setTimeout(() => controller.abort(), SOLVE_TIMEOUT_MS)
  try {
    const response = await fetch('/api/network-optimization/solve', {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(problem), signal: controller.signal,
    })
    if (!response.ok) {
      const body = await response.json().catch(() => ({ error: response.statusText }))
      throw new Error(body.error ?? 'Unable to compute the route plan.')
    }
    return response.json()
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') {
      throw new Error('The solver exceeded the 10 minute UI timeout. Reduce the planning horizon or model size.')
    }
    throw error
  } finally {
    window.clearTimeout(timeout)
  }
}

function Explanation({ plan }: { plan: PlanResult }) {
  if (plan.explanation.length === 0) return null
  return <section className="card"><div className="section-heading"><div><span className="eyebrow">HOW THE PLAN WAS BUILT</span><h3>Step-by-step explanation</h3><p>From the problem interpretation to the final plan and its cost proof.</p></div></div>
    {plan.explanation.map((section, i) => <details key={section.title} className="planner-explanation" open={i === 0 || i === plan.explanation.length - 1}><summary>{section.title}</summary><ul>{section.lines.map((line, j) => <li key={j}>{line}</li>)}</ul></details>)}
  </section>
}

export function RoutePlannerPage() {
  const [problem, setProblem] = useState<RoutePlanRequest | null>(null)
  const [cityCount, setCityCount] = useState(3)
  const [response, setResponse] = useState<RoutePlanResponse | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [solving, setSolving] = useState(false)
  const [elapsed, setElapsed] = useState(0)
  const [dataset, setDataset] = useState<Dataset>('dubai')
  const loadDubai = () => {
    setResponse(null); setError(null)
    return loadRoutePlanExample()
      .then(example => { setProblem(example); setCityCount(example.locations.length); setDataset('dubai') })
      .catch(e => setError(e instanceof Error ? e.message : 'Unable to load the example.'))
  }
  useEffect(() => { loadDubai().finally(() => setLoading(false)) }, [])
  useEffect(() => {
    if (!solving) return
    const started = Date.now()
    setElapsed(0)
    const timer = window.setInterval(() => setElapsed(Math.floor((Date.now() - started) / 1000)), 250)
    return () => window.clearInterval(timer)
  }, [solving])
  if (loading) return <section className="card"><p>Loading the Dubai network…</p></section>
  if (!problem) return <section className="card"><p className="notice error">{error}</p></section>
  const labels = problem.locations
  const currency = problem.currency || 'AED'
  const update = (change: Partial<RoutePlanRequest>) => { setProblem({ ...problem, ...change }); setResponse(null); setDataset('custom') }
  const vehicleLimit = (key: 'ownedVehicles' | 'rentalLimits', index: number, value: number | null) => {
    const current = problem[key] ?? problem.vehicleTypes.map(() => null)
    const next = current.map((limit, j) => j === index ? value : limit)
    update({ [key]: next.every(limit => limit == null) ? null : next })
  }
  const totalDemand = problem.demandMatrix.reduce((total, row) => total + row.reduce((sum, value) => sum + value, 0), 0)
  const applyCities = () => {
    const count = clampCityCount(cityCount)
    setCityCount(count)
    update(resizeNetwork(problem, count))
  }
  const loadNovaPoshta = () => {
    setProblem(novaPoshtaProblem(problem))
    setCityCount(novaPoshtaLocations.length)
    setResponse(null)
    setError(null)
    setDataset('novaPoshta')
  }
  async function solve() {
    setError(null); setResponse(null); setSolving(true)
    try { setResponse(await solveRoutePlan(problem!)) }
    catch (e) { setError(e instanceof Error ? e.message : 'Unable to compute the route plan.') }
    finally { setSolving(false) }
  }
  const plan = response?.plan
  return <>
    <section className="hero compact-hero planner-hero"><div><span className="eyebrow">FREIGHT NETWORK PLANNER</span><h2>One plan for routes, trucks and terminals.</h2><p>Each origin–destination demand is routed directly or through a hub terminal, packed into 20 t, 10 t and 5 t trucks, and scheduled through terminal queues. The planner proves the plan is the cheapest possible.</p></div></section>
    <BusinessBrief />
    <section className="card editor-card"><div className="section-heading"><div><span className="eyebrow">INPUT DATA</span><h3>Freight network</h3><p>Edit names, demand and directed distances. Cities resizes both tables together. New cells start at 0, and a new terminal copies the last city's capacity and handling cost.</p></div><span className="status ok">{labels.length} terminals · {fmt(totalDemand, 0)} t demand</span></div>
      <div className="dimension-controls"><label>Cities<input type="number" min={MIN_CITIES} max={MAX_CITIES} value={cityCount} onChange={event => setCityCount(Number(event.target.value))} /></label><button className="button secondary" onClick={applyCities}>Apply</button>
        <div className="dataset-switch"><span>Dataset</span><div className="segmented" role="group" aria-label="Dataset"><button type="button" className={dataset === 'dubai' ? 'active' : ''} aria-pressed={dataset === 'dubai'} onClick={loadDubai}>Dubai</button><button type="button" className={dataset === 'novaPoshta' ? 'active' : ''} aria-pressed={dataset === 'novaPoshta'} onClick={loadNovaPoshta}>Nova Poshta</button></div></div></div>
      <MatrixEditor title="Demand (tonnes)" unit="t" locations={labels} values={problem.demandMatrix} onChange={(i, j, value) => update({ demandMatrix: matrixWith(problem.demandMatrix, i, j, value) })} />
      <MatrixEditor title="Road distance (directed)" unit="km" locations={labels} values={problem.distanceMatrix} onChange={(i, j, value) => update({ distanceMatrix: matrixWith(problem.distanceMatrix, i, j, value) })} />
      <h4>Terminals</h4>
      <div className="table-scroll"><table className="route-table"><thead><tr><th>Terminal</th><th>Processing capacity (t/h)</th><th>Handling cost ({currency}/t)</th></tr></thead>
        <tbody>{labels.map((label, i) => <tr key={i}><td><input className="matrix-input" type="text" aria-label={`Terminal ${i + 1} name`} value={label} style={{ width: '11rem', textAlign: 'left' }} onChange={event => update({ locations: problem.locations.map((city, j) => j === i ? event.target.value : city) })} /></td><td><NumberInput value={problem.terminalCapacity[i]} onChange={value => update({ terminalCapacity: problem.terminalCapacity.map((v, j) => j === i ? value : v) })} /></td><td><NumberInput value={problem.terminalHandlingCost[i]} onChange={value => update({ terminalHandlingCost: problem.terminalHandlingCost.map((v, j) => j === i ? value : v) })} /></td></tr>)}</tbody></table></div>
      <h4>Vehicles</h4>
      <div className="table-scroll"><table className="route-table"><thead><tr><th>Type</th><th>Nominal capacity (t)</th><th>Usable load</th><th>Tariff ({currency}/km)</th><th>Rental ({currency}/trip)</th><th>Owned trucks</th><th>Rental limit</th></tr></thead>
        <tbody>{problem.vehicleTypes.map((vehicle, i) => <tr key={i}><td>{vehicle.name}</td>
          <td><NumberInput value={vehicle.nominalCapacity} onChange={value => update({ vehicleTypes: problem.vehicleTypes.map((v, j) => j === i ? { ...v, nominalCapacity: value } : v) })} /></td>
          <td>{fmt(vehicle.nominalCapacity * problem.loadingFactor)} t</td>
          <td><NumberInput value={vehicle.tariffPerKm} onChange={value => update({ vehicleTypes: problem.vehicleTypes.map((v, j) => j === i ? { ...v, tariffPerKm: value } : v) })} /></td>
          <td><NumberInput value={vehicle.rentalCostPerTrip} onChange={value => update({ vehicleTypes: problem.vehicleTypes.map((v, j) => j === i ? { ...v, rentalCostPerTrip: value } : v) })} /></td>
          <td><OptionalNumberInput value={problem.ownedVehicles?.[i] ?? null} onChange={value => vehicleLimit('ownedVehicles', i, value)} /></td>
          <td><OptionalNumberInput value={problem.rentalLimits?.[i] ?? null} onChange={value => vehicleLimit('rentalLimits', i, value)} /></td></tr>)}</tbody></table>
        <p className="graph-legend">Leave a fleet field empty for an unlimited number of trucks of that type. Rental limits apply only when rental is enabled.</p></div>
      <div className="planner-form-grid">
        <label>Currency<input type="text" value={problem.currency} onChange={e => update({ currency: e.target.value })} /></label>
        <label>Loading factor<input type="number" min="0.01" max="1" step="0.05" value={problem.loadingFactor} onChange={e => update({ loadingFactor: Number(e.target.value) })} /></label>
        <label>Average speed (km/h)<input type="number" min="1" value={problem.speedKmPerHour} onChange={e => update({ speedKmPerHour: Number(e.target.value) })} /></label>
        <label>Cargo ready at<input type="time" value={problem.readyTime} onChange={e => update({ readyTime: e.target.value })} /></label>
        <label>Planning horizon (h)<input type="number" min="0.5" step="0.5" value={problem.planningHorizonHours} onChange={e => update({ planningHorizonHours: Number(e.target.value) })} /></label>
        <label>Delivery deadline (optional)<input type="time" value={problem.deliveryDeadline ?? ''} onChange={e => update({ deliveryDeadline: e.target.value || null })} /></label>
        <label>Fallback time slot (h)<input type="number" min="0.05" step="0.25" value={problem.periodDurationHours} onChange={e => update({ periodDurationHours: Number(e.target.value) })} /></label>
      </div>
      <div className="planner-options">
        <label><input type="checkbox" checked={!problem.directOnly} onChange={e => update({ directOnly: !e.target.checked })} /> Allow transshipment through hubs</label>
        <label><input type="checkbox" checked={problem.consolidation} onChange={e => update({ consolidation: e.target.checked })} /> Allow consolidation of different shipments in one truck</label>
        <label><input type="checkbox" checked={problem.rentalEnabled} onChange={e => update({ rentalEnabled: e.target.checked })} /> Allow truck rental</label>
        <button className="button primary" onClick={solve} disabled={solving}>{solving ? 'Proving the optimum…' : 'Build optimal plan'}</button>
      </div>
      {solving && elapsed >= 2 && <WaitingPanel seconds={elapsed} />}
      {error && <p className="notice error">{error}</p>}
    </section>
    {response && plan && <>
      <ProofCard plan={plan} currency={response.currency} />
      {plan.totalCost != null && <>
        <CostCard plan={plan} currency={response.currency} readyTime={response.readyTime} />
        <ScenarioCard response={response} />
        <PlanTables plan={plan} labels={response.locations} currency={response.currency} readyTime={response.readyTime} />
      </>}
      <Explanation plan={plan} />
    </>}
  </>
}
