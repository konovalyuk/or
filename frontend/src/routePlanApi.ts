export interface VehicleTypeInput {
  name: string
  nominalCapacity: number
  tariffPerKm: number
  rentalCostPerTrip: number
}

export interface RoutePlanRequest {
  locations: string[]
  demandMatrix: number[][]
  distanceMatrix: number[][]
  vehicleTypes: VehicleTypeInput[]
  loadingFactor: number
  speedKmPerHour: number
  terminalCapacity: number[]
  terminalHandlingCost: number[]
  readyTime: string
  planningHorizonHours: number
  periodDurationHours: number
  deliveryDeadline: string | null
  /** null entry = unlimited for that vehicle type; null array = unlimited fleet */
  ownedVehicles: (number | null)[] | null
  rentalEnabled: boolean
  rentalLimits: (number | null)[] | null
  directOnly: boolean
  consolidation: boolean
  routeActivationCost: number[][] | null
  vehicleActivationCost: number[] | null
  discountBands: null
  currency: string
  nodeLimit: number
  maxVariables: number
  maxConstraints: number
  maxSparseNonZeros: number
}

export type PlanStatus = 'OPTIMAL' | 'FEASIBLE_NOT_PROVEN' | 'INFEASIBLE' | 'NO_SOLUTION_FOUND' | 'MODEL_TOO_LARGE'

export interface RoutePlan {
  shipmentIndex: number
  origin: number
  destination: number
  tonnage: number
  path: number[]
  distance: number
  firstDepartureHour: number
  deliveredHour: number
  handlingCost: number
}

export interface TripLoad {
  shipmentIndex: number
  origin: number
  destination: number
  legIndex: number
  tonnage: number
}

export interface PlannedTrip {
  tripIndex: number
  from: number
  to: number
  vehicleTypeIndex: number
  vehicleName: string
  rented: boolean
  nominalCapacity: number
  usableCapacity: number
  loadedTonnage: number
  distance: number
  departureHour: number
  arrivalHour: number
  transportCost: number
  rentalCost: number
  loads: TripLoad[]
}

export interface TerminalJob {
  city: number
  shipmentIndex: number
  visitIndex: number
  kind: 'ORIGIN' | 'TRANSIT' | 'DESTINATION'
  tonnage: number
  startHour: number
  endHour: number
  cost: number
}

export interface TerminalLoad {
  city: number
  outboundTonnage: number
  transitTonnage: number
  inboundTonnage: number
  totalTonnage: number
  capacityPerHour: number
  busyHours: number
  peakRatePerHour: number
  utilizationPercent: number
  firstStartHour: number | null
  lastEndHour: number | null
  handlingCostPerTon: number
  cost: number
}

export interface PlanResult {
  status: PlanStatus
  message: string
  method: string
  globallyOptimal: boolean
  totalCost: number | null
  lowerBound: number | null
  gapPercent: number | null
  transportCost: number
  terminalCost: number
  rentalCost: number
  activationCost: number
  totalTonnage: number
  costPerTon: number | null
  totalTruckKm: number
  tripCount: number
  transshipmentTonnage: number
  completionHour: number | null
  routes: RoutePlan[]
  trips: PlannedTrip[]
  terminalJobs: TerminalJob[]
  terminalLoads: TerminalLoad[]
  proof: string[]
  explanation: { title: string; lines: string[] }[]
  warnings: string[]
  searchNodes: number
}

export interface RoutePlanResponse {
  locations: string[]
  currency: string
  readyTime: string
  plan: PlanResult
  scenarios: Record<string, PlanResult>
  savingsVsDirect: number | null
  savingsPercent: number | null
}

export async function loadRoutePlanExample(): Promise<RoutePlanRequest> {
  const response = await fetch('/api/network-optimization/example')
  if (!response.ok) throw new Error('Unable to load the example network.')
  return response.json()
}

export async function solveRoutePlan(problem: RoutePlanRequest): Promise<RoutePlanResponse> {
  const controller = new AbortController()
  const timeout = window.setTimeout(() => controller.abort(), 180_000)
  try {
    const response = await fetch('/api/network-optimization/solve', {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(problem), signal: controller.signal,
    })
    if (!response.ok) {
      const body = await response.json().catch(() => ({ error: response.statusText }))
      throw new Error(body.error ?? 'Unable to compute the route plan.')
    }
    return response.json()
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') {
      throw new Error('The solver exceeded the 180 second UI timeout. Reduce the planning horizon or model size.')
    }
    throw error
  } finally {
    window.clearTimeout(timeout)
  }
}
