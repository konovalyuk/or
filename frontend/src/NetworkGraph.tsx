import { useRef } from 'react'
import type { PointerEvent } from 'react'
import { VIEW, type Point } from './networkData'

export interface FigureNode {
  id: number
  caption: string
  tone: 'plain' | 'permanent' | 'temporary' | 'pivot' | 'critical' | 'crossed' | 'anchor' | 'muted'
  badges?: { square?: string; triangle?: string }
}

export interface FigureEdge {
  from: number
  to: number
  label: string
  color: string
  width: number
  dash?: string
  directed: boolean
  effect?: 'glow' | 'pulse' | 'draw'
  muted?: boolean
}

const fillOf: Record<FigureNode['tone'], string> = {
  plain: '#e2e8f0',
  permanent: '#dcfce7',
  temporary: '#fef3c7',
  pivot: '#dbeafe',
  critical: '#fecaca',
  crossed: '#fee2e2',
  anchor: '#bfdbfe',
  muted: '#f1f5f9',
}

const strokeOf: Record<FigureNode['tone'], string> = {
  plain: '#334155',
  permanent: '#166534',
  temporary: '#b45309',
  pivot: '#1d4ed8',
  critical: '#b91c1c',
  crossed: '#b91c1c',
  anchor: '#1d4ed8',
  muted: '#cbd5e1',
}

const PAD_X = 80
const PAD_TOP = 72
const PAD_BOTTOM = 60

function frame(positions: Point[], fit: boolean) {
  if (!fit || positions.length === 0) return { x: 0, y: 0, width: VIEW.width, height: VIEW.height }
  const xs = positions.map(point => point.x)
  const ys = positions.map(point => point.y)
  const left = Math.min(...xs) - PAD_X
  const top = Math.min(...ys) - PAD_TOP
  const width = Math.max(320, Math.max(...xs) + PAD_X - left)
  const height = Math.max(160, Math.max(...ys) + PAD_BOTTOM - top)
  return { x: left, y: top, width, height }
}

function localPoint(svg: SVGSVGElement, event: PointerEvent<SVGElement>) {
  const box = svg.getBoundingClientRect()
  const view = svg.viewBox.baseVal
  return {
    x: view.x + ((event.clientX - box.left) / box.width) * view.width,
    y: view.y + ((event.clientY - box.top) / box.height) * view.height,
  }
}

function markerId(color: string) {
  return `network-arrow-${color.replace('#', '')}`
}

export function NetworkSvg({ positions, nodes, edges, interactive, fit = !interactive, onBackground, onNode, onMove, onEdge }: {
  positions: Point[]
  nodes: FigureNode[]
  edges: FigureEdge[]
  interactive?: boolean
  fit?: boolean
  onBackground?: (point: Point) => void
  onNode?: (id: number) => void
  onMove?: (id: number, point: Point) => void
  onEdge?: (from: number, to: number) => void
}) {
  const svgRef = useRef<SVGSVGElement>(null)
  const drag = useRef<{ id: number; moved: boolean } | null>(null)
  const at = (id: number) => positions[id - 1] ?? { x: 40, y: 40 }
  const box = frame(positions, fit)
  const colors = [...new Set(edges.filter(edge => edge.directed).map(edge => edge.color))]
  return <svg ref={svgRef} className="network-graph" viewBox={`${box.x} ${box.y} ${box.width} ${box.height}`} role="img" aria-label="Network graph"
    onPointerMove={event => {
      if (!drag.current || !svgRef.current || !onMove) return
      drag.current.moved = true
      onMove(drag.current.id, localPoint(svgRef.current, event))
    }}
    onPointerUp={() => {
      const current = drag.current
      drag.current = null
      if (current && !current.moved) onNode?.(current.id)
    }}>
    <rect className="network-canvas" fill="#f8fafc" x={box.x} y={box.y} width={box.width} height={box.height}
      onPointerDown={event => {
        if (!interactive || !svgRef.current) return
        onBackground?.(localPoint(svgRef.current, event))
      }} />
    <defs>
      {colors.map(color => <marker key={color} id={markerId(color)} markerWidth="8" markerHeight="8" refX="8" refY="3" orient="auto">
        <path d="M0,0 L0,6 L8,3 z" fill={color} />
      </marker>)}
    </defs>
    {[...edges].sort((left, right) => Number(right.muted ?? false) - Number(left.muted ?? false) || left.width - right.width).map(edge => {
      const start = at(edge.from)
      const end = at(edge.to)
      const dx = end.x - start.x
      const dy = end.y - start.y
      const length = Math.hypot(dx, dy) || 1
      const trim = 26
      const x1 = start.x + (dx / length) * trim
      const y1 = start.y + (dy / length) * trim
      const x2 = end.x - (dx / length) * trim
      const y2 = end.y - (dy / length) * trim
      const labelX = (start.x + end.x) / 2 + (-dy / length) * 14
      const labelY = (start.y + end.y) / 2 + (dx / length) * 14
      const effect = edge.effect ? `net-edge-${edge.effect}` : ''
      return <g key={`${edge.from}-${edge.to}-${edge.label}-${edge.effect ?? ''}`} className={edge.muted ? 'net-muted' : ''}>
        {interactive && <line x1={start.x} y1={start.y} x2={end.x} y2={end.y} stroke="transparent" strokeWidth="16"
          onPointerDown={event => { event.stopPropagation(); onEdge?.(edge.from, edge.to) }} />}
        <line className={effect} x1={x1} y1={y1} x2={x2} y2={y2} stroke={edge.color} strokeWidth={edge.width} strokeLinecap="round"
          strokeDasharray={edge.effect === 'draw' ? undefined : edge.dash}
          markerEnd={edge.directed ? `url(#${markerId(edge.color)})` : undefined} />
        {edge.label && <text x={labelX} y={labelY} textAnchor="middle" className="graph-edge-label" pointerEvents="none">{edge.label}</text>}
      </g>
    })}
    {nodes.map(node => {
      const point = at(node.id)
      return <g key={node.id} className={node.tone === 'muted' ? 'net-muted' : ''}
        onPointerDown={event => {
          if (!interactive) return
          event.stopPropagation()
          drag.current = { id: node.id, moved: false }
          svgRef.current?.setPointerCapture(event.pointerId)
        }}>
        {node.tone === 'pivot' && <circle className="net-node-halo" cx={point.x} cy={point.y} r="30" />}
        <circle cx={point.x} cy={point.y} r="22" fill={fillOf[node.tone]} stroke={strokeOf[node.tone]} strokeWidth={node.tone === 'anchor' || node.tone === 'pivot' ? 3 : 2} />
        {node.tone === 'crossed' && <line x1={point.x - 16} y1={point.y - 16} x2={point.x + 16} y2={point.y + 16} stroke="#b91c1c" strokeWidth="2" />}
        <text x={point.x} y={point.y + 4} textAnchor="middle" className="graph-node-label" pointerEvents="none">{node.id}</text>
        {node.badges?.triangle != null && <g className="net-badge net-badge-triangle" pointerEvents="none">
          <polygon points={`${point.x},${point.y - 66} ${point.x - 20},${point.y - 28} ${point.x + 20},${point.y - 28}`} />
          <text x={point.x} y={point.y - 33} textAnchor="middle">{node.badges.triangle}</text>
        </g>}
        {node.badges?.square != null && <g className="net-badge net-badge-square" pointerEvents="none">
          <rect x={point.x - 17} y={point.y + 27} width="34" height="23" rx="4" />
          <text x={point.x} y={point.y + 43} textAnchor="middle">{node.badges.square}</text>
        </g>}
        {node.caption && <text x={point.x} y={point.y + 40} textAnchor="middle" className="network-caption" pointerEvents="none">{node.caption}</text>}
      </g>
    })}
  </svg>
}
