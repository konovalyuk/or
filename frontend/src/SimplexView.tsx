import { useEffect, useState } from 'react'
import type { SimplexCell, SimplexIteration, SimplexRatio, SimplexResult } from './api'

function formatNumber(value: number) {
  if (!Number.isFinite(value)) return 'infinity'
  const text = value.toFixed(2)
  return text === '-0.00' ? '0.00' : text
}

function formatCell(cell: SimplexCell) {
  if (Math.abs(cell.m) < 1e-8) return formatNumber(cell.constant)
  const penalty = `${formatNumber(cell.m)}M`
  if (Math.abs(cell.constant) < 5e-4) return penalty
  return cell.constant > 0 ? `${penalty}+${formatNumber(cell.constant)}` : `${penalty}${formatNumber(cell.constant)}`
}

function ratioText(ratio: SimplexRatio | undefined) {
  if (!ratio || ratio.value == null) return ''
  return formatNumber(ratio.value)
}

function bound(value: number | null) {
  return value == null ? 'infinity' : formatNumber(value)
}

function relationMark(relation: string) {
  if (relation === 'GE') return '(>)'
  if (relation === 'EQ') return '(=)'
  return '(<)'
}

function deviationText(kind: string, deviation: number) {
  const text = formatNumber(Math.abs(deviation))
  if (text === '0.00') return '0.00'
  if (kind === 'SURPLUS') return `${text}+`
  if (kind === 'SLACK') return `${text}-`
  return text
}

function heading(iteration: SimplexIteration) {
  if (iteration.optimal && iteration.phase === 'ALTERNATIVE') return 'Second optimal basis'
  if (iteration.phase === 'ALTERNATIVE') return 'Alternative optimum'
  if (iteration.optimal && iteration.phase === 'PHASE_I') return 'Phase I optimum'
  if (iteration.optimal) return 'Optimality condition satisfied'
  if (iteration.phase === 'PHASE_I') return `Phase I · iteration ${iteration.number}`
  if (iteration.phase === 'PHASE_II') return `Phase II · iteration ${iteration.number}`
  if (iteration.phase === 'DUAL') return `Dual simplex · iteration ${iteration.number}`
  return `Iteration ${iteration.number}`
}

function bodyClass(iteration: SimplexIteration, row: number, column: number) {
  if (iteration.enteringColumn === column && iteration.leavingRow === row) return 'pivot-cell'
  if (iteration.enteringColumn === column) return 'enter-col'
  if (iteration.leavingRow === row) return 'leave-row'
  return ''
}

export function SimplexView({ result }: { result: SimplexResult }) {
  const [index, setIndex] = useState(0)
  useEffect(() => setIndex(0), [result])
  const solved = result.status === 'OPTIMAL' || result.status === 'ALTERNATIVE_OPTIMA'
  const iteration = result.iterations[Math.min(index, Math.max(0, result.iterations.length - 1))]
  const entering = iteration && iteration.enteringColumn != null ? iteration.columns[iteration.enteringColumn] : null
  const leaving = iteration && iteration.leavingRow != null ? iteration.basic[iteration.leavingRow] : null

  return <>
    <section className="result-grid">
      <article className="card metric">
        <p>Objective value</p>
        <strong>{result.objectiveValue == null ? '—' : formatNumber(result.objectiveValue)}</strong>
        <span>{result.message}</span>
      </article>
      {solved && <section className="card result-card">
        <div className="result-banner">
          <div>
            <span className="eyebrow">OPTIMAL BUSINESS PLAN</span>
            <h3>Optimal basic solution</h3>
            <p>{result.status === 'ALTERNATIVE_OPTIMA' ? 'This plan is the first optimal basis. The slider reaches another basis with the same objective value.' : result.degenerate ? 'One basic variable is zero, so the basis is degenerate.' : 'Structural variables and the original constraints at this basis.'}</p>
          </div>
        </div>
        <div className="table-scroll">
          <table className="route-table plan-table">
            <thead><tr><th>Variable</th><th>Value</th><th>Obj Coeff</th><th>Obj Val Contrib</th></tr></thead>
            <tbody>{result.variables.map(variable => <tr key={variable.name}><td>{variable.name}</td><td>{formatNumber(variable.value)}</td><td>{formatNumber(variable.objectiveCoefficient)}</td><td>{formatNumber(variable.contribution)}</td></tr>)}</tbody>
          </table>
        </div>
        <div className="table-scroll">
          <table className="route-table plan-table">
            <thead><tr><th>Constraint</th><th>RHS</th><th>Slack-/Surplus+</th></tr></thead>
            <tbody>{result.constraints.map(constraint => <tr key={constraint.index}><td>{constraint.index} {relationMark(constraint.relation)}</td><td>{formatNumber(constraint.rhs)}</td><td>{deviationText(constraint.kind, constraint.deviation)}</td></tr>)}</tbody>
          </table>
        </div>
      </section>}
    </section>
    {iteration && <section className="card replay-card">
      <div className="section-heading">
        <div>
          <span className="eyebrow">SIMPLEX ITERATIONS</span>
          <h3>{heading(iteration)}</h3>
          <p>{entering && leaving ? `${entering} enters and ${leaving} leaves.` : entering ? `${entering} is the entering column.` : iteration.optimal ? 'No improving pivot remains.' : 'The current basis.'}{iteration.degenerate ? ' A basic value is zero.' : ''}</p>
        </div>
        <span className={iteration.optimal ? 'status ok' : 'status warning'}>{index + 1} / {result.iterations.length}</span>
      </div>
      <div className="table-scroll">
        <table className="simplex-table">
          <thead>
            <tr>
              <th>Basic</th>
              {iteration.columns.map((column, columnIndex) => <th key={`${column}-${columnIndex}`} className={iteration.enteringColumn === columnIndex ? 'enter-col' : ''}>{column}</th>)}
              <th>Solution</th>
              {iteration.rowRatios && <th>Ratio</th>}
            </tr>
          </thead>
          <tbody>
            <tr>
              <th>{iteration.objectiveLabel}</th>
              {iteration.objectiveRow.map((cell, columnIndex) => <td key={columnIndex} className={iteration.enteringColumn === columnIndex ? 'enter-col' : ''}>{formatCell(cell)}</td>)}
              <td>{formatCell(iteration.objectiveRhs)}</td>
              {iteration.rowRatios && <td />}
            </tr>
            {iteration.body.map((row, rowIndex) => <tr key={rowIndex}>
              <th className={iteration.leavingRow === rowIndex ? 'leave-row' : ''}>{iteration.basic[rowIndex]}</th>
              {row.map((cell, columnIndex) => <td key={columnIndex} className={bodyClass(iteration, rowIndex, columnIndex)}>{formatCell(cell)}</td>)}
              <td className={iteration.leavingRow === rowIndex ? 'leave-row' : ''}>{formatCell(iteration.solution[rowIndex])}</td>
              {iteration.rowRatios && <td className={iteration.rowRatios[rowIndex]?.selected ? 'ratio-selected' : ''}>{ratioText(iteration.rowRatios[rowIndex])}</td>}
            </tr>)}
          </tbody>
          {iteration.columnRatios && <tfoot>
            <tr>
              <th>Ratio</th>
              {iteration.columnRatios.map((ratio, columnIndex) => <td key={columnIndex} className={ratio.selected ? 'ratio-selected' : ''}>{ratioText(ratio)}</td>)}
              <td />
            </tr>
          </tfoot>}
        </table>
      </div>
      {iteration.note && <p className="lp-note">{iteration.note}</p>}
      {iteration.operations.length > 0 && <div className="gauss-block">
        <h4>Gauss–Jordan elimination</h4>
        <ol className="gauss-list">{iteration.operations.map((line, lineIndex) => <li key={lineIndex}>{line}</li>)}</ol>
      </div>}
      <div className="step-controls">
        <button className="button secondary" disabled={index === 0} onClick={() => setIndex(index - 1)}>Previous</button>
        <input className="timeline" type="range" min="0" max={result.iterations.length - 1} value={index} onChange={event => setIndex(Number(event.target.value))} />
        <button className="button primary" disabled={index >= result.iterations.length - 1} onClick={() => setIndex(index + 1)}>Next</button>
      </div>
    </section>}
    {solved && result.sensitivity && <section className="card replay-card">
      <div className="section-heading">
        <div>
          <span className="eyebrow">SENSITIVITY ANALYSIS</span>
          <h3>Objective coefficients and right-hand sides</h3>
          <p>Ranges that keep this basis optimal. A missing end is unbounded.</p>
        </div>
      </div>
      <div className="table-scroll">
        <table className="route-table plan-table">
          <thead><tr><th>Variable</th><th>Current</th><th>Min</th><th>Max</th><th>Reduced cost</th></tr></thead>
          <tbody>{result.sensitivity.variables.map((range, rangeIndex) => <tr key={result.variables[rangeIndex]?.name ?? rangeIndex}><td>{result.variables[rangeIndex]?.name ?? `x${rangeIndex + 1}`}</td><td>{formatNumber(range.current)}</td><td>{bound(range.minimum)}</td><td>{bound(range.maximum)}</td><td>{formatNumber(range.price)}</td></tr>)}</tbody>
        </table>
      </div>
      <div className="table-scroll">
        <table className="route-table plan-table">
          <thead><tr><th>Constraint</th><th>Current RHS</th><th>Min</th><th>Max</th><th>Dual price</th></tr></thead>
          <tbody>{result.sensitivity.constraints.map((range, rangeIndex) => <tr key={rangeIndex}><td>{rangeIndex + 1} {relationMark(result.constraints[rangeIndex]?.relation ?? 'LE')}</td><td>{formatNumber(range.current)}</td><td>{bound(range.minimum)}</td><td>{bound(range.maximum)}</td><td>{formatNumber(range.price)}</td></tr>)}</tbody>
        </table>
      </div>
    </section>}
  </>
}
