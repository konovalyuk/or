export type Industry =
  | 'logistics' | 'energy' | 'retail' | 'manufacturing' | 'finance' | 'airlines'
  | 'healthcare' | 'public' | 'construction' | 'telecom' | 'agriculture' | 'forestry'

export type Method =
  | 'lp' | 'transportation' | 'assignment' | 'mst' | 'shortest-route' | 'max-flow' | 'cpm' | 'min-cost-flow'
  | 'integer' | 'tsp' | 'dp' | 'inventory' | 'decision' | 'queuing' | 'simulation' | 'markov' | 'goal' | 'heuristics'

export interface Application {
  id: string
  title: string
  problem: string
  payoff: string
  industries: Industry[]
  methods: Method[]
  companies: string[]
  companiesBy?: Partial<Record<Industry, string[]>>
  source: string
  documented?: boolean
}

export const INDUSTRIES: Record<Industry, { label: string; tagline: string }> = {
  logistics: { label: 'Logistics & transport', tagline: 'Fewer empty kilometres, fuller trucks, faster delivery.' },
  energy: { label: 'Oil, gas & energy', tagline: 'Every barrel and kilowatt sent where it earns most.' },
  retail: { label: 'Retail & supply chain', tagline: 'Right stock, right shelf, less cash frozen in inventory.' },
  manufacturing: { label: 'Manufacturing', tagline: 'Product mix and schedules that squeeze margin from capacity.' },
  finance: { label: 'Banking & finance', tagline: 'Portfolios and credit policies at the best risk-adjusted return.' },
  airlines: { label: 'Airlines & travel', tagline: 'Fuel, crews, seats and rooms priced and planned to the cent.' },
  healthcare: { label: 'Healthcare', tagline: 'Scarce rooms, staff and stock allocated fairly and fully.' },
  public: { label: 'Public sector & cities', tagline: 'Budgets, transit and taxes designed for measurable outcomes.' },
  construction: { label: 'Construction & projects', tagline: 'Deadlines protected by knowing which task really matters.' },
  telecom: { label: 'Telecom & networks', tagline: 'Coverage and capacity built for the lowest capital outlay.' },
  agriculture: { label: 'Agriculture & food', tagline: 'Feed, land and harvests planned for cost and yield.' },
  forestry: { label: 'Forestry, paper & materials', tagline: 'Cut patterns that turn the same raw material into more revenue.' },
}

export const METHODS: Record<Method, { label: string; chapter: string; route?: string }> = {
  lp: { label: 'Linear programming', chapter: 'Ch. 2–4', route: '/linear-programming' },
  transportation: { label: 'Transportation model', chapter: 'Ch. 5', route: '/transportation' },
  assignment: { label: 'Assignment model', chapter: 'Ch. 5.4', route: '/transportation' },
  mst: { label: 'Minimal spanning tree', chapter: 'Ch. 6.2', route: '/network/spanning-tree' },
  'shortest-route': { label: 'Shortest route', chapter: 'Ch. 6.3', route: '/network/shortest-route' },
  'max-flow': { label: 'Maximal flow', chapter: 'Ch. 6.4', route: '/network/max-flow' },
  cpm: { label: 'CPM & PERT', chapter: 'Ch. 6.5', route: '/network/critical-path' },
  'min-cost-flow': { label: 'Min-cost network flow', chapter: 'Ch. 22.1', route: '/production' },
  integer: { label: 'Integer programming', chapter: 'Ch. 9', route: '/production' },
  tsp: { label: 'Traveling salesperson', chapter: 'Ch. 11' },
  dp: { label: 'Dynamic programming', chapter: 'Ch. 12' },
  inventory: { label: 'Inventory models', chapter: 'Ch. 13, 16' },
  decision: { label: 'Decision analysis & games', chapter: 'Ch. 15' },
  queuing: { label: 'Queuing systems', chapter: 'Ch. 18' },
  simulation: { label: 'Simulation', chapter: 'Ch. 19' },
  markov: { label: 'Markov chains', chapter: 'Ch. 17' },
  goal: { label: 'Goal programming', chapter: 'Ch. 8' },
  heuristics: { label: 'Heuristics & metaheuristics', chapter: 'Ch. 10' },
}

export const APPLICATIONS: Application[] = [
  // Documented real-life cases (Taha, 10th ed., chapter openers and Chapter 26)
  { id: 'kroger', title: 'Pharmacy inventory across 2,500 stores', problem: 'Balance drug shortages against excess stock in every pharmacy.', payoff: '+$80M revenue and −$120M inventory in 16 months.', industries: ['retail', 'healthcare'], methods: ['inventory', 'simulation'], companies: ['Kroger'], source: 'Ch. 13 · Case 13', documented: true },
  { id: 'weyerhaeuser', title: 'Crosscutting trees into logs', problem: 'Choose cut combinations of 100-ft trees that meet each mill’s log specs.', payoff: 'About $7M a year in added profit.', industries: ['forestry', 'manufacturing'], methods: ['dp'], companies: ['Weyerhaeuser'], source: 'Ch. 12 · Case 8', documented: true },
  { id: 'heart-valves', title: 'Heart valve production by size', problem: 'Raw porcine hearts arrive in random sizes; demand is for specific sizes.', payoff: 'Savings above $1.476M in the first year.', industries: ['healthcare', 'manufacturing'], methods: ['lp'], companies: ['Medtronic', 'Edwards Lifesciences', 'Abbott'], source: 'Ch. 3 · Case 2', documented: true },
  { id: 'dell', title: 'Supplier “revolver” inventory', problem: 'Set the parts stock suppliers must hold near assembly plants.', payoff: 'About $2.7M a year saved.', industries: ['retail', 'manufacturing'], methods: ['inventory'], companies: ['Dell'], source: 'Ch. 16 · Case 14', documented: true },
  { id: 'frontier', title: 'Fuel tankering on multi-leg flights', problem: 'Load extra fuel where it is cheap without burning the savings on extra weight.', payoff: 'About $350,000 a year net savings.', industries: ['airlines', 'energy'], methods: ['lp', 'heuristics'], companies: ['Frontier Airlines', 'Emirates', 'flydubai'], source: 'Ch. 2 · Case 1', documented: true },
  { id: 'qantas', title: 'Call-centre shift staffing', problem: 'Staff six overlapping shifts from 7:00 to 22:00 at target service levels.', payoff: 'Over A$200,000 a year saved.', industries: ['airlines'], methods: ['lp', 'queuing'], companies: ['Qantas'], source: 'Ch. 2, 18 · Case 17', documented: true },
  { id: 'federal-travel', title: 'Where to host a training event', problem: 'Pick the host city that minimises total attendee travel cost.', payoff: 'At least $400,000 saved in one fiscal year.', industries: ['public', 'airlines'], methods: ['transportation'], companies: ['U.S. federal agencies'], source: 'Ch. 6 · Case 4', documented: true },
  { id: 'dsto-tsp', title: 'Surveillance flight planning', problem: 'Sequence up to 20 radar swaths into one flight path.', payoff: 'Missions 15% shorter, planned in 20 s instead of 1 h.', industries: ['public', 'airlines'], methods: ['tsp'], companies: ['Australian DSTO'], source: 'Ch. 11', documented: true },
  { id: 'fedex', title: 'Pilot bid lines', problem: 'Build round trips for 3,000+ pilots and 500+ aircraft under FAA rules.', payoff: 'Fewer bid lines, i.e. lower required manning.', industries: ['airlines', 'logistics'], methods: ['heuristics'], companies: ['FedEx'], source: 'Ch. 10', documented: true },
  { id: 'pfg', title: 'Trailer payloads within axle limits', problem: 'Position glass packs on a trailer so every axle stays legal.', payoff: 'Fuller legal loads, fewer trips.', industries: ['logistics', 'manufacturing'], methods: ['integer'], companies: ['PFG Building Glass'], source: 'Ch. 9 · Case 7', documented: true },
  { id: 'thai-navy', title: 'Ship routing for 2,929 recruits', problem: 'Bus and ship draftees from 34 centres to the main base at minimum cost.', payoff: 'One schedule for buses, ships and base capacities.', industries: ['public', 'logistics'], methods: ['lp', 'integer'], companies: ['Royal Thai Navy'], source: 'Ch. 7 · Case 5', documented: true },
  { id: 'mount-sinai', title: 'Operating-room time allocation', problem: 'Split a fixed budget of OR hours fairly among surgical departments.', payoff: 'Equitable daily schedule from a fixed budget.', industries: ['healthcare'], methods: ['goal', 'integer'], companies: ['Mount Sinai Hospital'], source: 'Ch. 8 · Case 6', documented: true },
  { id: 'la-posada', title: 'Hotel booking limits', problem: 'Cap discounted rooms to keep space for late full-price business guests.', payoff: 'Higher revenue per available room.', industries: ['airlines'], methods: ['decision'], companies: ['Hilton', 'Marriott', 'Jumeirah'], source: 'Ch. 15 · Case 10', documented: true },
  { id: 'atc', title: 'Buyer–seller meetings at trade events', problem: 'Schedule 620 sellers and 700 buyers into limited time slots by preference.', payoff: 'Higher satisfaction for both sides.', industries: ['airlines', 'public'], methods: ['assignment'], companies: ['Australian Tourist Commission', 'Dubai World Trade Centre'], source: 'Ch. 5 · Case 3', documented: true },
  { id: 'internal-transport', title: 'In-plant transport fleet', problem: 'Size the internal transport system so production never waits.', payoff: 'Right fleet size, no idle machines.', industries: ['manufacturing', 'logistics'], methods: ['queuing'], companies: ['Toyota', 'Bosch'], source: 'Ch. 18 · Case 16', documented: true },
  { id: 'cim-layout', title: 'Facility layout planning', problem: 'Divide floor space among units with competing goals.', payoff: 'A compromise layout every stakeholder accepts.', industries: ['manufacturing', 'construction'], methods: ['decision', 'goal'], companies: ['Siemens', 'ABB'], source: 'Ch. 15 · Case 9', documented: true },
  { id: 'gangtok', title: 'Land-cover change forecast', problem: 'Predict how forest and urban land shift year to year.', payoff: 'Evidence for zoning before land is lost.', industries: ['public', 'forestry'], methods: ['markov'], companies: ['City planning authorities'], source: 'Ch. 17 · Case 15', documented: true },

  // Linear programming (Ch. 2–4)
  { id: 'bank-loans', title: 'Bank loan portfolio', problem: 'Split $12M among personal, car, home, farm and commercial loans with bad-debt limits.', payoff: 'Maximum net return within regulatory risk limits.', industries: ['finance'], methods: ['lp'], companies: ['Emirates NBD', 'HSBC', 'Sberbank'], source: 'Example 2.4-1' },
  { id: 'product-mix', title: 'Product mix from scarce resources', problem: 'Decide output of each product from limited materials and machine hours.', payoff: 'Highest profit per day, plus the value of one more resource unit.', industries: ['manufacturing'], methods: ['lp'], companies: ['Jotun', 'AkzoNobel', 'Henkel'], source: 'Example 2.1-1 · Reddy Mikks' },
  { id: 'single-period-production', title: 'Seasonal production with penalties', problem: 'Make firm orders across cutting, sewing and packing; shortfall is penalised.', payoff: 'Profit-maximising plan that knows which orders to short.', industries: ['manufacturing', 'retail'], methods: ['lp'], companies: ['Inditex', 'Columbia Sportswear'], source: 'Example 2.4-2' },
  { id: 'multi-period-production', title: 'Production–inventory over months', problem: 'Produce when it is cheap, store until needed.', payoff: 'Lowest combined production and holding cost.', industries: ['manufacturing'], methods: ['lp', 'transportation'], companies: ['Saint-Gobain', 'Emirates Glass'], source: 'Examples 2.4-3, 5.2-1' },
  { id: 'smoothing', title: 'Workforce smoothing', problem: 'Hire, fire or hold staff as monthly demand swings.', payoff: 'Stable workforce at minimum labour cost.', industries: ['manufacturing', 'retail'], methods: ['lp'], companies: ['Amazon', 'Noon'], source: 'Example 2.4-4' },
  { id: 'bus-scheduling', title: 'Minimum city bus fleet', problem: 'Cover demand in six 4-hour periods with 8-hour shifts.', payoff: 'Fewest buses for the same service.', industries: ['public', 'logistics'], methods: ['lp'], companies: ['RTA Dubai', 'Mosgortrans', 'TfL'], source: 'Example 2.4-5' },
  { id: 'urban-renewal', title: 'Urban redevelopment', problem: 'Choose the housing mix for a condemned district to grow the tax base.', payoff: 'Maximum tax revenue within budget and zoning.', industries: ['public', 'construction'], methods: ['lp'], companies: ['Emaar', 'Nakheel', 'city councils'], source: 'Example 2.4-6' },
  { id: 'refining', title: 'Crude refining and gasoline blending', problem: 'Blend 1.5M bbl/day into fuels that meet octane specs.', payoff: 'Maximum refinery margin on every barrel.', industries: ['energy'], methods: ['lp'], companies: ['ADNOC', 'Aramco', 'Lukoil', 'Shell'], source: 'Example 2.4-7' },
  { id: 'diet', title: 'Least-cost feed blend', problem: 'Mix corn and soybean meal to meet protein and fibre minimums.', payoff: 'Nutrition targets met at the lowest cost per tonne.', industries: ['agriculture'], methods: ['lp'], companies: ['Cargill', 'Almarai', 'Miratorg'], source: 'Example 2.2-2' },
  { id: 'shelf-space', title: 'Shelf-space allocation', problem: 'Give each product the shelf width its demand and margin justify.', payoff: 'Higher profit per metre of shelf.', industries: ['retail'], methods: ['lp'], companies: ['Carrefour', 'Lulu', 'X5 Group'], source: 'Problem 2-77' },
  { id: 'pollution', title: 'Emission-compliant coal blend', problem: 'Mix coals to power a plant within sulfur limits.', payoff: 'Compliance at the cheapest fuel cost.', industries: ['energy'], methods: ['lp'], companies: ['DEWA', 'Rosseti', 'EDF'], source: 'Problem 2-80' },
  { id: 'aircraft-routes', title: 'Aircraft allocated to routes', problem: 'Assign aircraft types to routes by capacity, cost and demand.', payoff: 'More passengers carried per fleet dollar.', industries: ['airlines'], methods: ['lp'], companies: ['Emirates', 'Aeroflot', 'Etihad'], source: 'Problem 2-87' },
  { id: 'sensitivity', title: 'What is one more machine-hour worth?', problem: 'Dual prices show the value of each scarce resource.', payoff: 'Invest only where the shadow price beats the cost.', industries: ['manufacturing', 'finance'], methods: ['lp'], companies: ['Any capacity-constrained plant'], source: 'Sections 3.6, 4.3' },

  // Transportation and assignment (Ch. 5)
  { id: 'car-distribution', title: 'Cars from plants to distribution centres', problem: 'Ship quarterly output from three plants to two centres.', payoff: 'Minimum total freight bill for the network.', industries: ['logistics', 'manufacturing'], methods: ['transportation'], companies: ['Toyota', 'Hyundai', 'AvtoVAZ'], source: 'Example 5.1-1 · MG Auto' },
  { id: 'grain', title: 'Grain from silos to mills', problem: 'Route truckloads from three silos to four mills.', payoff: 'Lowest cost plan, proven optimal by MODI.', industries: ['agriculture', 'logistics'], methods: ['transportation'], companies: ['Al Ghurair Foods', 'Cargill', 'Rusagro'], source: 'Example 5.3-1 · SunRay' },
  { id: 'power-plants', title: 'Electricity from plants to cities', problem: 'Meet city demand from plants with different prices and capacities.', payoff: 'Cheapest dispatch; shortages priced explicitly.', industries: ['energy'], methods: ['transportation'], companies: ['DEWA', 'Inter RAO', 'TAQA'], source: 'Problem 5-6' },
  { id: 'refineries', title: 'Fuel from refineries to regions', problem: 'Supply daily demand by pipeline from three refineries.', payoff: 'Minimum distribution cost per million gallons.', industries: ['energy', 'logistics'], methods: ['transportation'], companies: ['ENOC', 'Gazprom Neft', 'ADNOC Distribution'], source: 'Problems 5-8 to 5-10' },
  { id: 'tool-sharpening', title: 'Blade sharpening vs buying new', problem: 'Choose daily between new blades, overnight or slow sharpening.', payoff: 'Minimum weekly tooling cost.', industries: ['manufacturing', 'forestry'], methods: ['transportation'], companies: ['Sawmills', 'Ilim Group'], source: 'Example 5.2-2' },
  { id: 'equipment-allocation', title: 'Machines assigned to task types', problem: 'Allocate machine categories to tasks by productivity.', payoff: 'Maximum output from the existing fleet.', industries: ['manufacturing', 'construction'], methods: ['transportation'], companies: ['Caterpillar rental fleets', 'Arabtec'], source: 'Problem 5-17' },
  { id: 'assignment', title: 'People assigned to jobs', problem: 'Match each worker to one job at the least total cost or time.', payoff: 'Optimal match found by the Hungarian method.', industries: ['logistics', 'manufacturing', 'healthcare'], methods: ['assignment'], companies: ['Aramex', 'Talabat', 'Yandex.Taxi'], companiesBy: { healthcare: ['Mediclinic', 'Cleveland Clinic Abu Dhabi'], manufacturing: ['Shift planners in any plant'] }, source: 'Examples 5.4-1, 5.4-2' },
  { id: 'perishable', title: 'Perishable supply across months', problem: 'Match monthly supply to demand when goods spoil.', payoff: 'Less waste at the same service level.', industries: ['agriculture', 'retail'], methods: ['transportation'], companies: ['Almarai', 'Danone'], source: 'Problem 5-18' },

  // Network models (Ch. 6, 22)
  { id: 'gas-pipeline', title: 'Offshore gas pipeline design', problem: 'Connect wellheads to the shore with the least pipe.', payoff: 'Minimum construction cost of the network.', industries: ['energy', 'construction'], methods: ['mst'], companies: ['ADNOC Offshore', 'Gazprom', 'Dragon Oil'], companiesBy: { construction: ['NPCC', 'McDermott', 'Saipem'] }, source: 'Section 6.1 · Problem 6-10' },
  { id: 'cable-tv', title: 'Cable and fibre roll-out', problem: 'Link new housing developments with the fewest cable miles.', payoff: 'Lowest capital cost for full coverage.', industries: ['telecom', 'construction'], methods: ['mst'], companies: ['du', 'e&', 'Rostelecom'], companiesBy: { construction: ['Emaar Communities', 'utility contractors'] }, source: 'Example 6.2-1' },
  { id: 'road-route', title: 'Shortest road route', problem: 'Find the shortest path between cities on a road network.', payoff: 'Less fuel and driver time on every trip.', industries: ['logistics'], methods: ['shortest-route'], companies: ['Aramex', 'DHL', 'CDEK'], source: 'Examples 6.3-4, 6.3-5' },
  { id: 'equipment-replacement', title: 'When to replace equipment', problem: 'Keep or replace each car over a 4-year horizon.', payoff: 'Lowest total cost of fleet ownership.', industries: ['logistics', 'manufacturing'], methods: ['shortest-route', 'dp'], companies: ['Hertz', 'Ekar', 'Delimobil'], source: 'Example 6.3-1' },
  { id: 'reliable-route', title: 'Most reliable route', problem: 'Choose the path with the highest probability of no failure.', payoff: 'Fewer disruptions in critical deliveries and links.', industries: ['telecom', 'logistics'], methods: ['shortest-route'], companies: ['Etisalat', 'MegaFon', 'DP World'], source: 'Example 6.3-2 · Problem 6-14' },
  { id: 'three-jug', title: 'Fewest steps to a target state', problem: 'Divide 8 gallons into two equal parts using 8-, 5- and 3-gallon jugs.', payoff: 'Any “minimum moves” process modelled as a shortest path.', industries: ['manufacturing'], methods: ['shortest-route'], companies: ['Process engineering teams'], source: 'Example 6.3-3' },
  { id: 'coal-slurry', title: 'Coal slurry pipeline capacity', problem: 'Maximum tons per year from mines to power plants.', payoff: 'Know the bottleneck before investing in pipe.', industries: ['energy'], methods: ['max-flow'], companies: ['SUEK', 'Peabody'], source: 'Section 6.1' },
  { id: 'oil-pipeline-capacity', title: 'Crude pipeline throughput', problem: 'Maximum barrels per day through pumps and pipe segments.', payoff: 'Throughput gains without new pipe where possible.', industries: ['energy'], methods: ['max-flow'], companies: ['Transneft', 'ADNOC', 'Aramco'], source: 'Problems 6-31, 6-32' },
  { id: 'feed-trucks', title: 'Route-capacity–limited deliveries', problem: 'Ship feed from silos to farms when each route has a cap.', payoff: 'Maximum delivered volume on the existing network.', industries: ['agriculture', 'logistics'], methods: ['max-flow'], companies: ['Cherkizovo', 'Al Rawabi'], source: 'Problems 6-33, 6-34' },
  { id: 'oil-min-cost', title: 'Oil fields to refineries at minimum cost', problem: 'Schedule flow through a capacitated pipeline network.', payoff: 'Lowest flow cost respecting every pipe limit.', industries: ['energy', 'logistics'], methods: ['min-cost-flow'], companies: ['ADNOC', 'Rosneft', 'Aramco'], source: 'Section 6.1 · Ch. 22.1' },
  { id: 'freight-hubs', title: 'Freight through hubs with a truck mix', problem: 'Route demands direct or via hubs, choose trucks and schedule terminals.', payoff: 'Proven minimum total cost; 4.1% below direct-only in the Dubai case.', industries: ['logistics'], methods: ['min-cost-flow', 'integer'], companies: ['RTA Dubai', 'Nova Poshta', 'DP World', 'Aramex'], source: 'Ch. 6, 9, 22 · this portal' },
  { id: 'construction', title: 'Construction project schedule', problem: 'Start and finish dates for every activity with precedence.', payoff: 'Know the critical path; use float instead of overtime.', industries: ['construction'], methods: ['cpm'], companies: ['Emaar', 'ALEC', 'PIK Group'], source: 'Section 6.1 · Problems 6-44, 6-47' },
  { id: 'book-production', title: 'Product launch timeline', problem: 'Coordinate editing, design and printing to a launch date.', payoff: 'Realistic launch date with risk from PERT.', industries: ['manufacturing', 'retail'], methods: ['cpm'], companies: ['Pearson', 'Samsung', 'Yandex'], source: 'Example 6.5-1 · Problem 6-48' },
  { id: 'utility-relocation', title: 'Utility line relocation', problem: 'Plan the reconductoring of 1,700 ft of 13.8 kV line during road works.', payoff: 'Shortest outage window.', industries: ['energy', 'construction'], methods: ['cpm'], companies: ['DEWA', 'Rosseti'], source: 'Problem 6-50' },

  { id: 'trim-loss', title: 'Paper-roll trim loss', problem: 'Cut standard jumbo rolls into ordered widths with the least waste.', payoff: 'Less trim waste per tonne of paper sold.', industries: ['forestry', 'manufacturing'], methods: ['lp'], companies: ['International Paper', 'Ilim Group', 'Mondi'], source: 'Classic LP application (earlier editions)' },
  { id: 'wallpaper', title: 'Pattern-matched sheet cutting', problem: 'Order cuts from one patterned roll so alignment waste is minimal.', payoff: 'Less material lost to pattern matching.', industries: ['forestry', 'retail'], methods: ['tsp'], companies: ['Decor and flooring producers'], source: 'Problem 11-13' },

  // Integer programming, TSP, heuristics (Ch. 9–11)
  { id: 'capital-budgeting', title: 'Capital budgeting', problem: 'Pick projects within yearly budgets to maximise return.', payoff: 'The best portfolio, not a rounded LP guess.', industries: ['finance', 'energy'], methods: ['integer'], companies: ['Mubadala', 'PIF', 'VTB Capital'], source: 'Example 9.1-1' },
  { id: 'set-covering', title: 'Fewest sites for full coverage', problem: 'Place phones, towers or stations so every street is covered.', payoff: 'Full coverage with the minimum number of sites.', industries: ['telecom', 'public'], methods: ['integer'], companies: ['du', 'MTS', 'Beeline'], companiesBy: { public: ['Dubai Police', 'Civil Defence', 'ambulance services'] }, source: 'Example 9.1-2 · Problem 9-25' },
  { id: 'fixed-charge', title: 'Supplier choice with fixed fees', problem: 'Choose providers that charge a flat fee plus a usage rate.', payoff: 'Lowest total bill, fees included.', industries: ['telecom', 'finance'], methods: ['integer'], companies: ['Procurement teams'], source: 'Example 9.1-3' },
  { id: 'job-sequencing', title: 'Job sequencing on a machine', problem: 'Order jobs to minimise holding cost and late penalties.', payoff: 'Fewer late orders, less work in progress.', industries: ['manufacturing'], methods: ['integer', 'heuristics'], companies: ['Emirates Global Aluminium', 'KAMAZ'], source: 'Examples 9.1-4, 10.3-2' },
  { id: 'tsp-delivery', title: 'Delivery and service tours', problem: 'Visit every stop once and return on the shortest tour.', payoff: 'Shorter routes; more stops per vehicle per day.', industries: ['logistics', 'retail'], methods: ['tsp'], companies: ['Talabat', 'Amazon', 'Ozon'], source: 'Ch. 11' },

  // Dynamic programming (Ch. 12)
  { id: 'cargo-loading', title: 'Cargo loading (knapsack)', problem: 'Choose items for a vessel or aircraft with weight limits.', payoff: 'Most valuable load per trip.', industries: ['logistics', 'airlines'], methods: ['dp', 'integer'], companies: ['Emirates SkyCargo', 'Maersk', 'Volga-Dnepr'], source: 'Section 12.3.1' },
  { id: 'workforce-size', title: 'Workforce sizing by week', problem: 'Balance over-staffing against hiring cost as needs change.', payoff: 'Minimum labour cost across the season.', industries: ['construction', 'retail'], methods: ['dp'], companies: ['ALEC', 'Majid Al Futtaim'], source: 'Section 12.3.2' },
  { id: 'investment-dp', title: 'Multi-year reinvestment', problem: 'Decide how much to reinvest each year at varying rates.', payoff: 'Maximum capital at the end of the horizon.', industries: ['finance'], methods: ['dp'], companies: ['Asset managers', 'family offices'], source: 'Section 12.3.4' },

  // Inventory (Ch. 13, 16)
  { id: 'eoq', title: 'How much and when to order', problem: 'Trade order setup cost against holding cost.', payoff: 'Lower total inventory cost with the same service.', industries: ['retail', 'manufacturing'], methods: ['inventory'], companies: ['Carrefour', 'IKEA', 'Magnit'], source: 'Section 13.3' },
  { id: 'price-breaks', title: 'Quantity discounts', problem: 'Is the supplier’s volume discount worth the extra stock?', payoff: 'Take discounts only when they pay.', industries: ['retail', 'manufacturing'], methods: ['inventory'], companies: ['Procurement teams'], source: 'Section 13.3.2' },
  { id: 'newsvendor', title: 'Single-season order (newsvendor)', problem: 'Order perishable or seasonal stock before demand is known.', payoff: 'Optimal balance of lost sales and leftovers.', industries: ['retail', 'agriculture'], methods: ['inventory'], companies: ['Zara', 'Spinneys', 'VkusVill'], source: 'Section 16.2' },

  // Decision analysis, Markov, queues, simulation (Ch. 15–19)
  { id: 'plant-size', title: 'Build big now or expand later', problem: 'Plant size under uncertain demand over 10 years.', payoff: 'Highest expected value with risk made explicit.', industries: ['manufacturing', 'energy'], methods: ['decision'], companies: ['EGA', 'Novatek'], source: 'Section 15.2' },
  { id: 'ahp', title: 'Choosing among strategic options', problem: 'Rank sites, suppliers or hires on weighted criteria.', payoff: 'A transparent, defensible decision.', industries: ['public', 'finance'], methods: ['decision'], companies: ['Boards and procurement'], source: 'Section 15.1' },
  { id: 'games', title: 'Competitive strategy', problem: 'Best response when a competitor reacts to your move.', payoff: 'Guaranteed value regardless of the rival’s choice.', industries: ['retail', 'telecom'], methods: ['decision'], companies: ['Telecom operators', 'retail chains'], source: 'Section 15.4' },
  { id: 'maintenance-markov', title: 'Preventive maintenance policy', problem: 'Machine states drift from excellent to fair month to month.', payoff: 'Maintenance spend matched to long-run condition.', industries: ['manufacturing', 'energy'], methods: ['markov'], companies: ['Siemens Energy', 'Rosatom'], source: 'Example 17.1-1' },
  { id: 'queues', title: 'How many servers to open', problem: 'Counters, docks, tellers or agents against waiting cost.', payoff: 'Lowest total cost of service plus waiting.', industries: ['retail', 'finance', 'logistics', 'healthcare'], methods: ['queuing'], companies: ['Lulu', 'Carrefour'], companiesBy: { finance: ['Emirates NBD', 'Sberbank'], logistics: ['DP World', 'Jebel Ali Port'], healthcare: ['Dubai Health', 'NMC'] }, source: 'Section 18.9' },
  { id: 'simulation', title: 'Test a process before building it', problem: 'Simulate arrivals and service to compare designs.', payoff: 'Investment decisions backed by evidence.', industries: ['logistics', 'healthcare', 'manufacturing'], methods: ['simulation'], companies: ['Dubai Airports', 'DHL'], companiesBy: { healthcare: ['SEHA', 'emergency departments'], manufacturing: ['Tesla', 'Toyota'] }, source: 'Ch. 19' },
  { id: 'tax-planning', title: 'Municipal tax planning', problem: 'Set tax rates to hit revenue goals with fairness constraints.', payoff: 'Smallest deviation from every policy goal.', industries: ['public'], methods: ['goal'], companies: ['City councils'], source: 'Example 8.1-1' },
  { id: 'advertising', title: 'Media budget across channels', problem: 'Split radio and TV spend to reach exposure and budget goals.', payoff: 'Maximum reach per dollar.', industries: ['retail', 'telecom'], methods: ['goal'], companies: ['Publicis', 'Noon'], source: 'Examples 8.2-1 to 8.2-3' },
]

export const PAGE_METHODS: Record<string, Method[]> = {
  '/transportation': ['transportation', 'assignment'],
  '/linear-programming': ['lp'],
  '/network/spanning-tree': ['mst'],
  '/network/shortest-route': ['shortest-route'],
  '/network/max-flow': ['max-flow'],
  '/network/critical-path': ['cpm'],
  '/production': ['min-cost-flow', 'integer'],
}

export const STUDY_METHODS: Method[] = ['transportation', 'assignment', 'lp', 'mst', 'shortest-route', 'max-flow', 'cpm']

export function byMethod(...methods: Method[]) {
  return APPLICATIONS.filter(app => app.methods.some(m => methods.includes(m)))
}

export function byIndustry(industry: Industry) {
  const apps = APPLICATIONS.filter(app => app.industries.includes(industry))
  return [...apps.filter(app => app.industries[0] === industry), ...apps.filter(app => app.industries[0] !== industry)]
}

export function companiesFor(app: Application, industry?: Industry | null) {
  return (industry && app.companiesBy?.[industry]) || app.companies
}

export function methodsForPath(path: string): Method[] {
  if (path === '/network') return PAGE_METHODS['/network/spanning-tree']
  return PAGE_METHODS[path] ?? []
}

export function liveRoute(app: Application) {
  return app.methods.map(m => METHODS[m].route).find(Boolean)
}

export const DOCUMENTED_RESULTS = [
  { value: 80, prefix: '+$', suffix: 'M', label: 'revenue', who: 'Kroger · inventory' },
  { value: 120, prefix: '−$', suffix: 'M', label: 'inventory', who: 'Kroger · inventory' },
  { value: 7, prefix: '$', suffix: 'M/yr', label: 'profit', who: 'Weyerhaeuser · DP' },
  { value: 2.7, prefix: '$', suffix: 'M/yr', label: 'saved', who: 'Dell · supply chain' },
  { value: 1.48, prefix: '$', suffix: 'M', label: 'saved', who: 'Heart valves · LP' },
  { value: 15, prefix: '−', suffix: '%', label: 'route length', who: 'DSTO · TSP' },
]
