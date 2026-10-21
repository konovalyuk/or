import { useEffect, useState } from 'react'
import type { CSSProperties } from 'react'
import { methodsForPath } from './applications/catalog'
import { ApplicationsTrigger } from './components/ApplicationsSheet'
import { navigate } from './navigation'
import { solveCpm, solveDijkstra, solveFloyd, solveMaxFlow, solveSpanningTree, type CpmResult, type DijkstraResult, type FlowResult, type FloydResult, type SpanningResult } from './api'
import { NetworkSvg, type FigureEdge, type FigureNode } from './NetworkGraph'
import { CpmView, DijkstraView, FlowView, FloydView, SpanningView } from './NetworkViews'
import { arcBetween, bookExample, cloneDraft, directed, meanOf, nextSymbol, num, type ArcDraft, type NetworkDraft, type NetworkKind, type Point } from './networkData'

type Solved =
  | { kind: 'SPANNING_TREE'; value: SpanningResult }
  | { kind: 'DIJKSTRA'; value: DijkstraResult }
  | { kind: 'FLOYD'; value: FloydResult }
  | { kind: 'MAX_FLOW'; value: FlowResult }
  | { kind: 'CPM' | 'PERT'; value: CpmResult }

function withoutArc(arcs: ArcDraft[], from: number, to: number, undirected: boolean) {
  return arcs.filter(arc => !((arc.from === from && arc.to === to) || (undirected && arc.from === to && arc.to === from)))
}

function writeValue(draft: NetworkDraft, kind: NetworkKind, from: number, to: number, raw: string): NetworkDraft {
  const undirected = kind === 'SPANNING_TREE'
  const previous = arcBetween(draft.arcs, from, to) ?? (undirected ? arcBetween(draft.arcs, to, from) : undefined)
  const remaining = withoutArc(draft.arcs, from, to, undirected)
  if (raw.trim() === '') return { ...draft, arcs: remaining }
  const value = Number(raw)
  if (!Number.isFinite(value)) return draft
  const created: ArcDraft = previous
    ? { ...previous, from, to, value }
    : { from, to, value, symbol: kind === 'CPM' || kind === 'PERT' ? nextSymbol(remaining) : '', optimistic: value, likely: value, pessimistic: value }
  const arcs = [...remaining, created]
  if (undirected) arcs.push({ ...created, from: to, to: from })
  return { ...draft, arcs }
}

function editorEdges(draft: NetworkDraft, kind: NetworkKind): FigureEdge[] {
  const edges: FigureEdge[] = []
  const seen = new Set<string>()
  for (const arc of draft.arcs) {
    const reverse = arcBetween(draft.arcs, arc.to, arc.from)
    const pair = reverse != null && kind !== 'SPANNING_TREE'
    const key = kind === 'SPANNING_TREE' || pair ? `${Math.min(arc.from, arc.to)}-${Math.max(arc.from, arc.to)}` : `${arc.from}>${arc.to}`
    if (seen.has(key)) continue
    seen.add(key)
    let label = num(arc.value)
    if (kind === 'PERT') label = `${arc.symbol ? `${arc.symbol} ` : ''}${num(meanOf(arc))}`
    else if (kind === 'CPM') label = `${arc.symbol ? `${arc.symbol} ` : ''}${num(arc.value)}`
    else if (pair) label = `${num(arc.value)} / ${num(reverse.value)}`
    edges.push({ from: arc.from, to: arc.to, label, color: '#334155', width: 1.7, directed: directed(kind) && !pair })
  }
  return edges
}

const tabs: { slug: string; kind: NetworkKind; title: string; caption: string; icon: string }[] = [
  { slug: 'spanning-tree', kind: 'SPANNING_TREE', title: 'Minimal spanning tree', caption: 'Prim growth', icon: '⟁' },
  { slug: 'shortest-route', kind: 'DIJKSTRA', title: 'Shortest route', caption: 'Dijkstra and Floyd', icon: '⇢' },
  { slug: 'max-flow', kind: 'MAX_FLOW', title: 'Maximal flow', caption: 'Labeling', icon: '≋' },
  { slug: 'critical-path', kind: 'CPM', title: 'Critical path', caption: 'CPM and PERT', icon: '◆' },
]

function tabOf(kind: NetworkKind) {
  if (kind === 'SPANNING_TREE') return 0
  if (kind === 'DIJKSTRA' || kind === 'FLOYD') return 1
  if (kind === 'MAX_FLOW') return 2
  return 3
}

function tabFromPath(path: string) {
  const index = tabs.findIndex(tab => tab.slug === path.split('/')[2])
  return index < 0 ? 0 : index
}

export function NetworkPage({ path }: { path: string }) {
  const tabIndex = tabFromPath(path)
  const [kind, setKind] = useState<NetworkKind>(tabs[tabIndex].kind)
  const [draft, setDraft] = useState<NetworkDraft>(() => bookExample(tabs[tabIndex].kind))
  const [anchor, setAnchor] = useState<number | null>(null)
  const [selected, setSelected] = useState<{ from: number; to: number } | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [solving, setSolving] = useState(false)
  const [solved, setSolved] = useState<Solved | null>(null)
  const [run, setRun] = useState(0)

  function choose(next: NetworkKind) {
    setKind(next)
    setDraft(bookExample(next))
    setAnchor(null)
    setSelected(null)
    setSolved(null)
    setError(null)
  }

  useEffect(() => {
    if (tabOf(kind) !== tabIndex) choose(tabs[tabIndex].kind)
  }, [tabIndex])

  function openTab(index: number) {
    if (index === tabIndex) return
    window.history.pushState({}, '', `/network/${tabs[index].slug}`)
    window.dispatchEvent(new PopStateEvent('popstate'))
  }

  function move(id: number, point: Point) {
    setDraft(current => ({ ...current, positions: current.positions.map((item, index) => index === id - 1 ? point : item) }))
  }

  function addNode(point: Point) {
    if (draft.nodes >= 12) return
    setDraft(current => ({ ...current, nodes: current.nodes + 1, positions: [...current.positions, point], scheduled: [...current.scheduled, 0] }))
    setAnchor(null)
  }

  function connect(id: number) {
    if (anchor == null || anchor === id) {
      setAnchor(anchor === id ? null : id)
      return
    }
    setDraft(current => arcBetween(current.arcs, anchor, id) || (kind === 'SPANNING_TREE' && arcBetween(current.arcs, id, anchor)) ? current : writeValue(current, kind, anchor, id, '1'))
    setSelected({ from: anchor, to: id })
    setAnchor(null)
  }

  function removeSelected() {
    if (selected) {
      setDraft(current => ({ ...current, arcs: withoutArc(current.arcs, selected.from, selected.to, kind === 'SPANNING_TREE') }))
      setSelected(null)
      return
    }
    if (anchor != null && draft.nodes > 2) {
      const id = anchor
      setDraft(current => ({
        ...current,
        nodes: current.nodes - 1,
        positions: current.positions.filter((_, index) => index !== id - 1),
        scheduled: current.scheduled.filter((_, index) => index !== id - 1),
        start: current.start === id ? 1 : current.start > id ? current.start - 1 : current.start,
        arcs: current.arcs.filter(arc => arc.from !== id && arc.to !== id).map(arc => ({ ...arc, from: arc.from > id ? arc.from - 1 : arc.from, to: arc.to > id ? arc.to - 1 : arc.to })),
      }))
      setAnchor(null)
    }
  }

  const selectedArc = selected ? arcBetween(draft.arcs, selected.from, selected.to) ?? (kind === 'SPANNING_TREE' ? arcBetween(draft.arcs, selected.to, selected.from) : undefined) : undefined
  const nodes: FigureNode[] = draft.positions.map((_, index) => ({ id: index + 1, caption: '', tone: anchor === index + 1 ? 'anchor' : 'plain' }))

  async function solve() {
    setSolving(true)
    setError(null)
    const body = {
      nodes: draft.nodes,
      from: draft.arcs.map(arc => arc.from),
      to: draft.arcs.map(arc => arc.to),
      value: draft.arcs.map(arc => kind === 'PERT' ? meanOf(arc) : arc.value),
      symbol: draft.arcs.map(arc => arc.symbol),
      start: draft.start,
      ...(kind === 'PERT' ? {
        optimistic: draft.arcs.map(arc => arc.optimistic),
        likely: draft.arcs.map(arc => arc.likely),
        pessimistic: draft.arcs.map(arc => arc.pessimistic),
        scheduled: Array.from({ length: draft.nodes }, (_, index) => draft.scheduled[index] ?? 0),
      } : {}),
    }
    setRun(current => current + 1)
    try {
      if (kind === 'SPANNING_TREE') setSolved({ kind, value: await solveSpanningTree(body) })
      else if (kind === 'DIJKSTRA') setSolved({ kind, value: await solveDijkstra(body) })
      else if (kind === 'FLOYD') setSolved({ kind, value: await solveFloyd(body) })
      else if (kind === 'MAX_FLOW') setSolved({ kind, value: await solveMaxFlow(body) })
      else setSolved({ kind, value: await solveCpm(body) })
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Unable to solve the network.')
    } finally {
      setSolving(false)
    }
  }

  return <>
    <section className="hero compact-hero"><div><span className="eyebrow">NETWORK MODELS</span><h2>Watch a network algorithm build its answer.</h2><p>The graph and its tables stay one above the other, so the whole step fits a tablet.</p></div><div className="hero-actions"><ApplicationsTrigger key={tabIndex} methods={methodsForPath(`/network/${tabs[tabIndex].slug}`)} title={`Where ${tabs[tabIndex].title.toLowerCase()} pays off`} /><button className="button secondary" onClick={() => navigate('/study')}>All models</button></div></section>
    <div className="network-tabs-wrap">
      <nav className="network-tabs" role="tablist" aria-label="Network models" style={{ '--tab': tabIndex } as CSSProperties}>
        <span className="network-tab-indicator" aria-hidden="true" />
        {tabs.map((tab, index) => <button key={tab.slug} type="button" role="tab" aria-selected={index === tabIndex} className={`network-tab ${index === tabIndex ? 'active' : ''}`} onClick={() => openTab(index)}>
          <span className="tab-icon">{tab.icon}</span>
          <span><b>{tab.title}</b><small>{tab.caption}</small></span>
        </button>)}
      </nav>
    </div>
    <section className="card editor-card network-editor">
      <div className="section-heading"><div><span className="eyebrow">STEP 1 · NETWORK</span><h3>{tabs[tabIndex].title}</h3><p>Click empty space to add a node, drag a node to move it, then click two nodes to join them. The matrix stays in step with the graph. An empty cell is infinity.</p></div>
        {(kind === 'DIJKSTRA' || kind === 'FLOYD') && <div className="segmented" role="group" aria-label="Shortest-route algorithm"><button type="button" className={kind === 'DIJKSTRA' ? 'active' : ''} onClick={() => kind !== 'DIJKSTRA' && choose('DIJKSTRA')}>Dijkstra</button><button type="button" className={kind === 'FLOYD' ? 'active' : ''} onClick={() => kind !== 'FLOYD' && choose('FLOYD')}>Floyd</button></div>}
        {(kind === 'CPM' || kind === 'PERT') && <div className="segmented" role="group" aria-label="Duration model"><button type="button" className={kind === 'CPM' ? 'active' : ''} onClick={() => kind !== 'CPM' && choose('CPM')}>Deterministic CPM</button><button type="button" className={kind === 'PERT' ? 'active' : ''} onClick={() => kind !== 'PERT' && choose('PERT')}>PERT</button></div>}
      </div>
      {(kind === 'SPANNING_TREE' || kind === 'DIJKSTRA') && <div className="solver-actions"><label>Start node<select value={draft.start} onChange={event => setDraft({ ...draft, start: Number(event.target.value) })}>{draft.positions.map((_, index) => <option key={index} value={index + 1}>Node {index + 1}</option>)}</select></label></div>}
      <div className="network-stack">
        <NetworkSvg positions={draft.positions} nodes={nodes} edges={editorEdges(draft, kind)} interactive onBackground={addNode} onNode={connect} onMove={move} onEdge={(from, to) => { setSelected({ from, to }); setAnchor(null) }} />
        <div className="dimension-controls">
          <button className="button secondary" onClick={() => { setDraft(cloneDraft(bookExample(kind))); setSelected(null); setAnchor(null); setSolved(null) }}>Load example</button>
          <button className="button secondary" onClick={removeSelected} disabled={selected == null && anchor == null}>Delete selected</button>
        </div>
        {selectedArc && <ArcForm draft={draft} kind={kind} arc={selectedArc} onChange={setDraft} />}
        <Matrix draft={draft} kind={kind} onChange={setDraft} />
      </div>
      {kind === 'PERT' && <Scheduled draft={draft} onChange={setDraft} />}
      <div className="solver-actions"><button className="button primary" onClick={solve} disabled={solving}>{solving ? 'Solving…' : 'Solve and explore'}</button></div>
      {error && <p className="notice error">{error}</p>}
    </section>
    {solved?.kind === 'SPANNING_TREE' && <SpanningView key={run} result={solved.value} positions={draft.positions} arcs={draft.arcs} />}
    {solved?.kind === 'DIJKSTRA' && <DijkstraView key={run} result={solved.value} positions={draft.positions} arcs={draft.arcs} />}
    {solved?.kind === 'FLOYD' && <FloydView key={run} result={solved.value} positions={draft.positions} arcs={draft.arcs} />}
    {solved?.kind === 'MAX_FLOW' && <FlowView key={run} result={solved.value} positions={draft.positions} />}
    {(solved?.kind === 'CPM' || solved?.kind === 'PERT') && <CpmView key={run} result={solved.value} positions={draft.positions} pert={solved.kind === 'PERT'} />}
  </>
}

function ArcForm({ draft, kind, arc, onChange }: { draft: NetworkDraft; kind: NetworkKind; arc: ArcDraft; onChange: (draft: NetworkDraft) => void }) {
  function patch(next: Partial<ArcDraft>) {
    onChange({ ...draft, arcs: draft.arcs.map(item => item.from === arc.from && item.to === arc.to ? { ...item, ...next } : kind === 'SPANNING_TREE' && item.from === arc.to && item.to === arc.from ? { ...item, ...next, from: item.from, to: item.to } : item) })
  }
  const reverse = arcBetween(draft.arcs, arc.to, arc.from)
  return <div className="solver-actions">
    <span>Arc {arc.from} → {arc.to}</span>
    {kind !== 'PERT' && <label>Value<input className="matrix-input" type="number" step="any" value={arc.value} onChange={event => onChange(writeValue(draft, kind, arc.from, arc.to, event.target.value))} /></label>}
    {(kind === 'CPM' || kind === 'PERT') && <label>Symbol<input className="matrix-input" value={arc.symbol} onChange={event => patch({ symbol: event.target.value })} /></label>}
    {kind === 'PERT' && <>
      <label>Optimistic<input className="matrix-input" type="number" step="any" value={arc.optimistic} onChange={event => patch({ optimistic: Number(event.target.value) })} /></label>
      <label>Most likely<input className="matrix-input" type="number" step="any" value={arc.likely} onChange={event => patch({ likely: Number(event.target.value) })} /></label>
      <label>Pessimistic<input className="matrix-input" type="number" step="any" value={arc.pessimistic} onChange={event => patch({ pessimistic: Number(event.target.value) })} /></label>
    </>}
    {kind === 'MAX_FLOW' && <label>Reverse {arc.to} → {arc.from}<input className="matrix-input" type="number" step="any" value={reverse?.value ?? ''} placeholder="0" onChange={event => onChange(writeValue(draft, kind, arc.to, arc.from, event.target.value))} /></label>}
  </div>
}

function Matrix({ draft, kind, onChange }: { draft: NetworkDraft; kind: NetworkKind; onChange: (draft: NetworkDraft) => void }) {
  return <div className="table-scroll"><table className="matrix-table input-table"><thead><tr><th>{kind === 'MAX_FLOW' ? 'Capacity' : kind === 'CPM' || kind === 'PERT' ? 'Activity' : 'Length'}</th>{draft.positions.map((_, column) => <th key={column}>{column + 1}</th>)}</tr></thead><tbody>
    {draft.positions.map((_, row) => <tr key={row}><th>{row + 1}</th>{draft.positions.map((__, column) => {
      if (row === column) return <td key={column}>—</td>
      const arc = arcBetween(draft.arcs, row + 1, column + 1) ?? (kind === 'SPANNING_TREE' ? arcBetween(draft.arcs, column + 1, row + 1) : undefined)
      if (kind === 'PERT') {
        return <td key={column}>{arc ? <span className="pert-cell"><input className="matrix-input network-cell" type="number" step="any" value={arc.optimistic} onChange={event => onChange(patchEstimate(draft, arc, 'optimistic', Number(event.target.value)))} /><input className="matrix-input network-cell" type="number" step="any" value={arc.likely} onChange={event => onChange(patchEstimate(draft, arc, 'likely', Number(event.target.value)))} /><input className="matrix-input network-cell" type="number" step="any" value={arc.pessimistic} onChange={event => onChange(patchEstimate(draft, arc, 'pessimistic', Number(event.target.value)))} /></span> : <input className="matrix-input network-cell" placeholder="∞" onChange={event => { if (event.target.value.trim() !== '') onChange(writeValue(draft, kind, row + 1, column + 1, event.target.value)) }} />}</td>
      }
      return <td key={column}><input className="matrix-input" type="number" step="any" placeholder="∞" value={arc ? arc.value : ''} onChange={event => onChange(writeValue(draft, kind, row + 1, column + 1, event.target.value))} /></td>
    })}</tr>)}
  </tbody></table></div>
}

function patchEstimate(draft: NetworkDraft, arc: ArcDraft, field: 'optimistic' | 'likely' | 'pessimistic', value: number): NetworkDraft {
  return { ...draft, arcs: draft.arcs.map(item => item.from === arc.from && item.to === arc.to ? { ...item, [field]: value } : item) }
}

function Scheduled({ draft, onChange }: { draft: NetworkDraft; onChange: (draft: NetworkDraft) => void }) {
  const values = Array.from({ length: draft.nodes }, (_, index) => draft.scheduled[index] ?? 0)
  return <div className="solver-actions">{values.map((value, index) => <label key={index}>Scheduled node {index + 1}<input className="matrix-input" type="number" step="any" value={value} onChange={event => { const scheduled = [...values]; scheduled[index] = Number(event.target.value); onChange({ ...draft, scheduled }) }} /></label>)}</div>
}
