export type NetworkKind = 'SPANNING_TREE' | 'DIJKSTRA' | 'FLOYD' | 'MAX_FLOW' | 'CPM' | 'PERT'

export interface Point { x: number; y: number }

export interface ArcDraft {
  from: number
  to: number
  value: number
  symbol: string
  optimistic: number
  likely: number
  pessimistic: number
}

export interface NetworkDraft {
  nodes: number
  positions: Point[]
  arcs: ArcDraft[]
  start: number
  scheduled: number[]
}

export const VIEW = { width: 760, height: 460 }

export function num(value: number | null | undefined) {
  if (value == null || !Number.isFinite(value)) return 'infinity'
  return value.toFixed(2)
}

export function short(value: number | null | undefined) {
  if (value == null || !Number.isFinite(value)) return '∞'
  const whole = Math.round(value)
  return Math.abs(value - whole) < 1e-9 ? String(whole) : value.toFixed(1)
}

export function routeText(nodes: number[]) {
  return nodes.length ? nodes.join('-') : '—'
}

export function arcBetween(arcs: ArcDraft[], from: number, to: number) {
  return arcs.find(arc => arc.from === from && arc.to === to)
}

export function meanOf(arc: ArcDraft) {
  return (arc.optimistic + 4 * arc.likely + arc.pessimistic) / 6
}

function edge(from: number, to: number, value: number, symbol = '', estimate?: [number, number, number]): ArcDraft {
  const [optimistic, likely, pessimistic] = estimate ?? [value, value, value]
  return { from, to, value, symbol, optimistic, likely, pessimistic }
}

function at(pairs: number[][]): Point[] {
  return pairs.map(([x, y]) => ({ x, y }))
}

export function bookExample(kind: NetworkKind): NetworkDraft {
  if (kind === 'SPANNING_TREE') {
    return {
      nodes: 6, start: 1, scheduled: [],
      positions: at([[90, 230], [250, 70], [270, 230], [270, 390], [470, 70], [560, 300]]),
      arcs: [
        edge(1, 2, 1), edge(1, 3, 5), edge(1, 4, 7), edge(1, 5, 9),
        edge(2, 3, 6), edge(2, 4, 4), edge(2, 5, 3),
        edge(3, 4, 5), edge(3, 6, 10),
        edge(4, 5, 8), edge(4, 6, 3),
      ],
    }
  }
  if (kind === 'DIJKSTRA' || kind === 'FLOYD') {
    const floyd = kind === 'FLOYD'
    return {
      nodes: 5, start: 1, scheduled: [],
      positions: floyd
        ? at([[90, 230], [280, 70], [280, 390], [500, 230], [650, 70]])
        : at([[90, 230], [280, 80], [300, 250], [520, 80], [640, 280]]),
      arcs: floyd
        ? [
            edge(1, 2, 3), edge(2, 1, 3), edge(1, 3, 10), edge(3, 1, 10),
            edge(2, 4, 5), edge(4, 2, 5), edge(3, 4, 6), edge(4, 3, 6),
            edge(3, 5, 15), edge(4, 5, 4), edge(5, 4, 4),
          ]
        : [
            edge(1, 2, 100), edge(1, 3, 30), edge(2, 3, 20),
            edge(3, 4, 10), edge(3, 5, 60), edge(4, 2, 15), edge(4, 5, 50),
          ],
    }
  }
  if (kind === 'MAX_FLOW') {
    return {
      nodes: 5, start: 1, scheduled: [],
      positions: at([[90, 230], [250, 360], [430, 360], [340, 90], [640, 230]]),
      arcs: [
        edge(1, 2, 20), edge(1, 3, 30), edge(1, 4, 10),
        edge(2, 3, 40), edge(2, 5, 30),
        edge(3, 4, 10), edge(4, 3, 5), edge(3, 5, 20),
        edge(4, 5, 20),
      ],
    }
  }
  const estimates: [number, number, number][] = [[3, 5, 7], [4, 6, 8], [1, 3, 5], [5, 8, 11], [1, 2, 3], [9, 11, 13], [1, 1, 1], [10, 12, 14], [0, 0, 0]]
  const activities: [number, number, number, string][] = [[1, 2, 5, 'A'], [1, 3, 6, 'B'], [2, 3, 3, 'C'], [2, 4, 8, 'D'], [3, 5, 2, 'E'], [3, 6, 11, 'F'], [4, 6, 1, 'G'], [5, 6, 12, 'H'], [4, 5, 0, '']]
  return {
    nodes: 6, start: 1, scheduled: [0, 5, 11, 12, 14, 26],
    positions: at([[80, 230], [230, 230], [360, 80], [360, 380], [520, 230], [680, 230]]),
    arcs: activities.map(([from, to, value, symbol], index) => edge(from, to, value, symbol, estimates[index])),
  }
}

export function directed(kind: NetworkKind) {
  return kind !== 'SPANNING_TREE'
}

export function nextSymbol(arcs: ArcDraft[]) {
  const used = new Set(arcs.map(arc => arc.symbol))
  for (const letter of 'ABCDEFGHJKLMNPQRSTUVWXYZ') {
    if (!used.has(letter)) return letter
  }
  return ''
}

export function cloneDraft(draft: NetworkDraft): NetworkDraft {
  return { ...draft, positions: draft.positions.map(point => ({ ...point })), arcs: draft.arcs.map(arc => ({ ...arc })), scheduled: [...draft.scheduled] }
}
