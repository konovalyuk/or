export type InitialMethod = 'NORTHWEST_CORNER' | 'LEAST_COST' | 'VOGEL_APPROXIMATION'

export interface TransportationResult {
  initialMethod: InitialMethod
  initialAllocation: number[][]
  initialBasic: boolean[][]
  initialCost: number
  initialSteps: InitialSolutionStep[]
  optimalAllocation: number[][]
  optimalBasic: boolean[][]
  optimalCost: number
  costs: number[][]
  supply: number[]
  demand: number[]
  sourceLabels: string[]
  destinationLabels: string[]
  dummyAdded: boolean
  dummyType: 'NONE' | 'SOURCE' | 'DESTINATION'
  dummyIndex: number
  dummyQuantity: number
  states: TransportationState[]
}

export interface InitialSolutionStep {
  step: number
  selectedRow: number
  selectedColumn: number
  allocatedAmount: number
  allocation: number[][]
  basic: boolean[][]
  remainingSupply: number[]
  remainingDemand: number[]
  totalCost: number
}

export interface TransportationState {
  iteration: number
  allocationBefore: number[][]
  basicBefore: boolean[][]
  u: number[]
  v: number[]
  evaluation: number[][]
  entering: { row: number; column: number } | null
  leaving: { row: number; column: number } | null
  cycle: { row: number; column: number }[]
  theta: number
  allocationAfter: number[][]
  basicAfter: boolean[][]
  totalCost: number
  optimal: boolean
}

export type LpSense = 'MAXIMIZE' | 'MINIMIZE'
export type LpRelation = 'LE' | 'GE' | 'EQ'
export type LpStatus = 'OPTIMAL' | 'ALTERNATIVE_OPTIMA' | 'INFEASIBLE' | 'UNBOUNDED'
export type OptimumShape = 'POINT' | 'SEGMENT' | 'RAY' | 'REGION' | 'NONE'

export interface GraphPoint { x: number; y: number }

export interface GraphicalConstraint {
  stepIndex: number
  label: string
  lineFrom: GraphPoint | null
  lineTo: GraphPoint | null
}

export interface GraphicalStep {
  index: number
  kind: 'NONNEGATIVE' | 'CONSTRAINT' | 'OBJECTIVE'
  constraintIndex: number
  title: string
  polygon: GraphPoint[]
  lineFrom: GraphPoint | null
  lineTo: GraphPoint | null
  feasible: boolean
}

export interface GraphicalResult {
  status: LpStatus
  sense: LpSense
  objectiveLabel: string
  objective: number[]
  levelStart: number
  levelEnd: number
  xMax: number
  yMax: number
  optimumShape: OptimumShape
  constraints: GraphicalConstraint[]
  optimalPoints: GraphPoint[]
  flatDirections: GraphPoint[]
  x1: number | null
  x2: number | null
  objectiveValue: number | null
  steps: GraphicalStep[]
  message: string
}

export async function solveGraphical(problem: {
  sense: LpSense
  objective: number[]
  coefficients: number[][]
  relations: LpRelation[]
  rhs: number[]
}): Promise<GraphicalResult> {
  const response = await fetch('/api/lp/graphical', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(problem),
  })
  if (!response.ok) {
    const body = await response.json().catch(() => ({ error: response.statusText }))
    throw new Error(body.error ?? 'Unable to solve the linear program.')
  }
  return response.json()
}

export type SimplexMethod = 'ALL_SLACK' | 'M_METHOD' | 'TWO_PHASE' | 'DUAL_SIMPLEX'
export type SimplexStatus = 'OPTIMAL' | 'ALTERNATIVE_OPTIMA' | 'INFEASIBLE' | 'UNBOUNDED' | 'CYCLING'

export interface SimplexCell { m: number; constant: number }
export interface SimplexRatio { value: number | null; selected: boolean }

export interface SimplexVariableRow {
  name: string
  value: number
  objectiveCoefficient: number
  contribution: number
}

export interface SimplexConstraintRow {
  index: number
  relation: LpRelation
  rhs: number
  deviation: number
  kind: 'SLACK' | 'SURPLUS' | 'EQUATION'
}

export interface SimplexRange {
  current: number
  minimum: number | null
  maximum: number | null
  price: number
}

export interface SimplexIteration {
  number: number
  phase: 'PRIMAL' | 'PHASE_I' | 'PHASE_II' | 'DUAL' | 'ALTERNATIVE'
  objectiveLabel: string
  columns: string[]
  basic: string[]
  objectiveRow: SimplexCell[]
  objectiveRhs: SimplexCell
  body: SimplexCell[][]
  solution: SimplexCell[]
  enteringColumn: number | null
  leavingRow: number | null
  rowRatios: SimplexRatio[] | null
  columnRatios: SimplexRatio[] | null
  operations: string[]
  optimal: boolean
  degenerate: boolean
  note: string | null
}

export interface SimplexResult {
  status: SimplexStatus
  method: SimplexMethod
  sense: LpSense
  objectiveValue: number | null
  degenerate: boolean
  message: string
  variables: SimplexVariableRow[]
  constraints: SimplexConstraintRow[]
  iterations: SimplexIteration[]
  sensitivity: { variables: SimplexRange[]; constraints: SimplexRange[] } | null
}

export async function solveSimplex(problem: {
  sense: LpSense
  objective: number[]
  coefficients: number[][]
  relations: LpRelation[]
  rhs: number[]
  method: SimplexMethod
}): Promise<SimplexResult> {
  const response = await fetch('/api/lp/simplex', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(problem),
  })
  if (!response.ok) {
    const body = await response.json().catch(() => ({ error: response.statusText }))
    throw new Error(body.error ?? 'Unable to solve the linear program.')
  }
  return response.json()
}

export async function applicableSimplexMethods(problem: {
  sense: LpSense
  objective: number[]
  coefficients: number[][]
  relations: LpRelation[]
  rhs: number[]
}): Promise<SimplexMethod[]> {
  const response = await fetch('/api/lp/simplex/methods', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(problem),
  })
  if (!response.ok) {
    const body = await response.json().catch(() => ({ error: response.statusText }))
    throw new Error(body.error ?? 'Unable to list simplex methods.')
  }
  return response.json()
}

export async function solveTransportation(problem: {
  costs: number[][]
  supply: number[]
  demand: number[]
  initialMethod: InitialMethod
}): Promise<TransportationResult> {
  const response = await fetch('/api/transportation/solve', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(problem),
  })
  if (!response.ok) {
    const body = await response.json().catch(() => ({ error: response.statusText }))
    throw new Error(body.error ?? 'Unable to solve the transportation problem.')
  }
  return response.json()
}
