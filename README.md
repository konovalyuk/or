# Operations Research Lab

Interactive application for operations research: an exact freight network planner (routes, truck fleet, terminal workload and schedule) and educational views for transportation problems, the MODI method, linear programming and network models. It is a Spring Boot API with a React/Vite single-page application, packaged as one executable JAR and one OCI-compatible Docker image.

## Prerequisites

- Java 25
- Node.js 20+
- Docker (for containerized execution)

## Local development

Run the backend in one terminal (the `dev` profile enables CORS for the local Vite server):

```bash
SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun
```

Run the frontend with live reload in another:

```bash
cd frontend
npm ci
npm run dev
```

Open http://localhost:5173. The Vite development server proxies `/api` requests to Spring Boot on port 8080.

The landing page offers two workflows:

- **Production** (`/production`) is the RTA Dubai freight network planner described below.
- **Education** replays the initial basic feasible solution cell by cell and then walks through the MODI potentials, evaluations, closed loop, theta, and pivots.

The transportation API accepts a rectangular cost matrix, supply, demand, and one of `NORTHWEST_CORNER`, `LEAST_COST`, or `VOGEL_APPROXIMATION`. Unequal totals are balanced automatically by adding a zero-cost dummy source or destination; the response identifies the dummy route and includes immutable initial-solution snapshots.

## Freight network planner (Production)

`/production` plans freight between logistics terminals: which path every tonne takes (direct or through a hub), how many 20 t, 10 t and 5 t trucks run on every leg, how cargo of different origin-destination pairs is consolidated, and when every terminal processes it. The result is a final plan with a **proven global optimum**, a full cost breakdown and the workload of every terminal. All optimization code (LP, branch-and-bound, scheduler) is implemented in this project; no external solver is used.

### Example data (illustrative)

The default example uses three Dubai logistics hubs. The values are realistic in magnitude but illustrative, not official RTA data; every value can be edited on the page.

| | Jebel Ali Port | Dubai South / DWC | DXB Cargo Village |
|---|---|---|---|
| Demand from Jebel Ali Port (t) | — | 12 | 8 |
| Demand from Dubai South / DWC (t) | 9 | — | 11 |
| Demand from DXB Cargo Village (t) | 6 | 10 | — |
| Terminal capacity (t/h) | 120 | 100 | 80 |
| Handling (AED/t) | 18 | 20 | 28 |

Directed road distances (km): Jebel Ali Port → DWC 16, → DXB 40; DWC → Jebel Ali 15, → DXB 45; DXB → Jebel Ali 39, → DWC 44. Trucks: 20 t at 9.50 AED/km, 10 t at 6.80 AED/km, 5 t at 4.20 AED/km, loading factor 0.8 (usable 16 / 8 / 4 t). Cargo is ready at 20:00, trucks drive at 60 km/h, the planning horizon is 4 h.

Verified result: **3,958.00 AED** (transport 1,308.00 + handling 2,650.00), 70.68 AED/t, 5 trucks, 160 truck-km, all cargo delivered by 21:23. Terminal workload: Jebel Ali Port 35 t / 630 AED, Dubai South / DWC 52 t (10 t of it transit) / 1,040 AED, DXB Cargo Village 35 t / 980 AED. Direct trucks only cost 4,127.20 AED, so the network saves 169.20 AED (4.10 %). The optimum was cross-checked by an independent brute-force enumeration on a 1 t grid.

### How optimality is proven

1. **Static MILP (lower bound).** Multi-commodity flow with split paths, transshipment, consolidation and integer truck counts per arc and vehicle type, solved to optimality by LP relaxation and branch-and-bound. It has exactly the same transport and handling costs as the full problem but ignores time, terminal capacity, fleet limits and deadlines. It is a relaxation, so its optimum is a lower bound for the full problem.
2. **Continuous-time schedule.** The same routes, trucks and loads are scheduled event by event: every terminal is a single queue processing at its capacity rate (t/h), a truck leaves only after all its loads were processed, cargo is processed at the next terminal only after the truck arrived. The schedule is checked against the horizon, the optional deadline and the owned-fleet limits.
3. **Certificate.** If the schedule is feasible, the plan is feasible for the full problem and costs exactly the lower bound: gap 0 %, status `OPTIMAL`. The page shows the plan cost, lower bound, gap, branch-and-bound nodes and the proof steps.
4. **Fallback.** If the cost-optimal static plan cannot be scheduled (tight terminals, horizon, deadline or fleet), routes, trucks and time slots are optimized jointly in a time-expanded MILP. Its times are discretized by the configured slot length, and the result is reported with the static lower bound and the remaining gap. Activation costs, if given, also use this model.

Before a plan is returned, the service verifies that trip costs add up to the transport cost, terminal costs add up to the handling cost, and no terminal exceeds its capacity rate; a violation returns HTTP 500 instead of a wrong plan.

### Modelling assumptions

- Every non-zero demand cell is a separate commodity; it may be split across several paths.
- Handling is charged per tonne at every terminal the cargo visits: origin, every transit hub and destination.
- Transport is charged per loaded one-way trip (tariff × km of the vehicle type), independent of the load. Empty returns and repositioning are not modelled or charged; an owned truck is available again when its trip arrives.
- Usable capacity = nominal capacity × loading factor.
- An empty owned-fleet or rental-limit field means an unlimited number of trucks of that type.
- Volume discount bands are not supported by the exact planner and are rejected with HTTP 400.

### API

- `GET /api/network-optimization/example` returns the Dubai example request.
- `POST /api/network-optimization/solve` accepts the same structure (`locations`, `demandMatrix`, `distanceMatrix`, `vehicleTypes`, `loadingFactor`, `speedKmPerHour`, `terminalCapacity`, `terminalHandlingCost`, `readyTime`, `planningHorizonHours`, `periodDurationHours`, `deliveryDeadline`, `ownedVehicles`, `rentalEnabled`, `rentalLimits`, `directOnly`, `consolidation`, `currency`, solver limits) and returns `plan` (status, proof, lower bound, gap, costs, routes, trips with loads, terminal jobs, terminal loads, step-by-step explanation), the `directOnly` and `optimizedNetwork` scenarios (plus owned-fleet scenarios when limits are set) and the savings against direct trucks.

`./gradlew test` covers the Dubai optimum and its cost decomposition, terminal workload, schedule ordering, the direct-only calculation, the transit handling and processing cases of the time-expanded model, and the HTTP endpoints.

## Production-like JAR

```bash
./gradlew bootJar
java -jar build/libs/or-0.0.1-SNAPSHOT.jar
```

The frontend is built and embedded into the JAR. Open http://localhost:8080. The readiness endpoint is http://localhost:8080/actuator/health.

## Docker

```bash
docker build -t operations-research-lab .
docker run --rm -p 8080:8080 operations-research-lab
```

The image accepts `PORT` and defaults to `8080`, so it can run on Render, Railway, Fly.io, Cloud Run, Azure Container Apps, AWS ECS/Fargate, or any OCI-compatible service.

## GitHub and deployment

Create a private empty GitHub repository, then add it as `origin` and push `main`:

```bash
git remote add origin https://github.com/<account>/<repository>.git
git push -u origin main
```

For Render, create a new **Web Service**, connect the repository, choose **Docker**, and expose port `8080`. Other cloud providers only need the Dockerfile and a public HTTP route to the application port. Do not commit cloud tokens or `.env` files; configure secrets in the provider dashboard.
