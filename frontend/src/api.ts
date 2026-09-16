export type InitialMethod = 'NORTHWEST_CORNER' | 'LEAST_COST' | 'VOGEL_APPROXIMATION'

export interface TransportationResult {
  initialMethod: InitialMethod
  initialAllocation: number[][]
  optimalAllocation: number[][]
  optimalCost: number
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
