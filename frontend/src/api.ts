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
