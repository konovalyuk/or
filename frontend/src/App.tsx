import { useMemo, useState } from 'react'
import { type InitialMethod, solveTransportation, type TransportationResult } from './api'

const sunRayCosts = [[10, 2, 20, 11], [12, 7, 9, 20], [4, 14, 16, 18]]
const sunRaySupply = [15, 25, 10]
const sunRayDemand = [5, 15, 15, 15]

function NumberInput({ value, onChange }: { value: number; onChange: (value: number) => void }) {
  return <input className="matrix-input" type="number" min="0" step="any" value={value}
    onChange={(event) => onChange(Number(event.target.value))} />
}

function AllocationTable({ allocation, supply, demand }: { allocation: number[][]; supply: number[]; demand: number[] }) {
  return <div className="table-scroll"><table className="matrix-table result-table">
    <thead><tr><th>Source / destination</th>{demand.map((_, j) => <th key={j}>D{j + 1}</th>)}<th>Supply</th></tr></thead>
    <tbody>{allocation.map((row, i) => <tr key={i}><th>S{i + 1}</th>{row.map((value, j) => <td key={j}>{value > 0 ? value : '—'}</td>)}<td>{supply[i]}</td></tr>)}</tbody>
    <tfoot><tr><th>Demand</th>{demand.map((value, j) => <td key={j}>{value}</td>)}<td /></tr></tfoot>
  </table></div>
}

export function App() {
  const [costs, setCosts] = useState(sunRayCosts)
  const [supply, setSupply] = useState(sunRaySupply)
  const [demand, setDemand] = useState(sunRayDemand)
  const [method, setMethod] = useState<InitialMethod>('VOGEL_APPROXIMATION')
  const [result, setResult] = useState<TransportationResult | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [solving, setSolving] = useState(false)
  const balanced = useMemo(() => supply.reduce((a, b) => a + b, 0) === demand.reduce((a, b) => a + b, 0), [supply, demand])

  function updateCost(row: number, column: number, value: number) {
    setCosts(costs.map((line, i) => line.map((cell, j) => i === row && j === column ? value : cell)))
  }
  function updateSupply(row: number, value: number) { setSupply(supply.map((cell, i) => i === row ? value : cell)) }
  function updateDemand(column: number, value: number) { setDemand(demand.map((cell, i) => i === column ? value : cell)) }
  function loadSunRay() { setCosts(sunRayCosts); setSupply(sunRaySupply); setDemand(sunRayDemand); setResult(null); setError(null) }

  async function solve() {
    setError(null)
    if (!balanced) { setError('Total supply must equal total demand.'); return }
    setSolving(true)
    try { setResult(await solveTransportation({ costs, supply, demand, initialMethod: method })) }
    catch (reason) { setError(reason instanceof Error ? reason.message : 'Unable to solve the problem.') }
    finally { setSolving(false) }
  }

  return <div className="app-shell">
    <header className="topbar"><div className="page-width brand"><span className="brand-mark">∑</span><div><h1>Operations Research Lab</h1><p>Transportation model · MODI method</p></div></div></header>
    <main className="page-width content">
      <section className="hero"><div><span className="eyebrow">INTERACTIVE WORKSPACE</span><h2>Transportation problem solver</h2><p>Build a balanced transportation table, choose an initial solution, and inspect the optimal allocation.</p></div><button className="button secondary" onClick={loadSunRay}>Load SunRay example</button></section>
      <section className="card"><div className="section-heading"><div><h3>Problem data</h3><p>Unit transportation costs, supply, and demand.</p></div><span className={balanced ? 'status ok' : 'status warning'}>{balanced ? 'Balanced' : 'Needs balancing'}</span></div>
        <div className="table-scroll"><table className="matrix-table"><thead><tr><th>Cost</th>{demand.map((_, j) => <th key={j}>D{j + 1}</th>)}<th>Supply</th></tr></thead><tbody>{costs.map((row, i) => <tr key={i}><th>S{i + 1}</th>{row.map((value, j) => <td key={j}><NumberInput value={value} onChange={(next) => updateCost(i, j, next)} /></td>)}<td><NumberInput value={supply[i]} onChange={(next) => updateSupply(i, next)} /></td></tr>)}</tbody><tfoot><tr><th>Demand</th>{demand.map((value, j) => <td key={j}><NumberInput value={value} onChange={(next) => updateDemand(j, next)} /></td>)}<td /></tr></tfoot></table></div>
        <div className="solver-actions"><label>Initial basic solution<select value={method} onChange={(event) => setMethod(event.target.value as InitialMethod)}><option value="VOGEL_APPROXIMATION">Vogel approximation</option><option value="LEAST_COST">Least-cost method</option><option value="NORTHWEST_CORNER">Northwest corner</option></select></label><button className="button primary" onClick={solve} disabled={solving}>{solving ? 'Solving…' : 'Solve problem'}</button></div>
        {error && <p className="notice error">{error}</p>}
      </section>
      {result && <section className="result-grid"><article className="card metric"><p>Optimal cost</p><strong>{result.optimalCost.toFixed(2)}</strong><span>Method: {result.initialMethod.replaceAll('_', ' ').toLowerCase()}</span></article><article className="card allocation"><div className="section-heading"><div><h3>Optimal allocation</h3><p>Positive shipments in the final basic feasible solution.</p></div></div><AllocationTable allocation={result.optimalAllocation} supply={supply} demand={demand} /></article></section>}
    </main>
    <footer>Operations Research Lab · Transportation Model and Its Variants</footer>
  </div>
}
