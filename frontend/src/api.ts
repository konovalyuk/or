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
