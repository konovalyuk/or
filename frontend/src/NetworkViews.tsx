import { useState } from 'react'
import type { ReactNode } from 'react'
import type { CpmResult, DijkstraResult, FlowIteration, FlowResult, FloydResult, SpanningResult } from './api'
import { GanttChart, activityKey } from './GanttChart'
import { NetworkSvg, type FigureEdge, type FigureNode } from './NetworkGraph'
import { num, routeText, short, type ArcDraft, type Point } from './networkData'

function Slider({ count, index, setIndex }: { count: number; index: number; setIndex: (value: number) => void }) {
  return <div className="step-controls">
    <button className="button secondary" disabled={index === 0} onClick={() => setIndex(index - 1)}>Previous</button>
    <input className="timeline" type="range" min={0} max={Math.max(0, count - 1)} value={index} onChange={event => setIndex(Number(event.target.value))} />
    <button className="button primary" disabled={index >= count - 1} onClick={() => setIndex(index + 1)}>Next</button>
    <span className="status ok">{index + 1} / {count}</span>
  </div>
}

function StepLayout({ graph, legend, count, index, setIndex, children, final }: {
  graph: ReactNode
  legend?: ReactNode
  count: number
  index: number
  setIndex: (value: number) => void
  children?: ReactNode
  final: ReactNode
}) {
  const last = index >= count - 1
  return <>
    <div className="network-stack">
      <div className="step-graph">{graph}{legend && <p className="graph-legend">{legend}</p>}</div>
      <Slider count={count} index={index} setIndex={setIndex} />
      {children}
    </div>
    {last
      ? <div className="final-panel"><span className="eyebrow">FINAL ANSWER</span>{final}</div>
      : <p className="final-hint">Press Next until the last step to see the final answer.</p>}
  </>
}

function nodesOf(positions: Point[], tone: (id: number) => FigureNode['tone'], caption: (id: number) => string = () => ''): FigureNode[] {
  return positions.map((_, index) => ({ id: index + 1, caption: caption(index + 1), tone: tone(index + 1) }))
}

function sameEdge(from: number, to: number, otherFrom: number, otherTo: number, undirected: boolean) {
  return (from === otherFrom && to === otherTo) || (undirected && from === otherTo && to === otherFrom)
}

function onPath(path: number[], from: number, to: number) {
  return path.some((node, place) => node === from && path[place + 1] === to)
}

export function SpanningView({ result, positions, arcs }: { result: SpanningResult; positions: Point[]; arcs: ArcDraft[] }) {
  const [index, setIndex] = useState(0)
  const step = result.iterations[index]
  const tree = result.iterations.slice(1, index + 1).map(item => item.added).filter(item => item != null)
  const edges: FigureEdge[] = []
  const seen = new Set<string>()
  for (const arc of arcs) {
    const key = [arc.from, arc.to].sort().join('-')
    if (seen.has(key)) continue
    seen.add(key)
    const added = step.added != null && sameEdge(arc.from, arc.to, step.added.from, step.added.to, true)
    const kept = tree.some(item => sameEdge(arc.from, arc.to, item.from, item.to, true)) && !added
    const candidate = step.candidates.some(item => sameEdge(arc.from, arc.to, item.from, item.to, true))
    edges.push({
      from: arc.from, to: arc.to, label: num(arc.value), directed: false,
      color: added ? '#b45309' : kept ? '#166534' : candidate ? '#64748b' : '#cbd5e1',
      width: added || kept ? 3.5 : candidate ? 1.4 : 1,
      dash: added ? '7 4' : undefined,
      effect: added ? 'glow' : undefined,
    })
  }
  const finalEdges: FigureEdge[] = result.tree.map(link => ({ from: link.from, to: link.to, label: num(link.length), directed: false, color: '#166534', width: 4, effect: 'draw' }))
  return <section className="card history">
    <div className="section-heading"><div><span className="eyebrow">MINIMAL SPANNING TREE</span><h3>{index === 0 ? `Start at node ${result.start}` : `Connect node ${step.added?.to}`}</h3><p>Each iteration links the closest unconnected node to the connected set.</p></div><span className="status ok">Length so far {num(step.length)}</span></div>
    <StepLayout count={result.iterations.length} index={index} setIndex={setIndex}
      graph={<NetworkSvg positions={positions} nodes={nodesOf(positions, id => step.connected.includes(id) ? 'permanent' : 'plain')} edges={edges} />}
      legend="Green = already in the tree · dashed amber = added at this iteration · thin = still a candidate"
      final={<>
        <div className="final-banner"><div><h4>Minimal spanning tree</h4><p>{result.message}</p></div><strong>Tree length {num(result.length)}</strong></div>
        <NetworkSvg positions={positions} nodes={nodesOf(positions, () => 'permanent')} edges={finalEdges} />
        <div className="table-scroll"><table className="route-table"><thead><tr><th>Order</th><th>Link</th><th>Length</th></tr></thead><tbody>
          {result.tree.map((link, order) => <tr key={`${link.from}-${link.to}`}><td>{order + 1}</td><td>{link.from}–{link.to}</td><td>{num(link.length)}</td></tr>)}
          <tr className="total-row"><td colSpan={2}>Total</td><td>{num(result.length)}</td></tr>
        </tbody></table></div>
      </>}>
      <div className="table-scroll"><table className="matrix-table"><thead><tr><th>Connected C</th><th>Unconnected</th><th>Chosen arc</th><th>Length</th></tr></thead><tbody><tr>
        <td>{step.connected.join(', ') || '—'}</td><td>{step.unconnected.join(', ') || '—'}</td>
        <td>{step.added ? `${step.added.from}–${step.added.to}` : '—'}</td><td>{num(step.length)}</td>
      </tr></tbody></table></div>
      <div className="table-scroll"><table className="matrix-table"><thead><tr><th>Candidate</th><th>Length</th></tr></thead><tbody>
        {step.candidates.length === 0 && <tr><td colSpan={2}>The start node is chosen. No arc is added yet.</td></tr>}
        {step.candidates.map(link => <tr key={`${link.from}-${link.to}`} className={step.added && sameEdge(link.from, link.to, step.added.from, step.added.to, true) ? 'row-current' : ''}><td>{link.from}–{link.to}</td><td>{num(link.length)}</td></tr>)}
      </tbody></table></div>
    </StepLayout>
  </section>
}

function labelBorn(result: DijkstraResult, node: number, upto: number) {
  let born = 0
  for (let index = 0; index <= upto; index++) {
    const label = result.iterations[index].labels[node - 1]
    const previous = index === 0 ? null : result.iterations[index - 1].labels[node - 1]
    const changed = label.status != null && (previous == null || previous.distance !== label.distance || previous.predecessor !== label.predecessor || previous.status !== label.status)
    if (changed) born = result.iterations[index].number
  }
  return born
}

export function DijkstraView({ result, positions, arcs }: { result: DijkstraResult; positions: Point[]; arcs: ArcDraft[] }) {
  const [index, setIndex] = useState(0)
  const step = result.iterations[index]
  const fixedFrom = step.labels[step.permanentNode - 1]?.predecessor
  const edges: FigureEdge[] = arcs.map(arc => {
    const labeled = step.labels[arc.to - 1]
    const treeEdge = labeled?.predecessor === arc.from && labeled.status != null
    const justFixed = index > 0 && arc.from === fixedFrom && arc.to === step.permanentNode
    return {
      from: arc.from, to: arc.to, label: num(arc.value), directed: true,
      color: justFixed ? '#b45309' : treeEdge ? '#166534' : '#94a3b8',
      width: justFixed || treeEdge ? 3 : 1.3,
      effect: justFixed ? 'pulse' : undefined,
    }
  })
  const label = (id: number) => {
    const item = step.labels[id - 1]
    if (!item.status || item.distance == null) return ''
    return `[${num(item.distance)}, ${item.predecessor ?? '—'}] (${labelBorn(result, id, index)})`
  }
  return <section className="card history">
    <div className="section-heading"><div><span className="eyebrow">DIJKSTRA</span><h3>Iteration {step.number}: node {step.permanentNode} becomes permanent</h3><p>Temporary labels are replaced only by a strictly shorter distance.</p></div></div>
    <StepLayout count={result.iterations.length} index={index} setIndex={setIndex}
      graph={<NetworkSvg positions={positions} edges={edges} nodes={nodesOf(positions,
        id => id === step.permanentNode ? 'pivot' : step.labels[id - 1].status === 'PERMANENT' ? 'permanent' : step.labels[id - 1].status === 'TEMPORARY' ? 'temporary' : 'plain', label)} />}
      legend="Blue = made permanent now · green = permanent · amber = temporary · the number in parentheses is the iteration that wrote the label"
      final={<DijkstraFinal result={result} positions={positions} arcs={arcs} />}>
      <div className="table-scroll"><table className="matrix-table"><thead><tr><th>Node</th><th>Label</th><th>Status</th></tr></thead><tbody>
        {step.labels.map((item, node) => <tr key={node} className={node + 1 === step.permanentNode ? 'row-current' : ''}><td>{node + 1}</td><td>{label(node + 1) || '—'}</td><td>{item.status === 'PERMANENT' ? 'Permanent' : item.status === 'TEMPORARY' ? 'Temporary' : '—'}</td></tr>)}
      </tbody></table></div>
    </StepLayout>
  </section>
}

function DijkstraFinal({ result, positions, arcs }: { result: DijkstraResult; positions: Point[]; arcs: ArcDraft[] }) {
  const [destination, setDestination] = useState(result.routes.length)
  const route = result.routes[destination - 1]
  const path = route?.nodes ?? []
  const edges: FigureEdge[] = arcs.map(arc => {
    const red = onPath(path, arc.from, arc.to)
    return { from: arc.from, to: arc.to, label: num(arc.value), directed: true, color: red ? '#dc2626' : '#cbd5e1', width: red ? 3.5 : 1.2, effect: red ? 'draw' : undefined }
  })
  return <>
    <div className="final-banner">
      <label className="final-select">Destination<select value={destination} onChange={event => setDestination(Number(event.target.value))}>{result.routes.map(item => <option key={item.node} value={item.node}>Node {item.node}</option>)}</select></label>
      <strong>{num(route?.distance)}</strong>
    </div>
    <NetworkSvg key={destination} positions={positions} edges={edges} nodes={nodesOf(positions, id => path.includes(id) ? 'critical' : 'plain')} />
    <div className="table-scroll"><table className="route-table"><thead><tr><th>Node</th><th>Distance</th><th>Route</th></tr></thead><tbody>
      {route && <tr><td>{route.node}</td><td>{num(route.distance)}</td><td>{routeText(route.nodes)}</td></tr>}
    </tbody></table></div>
  </>
}

function cellName(row: number, column: number) {
  return row < 10 && column < 10 ? `d${row}${column}` : `d${row},${column}`
}

export function FloydView({ result, positions, arcs }: { result: FloydResult; positions: Point[]; arcs: ArcDraft[] }) {
  const [index, setIndex] = useState(0)
  const step = result.iterations[index]
  const previous = index > 0 ? result.iterations[index - 1] : null
  const pivot = step.pivot
  const lit = new Map<string, number | null>()
  const touched = new Set<number>()
  for (const cell of step.improved) {
    lit.set(`${cell.row}>${pivot}`, step.distance[cell.row - 1][pivot - 1])
    lit.set(`${pivot}>${cell.column}`, step.distance[pivot - 1][cell.column - 1])
    touched.add(cell.row)
    touched.add(cell.column)
  }
  const edges: FigureEdge[] = arcs.map(arc => {
    const hot = lit.has(`${arc.from}>${arc.to}`)
    return { from: arc.from, to: arc.to, label: num(arc.value), directed: true, color: hot ? '#d97706' : '#94a3b8', width: hot ? 3.2 : 1.3, effect: hot ? 'glow' : undefined, muted: pivot > 0 && !hot }
  })
  for (const [key, value] of lit) {
    const [from, to] = key.split('>').map(Number)
    if (arcs.some(arc => arc.from === from && arc.to === to)) continue
    edges.push({ from, to, label: num(value), directed: true, color: '#d97706', width: 2.4, dash: '6 5', effect: 'glow' })
  }
  const improved = (row: number, column: number) => step.improved.some(cell => cell.row === row && cell.column === column)
  return <section className="card history">
    <div className="section-heading"><div><span className="eyebrow">FLOYD</span><h3>{pivot === 0 ? 'Initial matrices D0 and S0' : `Triple operation with pivot node ${pivot}`}</h3><p>{pivot === 0 ? 'Distances of direct arcs; every sequence entry points to its column.' : `A cell improves when d(i,${pivot}) + d(${pivot},j) is shorter than d(i,j).`}</p></div><span className="status ok">{step.improved.length} improved</span></div>
    <StepLayout count={result.iterations.length} index={index} setIndex={setIndex}
      graph={<NetworkSvg positions={positions} edges={edges} nodes={nodesOf(positions, id => id === pivot ? 'pivot' : touched.has(id) ? 'temporary' : pivot > 0 ? 'muted' : 'plain')} />}
      legend="Blue = pivot node · glowing amber = legs i→k and k→j of an improved cell · dashed = a leg that is already a shortcut, not a direct arc"
      final={<FloydFinal result={result} positions={positions} arcs={arcs} />}>
      {pivot > 0 && <div className="improvement-list">{step.improved.length === 0
        ? <span className="chip">No cell improves at this pivot.</span>
        : step.improved.map(cell => <span key={`${cell.row}-${cell.column}`} className="chip chip-improved">{cellName(cell.row, cell.column)}: {num(previous?.distance[cell.row - 1][cell.column - 1])} → {num(step.distance[cell.row - 1][cell.column - 1])} via {pivot}</span>)}</div>}
      <Matrix title={`D${pivot}`} matrix={step.distance} pivot={pivot} improved={improved} />
      <Matrix title={`S${pivot}`} matrix={step.sequence} pivot={pivot} improved={improved} integer />
    </StepLayout>
  </section>
}

function FloydFinal({ result, positions, arcs }: { result: FloydResult; positions: Point[]; arcs: ArcDraft[] }) {
  const [from, setFrom] = useState(1)
  const [to, setTo] = useState(positions.length)
  const route = result.routes.find(item => item.from === from && item.to === to)
  const path = route?.nodes ?? []
  const edges: FigureEdge[] = arcs.map(arc => {
    const red = onPath(path, arc.from, arc.to)
    return { from: arc.from, to: arc.to, label: num(arc.value), directed: true, color: red ? '#dc2626' : '#cbd5e1', width: red ? 3.5 : 1.2, effect: red ? 'draw' : undefined }
  })
  return <>
    <div className="final-banner">
      <div className="final-selects">
        <label className="final-select">From node<select value={from} onChange={event => setFrom(Number(event.target.value))}>{positions.map((_, id) => <option key={id} value={id + 1}>{id + 1}</option>)}</select></label>
        <label className="final-select">To node<select value={to} onChange={event => setTo(Number(event.target.value))}>{positions.map((_, id) => <option key={id} value={id + 1}>{id + 1}</option>)}</select></label>
      </div>
      <strong>{from === to ? '—' : num(route?.distance)}</strong>
    </div>
    <NetworkSvg key={`${from}-${to}`} positions={positions} edges={edges} nodes={nodesOf(positions, id => path.includes(id) ? 'critical' : 'plain')} />
    <div className="table-scroll"><table className="route-table"><thead><tr><th>From</th><th>To</th><th>Distance</th><th>Route</th></tr></thead><tbody>
      <tr><td>{from}</td><td>{to}</td><td>{from === to ? '—' : num(route?.distance)}</td><td>{from === to ? 'Choose two different nodes.' : routeText(path)}</td></tr>
    </tbody></table></div>
  </>
}

function Matrix({ title, matrix, pivot, improved, integer }: { title: string; matrix: (number | null)[][]; pivot: number; improved: (row: number, column: number) => boolean; integer?: boolean }) {
  return <div className="table-scroll"><table className="matrix-table"><thead><tr><th>{title}</th>{matrix.map((_, column) => <th key={column} className={pivot === column + 1 ? 'cell-pivot' : ''}>{column + 1}</th>)}</tr></thead><tbody>
    {matrix.map((row, i) => <tr key={i}>{[<th key="h" className={pivot === i + 1 ? 'cell-pivot' : ''}>{i + 1}</th>, ...row.map((value, j) => {
      const changed = improved(i + 1, j + 1)
      const pivotCell = pivot === i + 1 || pivot === j + 1
      return <td key={j} className={changed ? 'cell-improved' : pivotCell ? 'cell-pivot' : ''}>{i === j ? '—' : value == null ? 'infinity' : integer ? String(value) : num(value)}</td>
    })]}</tr>)}
  </tbody></table></div>
}

function flowLabels(iteration: FlowIteration) {
  if (iteration.labels.length === 0) return '—'
  return iteration.labels.map(label => `(${label.node})[${label.infinite ? '∞' : short(label.amount)}, ${label.predecessor ?? '—'}]`).join(' ')
}

export function FlowView({ result, positions }: { result: FlowResult; positions: Point[] }) {
  const [index, setIndex] = useState(0)
  const step = result.iterations[index]
  const residual = step.residualBefore
  const edges: FigureEdge[] = []
  residual.forEach((row, i) => row.forEach((value, j) => {
    if (i === j || value == null || value <= 1e-9) return
    const hot = onPath(step.path, i + 1, j + 1)
    edges.push({ from: i + 1, to: j + 1, label: num(value), directed: true, color: hot ? '#b45309' : '#64748b', width: hot ? 3.2 : 1.3, dash: hot ? '7 4' : undefined, effect: hot ? 'glow' : undefined })
  }))
  const labelOf = (id: number) => step.labels.find(label => label.node === id)
  const cumulative = (upto: number) => result.iterations.slice(0, upto + 1).reduce((sum, item) => sum + (item.flow ?? 0), 0)
  const flowEdges: FigureEdge[] = result.arcs.map(arc => ({ from: arc.from, to: arc.to, label: num(arc.amount), directed: true, color: '#1d4ed8', width: Math.min(6, 1.5 + arc.amount / 8), effect: 'draw' }))
  return <section className="card history">
    <div className="section-heading"><div><span className="eyebrow">MAXIMAL FLOW</span><h3>{index === 0 ? 'Initial residual network' : step.flow == null ? 'No breakthrough: the algorithm stops' : `Breakthrough ${index}: ${routeText(step.path)}`}</h3><p>{step.flow == null ? 'Residuals before this iteration are drawn on the arcs.' : `The path carries f = ${num(step.flow)}, the smallest label on it.`}</p></div><span className="status ok">F so far {num(cumulative(index))}</span></div>
    <StepLayout count={result.iterations.length} index={index} setIndex={setIndex}
      graph={<NetworkSvg positions={positions} edges={edges} nodes={nodesOf(positions, id => labelOf(id)?.crossedOut ? 'crossed' : step.path.includes(id) ? 'permanent' : labelOf(id) ? 'temporary' : 'plain', id => {
        const label = labelOf(id)
        if (!label) return ''
        return `[${label.infinite ? '∞' : short(label.amount)}, ${label.predecessor ?? '—'}]`
      })} />}
      legend="Arcs show residual capacity before the breakthrough · a crossed node was removed by backtracking · the dashed path carries the flow"
      final={<>
        <div className="final-banner"><div><h4>Maximal flow</h4><p>{result.message}</p></div><strong>F = {num(result.flow)}</strong></div>
        <NetworkSvg positions={positions} edges={flowEdges} nodes={nodesOf(positions, () => 'permanent')} />
        <div className="table-scroll"><table className="route-table"><thead><tr><th>Arc</th><th>Flow</th></tr></thead><tbody>
          {result.arcs.map(arc => <tr key={`${arc.from}-${arc.to}`}><td>{arc.from} → {arc.to}</td><td>{num(arc.amount)}</td></tr>)}
          <tr className="total-row"><td>Total out of the source</td><td>{num(result.flow)}</td></tr>
        </tbody></table></div>
      </>}>
      <div className="table-scroll"><table className="matrix-table"><thead><tr><th>Iteration</th><th>Labels [a, i]</th><th>Crossed out</th><th>Path N<sub>p</sub></th><th>f<sub>p</sub></th><th>F</th></tr></thead><tbody>
        {result.iterations.slice(0, index + 1).map((item, place) => <tr key={place} className={place === index ? 'row-current' : ''}>
          <td>{place + 1}</td>
          <td className="labels-cell">{flowLabels(item)}</td>
          <td>{item.labels.filter(label => label.crossedOut).map(label => label.node).join(', ') || '—'}</td>
          <td>{item.path.length ? routeText(item.path) : place === 0 ? 'Start' : 'None'}</td>
          <td>{item.flow == null ? '—' : num(item.flow)}</td>
          <td>{num(cumulative(place))}</td>
        </tr>)}
      </tbody></table></div>
      <Matrix title="Residual after" matrix={step.residualAfter} pivot={0} improved={() => false} />
    </StepLayout>
  </section>
}

export function CpmView({ result, positions, pert }: { result: CpmResult; positions: Point[]; pert: boolean }) {
  const passes = [...result.forward.map(step => ({ phase: 'FORWARD' as const, ...step })), ...result.backward.map(step => ({ phase: 'BACKWARD' as const, ...step }))]
  const [index, setIndex] = useState(0)
  const last = index === passes.length - 1
  const current = passes[index]
  const forwardCount = Math.min(index + 1, result.forward.length)
  const backwardCount = Math.max(0, index + 1 - result.forward.length)
  const earliest = new Map(result.forward.slice(0, forwardCount).map(step => [step.node, step.value]))
  const latest = new Map(result.backward.slice(0, backwardCount).map(step => [step.node, step.value]))
  const criticalNodes = new Set(result.criticalPath)
  const forwardPhase = current.phase === 'FORWARD'
  const edges: FigureEdge[] = result.activities.map(activity => {
    const label = `${activity.symbol ? `${activity.symbol} ` : ''}${short(activity.duration)}`
    if (last) {
      return { from: activity.from, to: activity.to, label, directed: true, color: activity.critical ? '#dc2626' : '#94a3b8', width: activity.critical ? 3.4 : 1.3, effect: activity.critical ? 'draw' : undefined }
    }
    const compared = forwardPhase ? activity.to === current.node : activity.from === current.node
    const decisive = compared && (forwardPhase ? activity.from === current.via : activity.to === current.via)
    if (decisive) return { from: activity.from, to: activity.to, label, directed: true, color: '#d97706', width: 3.6, effect: 'glow' }
    if (compared) return { from: activity.from, to: activity.to, label, directed: true, color: '#64748b', width: 1.5, dash: '5 4' }
    const reached = forwardPhase ? earliest.has(activity.to) : true
    return { from: activity.from, to: activity.to, label, directed: true, color: '#94a3b8', width: 1.3, muted: !reached }
  })
  const nodes: FigureNode[] = positions.map((_, place) => {
    const id = place + 1
    let tone: FigureNode['tone'] = 'plain'
    if (last) tone = criticalNodes.has(id) ? 'critical' : 'plain'
    else if (id === current.node) tone = 'pivot'
    else if (forwardPhase) tone = earliest.has(id) ? 'plain' : 'muted'
    else tone = latest.has(id) ? 'permanent' : 'plain'
    return { id, caption: '', tone, badges: { square: earliest.has(id) ? short(earliest.get(id)) : undefined, triangle: latest.has(id) ? short(latest.get(id)) : undefined } }
  })
  return <section className="card history">
    <div className="section-heading"><div><span className="eyebrow">{pert ? 'PERT' : 'CPM'}</span><h3>{forwardPhase ? `Forward pass · node ${current.node}` : `Backward pass · node ${current.node}`}</h3><p>{forwardPhase ? 'The square □ is the earliest occurrence time: the largest □i + Dij over arcs entering the node.' : 'The triangle Δ is the latest occurrence time: the smallest Δj − Dij over arcs leaving the node.'}</p></div></div>
    <StepLayout count={passes.length} index={index} setIndex={setIndex}
      graph={<NetworkSvg positions={positions} edges={edges} nodes={nodes} />}
      legend={last ? 'Red = critical path · □ = earliest time · Δ = latest time' : 'Blue = node being computed · glowing amber = arc that decides the value · dashed = other compared arcs · □ = earliest · Δ = latest'}
      final={<>
        <div className="final-banner"><div><h4>Project duration</h4><p>Critical path {routeText(result.criticalPath)}. {result.message}</p></div><strong>{num(result.project)}</strong></div>
        <ActivityTable result={result} />
        <h4>Time schedule</h4>
        <GanttChart activities={result.activities} project={result.project} />
        {pert && <PertTables result={result} />}
      </>}>
      <p className="step-callout">{forwardPhase ? 'Forward pass' : 'Backward pass'} · {current.formula}</p>
      <div className="table-scroll"><table className="matrix-table"><thead><tr><th>Node</th><th>Earliest □</th><th>Latest Δ</th></tr></thead><tbody>
        {[...result.forward].sort((left, right) => left.node - right.node).map(step => <tr key={step.node} className={step.node === current.node ? 'row-current' : ''}>
          <td>{step.node}</td><td>{earliest.has(step.node) ? num(earliest.get(step.node)) : '—'}</td><td>{latest.has(step.node) ? num(latest.get(step.node)) : '—'}</td>
        </tr>)}
      </tbody></table></div>
    </StepLayout>
  </section>
}

function ActivityTable({ result }: { result: CpmResult }) {
  return <div className="table-scroll"><table className="matrix-table"><thead><tr><th>Activity</th><th>Symbol</th><th>Duration</th><th>Earliest start</th><th>Latest completion</th><th>Total float</th><th>Free float</th><th></th></tr></thead><tbody>
    {result.activities.map(activity => <tr key={activityKey(activity)} className={activity.critical ? 'cell-critical' : ''}><td>{activity.from}–{activity.to}</td><td>{activity.symbol || '—'}</td><td>{num(activity.duration)}</td><td>{num(activity.earliestStart)}</td><td>{num(activity.latestCompletion)}</td><td>{num(activity.totalFloat)}</td><td>{num(activity.freeFloat)}</td><td>{activity.critical ? 'Critical' : activity.redFlag ? 'Red flag' : ''}</td></tr>)}
  </tbody></table></div>
}

function PertTables({ result }: { result: CpmResult }) {
  return <>
    <h4>PERT estimates</h4>
    <div className="table-scroll"><table className="matrix-table"><thead><tr><th>Activity</th><th>Optimistic</th><th>Most likely</th><th>Pessimistic</th><th>Mean</th><th>Variance</th></tr></thead><tbody>
      {result.estimates.map(arc => <tr key={`${arc.from}-${arc.to}`}><td>{arc.symbol || `${arc.from}–${arc.to}`}</td><td>{num(arc.optimistic)}</td><td>{num(arc.likely)}</td><td>{num(arc.pessimistic)}</td><td>{num(arc.mean)}</td><td>{num(arc.variance)}</td></tr>)}
    </tbody></table></div>
    <h4>Probability of meeting the scheduled times</h4>
    <div className="table-scroll"><table className="matrix-table"><thead><tr><th>Node</th><th>Longest path</th><th>Mean</th><th>Std. dev.</th><th>Scheduled</th><th>K</th><th>Probability</th></tr></thead><tbody>
      {result.nodes.map(node => <tr key={node.node}><td>{node.node}</td><td>{routeText(node.path)}</td><td>{num(node.mean)}</td><td>{num(node.standardDeviation)}</td><td>{node.scheduled == null ? '—' : num(node.scheduled)}</td><td>{node.deviate == null ? '—' : node.deviate.toFixed(2)}</td><td>{node.probability == null ? '—' : node.probability.toFixed(4)}</td></tr>)}
    </tbody></table></div>
  </>
}
