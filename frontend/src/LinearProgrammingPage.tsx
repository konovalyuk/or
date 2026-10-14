import { useEffect, useState } from 'react'
import { applicableSimplexMethods, solveGraphical, solveSimplex, type GraphicalResult, type GraphicalStep, type GraphPoint, type LpRelation, type LpSense, type SimplexMethod, type SimplexResult } from './api'
import { navigate } from './App'
import { SimplexView } from './SimplexView'

const methodLabel: Record<SimplexMethod, string> = {
  ALL_SLACK: 'All-slack',
  M_METHOD: 'M-method',
  TWO_PHASE: 'Two-phase',
  DUAL_SIMPLEX: 'Dual simplex',
}

const reddyObjective = [5, 4]
const reddyCoefficients = [[6, 4], [1, 2], [-1, 1], [0, 1]]
const reddyRelations: LpRelation[] = ['LE', 'LE', 'LE', 'LE']
const reddyRhs = [24, 6, 1, 2]
const lineColors = ['#1d4ed8', '#b45309', '#7c3aed', '#0f766e', '#be123c', '#0369a1']

function fmt(value: number) {
  const rounded = Math.round(value * 100) / 100
  return Object.is(rounded, -0) ? '0' : String(rounded)
}

function pointLabel(point: GraphPoint) {
  return `(${fmt(point.x)}, ${fmt(point.y)})`
}

function ticks(max: number) {
  if (max <= 0) return [0]
  const rough = max / 5
  const pow = 10 ** Math.floor(Math.log10(rough))
  const err = rough / pow
  const step = err >= 7.5 ? 10 * pow : err >= 3.5 ? 5 * pow : err >= 1.5 ? 2 * pow : pow
  const values: number[] = []
  for (let value = 0; value <= max + step * 0.01; value += step) values.push(Math.round(value * 1e9) / 1e9)
  return values
}

function clipToBox(a: number, b: number, c: number, minX: number, minY: number, maxX: number, maxY: number): [GraphPoint, GraphPoint] | null {
  const hits: GraphPoint[] = []
  const add = (x: number, y: number) => {
    if (hits.some(point => Math.hypot(point.x - x, point.y - y) < 1e-6)) return
    hits.push({ x, y })
  }
  const edge = (x1: number, y1: number, x2: number, y2: number) => {
    const dx = x2 - x1
    const dy = y2 - y1
    const denom = a * dx + b * dy
    const value = a * x1 + b * y1 - c
    if (Math.abs(denom) < 1e-12) {
      if (Math.abs(value) <= 1e-6 * (1 + Math.abs(c))) { add(x1, y1); add(x2, y2) }
      return
    }
    const t = -value / denom
    if (t >= -1e-8 && t <= 1 + 1e-8) {
      const clamped = Math.min(1, Math.max(0, t))
      add(x1 + clamped * dx, y1 + clamped * dy)
    }
  }
  edge(minX, minY, maxX, minY)
  edge(minX, maxY, maxX, maxY)
  edge(minX, minY, minX, maxY)
  edge(maxX, minY, maxX, maxY)
  if (hits.length < 2) return null
  let best = -1
  let left = 0
  let right = 1
  for (let i = 0; i < hits.length; i++) {
    for (let j = i + 1; j < hits.length; j++) {
      const distance = Math.hypot(hits[i].x - hits[j].x, hits[i].y - hits[j].y)
      if (distance > best) { best = distance; left = i; right = j }
    }
  }
  return [hits[left], hits[right]]
}

function CoefficientInput({ value, onChange }: { value: number; onChange: (value: number) => void }) {
  return <input className="matrix-input" type="number" step="any" value={Number.isFinite(value) ? value : 0} onChange={event => {
    const next = Number(event.target.value)
    if (Number.isFinite(next)) onChange(next)
  }} />
}

function LpGraph({ result, step, objectiveLevel, showOptimum }: { result: GraphicalResult; step: GraphicalStep; objectiveLevel: number | null; showOptimum: boolean }) {
  const width = 720
  const height = 460
  const padLeft = 52
  const padRight = 18
  const padTop = 16
  const padBottom = 40
  const mapX = (x: number) => padLeft + (x / result.xMax) * (width - padLeft - padRight)
  const mapY = (y: number) => height - padBottom - (y / result.yMax) * (height - padTop - padBottom)
  const path = step.polygon.map((point, index) => `${index ? 'L' : 'M'}${mapX(point.x)},${mapY(point.y)}`).join(' ')
  const visibleLines = result.constraints.filter(line => line.stepIndex <= step.index && line.lineFrom && line.lineTo)
  const moving = objectiveLevel == null ? null : clipToBox(result.objective[0], result.objective[1], objectiveLevel, -result.xMax * 0.08, -result.yMax * 0.08, result.xMax, result.yMax)
  return <svg className="lp-plot" viewBox={`0 0 ${width} ${height}`} role="img" aria-label="Feasible region">
    {ticks(result.xMax).map(tick => <line key={`vx${tick}`} className="lp-grid" x1={mapX(tick)} y1={mapY(0)} x2={mapX(tick)} y2={mapY(result.yMax)} />)}
    {ticks(result.yMax).map(tick => <line key={`hy${tick}`} className="lp-grid" x1={mapX(0)} y1={mapY(tick)} x2={mapX(result.xMax)} y2={mapY(tick)} />)}
    {step.polygon.length >= 3 && <path className="lp-region" d={`${path} Z`} />}
    {step.polygon.length === 2 && <line x1={mapX(step.polygon[0].x)} y1={mapY(step.polygon[0].y)} x2={mapX(step.polygon[1].x)} y2={mapY(step.polygon[1].y)} stroke="#93c5fd" strokeWidth="8" strokeLinecap="round" />}
    {visibleLines.map(line => {
      const current = line.stepIndex === step.index
      return <line key={line.stepIndex} x1={mapX(line.lineFrom!.x)} y1={mapY(line.lineFrom!.y)} x2={mapX(line.lineTo!.x)} y2={mapY(line.lineTo!.y)} stroke={lineColors[(line.stepIndex - 1) % lineColors.length]} strokeWidth={current ? 3 : 1.5} opacity={current ? 1 : 0.75} />
    })}
    {moving && <line x1={mapX(moving[0].x)} y1={mapY(moving[0].y)} x2={mapX(moving[1].x)} y2={mapY(moving[1].y)} stroke="#15803d" strokeWidth="2.5" strokeDasharray="7 5" />}
    {showOptimum && result.optimalPoints.length === 2 && <line x1={mapX(result.optimalPoints[0].x)} y1={mapY(result.optimalPoints[0].y)} x2={mapX(result.optimalPoints[1].x)} y2={mapY(result.optimalPoints[1].y)} stroke="#b91c1c" strokeWidth="4" strokeLinecap="round" />}
    {showOptimum && result.optimumShape === 'RAY' && result.optimalPoints.flatMap(point => result.flatDirections.map(direction => {
      const clipped = clipToBox(direction.y, -direction.x, direction.y * point.x - direction.x * point.y, 0, 0, result.xMax, result.yMax)
      if (!clipped) return null
      const end = [clipped[0], clipped[1]]
        .map(candidate => ({ candidate, ahead: (candidate.x - point.x) * direction.x + (candidate.y - point.y) * direction.y }))
        .sort((left, right) => right.ahead - left.ahead)[0]
      if (end.ahead <= 1e-6) return null
      return <line key={`${point.x}-${point.y}-${direction.x}-${direction.y}`} x1={mapX(point.x)} y1={mapY(point.y)} x2={mapX(end.candidate.x)} y2={mapY(end.candidate.y)} stroke="#b91c1c" strokeWidth="4" strokeLinecap="round" />
    }))}
    <line className="lp-axis" x1={mapX(0)} y1={mapY(0)} x2={mapX(result.xMax)} y2={mapY(0)} />
    <line className="lp-axis" x1={mapX(0)} y1={mapY(0)} x2={mapX(0)} y2={mapY(result.yMax)} />
    {ticks(result.xMax).map(tick => <text key={`tx${tick}`} className="lp-tick" x={mapX(tick)} y={mapY(0) + 16} textAnchor="middle">{fmt(tick)}</text>)}
    {ticks(result.yMax).filter(tick => tick > 0).map(tick => <text key={`ty${tick}`} className="lp-tick" x={mapX(0) - 8} y={mapY(tick) + 4} textAnchor="end">{fmt(tick)}</text>)}
    <text className="lp-tick" x={mapX(result.xMax) - 4} y={mapY(0) + 16} textAnchor="end">x1</text>
    <text className="lp-tick" x={mapX(0) + 8} y={padTop + 12}>x2</text>
    {showOptimum && result.optimalPoints.map(point => <g key={`${point.x}-${point.y}`}><circle cx={mapX(point.x)} cy={mapY(point.y)} r="5" fill="#b91c1c" /><text className="lp-tick" x={mapX(point.x) + 8} y={mapY(point.y) - 8}>{pointLabel(point)}</text></g>)}
  </svg>
}

function ObjectiveMotion({ result, active }: { result: GraphicalResult; active: boolean }) {
  const [progress, setProgress] = useState(0)
  const [done, setDone] = useState(false)
  useEffect(() => {
    if (!active || result.status === 'INFEASIBLE') {
      setProgress(1)
      setDone(active && result.status === 'INFEASIBLE')
      return
    }
    setProgress(0)
    setDone(false)
    const started = performance.now()
    const duration = Math.abs(result.levelEnd - result.levelStart) < 1e-8 ? 500 : 2000
    let frame = 0
    const tick = (now: number) => {
      const raw = Math.min(1, (now - started) / duration)
      const eased = raw < 0.5 ? 2 * raw * raw : 1 - ((-2 * raw + 2) ** 2) / 2
      setProgress(eased)
      if (raw < 1) frame = requestAnimationFrame(tick)
      else setDone(true)
    }
    frame = requestAnimationFrame(tick)
    return () => cancelAnimationFrame(frame)
  }, [active, result])
  const level = result.levelStart + (result.levelEnd - result.levelStart) * progress
  const step = result.steps[result.steps.length - 1]
  const finished = done && result.status !== 'UNBOUNDED' && result.status !== 'INFEASIBLE'
  return <><LpGraph result={result} step={step} objectiveLevel={result.status === 'INFEASIBLE' ? null : level} showOptimum={finished} />
    {done && <SolutionReadout result={result} />}</>
}

function SolutionReadout({ result }: { result: GraphicalResult }) {
  if (result.status === 'INFEASIBLE' || result.status === 'UNBOUNDED') {
    return <p className="lp-note">{result.message}</p>
  }
  return <div>
    {result.optimumShape === 'POINT' && result.x1 != null && result.x2 != null && result.objectiveValue != null && <div className="lp-readout"><div><span>x1</span><strong>{fmt(result.x1)}</strong></div><div><span>x2</span><strong>{fmt(result.x2)}</strong></div><div><span>z</span><strong>{fmt(result.objectiveValue)}</strong></div></div>}
    {result.optimumShape !== 'POINT' && result.objectiveValue != null && <div className="lp-readout"><div><span>z</span><strong>{fmt(result.objectiveValue)}</strong></div>{result.optimalPoints.map(point => <div key={`${point.x}-${point.y}`}><span>corner</span><strong>{pointLabel(point)}</strong></div>)}</div>}
    <p className="lp-note">{result.message}</p>
  </div>
}

export function LinearProgrammingPage() {
  const [sense, setSense] = useState<LpSense>('MAXIMIZE')
  const [variableCount, setVariableCount] = useState(2)
  const [constraintCount, setConstraintCount] = useState(4)
  const [objective, setObjective] = useState([...reddyObjective])
  const [coefficients, setCoefficients] = useState(reddyCoefficients.map(row => [...row]))
  const [relations, setRelations] = useState<LpRelation[]>([...reddyRelations])
  const [rhs, setRhs] = useState([...reddyRhs])
  const [result, setResult] = useState<GraphicalResult | null>(null)
  const [simplexResult, setSimplexResult] = useState<SimplexResult | null>(null)
  const [approach, setApproach] = useState<'GRAPHICAL' | 'SIMPLEX'>('GRAPHICAL')
  const [method, setMethod] = useState<SimplexMethod>('ALL_SLACK')
  const [methods, setMethods] = useState<SimplexMethod[]>(['ALL_SLACK'])
  const [stepIndex, setStepIndex] = useState(0)
  const [unlocked, setUnlocked] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [solving, setSolving] = useState(false)
  const twoVariables = objective.length === 2
  const effective = twoVariables ? approach : 'SIMPLEX'

  useEffect(() => {
    if (!result) return
    const lastConstraint = [...result.steps].reverse().find(step => step.kind === 'CONSTRAINT')
    if (lastConstraint && stepIndex >= lastConstraint.index) setUnlocked(true)
  }, [result, stepIndex])

  useEffect(() => {
    let cancelled = false
    const handle = window.setTimeout(() => {
      applicableSimplexMethods({ sense, objective, coefficients, relations, rhs }).then(list => {
        if (cancelled || list.length === 0) return
        setMethods(list)
        setMethod(current => list.includes(current) ? current : list[0])
      }).catch(() => {})
    }, 150)
    return () => { cancelled = true; window.clearTimeout(handle) }
  }, [sense, objective, coefficients, relations, rhs])

  function clearResult() { setResult(null); setSimplexResult(null); setStepIndex(0); setUnlocked(false) }

  function loadExample() {
    setSense('MAXIMIZE')
    setVariableCount(2)
    setConstraintCount(4)
    setApproach('GRAPHICAL')
    setMethod('ALL_SLACK')
    setMethods(['ALL_SLACK'])
    setObjective([...reddyObjective])
    setCoefficients(reddyCoefficients.map(row => [...row]))
    setRelations([...reddyRelations])
    setRhs([...reddyRhs])
    setError(null)
    clearResult()
  }

  function applyDimensions() {
    const variables = Math.max(1, Math.min(12, variableCount))
    const constraints = Math.max(1, Math.min(20, constraintCount))
    setVariableCount(variables)
    setConstraintCount(constraints)
    setObjective(Array.from({ length: variables }, (_, index) => objective[index] ?? 0))
    setCoefficients(Array.from({ length: constraints }, (_, row) => Array.from({ length: variables }, (_, column) => coefficients[row]?.[column] ?? 0)))
    setRelations(Array.from({ length: constraints }, (_, row) => relations[row] ?? 'LE'))
    setRhs(Array.from({ length: constraints }, (_, row) => rhs[row] ?? 0))
    setError(null)
    clearResult()
  }

  async function solve() {
    setError(null)
    setSolving(true)
    try {
      if (effective === 'GRAPHICAL') {
        const solved = await solveGraphical({ sense, objective, coefficients, relations, rhs })
        setResult(solved)
        setSimplexResult(null)
        setStepIndex(0)
        setUnlocked(false)
      } else {
        const solved = await solveSimplex({ sense, objective, coefficients, relations, rhs, method })
        setSimplexResult(solved)
        setResult(null)
      }
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Unable to solve the linear program.')
    } finally {
      setSolving(false)
    }
  }

  const step = result?.steps[stepIndex]
  const objectiveStep = result ? result.steps.length - 1 : 0
  const lastConstraintIndex = result?.steps.reduce((index, item) => item.kind === 'CONSTRAINT' ? item.index : index, 0) ?? 0
  const canOpenObjective = unlocked || stepIndex >= lastConstraintIndex

  return <>
    <section className="hero compact-hero">
      <div>
        <span className="eyebrow">LINEAR PROGRAMMING · CHAPTERS 2–4</span>
        <h2>Graph the region, or read the simplex tableau.</h2>
        <p>Two variables can be drawn as a feasible region. Any size can be solved with the simplex method, the M-method, two-phase, or dual simplex.</p>
      </div>
      <div className="hero-actions"><button className="button secondary" onClick={() => navigate('/study')}>All topics</button></div>
    </section>
    <section className="card editor-card">
      <div className="section-heading">
        <div>
          <span className="eyebrow">REDDY MIKKS BY DEFAULT</span>
          <h3>Linear program</h3>
          <p>Enter the objective, then each structural constraint. Variables stay nonnegative.</p>
        </div>
      </div>
      <div className="dimension-controls">
        <label>Variables<input type="number" min="1" max="12" value={variableCount} onChange={event => setVariableCount(Number(event.target.value))} /></label>
        <label>Constraints<input type="number" min="1" max="20" value={constraintCount} onChange={event => setConstraintCount(Number(event.target.value))} /></label>
        <button className="button secondary" onClick={applyDimensions}>Apply dimensions</button>
        <button className="button secondary" onClick={loadExample}>Load example</button>
      </div>
      <div className="table-scroll">
        <table className="matrix-table input-table">
          <thead><tr><th>Row</th>{objective.map((_, index) => <th key={index}>x{index + 1}</th>)}<th>Relation</th><th>RHS</th></tr></thead>
          <tbody>
            <tr>
              <th><select className="cell-select" value={sense} onChange={event => { setSense(event.target.value as LpSense); clearResult() }}><option value="MAXIMIZE">Maximize z</option><option value="MINIMIZE">Minimize z</option></select></th>
              {objective.map((value, index) => <td key={index}><CoefficientInput value={value} onChange={next => { setObjective(objective.map((item, column) => column === index ? next : item)); clearResult() }} /></td>)}
              <td /><td />
            </tr>
            {coefficients.map((row, rowIndex) => <tr key={rowIndex}>
              <th>({rowIndex + 1})</th>
              {row.map((value, column) => <td key={column}><CoefficientInput value={value} onChange={next => { setCoefficients(coefficients.map((line, index) => index === rowIndex ? line.map((item, itemIndex) => itemIndex === column ? next : item) : line)); clearResult() }} /></td>)}
              <td><select className="cell-select" value={relations[rowIndex]} onChange={event => { setRelations(relations.map((relation, index) => index === rowIndex ? event.target.value as LpRelation : relation)); clearResult() }}><option value="LE">≤</option><option value="GE">≥</option><option value="EQ">=</option></select></td>
              <td><CoefficientInput value={rhs[rowIndex]} onChange={next => { setRhs(rhs.map((value, index) => index === rowIndex ? next : value)); clearResult() }} /></td>
            </tr>)}
          </tbody>
        </table>
      </div>
      <p className="lp-note">x1, x2, … ≥ 0. The graphical method is available for exactly two variables.</p>
      <div className="solver-actions">
        <div className="solver-choices">
          {twoVariables && <label>Solution<select value={approach} onChange={event => { setApproach(event.target.value as 'GRAPHICAL' | 'SIMPLEX'); clearResult() }}><option value="GRAPHICAL">Graphical</option><option value="SIMPLEX">Algebraic (simplex)</option></select></label>}
          {effective === 'SIMPLEX' && <label>Simplex method<select value={method} onChange={event => { setMethod(event.target.value as SimplexMethod); clearResult() }}>{methods.map(item => <option key={item} value={item}>{methodLabel[item]}</option>)}</select></label>}
        </div>
        <button className="button primary" onClick={solve} disabled={solving || methods.length === 0}>{solving ? 'Solving…' : effective === 'GRAPHICAL' ? 'Solve and graph' : 'Solve'}</button>
      </div>
      {error && <p className="notice error">{error}</p>}
    </section>
    {simplexResult && <SimplexView result={simplexResult} />}
    {result && step && <section className="card">
      <div className="section-heading">
        <div>
          <span className="eyebrow">GRAPHICAL SOLUTION</span>
          <h3>{step.title}</h3>
          <p>{step.kind === 'NONNEGATIVE' ? 'The search starts in the first quadrant.' : step.kind === 'OBJECTIVE' ? 'The objective line moves out from the origin until it supports the feasible region.' : step.feasible ? 'The shaded set is what remains after this cut.' : 'This cut removes every remaining point.'}</p>
        </div>
        <span className={step.feasible ? 'status ok' : 'status warning'}>{stepIndex + 1} / {result.steps.length}</span>
      </div>
      <div className="lp-layout">
        <ol className="equation-list">
          {result.constraints.map(constraint => <li key={constraint.stepIndex}><button className={`equation-button ${stepIndex === constraint.stepIndex ? 'active' : ''}`} onClick={() => setStepIndex(constraint.stepIndex)}><small>Constraint</small>{constraint.label}</button></li>)}
          <li><button className={`equation-button ${stepIndex === objectiveStep ? 'active' : ''}`} disabled={!canOpenObjective} onClick={() => setStepIndex(objectiveStep)}><small>Objective</small>{result.objectiveLabel}</button></li>
        </ol>
        <div className="lp-graph">
          {step.kind === 'OBJECTIVE' ? <ObjectiveMotion result={result} active /> : <LpGraph result={result} step={step} objectiveLevel={null} showOptimum={false} />}
          {step.kind !== 'OBJECTIVE' && !step.feasible && <p className="lp-note">No feasible point remains.</p>}
        </div>
      </div>
      <div className="step-controls">
        <button className="button secondary" disabled={stepIndex === 0} onClick={() => setStepIndex(stepIndex - 1)}>Previous</button>
        <input className="timeline" type="range" min="0" max={canOpenObjective ? objectiveStep : lastConstraintIndex} value={Math.min(stepIndex, canOpenObjective ? objectiveStep : lastConstraintIndex)} onChange={event => setStepIndex(Number(event.target.value))} />
        <button className="button primary" disabled={stepIndex >= (canOpenObjective ? objectiveStep : lastConstraintIndex)} onClick={() => setStepIndex(stepIndex + 1)}>Next</button>
      </div>
    </section>}
  </>
}
